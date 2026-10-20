"""Create/resume/begin-upload/commit. Transactions always recheck membership.

Store.run invokes a callback with get/set semantics in one serializable
transaction. The archive store implements immutable, generation-pinned reads
and create-if-absent writes. Tests use the same service with both adapters.
"""
from dataclasses import dataclass
import re
import time
from .archive import MAX_ARCHIVE, validate_archive
from .errors import require
from .schema import HASH, integer, valid_id
from .membership import MembershipService
from .editing import EditingService

UPLOAD_TTL_MS = 30 * 60 * 1000
MAX_ROOTS = 50
MAX_UPLOADS_PER_DAY = 120


@dataclass(frozen=True)
class Actor:
    uid: str
    email_verified: bool
    email: str = ""


def authenticate(actor):
    require(isinstance(actor, Actor) and type(actor.uid) is str and
            re.fullmatch(r'[A-Za-z0-9_-]{1,128}', actor.uid) and actor.email_verified is True,
            'Verified sign-in required', 'unauthenticated')


def fields(data, names):
    require(type(data) is dict and set(data) == set(names.split()), 'Unexpected request fields')


def root_path(remote):
    require(valid_id(remote), 'Invalid remote ID')
    return f'familyWardrobes/{remote}'


def access(tx, path, actor, now, edit=False, owner=False):
    root = tx.get(path)
    require(root is not None and not root['archived'], 'Family access denied', 'permission-denied')
    role = 'owner' if root['ownerUid'] == actor.uid else None
    if role is None:
        member = tx.get(f'{path}/members/{actor.uid}')
        if (member and member.get('active') is True and member.get('role') in ('viewer','editor')
                and integer(member.get('expiresAt')) and member['expiresAt'] > now):
            role = member['role']
    require(role is not None and (not edit or role in ('owner','editor')) and (not owner or role == 'owner'),
            'Family access denied', 'permission-denied')
    return root, role


def public_head(root, role):
    return {k: root[k] for k in ('remoteId','wardrobeId','ownerUid','revision','head')} | {'role':role}


class FamilyService(MembershipService, EditingService):
    def __init__(self, store, archives, clock=None):
        self.store, self.archives = store, archives
        self.clock = clock or (lambda: int(time.time() * 1000))

    def create(self, actor, data):
        authenticate(actor); fields(data, 'remoteId wardrobeId')
        path = root_path(data['remoteId']); require(valid_id(data['wardrobeId']))
        def operation(tx):
            root = tx.get(path)
            if root:
                require(root['ownerUid'] == actor.uid and root['wardrobeId'] == data['wardrobeId'] and not root['archived'],
                        'Reservation conflict', 'already-exists')
                return public_head(root, 'owner')
            quota_path = f'familyQuotas/{actor.uid}'
            quota = tx.get(quota_path) or {'roots':0, 'day':0, 'uploads':0}
            require(quota['roots'] < MAX_ROOTS, 'Family wardrobe limit', 'resource-exhausted')
            root = dict(remoteId=data['remoteId'], wardrobeId=data['wardrobeId'], ownerUid=actor.uid,
                        revision=0, head=None, archived=False, formatVersion=1)
            tx.set(path, root)
            tx.set(quota_path, quota | {'roots':quota['roots'] + 1})
            return public_head(root, 'owner')
        return self.store.run(operation)

    def resume(self, actor, data):
        authenticate(actor); fields(data, 'remoteId')
        path = root_path(data['remoteId'])
        def operation(tx):
            root, role = access(tx, path, actor, self.clock())
            return public_head(root, role)
        return self.store.run(operation)

    def begin_upload(self, actor, data):
        authenticate(actor); fields(data, 'remoteId archiveId baseRevision sha256 size')
        path = root_path(data['remoteId'])
        require(valid_id(data['archiveId']) and integer(data['baseRevision']) and
                type(data['sha256']) is str and HASH.fullmatch(data['sha256']) and integer(data['size'], 1, MAX_ARCHIVE))
        upload_path = f"{path}/uploads/{data['archiveId']}"
        def operation(tx):
            now = self.clock()
            root, _ = access(tx, path, actor, now, edit=True)
            existing = tx.get(upload_path)
            expected = {k:data[k] for k in ('archiveId','baseRevision','sha256','size')} | {'authorUid':actor.uid}
            if existing:
                require(all(existing[k] == v for k,v in expected.items()), 'Upload ID conflict', 'already-exists')
                if existing['status'] == 'committed':
                    return existing
                require(root['revision'] == data['baseRevision'], 'Revision conflict', 'aborted')
                require(existing['expiresAt'] > now, 'Upload expired; use a new archive ID', 'failed-precondition', 'UPLOAD_EXPIRED')
                return existing
            require(root['revision'] == data['baseRevision'], 'Revision conflict', 'aborted')
            quota_path = f'familyQuotas/{actor.uid}'
            quota = tx.get(quota_path) or {'roots':0, 'day':0, 'uploads':0}
            day = now // 86400000
            count = quota['uploads'] if quota['day'] == day else 0
            require(count < MAX_UPLOADS_PER_DAY, 'Daily upload limit', 'resource-exhausted')
            record = expected | {'status':'pending', 'expiresAt':now + UPLOAD_TTL_MS,
                                 'stagingPath':f"{path}/staging/{actor.uid}/{data['archiveId']}.zip"}
            tx.set(upload_path, record)
            tx.set(quota_path, quota | {'day':day, 'uploads':count+1})
            return record
        return self.store.run(operation)

    def commit(self, actor, data):
        authenticate(actor); fields(data, 'remoteId archiveId')
        path = root_path(data['remoteId']); require(valid_id(data['archiveId']))
        upload_path = f"{path}/uploads/{data['archiveId']}"
        def check(tx):
            root, _ = access(tx, path, actor, self.clock(), edit=True)
            upload = tx.get(upload_path)
            require(upload and upload['authorUid'] == actor.uid, 'Upload access denied', 'permission-denied')
            if upload['status'] == 'committed':
                return root, upload
            require(upload['expiresAt'] > self.clock(), 'Upload expired', 'failed-precondition', 'UPLOAD_EXPIRED')
            require(root['revision'] == upload['baseRevision'], 'Revision conflict', 'aborted')
            return root, upload
        root, upload = self.store.run(check)
        if upload['status'] == 'committed':
            return upload['result']
        data_bytes = self.archives.read(upload['stagingPath'], upload['size'])
        counts = validate_archive(data_bytes, root, upload['baseRevision'], upload['sha256'])
        final_path = f"{path}/snapshots/{data['archiveId']}.zip"
        generation = self.archives.put_once(final_path, data_bytes, upload['sha256'])
        # An immutable orphan is safe: rules expose only the current committed head.
        # Never delete it here: a concurrent retry might have just committed it.
        def publish(tx):
            current, current_upload = check(tx)
            if current_upload['status'] == 'committed':
                return current_upload['result']
            require(current_upload == upload, 'Upload changed', 'aborted')
            revision = current['revision'] + 1
            require(integer(revision), 'Revision exhausted', 'failed-precondition')
            head = dict(revision=revision, baseRevision=upload['baseRevision'], archiveId=data['archiveId'],
                        archive=final_path, sha256=upload['sha256'], size=upload['size'], generation=str(generation),
                        authorUid=actor.uid, committedAt=self.clock(), counts=counts)
            result = {k:current[k] for k in ('remoteId','wardrobeId','ownerUid')} | {'head':head, 'revision':revision}
            tx.set(path, current | {'head':head, 'revision':revision})
            tx.set(upload_path, upload | {'status':'committed', 'result':result})
            return result
        return self.store.run(publish)
