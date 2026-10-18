"""FAM-02. Callable-only invitations; indexes are hints, never authorization.

Fixed-size inbox/directory/access documents bound transaction reads and response
sizes. Invitation IDs survive retries; terminal invitations never grant again.
All transaction reads precede writes (including role/revoke replays).
"""
import hashlib
import re
from .errors import require
from .schema import integer, valid_id

LIMIT = 50
INVITE_TTL = 7 * 86400000
MEMBER_EXPIRY = 9007199254740991  # Until explicit revoke; compatible with Storage rules.


def authorize(actor, data, names):
    from .service import authenticate, fields
    authenticate(actor); fields(data, names)


def email(value):
    require(type(value) is str and len(value) <= 254 and value.isascii(), 'Invalid email')
    value = value.strip().lower()
    require(re.fullmatch(r"[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z]{2,63}", value), 'Invalid email')
    return value


def email_key(value):
    return hashlib.sha256(email(value).encode('utf-8')).hexdigest()


def owned(tx, remote, actor, now):
    from .service import root_path, access
    return access(tx, root_path(remote), actor, now, owner=True)[0]


def visible(tx, remote, actor, now):
    root = tx.get(f'familyWardrobes/{remote}')
    if not root or root.get('archived'): return None
    role = 'owner' if root['ownerUid'] == actor.uid else None
    if role is None:
        member = tx.get(f'familyWardrobes/{remote}/members/{actor.uid}')
        if member and member.get('active') is True and member.get('role') in ('viewer', 'editor') and integer(member.get('expiresAt')) and member['expiresAt'] > now:
            role = member['role']
    if role is None: return None
    return {k: root[k] for k in ('remoteId', 'wardrobeId', 'ownerUid')} | {
        'role': role, 'name': root.get('displayName', ''), 'ownerEmail': root.get('ownerEmail', ''), 'ready': root.get('head') is not None}


def pending(entries, now):
    return {key: value for key, value in entries.items() if value['expiresAt'] > now}


def directory_path(uid): return f'familyDirectories/{uid}'
def inbox_path(key): return f'familyInboxes/{key}'
def access_path(remote): return f'familyAccess/{remote}'
def invite_path(invite):
    require(valid_id(invite), 'Invalid invitation ID')
    return f'familyInvites/{invite}'


def state(tx, path): return tx.get(path) or {'entries': {}}
def access_state(tx, remote): return tx.get(access_path(remote)) or {'members': {}, 'invites': {}}


