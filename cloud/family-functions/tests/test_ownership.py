"""FAM-04: ownership transfer and account deletion (user decisions of 29.10.2026)."""
import hashlib
import io
import json
import unittest
import zipfile
from family.errors import FamilyError
from family.ownership import derived_id
from family.service import Actor, FamilyService
from test_family import MemoryArchives
from test_membership import TransactionStore
from fixtures import REMOTE, WARDROBE, GARMENT, uid, archive, envelope

OTHER, LONELY, FOREIGN = uid(20), uid(21), uid(22)


class Accounts:
    def __init__(self): self.deleted = []; self.fail = 0
    def delete(self, uid):
        if self.fail:
            self.fail -= 1; raise RuntimeError('Auth unavailable')
        self.deleted.append(uid)


class OwnershipTests(unittest.TestCase):
    def setUp(self):
        self.store = TransactionStore(); self.blobs = MemoryArchives(); self.accounts = Accounts(); self.now = 1800000000000
        self.service = FamilyService(self.store, self.blobs, lambda: self.now, self.accounts)
        def actor(name): return Actor(name, True, f'{name}@example.com', self.now // 1000)
        self.owner, self.editor, self.second, self.viewer, self.other = map(actor, ['owner', 'editor', 'second', 'viewer', 'other'])
        self.invite = 900

    def wardrobe(self, remote=REMOTE, owner=None, commit=True):
        owner = owner or self.owner
        self.service.create(owner, dict(remoteId=remote, wardrobeId=WARDROBE))
        if commit:
            data = archive(envelope(owner=owner.uid, remote=remote))
            upload = self.service.begin_upload(owner, dict(remoteId=remote, archiveId=uid(self.invite + 500), baseRevision=0,
                                                           sha256=hashlib.sha256(data).hexdigest(), size=len(data)))
            self.blobs.data[upload['stagingPath']] = data
            self.service.commit(owner, dict(remoteId=remote, archiveId=uid(self.invite + 500)))
            self.invite += 1

    def join(self, member, role, remote=REMOTE, owner=None):
        self.invite += 1
        self.service.invite(owner or self.owner, dict(remoteId=remote, inviteId=uid(self.invite), email=member.email, role=role, name='Family'))
        self.service.respond_invite(member, dict(inviteId=uid(self.invite), action='accept'))

    def root(self, remote=REMOTE): return self.store.data.get(f'familyWardrobes/{remote}')
    def envelope_owner(self, remote=REMOTE):
        with zipfile.ZipFile(io.BytesIO(self.blobs.data[self.root(remote)['head']['archive']])) as z:
            return json.loads(z.read('manifest.json'))['ownerUid']
    def error(self, code, call, reason=None):
        with self.assertRaises(FamilyError) as caught: call()
        self.assertEqual(code, caught.exception.code)
        if reason: self.assertEqual(reason, caught.exception.reason)
    def transfer(self, to, version=1, request=uid(600), actor=None, remote=REMOTE):
        return self.service.transfer_owner(actor or self.owner, dict(remoteId=remote, uid=to.uid, expectedVersion=version, requestId=request))
    def role(self, actor, remote=REMOTE): return self.service.resume(actor, dict(remoteId=remote))['role']

    def test_transfer_republishes_the_head_and_keeps_the_former_owner_as_editor(self):
        self.wardrobe(); self.join(self.editor, 'editor'); self.join(self.viewer, 'viewer')
        result = self.transfer(self.editor)
        self.assertEqual({'remoteId': REMOTE, 'ownerUid': 'editor', 'revision': 2}, result)
        root = self.root()
        self.assertEqual(('editor', 'editor@example.com', 'owner'), (root['ownerUid'], root['ownerEmail'], root['head']['authorUid']))
        self.assertEqual('editor', self.envelope_owner())
        self.assertEqual(['owner', 'editor', 'viewer'], [self.role(a) for a in (self.editor, self.owner, self.viewer)])
        # The former owner writes as an editor; the new owner manages members.
        self.assertEqual(3, self.service.edit_item(self.owner, dict(remoteId=REMOTE, editId=uid(601), garmentId=GARMENT,
                                                                    baseRevision=2, changes={'size': 'M'}))['revision'])
        members = {m['uid']: m['role'] for m in self.service.members(self.editor, dict(remoteId=REMOTE))['members']}
        self.assertEqual({'owner': 'editor', 'viewer': 'viewer'}, members)
        self.error('permission-denied', lambda: self.service.members(self.owner, dict(remoteId=REMOTE)))
        self.assertEqual(result, self.transfer(self.editor))  # replay
        self.assertEqual(3, self.root()['revision'])
        self.assertEqual((1, 0), (self.store.data['familyQuotas/editor']['roots'], self.store.data['familyQuotas/owner']['roots']))
        self.assertLessEqual(len([p for p in self.blobs.data if '/snapshots/' in p]), 2)
        # The new owner can remove the former one.
        self.service.change_member(self.editor, dict(remoteId=REMOTE, uid='owner', role='revoked', expectedVersion=1, requestId=uid(602)))
        self.error('permission-denied', lambda: self.role(self.owner))

    def test_only_the_owner_hands_over_and_only_to_a_current_active_editor(self):
        self.wardrobe(); self.join(self.editor, 'editor'); self.join(self.viewer, 'viewer')
        self.error('failed-precondition', lambda: self.transfer(self.viewer), 'OWNER_NOT_EDITOR')
        self.error('failed-precondition', lambda: self.transfer(self.other), 'OWNER_NOT_EDITOR')
        self.error('aborted', lambda: self.transfer(self.editor, version=2))
        self.error('permission-denied', lambda: self.transfer(self.viewer, actor=self.editor))
        self.error('invalid-argument', lambda: self.transfer(self.owner))
        self.transfer(self.editor)
        self.error('already-exists', lambda: self.transfer(self.viewer, request=uid(600)))
        self.assertEqual(('editor', 2), (self.root()['ownerUid'], self.root()['revision']))

    def test_wardrobe_without_snapshot_changes_owner_without_a_revision(self):
        self.wardrobe(commit=False); self.join(self.editor, 'editor')
        self.assertEqual(0, self.transfer(self.editor)['revision'])
        self.assertEqual(('editor', None), (self.root()['ownerUid'], self.root()['head']))

    def test_a_commit_during_the_transfer_is_kept_and_the_transfer_retried(self):
        self.wardrobe(); self.join(self.editor, 'editor')
        def competitor():
            self.blobs.after_put = None
            self.service.edit_item(self.editor, dict(remoteId=REMOTE, editId=uid(601), garmentId=GARMENT, baseRevision=1, changes={'size': 'S'}))
        self.blobs.after_put = competitor
        self.assertEqual(3, self.transfer(self.editor)['revision'])
        with zipfile.ZipFile(io.BytesIO(self.blobs.data[self.root()['head']['archive']])) as z:
            state = json.loads(z.read('manifest.json'))
        self.assertEqual(('editor', 'S'), (state['ownerUid'], next(g for g in state['snapshot']['garments'] if g['id'] == GARMENT)['size']))

    def test_a_role_change_of_the_new_owner_during_the_transfer_stops_it(self):
        self.wardrobe(); self.join(self.editor, 'editor')
        def demote():
            self.blobs.after_put = None
            self.service.change_member(self.owner, dict(remoteId=REMOTE, uid='editor', role='viewer', expectedVersion=1, requestId=uid(601)))
        self.blobs.after_put = demote
        self.error('aborted', lambda: self.transfer(self.editor))
        self.assertEqual(('owner', 1), (self.root()['ownerUid'], self.root()['revision']))
        self.assertEqual(1, len([p for p in self.blobs.data if '/snapshots/' in p]))

    def deletion(self, owners=None, actor=None, request=uid(700)):
        return self.service.delete_account(actor or self.owner, dict(requestId=request, owners=owners or {}))

    def test_deletion_needs_a_recent_sign_in_and_a_choice_before_anything_changes(self):
        self.wardrobe(); self.join(self.editor, 'editor'); self.join(self.second, 'editor')
        stale = Actor('owner', True, 'owner@example.com', self.now // 1000 - 301)
        self.error('failed-precondition', lambda: self.deletion(actor=stale), 'REAUTH_REQUIRED')
        self.error('failed-precondition', lambda: self.deletion(), 'OWNER_CHOICE_REQUIRED')
        self.error('failed-precondition', lambda: self.deletion({REMOTE: 'viewer'}), 'OWNER_NOT_EDITOR')
        self.error('failed-precondition', lambda: self.deletion({REMOTE: 'editor', OTHER: 'editor'}), 'OWNER_NOT_EDITOR')
        self.assertEqual(('owner', 1, []), (self.root()['ownerUid'], self.root()['revision'], self.accounts.deleted))

    def test_owner_deletion_hands_over_deletes_and_leaves_everything_else(self):
        # REMOTE: two editors and a viewer (the owner chooses); OTHER: one editor (taken automatically);
        # LONELY: only a viewer and a pending invitation (deleted); FOREIGN: someone else's, the owner edits there.
        self.wardrobe(); self.join(self.editor, 'editor'); self.join(self.second, 'editor'); self.join(self.viewer, 'viewer')
        self.wardrobe(OTHER); self.join(self.editor, 'editor', OTHER)
        self.wardrobe(LONELY); self.join(self.viewer, 'viewer', LONELY)
        self.invite += 1
        self.service.invite(self.owner, dict(remoteId=LONELY, inviteId=uid(self.invite), email='late@example.com', role='viewer', name='Family'))
        pending = uid(self.invite)
        self.wardrobe(FOREIGN, owner=self.other); self.join(self.owner, 'editor', FOREIGN, owner=self.other)
        self.blobs.data[f'familyWardrobes/{FOREIGN}/staging/owner/{uid(800)}.zip'] = b'x'
        self.store.seed('users/owner/state/head', {'revision': 3}); self.blobs.data['users/owner/snapshots/a.zip'] = b'zip'
        self.blobs.data['users/other/snapshots/b.zip'] = b'kept'
        result = self.deletion({REMOTE: 'second'})
        self.assertEqual({'transferred': [REMOTE, OTHER], 'deleted': [LONELY], 'left': [FOREIGN]},
                         {k: sorted(v) for k, v in result.items()})
        self.assertEqual(('second', 'second'), (self.root()['ownerUid'], self.envelope_owner()))
        self.assertEqual(('editor', 'editor'), (self.root(OTHER)['ownerUid'], self.envelope_owner(OTHER)))
        self.assertEqual(['editor', 'viewer'], [self.role(a) for a in (self.editor, self.viewer)])
        # Without the owner: not an editor of the wardrobes it handed over, not a member of the one it left.
        for remote in (REMOTE, OTHER, FOREIGN):
            member = self.store.data.get(f'familyWardrobes/{remote}/members/owner')
            self.assertFalse(member and member['active'])
        self.assertIsNone(self.root(LONELY))
        self.assertFalse(any(p.startswith(f'familyWardrobes/{LONELY}') for p in list(self.store.data) + list(self.blobs.data)))
        self.error('permission-denied', lambda: self.role(self.viewer, LONELY))
        self.assertEqual('revoked', self.store.data[f'familyInvites/{pending}']['status'])
        self.assertEqual([], self.service.invitations(Actor('late', True, 'late@example.com'), {})['invitations'])
        self.assertFalse(any(p.startswith(('users/owner', 'familyQuotas/owner', 'familyDirectories/owner', 'familyInviteQuotas/owner'))
                             for p in self.store.data))
        self.assertNotIn('users/owner/snapshots/a.zip', self.blobs.data); self.assertIn('users/other/snapshots/b.zip', self.blobs.data)
        self.assertNotIn(f'familyWardrobes/{FOREIGN}/staging/owner/{uid(800)}.zip', self.blobs.data)
        self.assertEqual(['owner'], self.accounts.deleted)

    def test_interrupted_deletion_completes_on_a_second_call_without_extra_revisions(self):
        self.wardrobe(); self.join(self.editor, 'editor')
        self.wardrobe(LONELY)
        self.accounts.fail = 1
        with self.assertRaises(RuntimeError): self.deletion()
        revision = self.root()['revision']
        self.assertEqual('editor', self.root()['ownerUid'])
        # The deletion of an already handed-over wardrobe is not repeated; the account itself is removed now.
        self.assertEqual({'transferred': [], 'deleted': [], 'left': []}, self.deletion())
        self.assertEqual((revision, ['owner']), (self.root()['revision'], self.accounts.deleted))

    def test_member_account_deletion_leaves_and_keeps_the_wardrobe(self):
        self.wardrobe(); self.join(self.editor, 'editor')
        self.assertEqual({'transferred': [], 'deleted': [], 'left': [REMOTE]}, self.deletion(actor=self.editor))
        self.assertEqual(('owner', 1), (self.root()['ownerUid'], self.root()['revision']))
        self.assertEqual({}, {m['uid']: m for m in self.service.members(self.owner, dict(remoteId=REMOTE))['members']})
        self.assertEqual(['editor'], self.accounts.deleted)

    def test_derived_request_ids_are_stable_and_distinct(self):
        self.assertEqual(derived_id(uid(1), 'owner:a'), derived_id(uid(1), 'owner:a'))
        self.assertNotEqual(derived_id(uid(1), 'owner:a'), derived_id(uid(1), 'leave:a'))


if __name__ == '__main__':
    unittest.main()
