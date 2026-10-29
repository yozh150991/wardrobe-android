"""Small shared changes applied by the server to the current head (delta model v1).

The client uploads only the new photo, as a one-entry ZIP, plus a JSON description of the change. The server
applies it to whatever head is current, validates the complete result and publishes it. No client base revision
is involved, so an addition or a photo replacement does not conflict with other members' commits; a concurrent
commit between read and publish only makes the server apply the change again to the newer head.

Change JSON (bound to the change ID on first apply; a replay must send the identical text):
  {"kind":"add","garment":{row},"customCategory":{row}|null,"customColor":{row}|null,"photo":{"id":ID,"ext":"jpg"|"png"}}
  {"kind":"photo","garmentId":ID,"fingerprint":""|HASH,"photo":{"id":ID,"ext":"jpg"|"png"}}
  {"kind":"outfit","outfit":{row},"parts":[rows],"ideas":[rows],"previous":null|{"outfit":{row},"parts":[rows],"ideas":[rows]}}

An outfit change uploads nothing: apply_change creates its request record itself. It carries the outfit's own rows as
the phone resolved them, and for an edit the rows the editor started from. Only a change to that same outfit by someone
else in the meantime is a conflict (CHANGE_OUTFIT_CHANGED); commits to other outfits or items are not.
"""
import copy
import hashlib
import io
import json
import uuid
import zipfile
from .archive import MAX_ARCHIVE, MAX_PHOTO, validate_archive
from .errors import FamilyError, require
from .schema import HASH, integer, label_key, row_shape, strict_json, valid_id

FEATURES = ['change1', 'outfit1']
MAX_CHANGE_ARCHIVE = MAX_PHOTO + 64 * 1024
MAX_CHANGE_JSON = 64 * 1024
APPLY_ATTEMPTS = 3
LABELS = (('customCategory', 'customCategories', 'customCategoryId', 'baseCategory', 'category'),
          ('customColor', 'customColors', 'customColorId', 'baseColor', 'color'))


def referenced_photos(snapshot):
    """Exactly the set validate_snapshot requires in photoFiles."""
    photos = set()
    for kind in ('garments', 'wishes', 'ideas', 'wishEvents'):
        for row in snapshot[kind]:
            for field in ('photoId', 'needPhotoId'):
                if row.get(field):
                    photos.add(row[field])
    for undo in snapshot['purchaseUndos']:
        garment = strict_json(undo['garmentJson'])
        if type(garment) is dict and garment.get('photoId'):
            photos.add(garment['photoId'])
    return photos


def outfit_rows(rows):
    """{"outfit","parts","ideas"} of one outfit, shaped exactly like manifest rows."""
    require(type(rows) is dict and set(rows) == {'outfit', 'parts', 'ideas'}, 'Invalid outfit change')
    row_shape('outfits', rows['outfit']); oid = rows['outfit']['id']
    require(type(rows['parts']) is list and type(rows['ideas']) is list and 1 <= len(rows['parts']) + len(rows['ideas']) <= 12,
            'Invalid outfit change')
    for kind in ('parts', 'ideas'):
        for row in rows[kind]:
            row_shape(kind, row); require(row['outfitId'] == oid, 'Invalid outfit change')
    require(len({p['garmentId'] for p in rows['parts']}) == len(rows['parts']) and
            len({i['partId'] for i in rows['ideas']}) == len(rows['ideas']), 'Invalid outfit change')
    signature = '|'.join(sorted([p['garmentId'] for p in rows['parts']] + [i['partId'] for i in rows['ideas']]))
    require(rows['outfit']['signature'] == signature, 'Invalid outfit change')
    return oid


def same_outfit(a, b):
    """Row-for-row equality; the order of parts and ideas carries no meaning."""
    def key(rows):
        return (rows['outfit'], sorted(p['garmentId'] for p in rows['parts']),
                sorted(json.dumps(i, sort_keys=True) for i in rows['ideas']))
    return key(a) == key(b)


def current_outfit(snapshot, oid):
    outfit = next((o for o in snapshot['outfits'] if o['id'] == oid), None)
    if outfit is None:
        return None
    return {'outfit': outfit, 'parts': [p for p in snapshot['parts'] if p['outfitId'] == oid],
            'ideas': [i for i in snapshot['ideas'] if i['outfitId'] == oid]}