class MembershipService:
    def directory(self, actor, data):
        authorize(actor, data, '')
        def operation(tx):
            # Equality query also discovers roots created before the directory index.
            roots = tx.owned_roots(actor.uid, LIMIT + 1)
            require(len(roots) <= LIMIT, 'Family wardrobe limit', 'resource-exhausted')
            entries = state(tx, directory_path(actor.uid))['entries']
            ids = sorted(set(entries) | {r['remoteId'] for r in roots})
            require(len(ids) <= 2 * LIMIT, 'Family wardrobe limit', 'resource-exhausted')
            result = [visible(tx, remote, actor, self.clock()) for remote in ids]
            return {'wardrobes': [r for r in result if r is not None]}
        return self.store.run(operation)

    def invitations(self, actor, data):
        authorize(actor, data, '')
        key = email_key(actor.email)
        def operation(tx):
            entries = pending(state(tx, inbox_path(key))['entries'], self.clock())
            result = []
            for id_ in sorted(entries):
                invite = tx.get(invite_path(id_))
                root = tx.get(f"familyWardrobes/{invite['remoteId']}") if invite else None
                if (invite and invite['recipientKey'] == key and invite['status'] == 'pending' and
                    invite['expiresAt'] > self.clock() and root and not root['archived']):
                    result.append(invite)
            return {'invitations': result}
        return self.store.run(operation)

    def members(self, actor, data):
        authorize(actor, data, 'remoteId')
        def operation(tx):
            owned(tx, data['remoteId'], actor, self.clock())
            metadata = access_state(tx, data['remoteId'])
            members = []
            for uid in sorted(metadata['members']):
                value = tx.get(f"familyWardrobes/{data['remoteId']}/members/{uid}")
                if value and value['active'] and value['expiresAt'] > self.clock():
                    members.append({k: value[k] for k in ('uid', 'email', 'role', 'version')})
            invites = []
            for id_ in sorted(pending(metadata['invites'], self.clock())):
                value = tx.get(invite_path(id_))
                if value and value['status'] == 'pending' and value['expiresAt'] > self.clock(): invites.append(value)
            return {'members': members, 'invitations': invites}
        return self.store.run(operation)

    def invite(self, actor, data):
        authorize(actor, data, 'remoteId inviteId email role name')
        recipient = email(data['email']); sender = email(actor.email); key = email_key(recipient)
        require(recipient != sender, 'Cannot invite yourself')
        require(data['role'] in ('viewer', 'editor'), 'Invalid role')
        require(type(data['name']) is str and 0 < len(data['name'].strip()) <= 80 and
                not any(ord(c) < 32 for c in data['name']), 'Invalid wardrobe name')
        target = invite_path(data['inviteId']); remote = data['remoteId']
        slot_path = f'familyWardrobes/{remote}/inviteSlots/{key}'
        def operation(tx):
            now = self.clock(); root = owned(tx, remote, actor, now)
            existing = tx.get(target)
            binding = dict(remoteId=remote, inviteId=data['inviteId'], recipientEmail=recipient,
                           recipientKey=key, role=data['role'], name=data['name'].strip(), ownerUid=actor.uid)
            if existing:
                require(all(existing[k] == v for k, v in binding.items()), 'Invitation ID conflict', 'already-exists')
                return existing  # Terminal or expired replay cannot create another grant.
            slot = tx.get(slot_path) or {}
            previous = tx.get(invite_path(slot['inviteId'])) if slot.get('inviteId') else None
            member = tx.get(f"familyWardrobes/{remote}/members/{slot['uid']}") if slot.get('uid') else None
            require(not member or not member['active'] or member['expiresAt'] <= now, 'Already a member', 'already-exists')
            require(not previous or previous['status'] != 'pending' or previous['expiresAt'] <= now, 'Invitation already pending', 'already-exists')
            inbox = pending(state(tx, inbox_path(key))['entries'], now)
            metadata = access_state(tx, remote); invites = pending(metadata['invites'], now)
            quota_path = f'familyInviteQuotas/{actor.uid}'
            quota = tx.get(quota_path) or {'day': 0, 'count': 0}
            count = quota['count'] if quota['day'] == now // 86400000 else 0
            require(len(inbox) < LIMIT and len(invites) < LIMIT and count < 30, 'Invitation limit', 'resource-exhausted')
            record = binding | dict(ownerEmail=sender, status='pending', expiresAt=now + INVITE_TTL)
            stub = {'expiresAt': record['expiresAt']}
            tx.set(target, record); tx.set(slot_path, {'inviteId': data['inviteId']})
            tx.set(inbox_path(key), {'entries': inbox | {data['inviteId']: stub}})
            tx.set(access_path(remote), metadata | {'invites': invites | {data['inviteId']: stub}})
            tx.set(quota_path, {'day': now // 86400000, 'count': count + 1})
            tx.set(f'familyWardrobes/{remote}', root | {'displayName': record['name'], 'ownerEmail': sender})
            return record
        return self.store.run(operation)

    def respond_invite(self, actor, data):
        authorize(actor, data, 'inviteId action')
        require(data['action'] in ('accept', 'decline'), 'Invalid invitation response')
        key = email_key(actor.email); target = invite_path(data['inviteId'])
        def operation(tx):
            now = self.clock(); invite = tx.get(target)
            require(invite and invite['recipientKey'] == key, 'Invitation access denied', 'permission-denied')
            remote = invite['remoteId']; root = tx.get(f'familyWardrobes/{remote}')
            require(root and not root['archived'], 'Family access denied', 'permission-denied')
            member_path = f'familyWardrobes/{remote}/members/{actor.uid}'; member = tx.get(member_path)
            desired = 'accepted' if data['action'] == 'accept' else 'declined'
            if invite['status'] != 'pending':
                require(invite['status'] == desired and invite.get('respondedUid') == actor.uid,
                        'Invitation no longer pending', 'failed-precondition', 'INVITE_CLOSED')
                if desired == 'accepted':
                    require(member and member['active'] and member['expiresAt'] > now and member.get('inviteId') == data['inviteId'],
                            'Family access denied', 'permission-denied')
                return {'status': desired, 'remoteId': remote}
            require(invite['expiresAt'] > now, 'Invitation expired', 'failed-precondition', 'INVITE_EXPIRED')
            slot_path = f"familyWardrobes/{remote}/inviteSlots/{key}"; slot = tx.get(slot_path)
            require(slot and slot['inviteId'] == data['inviteId'], 'Invitation replaced', 'failed-precondition', 'INVITE_CLOSED')
            inbox = state(tx, inbox_path(key)); metadata = access_state(tx, remote)
            directory = state(tx, directory_path(actor.uid))
            if desired == 'accepted':
                require(root['ownerUid'] != actor.uid, 'Cannot invite owner')
                require(not member or not member['active'] or member['expiresAt'] <= now, 'Already a member', 'already-exists')
                require(len(metadata['members']) < LIMIT and (remote in directory['entries'] or len(directory['entries']) < LIMIT),
                        'Membership limit', 'resource-exhausted')
            # No reads after this point.
            inbox['entries'].pop(data['inviteId'], None); metadata['invites'].pop(data['inviteId'], None)
            if desired == 'accepted':
                value = dict(uid=actor.uid, email=email(actor.email), role=invite['role'], active=True,
                             expiresAt=MEMBER_EXPIRY, version=(member or {}).get('version', 0) + 1,
                             inviteId=data['inviteId'])
                metadata['members'][actor.uid] = True; directory['entries'][remote] = True
                tx.set(member_path, value); tx.set(directory_path(actor.uid), directory)
                tx.set(slot_path, slot | {'uid': actor.uid})
            tx.set(target, invite | {'status': desired, 'respondedUid': actor.uid})
            tx.set(inbox_path(key), inbox); tx.set(access_path(remote), metadata)
            return {'status': desired, 'remoteId': remote}
        return self.store.run(operation)

    def cancel_invite(self, actor, data):
        authorize(actor, data, 'remoteId inviteId')
        target = invite_path(data['inviteId'])
        def operation(tx):
            owned(tx, data['remoteId'], actor, self.clock()); invite = tx.get(target)
            require(invite and invite['remoteId'] == data['remoteId'], 'Invitation access denied', 'permission-denied')
            if invite['status'] == 'revoked': return {'status': 'revoked'}
            require(invite['status'] == 'pending', 'Invitation no longer pending', 'failed-precondition', 'INVITE_CLOSED')
            inbox = state(tx, inbox_path(invite['recipientKey'])); metadata = access_state(tx, data['remoteId'])
            inbox['entries'].pop(data['inviteId'], None); metadata['invites'].pop(data['inviteId'], None)
            tx.set(target, invite | {'status': 'revoked'}); tx.set(inbox_path(invite['recipientKey']), inbox)
            tx.set(access_path(data['remoteId']), metadata)
            return {'status': 'revoked'}
        return self.store.run(operation)

    def change_member(self, actor, data):
        authorize(actor, data, 'remoteId uid role expectedVersion requestId')
        require(type(data['uid']) is str and re.fullmatch(r'[A-Za-z0-9_-]{1,128}', data['uid']), 'Invalid member')
        require(data['role'] in ('viewer', 'editor', 'revoked') and integer(data['expectedVersion'], 1) and valid_id(data['requestId']), 'Invalid membership change')
        def operation(tx):
            root = owned(tx, data['remoteId'], actor, self.clock())
            require(data['uid'] != root['ownerUid'], 'Cannot change owner')
            return self._change(tx, data, self.clock())
        return self.store.run(operation)

    def leave(self, actor, data):
        authorize(actor, data, 'remoteId requestId')
        from .service import root_path
        root_path(data['remoteId']); require(valid_id(data['requestId']))
        def operation(tx):
            root = tx.get(f"familyWardrobes/{data['remoteId']}")
            require(root and root['ownerUid'] != actor.uid, 'Owner cannot leave', 'permission-denied')
            member = tx.get(f"familyWardrobes/{data['remoteId']}/members/{actor.uid}")
            require(member is not None, 'Family access denied', 'permission-denied')
            # The current UID is the only permitted target, including retry after leave.
            return self._change(tx, data | {'uid': actor.uid, 'role': 'revoked', 'expectedVersion': member['version']}, self.clock(), leaving=True)
        return self.store.run(operation)

    def _change(self, tx, data, now, leaving=False):
        remote = data['remoteId']; path = f"familyWardrobes/{remote}/members/{data['uid']}"
        member = tx.get(path)
        require(member is not None, 'Member not found', 'not-found')
        fingerprint = {'role': data['role'], 'expectedVersion': data['expectedVersion']}
        if member.get('lastRequest') == data['requestId']:
            require((leaving and member.get('lastChange', {}).get('role') == 'revoked') or (not leaving and member.get('lastChange') == fingerprint), 'Request ID conflict', 'already-exists')
            return {'version': member['version'], 'role': member['role'], 'active': member['active']}
        require(member['active'] and member['expiresAt'] > now and member['version'] == data['expectedVersion'],
                'Membership changed; refresh', 'aborted')
        metadata = access_state(tx, remote); directory = state(tx, directory_path(data['uid']))
        # Read the accepted invitation before any write; close it when removing the member.
        invitation = tx.get(invite_path(member['inviteId'])) if member.get('inviteId') else None
        revoked = data['role'] == 'revoked'
        updated = member | {'active': not revoked, 'role': member['role'] if revoked else data['role'],
                            'version': member['version'] + 1, 'lastRequest': data['requestId'], 'lastChange': fingerprint}
        if revoked:
            metadata['members'].pop(data['uid'], None); directory['entries'].pop(remote, None)
            tx.set(access_path(remote), metadata); tx.set(directory_path(data['uid']), directory)
            if invitation: tx.set(invite_path(member['inviteId']), invitation | {'status': 'revoked'})
        tx.set(path, updated)
        return {'version': updated['version'], 'role': updated['role'], 'active': updated['active']}
