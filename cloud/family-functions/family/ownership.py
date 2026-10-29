"""FAM-04: ownership transfer and account deletion (user decisions of 29.10.2026).

- The owner may hand a shared wardrobe to an active editor; the former owner stays as an editor.
- Deleting an account: every owned shared wardrobe goes to the editor the owner chose (a single editor is taken
  automatically); a wardrobe without editors is deleted for everyone. Then the account's memberships, its inbox,
  quotas and directory, its private cloud copy and finally the Firebase account itself are removed.

The archive identity contains the owner (every reader checks it), so a transfer republishes the head with the new
owner in its envelope as one more revision; photos are copied byte for byte. Each step is idempotent, so an
interrupted deletion is completed by calling it again.
"""
import hashlib
import io
import json
import re
import uuid
import zipfile
from .archive import MAX_ARCHIVE, validate_archive
from .errors import FamilyError, require
from .schema import integer, strict_json, valid_id
from .membership import (LIMIT, MEMBER_EXPIRY, access_path, access_state, directory_path, email, email_key,
                         inbox_path, invite_path, state)

RECENT_SIGN_IN_S = 5 * 60
TRANSFER_ATTEMPTS = 3
_UID = re.compile(r'[A-Za-z0-9_-]{1,128}')


def owner_archive(source, root, new_owner):
    """The current head with [new_owner] in its envelope and the current revision as its base."""
    try:
        with zipfile.ZipFile(io.BytesIO(source)) as archive:
            envelope = strict_json(archive.read('manifest.json').decode('utf-8'))
            envelope = envelope | {'ownerUid': new_owner, 'baseRevision': root['revision']}
            output = io.BytesIO()
            with zipfile.ZipFile(output, 'w') as dest:
                for name in sorted(archive.namelist()):
                    manifest = name == 'manifest.json'
                    content = (json.dumps(envelope, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode('utf-8')
                               if manifest else archive.read(name))
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
    return result, checksum, validate_archive(result, root | {'ownerUid': new_owner}, root['revision'], checksum)


def derived_id(request, scope):
    """A stable request ID per step of one account deletion, so a repeated call replays every step."""
    return str(uuid.uuid5(uuid.UUID(request), scope))


class OwnershipService:
    def transfer_owner(self, actor, data):
        from .service import authenticate, fields, root_path
        authenticate(actor); fields(data, 'remoteId uid expectedVersion requestId')
        root_path(data['remoteId'])
        require(type(data['uid']) is str and _UID.fullmatch(data['uid']) and data['uid'] != actor.uid and
                integer(data['expectedVersion'], 1) and valid_id(data['requestId']), 'Invalid ownership transfer')
        return self._transfer(actor, data['remoteId'], data['uid'], data['expectedVersion'], data['requestId'], stay=True)

    def _transfer(self, actor, remote, target, expected_version, request_id, stay):
        from .service import root_path, access, published_root
        path = root_path(remote); member_path = f'{path}/members/{target}'
        def replayed(root):
            done = (root or {}).get('lastTransfer')
            if done and done['requestId'] == request_id:
                require(done['from'] == actor.uid and done['to'] == target, 'Request ID conflict', 'already-exists')
                return done['result']
            return None
        def check(tx):
            now = self.clock(); root = tx.get(path)
            replay = replayed(root)
            if replay is not None:
                return None, None, replay
            root, _ = access(tx, path, actor, now, owner=True)
            member = tx.get(member_path)
            require(member and member['active'] and member['expiresAt'] > now and member['role'] == 'editor',
                    'The new owner must be an active editor', 'failed-precondition', 'OWNER_NOT_EDITOR')
            require(member['version'] == expected_version, 'Membership changed; refresh', 'aborted')
            return root, member, None
        for _ in range(TRANSFER_ATTEMPTS):
            root, member, replay = self.store.run(check)
            if replay is not None:
                return replay
            final = generation = checksum = counts = size = archive_id = None
            if root['head'] is not None:
                head = root['head']
                source = self.archives.read(head['archive'], head['size'])
                require(hashlib.sha256(source).hexdigest() == head['sha256'], 'Archive checksum mismatch')
                payload, checksum, counts = owner_archive(source, root, target)
                archive_id = str(uuid.uuid4()); final = f'{path}/snapshots/{archive_id}.zip'; size = len(payload)
                generation = self.archives.put_once(final, payload, checksum)
            def publish(tx, base=root, member=member, final=final, generation=generation, checksum=checksum,
                        counts=counts, size=size, archive_id=archive_id):
                now = self.clock(); current = tx.get(path)
                replay = replayed(current)
                if replay is not None:
                    return 'replayed', replay
                current, _ = access(tx, path, actor, now, owner=True)
                if current['revision'] != base['revision'] or current['head'] != base['head']:
                    return 'moved', None
                latest = tx.get(member_path)
                require(latest == member, 'Membership changed; refresh', 'aborted')
                former = tx.get(f'{path}/members/{actor.uid}')
                metadata = access_state(tx, remote)
                target_directory = state(tx, directory_path(target)); actor_directory = state(tx, directory_path(actor.uid))
                target_quota = tx.get(f'familyQuotas/{target}') or {'roots': 0, 'day': 0, 'uploads': 0}
                actor_quota = tx.get(f'familyQuotas/{actor.uid}') or {'roots': 0, 'day': 0, 'uploads': 0}
                # No reads after this point.
                if final:
                    revision = current['revision'] + 1
                    head = dict(revision=revision, baseRevision=current['revision'], archiveId=archive_id, archive=final,
                                sha256=checksum, size=size, generation=str(generation), authorUid=actor.uid,
                                committedAt=now, counts=counts)
                    following = published_root(current, head, revision)
                else:
                    revision = current['revision']; following = dict(current)
                result = {'remoteId': remote, 'ownerUid': target, 'revision': revision}
                tx.set(path, following | {'ownerUid': target, 'ownerEmail': member['email'],
                                          'lastTransfer': {'requestId': request_id, 'from': actor.uid, 'to': target, 'result': result}})
                tx.set(member_path, member | {'active': False, 'version': member['version'] + 1, 'lastRequest': request_id,
                                              'lastChange': {'role': 'owner', 'expectedVersion': expected_version}})
                metadata['members'].pop(target, None); target_directory['entries'].pop(remote, None)
                if stay:
                    tx.set(f'{path}/members/{actor.uid}', dict(uid=actor.uid, email=email(actor.email), role='editor', active=True,
                                                               expiresAt=MEMBER_EXPIRY, version=(former or {}).get('version', 0) + 1))
                    metadata['members'][actor.uid] = True; actor_directory['entries'][remote] = True
                tx.set(access_path(remote), metadata)
                tx.set(directory_path(target), target_directory); tx.set(directory_path(actor.uid), actor_directory)
                tx.set(f'familyQuotas/{target}', target_quota | {'roots': target_quota['roots'] + 1})
                tx.set(f'familyQuotas/{actor.uid}', actor_quota | {'roots': max(0, actor_quota['roots'] - 1)})
                return 'committed', (result, current.get('previousArchive'))
            try:
                outcome, value = self.store.run(publish)
            except FamilyError:
                self._discard(final)
                raise
            if outcome == 'committed':
                result, retired = value
                self._retire(path, retired, final)
                return result
            self._discard(final)
            if outcome == 'replayed':
                return value
        raise FamilyError('aborted', 'Shared wardrobe is busy; retry', 'CHANGE_BUSY')

    def delete_account(self, actor, data):
        from .service import authenticate, fields, root_path
        authenticate(actor); fields(data, 'requestId owners')
        require(valid_id(data['requestId']) and type(data['owners']) is dict and len(data['owners']) <= LIMIT, 'Invalid request')
        for remote, uid in data['owners'].items():
            root_path(remote); require(type(uid) is str and _UID.fullmatch(uid) is not None, 'Invalid new owner')
        # Firebase accepts an hours-old ID token; deletion needs a sign-in from the last minutes.
        require(0 <= self.clock() // 1000 - actor.auth_time <= RECENT_SIGN_IN_S, 'Sign in again to delete the account',
                'failed-precondition', 'REAUTH_REQUIRED')
        # Everything is decided before anything changes: no wardrobe is touched while one still needs a choice.
        plan = self.store.run(lambda tx: self._deletion_plan(tx, actor, data['owners']))
        for remote, (target, version) in sorted(plan['transfer'].items()):
            self._transfer(actor, remote, target, version, derived_id(data['requestId'], 'owner:' + remote), stay=False)
        for remote in plan['delete']:
            self._delete_root(actor, remote)
        for remote in plan['leave']:
            self.leave(actor, {'remoteId': remote, 'requestId': derived_id(data['requestId'], 'leave:' + remote)})
            self.archives.delete_prefix(f'familyWardrobes/{remote}/staging/{actor.uid}/')
        for path in (f'familyQuotas/{actor.uid}', f'familyInviteQuotas/{actor.uid}', directory_path(actor.uid), f'users/{actor.uid}'):
            self.store.delete_tree(path)
        try:
            self.store.delete_tree(inbox_path(email_key(actor.email)))
        except FamilyError:
            pass  # No addressable e-mail, so no inbox exists.
        self.archives.delete_prefix(f'users/{actor.uid}/')
        self.accounts.delete(actor.uid)
        return {'transferred': sorted(plan['transfer']), 'deleted': plan['delete'], 'left': plan['leave']}

    def _deletion_plan(self, tx, actor, owners):
        now = self.clock()
        roots = tx.owned_roots(actor.uid, LIMIT + 1)
        require(len(roots) <= LIMIT, 'Family wardrobe limit', 'resource-exhausted')
        transfer, delete = {}, []
        for root in sorted(roots, key=lambda r: r['remoteId']):
            remote = root['remoteId']
            if root.get('archived'):
                delete.append(remote)  # left over from an interrupted deletion: finish it
                continue
            editors = []
            for uid in sorted(access_state(tx, remote)['members']):
                member = tx.get(f'familyWardrobes/{remote}/members/{uid}')
                if member and member['active'] and member['expiresAt'] > now and member['role'] == 'editor':
                    editors.append(member)
            choice = owners.get(remote)
            if choice is not None:
                chosen = next((m for m in editors if m['uid'] == choice), None)
                require(chosen is not None, 'The new owner must be an active editor', 'failed-precondition', 'OWNER_NOT_EDITOR')
            elif len(editors) == 1:
                chosen = editors[0]
            elif editors:
                raise FamilyError('failed-precondition', 'Choose the new owner of every shared wardrobe', 'OWNER_CHOICE_REQUIRED')
            else:
                delete.append(remote)
                continue
            transfer[remote] = (chosen['uid'], chosen['version'])
        require(set(owners) <= set(transfer), 'Unknown shared wardrobe', 'failed-precondition', 'OWNER_NOT_EDITOR')
        leave = []
        for remote in sorted(state(tx, directory_path(actor.uid))['entries']):
            member = tx.get(f'familyWardrobes/{remote}/members/{actor.uid}')
            if remote not in transfer and remote not in delete and member and member['active']:
                leave.append(remote)
        return {'transfer': transfer, 'delete': delete, 'leave': leave}

    def _delete_root(self, actor, remote):
        """Access ends at once (archived), pending invitations close, then every object and record goes."""
        from .service import root_path
        path = root_path(remote)
        def close(tx):
            root = tx.get(path)
            if root is None:
                return
            require(root['ownerUid'] == actor.uid, 'Family access denied', 'permission-denied')
            metadata = access_state(tx, remote)
            invites = {i: tx.get(invite_path(i)) for i in sorted(metadata['invites'])}
            inboxes = {v['recipientKey']: state(tx, inbox_path(v['recipientKey'])) for v in invites.values() if v}
            # No reads after this point.
            for i, invite in invites.items():
                if invite and invite['status'] == 'pending':
                    tx.set(invite_path(i), invite | {'status': 'revoked'})
                    inboxes[invite['recipientKey']]['entries'].pop(i, None)
            for key, inbox in inboxes.items():
                tx.set(inbox_path(key), inbox)
            tx.set(path, root | {'archived': True})
        self.store.run(close)
        self.archives.delete_prefix(f'{path}/')
        self.store.delete_tree(path)
        self.store.delete_tree(access_path(remote))