def parse_change(text):
    require(type(text) is str and 0 < len(text) <= MAX_CHANGE_JSON, 'Invalid change')
    change = strict_json(text)
    require(type(change) is dict and change.get('kind') in ('add', 'photo', 'outfit'), 'Invalid change')
    keys = {'add': {'kind', 'garment', 'customCategory', 'customColor', 'photo'},
            'photo': {'kind', 'garmentId', 'fingerprint', 'photo'},
            'outfit': {'kind', 'outfit', 'parts', 'ideas', 'previous'}}[change['kind']]
    require(set(change) == keys, 'Unexpected change fields')
    if change['kind'] == 'outfit':
        oid = outfit_rows({k: change[k] for k in ('outfit', 'parts', 'ideas')})
        previous = change['previous']
        if previous is not None:
            require(outfit_rows(previous) == oid, 'Invalid outfit change')
            require(previous['outfit']['createdAt'] == change['outfit']['createdAt'], 'Invalid outfit change')
        return change
    photo = change['photo']
    require(type(photo) is dict and set(photo) == {'id', 'ext'} and valid_id(photo['id']) and photo['ext'] in ('jpg', 'png'),
            'Invalid change photo')
    if change['kind'] == 'add':
        row_shape('garments', change['garment'])
        require(change['garment']['photoId'] == photo['id'], 'Invalid change photo')
        for field, kind, *_ in LABELS:
            if change[field] is not None:
                row_shape(kind, change[field])
    else:
        require(valid_id(change['garmentId']) and type(change['fingerprint']) is str and
                (change['fingerprint'] == '' or HASH.fullmatch(change['fingerprint'])), 'Invalid change')
    return change


def change_photo(data, photo):
    """The staged ZIP holds exactly the new photo; the full archive validation decodes it later."""
    name = f"photos/{photo['id']}.{photo['ext']}"
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            entries = archive.infolist()
            require(len(entries) == 1 and entries[0].filename == name and entries[0].orig_filename == name, 'Unexpected change files')
            entry = entries[0]
            require(not entry.is_dir() and not entry.flag_bits & 1 and entry.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED))
            require(0 < entry.file_size <= MAX_PHOTO, 'Photo size limit')
            return name, archive.read(name)
    except FamilyError:
        raise
    except (ValueError, KeyError, OSError, RuntimeError, zipfile.BadZipFile) as exc:
        raise FamilyError('invalid-argument', 'Invalid change archive') from exc


def apply_outfit(s, change, wardrobe_id):
    outfit = copy.deepcopy(change['outfit']); oid = outfit['id']
    parts = copy.deepcopy(change['parts']); ideas = copy.deepcopy(change['ideas'])
    require(outfit['wardrobeId'] == wardrobe_id, 'Cross-wardrobe record')
    current = current_outfit(s, oid)
    if change['previous'] is None:
        require(current is None, 'Outfit ID collision', 'already-exists')
    elif current is None:
        raise FamilyError('failed-precondition', 'Outfit no longer exists', 'CHANGE_ITEM_MISSING')
    elif not same_outfit(current, change['previous']):
        raise FamilyError('failed-precondition', 'Outfit changed by someone else', 'CHANGE_OUTFIT_CHANGED')
    garments = {g['id'] for g in s['garments'] if g['wardrobeId'] == wardrobe_id}
    wishes = {w['id']: w for w in s['wishes'] if w['wardrobeId'] == wardrobe_id}
    for p in parts:
        if p['garmentId'] not in garments:
            raise FamilyError('failed-precondition', 'Item no longer exists', 'CHANGE_ITEM_MISSING')
    for i in ideas:
        if i['wishId'] is not None:
            wish = wishes.get(i['wishId'])
            if wish is None or wish['ownedId'] or wish['photoId'] != i['photoId']:
                raise FamilyError('failed-precondition', 'Wish changed or no longer exists', 'CHANGE_ITEM_MISSING')
        else:
            require(i['photoId'] == '', 'Invalid outfit change')
    signature = outfit['signature']  # checked against the rows by parse_change
    # Same rule as the phone: one outfit per combination in a wardrobe.
    if any(o['id'] != oid and o['wardrobeId'] == wardrobe_id and o['signature'] == signature for o in s['outfits']):
        raise FamilyError('failed-precondition', 'The same outfit exists', 'CHANGE_OUTFIT_DUPLICATE')
    s['outfits'] = [o for o in s['outfits'] if o['id'] != oid] + [outfit]
    s['parts'] = [p for p in s['parts'] if p['outfitId'] != oid] + parts
    s['ideas'] = [i for i in s['ideas'] if i['outfitId'] != oid] + ideas
    # Same as FamilyOutfitChange.apply: only capsules linked to this outfit are reconciled; manual extras
    # and packed flags of items that stay are kept. Wears and plans describe the past and are never touched.
    affected = {c['capsuleId'] for c in s['capsuleOutfits'] if c['outfitId'] == oid}
    packing = [r for r in s['capsuleItems'] if r['capsuleId'] not in affected]
    for capsule in sorted(affected):
        old = [r for r in s['capsuleItems'] if r['capsuleId'] == capsule]
        links = {c['outfitId'] for c in s['capsuleOutfits'] if c['capsuleId'] == capsule}
        needed = [p['garmentId'] for p in s['parts'] if p['outfitId'] in links] + [r['garmentId'] for r in old if r['manual']]
        for garment in dict.fromkeys(needed):
            packing.append(next((r for r in old if r['garmentId'] == garment),
                                {'capsuleId': capsule, 'garmentId': garment, 'manual': False, 'packed': False}))
    s['capsuleItems'] = packing
    referenced = referenced_photos(s)
    dropped = {f"photos/{p}.{s['photoFiles'][p]}" for p in s['photoFiles'] if p not in referenced}
    s['photoFiles'] = {p: e for p, e in s['photoFiles'].items() if p in referenced}
    return s, dropped


