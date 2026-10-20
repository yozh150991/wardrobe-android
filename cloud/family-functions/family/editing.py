"""Metadata edits on an immutable family snapshot; durable UUID replay and CAS."""
import hashlib
import io
import json
import zipfile
from .archive import validate_archive, MAX_ARCHIVE
from .errors import require
from .schema import valid_id, integer, text_length

EDIT_LIMITS = {'name': 120, 'size': 40, 'location': 200, 'description': 1000}


def validate_edit(data):
    require(valid_id(data['editId']) and valid_id(data['garmentId']) and integer(data['baseRevision'], 1))
    changes = data['changes']
    require(type(changes) is dict and 0 < len(changes) <= 4 and set(changes) <= set(EDIT_LIMITS), 'Invalid edit fields')
    for key, value in changes.items():
        require(type(value) is str and text_length(value) <= EDIT_LIMITS[key], 'Edit text limit')
        require(not any(ord(c) < 32 and c not in '\n\t' for c in value), 'Invalid edit text')
        if key == 'name': require(bool(value.strip()), 'Name is required')


def patch_archive(source, root, data):
    head = root['head']
    validate_archive(source, root, head['baseRevision'], head['sha256'])
    with zipfile.ZipFile(io.BytesIO(source)) as archive:
        envelope = json.loads(archive.read('manifest.json').decode('utf-8'))
        garment = next((g for g in envelope['snapshot']['garments'] if g['id'] == data['garmentId']), None)
        require(garment is not None, 'Item no longer exists', 'failed-precondition', 'EDIT_ITEM_MISSING')
        garment.update(data['changes']); envelope['baseRevision'] = data['baseRevision']
        output = io.BytesIO()
        # Deterministic bytes across retries, including timestamps and ZIP metadata.
        with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as dest:
            for name in sorted(archive.namelist()):
                content = (json.dumps(envelope, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')
                           if name == 'manifest.json' else archive.read(name))
                info = zipfile.ZipInfo(name, date_time=(1980,1,1,0,0,0)); info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100600 << 16
                dest.writestr(info, content)
    result = output.getvalue(); require(len(result) <= MAX_ARCHIVE, 'Archive size limit', 'resource-exhausted')
    checksum = hashlib.sha256(result).hexdigest()
    counts = validate_archive(result, root, data['baseRevision'], checksum)
    return result, checksum, counts


class EditingService:
    def edit_item(self, actor, data):
        from .service import authenticate, fields, root_path, access, MAX_UPLOADS_PER_DAY
        authenticate(actor); fields(data, 'remoteId editId garmentId baseRevision changes'); validate_edit(data)
        path = root_path(data['remoteId']); edit_path = f"{path}/edits/{data['editId']}"
        # Copy request data; transaction retries must not observe mutable caller input.
        data = json.loads(json.dumps(data))
        binding = data | {'authorUid': actor.uid}
        def prepare(tx):
            now = self.clock(); root, _ = access(tx, path, actor, now, edit=True)
            existing = tx.get(edit_path)
            if existing:
                require(existing['request'] == binding, 'Edit ID conflict', 'already-exists')
                if existing['status'] == 'committed': return root, existing
            require(root['head'] and root['revision'] == data['baseRevision'], 'Revision conflict', 'aborted', 'EDIT_CONFLICT')
            if existing: return root, existing
            quota_path = f'familyQuotas/{actor.uid}'
            quota = tx.get(quota_path) or {'roots':0, 'day':0, 'uploads':0}
            count = quota['uploads'] if quota['day'] == now // 86400000 else 0
            require(count < MAX_UPLOADS_PER_DAY, 'Daily upload limit', 'resource-exhausted')
            record = {'request':binding, 'status':'pending'}
            tx.set(edit_path, record); tx.set(quota_path, quota | {'day':now // 86400000, 'uploads':count+1})
            return root, record
        root, record = self.store.run(prepare)
        if record['status'] == 'committed': return record['result']
        source = self.archives.read(root['head']['archive'], root['head']['size'])
        payload, checksum, counts = patch_archive(source, root, data)
        final = f"{path}/snapshots/{data['editId']}.zip"
        generation = self.archives.put_once(final, payload, checksum)
        def publish(tx):
            current, _ = access(tx, path, actor, self.clock(), edit=True)
            latest = tx.get(edit_path)
            require(latest and latest['request'] == binding, 'Edit changed', 'aborted')
            if latest['status'] == 'committed': return latest['result']
            require(current['revision'] == data['baseRevision'] and current['head'] == root['head'],
                    'Revision conflict', 'aborted', 'EDIT_CONFLICT')
            revision = current['revision'] + 1; require(integer(revision), 'Revision exhausted', 'failed-precondition')
            head = dict(revision=revision, baseRevision=data['baseRevision'], archiveId=data['editId'], archive=final,
                        sha256=checksum, size=len(payload), generation=str(generation), authorUid=actor.uid,
                        committedAt=self.clock(), counts=counts)
            result = {'remoteId':data['remoteId'], 'editId':data['editId'], 'revision':revision, 'status':'committed'}
            tx.set(path, current | {'head':head, 'revision':revision})
            tx.set(edit_path, latest | {'status':'committed', 'result':result})
            return result
        return self.store.run(publish)