def apply_to_snapshot(snapshot, change, wardrobe_id):
    """Returns (changed snapshot, ZIP entries no longer referenced). Never mutates the input."""
    s = copy.deepcopy(snapshot)
    if change['kind'] == 'outfit':
        return apply_outfit(s, change, wardrobe_id)
    photo = change['photo']
    require(photo['id'] not in s['photoFiles'], 'Photo ID collision', 'already-exists')
    if change['kind'] == 'add':
        garment = copy.deepcopy(change['garment'])
        require(garment['wardrobeId'] == wardrobe_id, 'Cross-wardrobe record')
        require(all(g['id'] != garment['id'] for g in s['garments']), 'Item ID collision', 'already-exists')
        for field, kind, reference, base, own in LABELS:
            label = change[field]
            if label is None:
                continue
            require(label['wardrobeId'] == wardrobe_id and garment[reference] == label['id'] and label[base] == garment[own],
                    'Invalid custom label')
            # Same rule as the phone's FamilyAddition.merge: an existing label with the same name is reused.
            existing = next((r for r in s[kind] if label_key(r['nameKey']) == label_key(label['nameKey'])), None)
            if existing is not None:
                require(existing[base] == garment[own], 'Custom label conflict', 'failed-precondition', 'CHANGE_LABEL_CONFLICT')
                garment[reference] = existing['id']
            else:
                require(all(r['id'] != label['id'] for r in s[kind]), 'Label ID collision', 'already-exists')
                s[kind].append(label)
        s['garments'].append(garment)
        s['photoFiles'][photo['id']] = photo['ext']
        return s, set()
    garment = next((g for g in s['garments'] if g['id'] == change['garmentId']), None)
    if garment is None:
        raise FamilyError('failed-precondition', 'Item no longer exists', 'CHANGE_ITEM_MISSING')
    old = garment['photoId']
    garment.update(photoId=photo['id'], photoFingerprint=change['fingerprint'])
    s['photoFiles'][photo['id']] = photo['ext']
    dropped = set()
    if old not in referenced_photos(s):
        dropped.add(f"photos/{old}.{s['photoFiles'].pop(old)}")
    return s, dropped


def changed_archive(source, root, change, photo_name, photo_bytes):
    try:
        with zipfile.ZipFile(io.BytesIO(source)) as archive:
            envelope = strict_json(archive.read('manifest.json').decode('utf-8'))
            snapshot, dropped = apply_to_snapshot(envelope['snapshot'], change, root['wardrobeId'])
            envelope = envelope | {'snapshot': snapshot, 'baseRevision': root['revision']}
            output = io.BytesIO()
            with zipfile.ZipFile(output, 'w') as dest:
                for name in sorted((set(archive.namelist()) - dropped) | ({photo_name} if photo_name else set())):
                    manifest = name == 'manifest.json'
                    content = (json.dumps(envelope, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')
                               if manifest else photo_bytes if name == photo_name else archive.read(name))
                    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED if manifest else zipfile.ZIP_STORED
                    info.external_attr = 0o100600 << 16
                    dest.writestr(info, content, compresslevel=6 if manifest else None)
    except FamilyError:
        raise
    except (ValueError, KeyError, TypeError, AttributeError, OSError, zipfile.BadZipFile) as exc:
        raise FamilyError('invalid-argument', 'Invalid family archive') from exc
    result = output.getvalue()
    require(len(result) <= MAX_ARCHIVE, 'Archive size limit', 'resource-exhausted')
    checksum = hashlib.sha256(result).hexdigest()
    return result, checksum, validate_archive(result, root, root['revision'], checksum)


def ticket(record):
    return {k: record[k] for k in ('archiveId', 'sha256', 'size', 'authorUid', 'status', 'expiresAt', 'stagingPath', 'baseRevision')}


class ApplyingService:
    def begin_change(self, actor, data):
        from .service import (authenticate, fields, root_path, access, UPLOAD_TTL_MS, MAX_UPLOADS_PER_DAY,
                              STAGING_PURGE_AGE_MS)
        authenticate(actor); fields(data, 'remoteId changeId sha256 size')
        path = root_path(data['remoteId'])
        require(valid_id(data['changeId']) and type(data['sha256']) is str and HASH.fullmatch(data['sha256'])
                and integer(data['size'], 1, MAX_CHANGE_ARCHIVE))
        upload_path = f"{path}/uploads/{data['changeId']}"
        binding = {'archiveId': data['changeId'], 'kind': 'change', 'sha256': data['sha256'], 'size': data['size'], 'authorUid': actor.uid}
        def operation(tx):
            now = self.clock()
            root, _ = access(tx, path, actor, now, edit=True)
            require(root['head'] is not None, 'Shared wardrobe has no snapshot', 'failed-precondition')
            existing = tx.get(upload_path)
            if existing:
                require(all(existing.get(k) == v for k, v in binding.items()), 'Upload ID conflict', 'already-exists')
                if existing['status'] == 'committed':
                    return existing
                # Storage rules accept the staged object only while baseRevision is the current revision.
                record = existing | {'baseRevision': root['revision'], 'expiresAt': now + UPLOAD_TTL_MS}
                tx.set(upload_path, record)
                return record
            quota_path = f'familyQuotas/{actor.uid}'
            quota = tx.get(quota_path) or {'roots': 0, 'day': 0, 'uploads': 0}
            day = now // 86400000
            count = quota['uploads'] if quota['day'] == day else 0
            require(count < MAX_UPLOADS_PER_DAY, 'Daily upload limit', 'resource-exhausted')
            record = binding | {'baseRevision': root['revision'], 'status': 'pending', 'expiresAt': now + UPLOAD_TTL_MS,
                                'stagingPath': f"{path}/staging/{actor.uid}/{data['changeId']}.zip"}
            tx.set(upload_path, record)
            tx.set(quota_path, quota | {'day': day, 'uploads': count + 1})
            return record
        record = self.store.run(operation)
        if record['status'] == 'pending':
            try:
                self.archives.purge(f"{path}/staging/{actor.uid}/", self.clock() - STAGING_PURGE_AGE_MS, keep={record['stagingPath']})
            except Exception:  # noqa: BLE001 - cleanup is best effort by design
                pass
        return ticket(record)

    def apply_change(self, actor, data):
        from .service import authenticate, fields, root_path, access, published_root, MAX_UPLOADS_PER_DAY
        authenticate(actor); fields(data, 'remoteId changeId change')
        path = root_path(data['remoteId']); require(valid_id(data['changeId']))
        text = data['change']; change = parse_change(text)
        # Only additions and photo replacements stage a file (begin_change); an outfit change is just its JSON.
        staged = change['kind'] != 'outfit'
        upload_path = f"{path}/uploads/{data['changeId']}"
        def check(tx, bind):
            now = self.clock()
            root, _ = access(tx, path, actor, now, edit=True)
            record = tx.get(upload_path)
            if record is None and not staged and bind:
                require(root['head'] is not None, 'Shared wardrobe has no snapshot', 'failed-precondition')
                quota_path = f'familyQuotas/{actor.uid}'
                quota = tx.get(quota_path) or {'roots': 0, 'day': 0, 'uploads': 0}
                day = now // 86400000
                count = quota['uploads'] if quota['day'] == day else 0
                require(count < MAX_UPLOADS_PER_DAY, 'Daily upload limit', 'resource-exhausted')
                record = {'archiveId': data['changeId'], 'kind': 'change', 'authorUid': actor.uid, 'status': 'pending',
                          'request': text, 'createdAt': now}
                tx.set(upload_path, record); tx.set(quota_path, quota | {'day': day, 'uploads': count + 1})
                return root, record
            require(record and record.get('kind') == 'change' and record['authorUid'] == actor.uid, 'Upload access denied', 'permission-denied')
            if record.get('request') is not None:
                require(record['request'] == text, 'Change ID conflict', 'already-exists')
            # A staged reservation is only for a change with a photo, and an outfit request never has one.
            require(('stagingPath' in record) == staged, 'Change ID conflict', 'already-exists')
            if record['status'] == 'committed':
                return root, record
            if staged:
                require(record['expiresAt'] > now, 'Upload expired', 'failed-precondition', 'UPLOAD_EXPIRED')
            require(root['head'] is not None, 'Shared wardrobe has no snapshot', 'failed-precondition')
            if bind and record.get('request') is None:
                record = record | {'request': text}
                tx.set(upload_path, record)
            return root, record
        root, record = self.store.run(lambda tx: check(tx, True))
        if record['status'] == 'committed':
            return record['result']
        photo_name = photo_bytes = None
        if staged:
            try:
                delta = self.archives.read(record['stagingPath'], record['size'])
            except FamilyError as exc:
                if exc.reason == 'ARCHIVE_MISSING':
                    _, latest = self.store.run(lambda tx: check(tx, False))
                    if latest['status'] == 'committed':
                        return latest['result']
                raise
            require(hashlib.sha256(delta).hexdigest() == record['sha256'], 'Archive checksum mismatch')
            photo_name, photo_bytes = change_photo(delta, change['photo'])
        for _ in range(APPLY_ATTEMPTS):
            head = root['head']
            source = self.archives.read(head['archive'], head['size'])
            require(hashlib.sha256(source).hexdigest() == head['sha256'], 'Archive checksum mismatch')
            payload, checksum, counts = changed_archive(source, root, change, photo_name, photo_bytes)
            archive_id = str(uuid.uuid4()); final = f'{path}/snapshots/{archive_id}.zip'
            generation = self.archives.put_once(final, payload, checksum)
            def publish(tx, base=root, archive_id=archive_id, final=final, payload=payload, checksum=checksum,
                        generation=generation, counts=counts):
                current, latest = check(tx, False)
                require(latest.get('request') == text, 'Upload changed', 'aborted')
                if latest['status'] == 'committed':
                    return 'replayed', latest['result']
                if current['revision'] != base['revision'] or current['head'] != base['head']:
                    return 'moved', current
                revision = current['revision'] + 1
                require(integer(revision), 'Revision exhausted', 'failed-precondition')
                new_head = dict(revision=revision, baseRevision=current['revision'], archiveId=archive_id, archive=final,
                                sha256=checksum, size=len(payload), generation=str(generation), authorUid=actor.uid,
                                committedAt=self.clock(), counts=counts)
                result = {'remoteId': data['remoteId'], 'changeId': data['changeId'], 'revision': revision,
                          'archiveId': archive_id, 'status': 'committed'}
                tx.set(path, published_root(current, new_head, revision))
                tx.set(upload_path, latest | {'status': 'committed', 'result': result})
                return 'committed', (result, current.get('previousArchive'))
            try:
                state, value = self.store.run(publish)
            except FamilyError:
                self._discard(final)  # this attempt's object is referenced by nothing
                raise
            if state == 'committed':
                result, retired = value
                if staged:
                    self._discard(record['stagingPath'])
                self._retire(path, retired, final)
                return result
            self._discard(final)
            if state == 'replayed':
                return value
            root = value  # another commit landed first: apply the same change to the newer head
        raise FamilyError('aborted', 'Shared wardrobe is busy; retry', 'CHANGE_BUSY')
