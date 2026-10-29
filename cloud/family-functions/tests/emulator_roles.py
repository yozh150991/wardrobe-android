"""FAM-04 role matrix against guarded local demo emulators: owner, editor, viewer, stranger and a revoked editor.

Every write path of a shared wardrobe (full archive, item edit, server-applied change with and without a staged
photo) is tried by every role; only owner and active editors may write, and revocation takes effect at once.
"""
import hashlib
import json
import unittest
import emulator_family as base
from fixtures import REMOTE, WARDROBE, GARMENT, OUTFIT, snapshot, uid, archive, envelope
from test_changes import change_zip


class RoleMatrixTests(unittest.TestCase):
    call = base.CallableTests.call
    login = base.CallableTests.login

    def test_only_owner_and_active_editors_write_through_any_path(self):
        base.clear_db()
        for blob in base.BUCKET.list_blobs(prefix='familyWardrobes/'): blob.delete()
        (owner, o), (editor, e), (viewer, v), (stranger, s), (revoked, r) = [self.login(True) for _ in range(5)]
        def ok(name, data, token):
            result = self.call('family_' + name, data, token); self.assertNotIn('error', result, (name, result)); return result['result']
        def status(name, data, token):
            result = self.call('family_' + name, data, token); return result.get('error', {}).get('status', 'OK')
        ok('create', dict(remoteId=REMOTE, wardrobeId=WARDROBE), o)
        payload = archive(envelope(owner=owner.uid))
        upload = ok('begin_upload', dict(remoteId=REMOTE, archiveId=uid(800), baseRevision=0,
                                         sha256=hashlib.sha256(payload).hexdigest(), size=len(payload)), o)
        base.BUCKET.blob(upload['stagingPath']).upload_from_string(payload, content_type='application/zip')
        ok('commit', dict(remoteId=REMOTE, archiveId=uid(800)), o)
        for number, (user, token, role) in enumerate([(editor, e, 'editor'), (viewer, v, 'viewer'), (revoked, r, 'editor')]):
            ok('invite', dict(remoteId=REMOTE, inviteId=uid(810 + number), email=user.email, role=role, name='Family'), o)
            ok('respond_invite', dict(inviteId=uid(810 + number), action='accept'), token)
        ok('change_member', dict(remoteId=REMOTE, uid=revoked.uid, role='revoked', expectedVersion=1, requestId=uid(820)), o)

        self.assertEqual(['owner', 'editor', 'viewer'], [ok('resume', dict(remoteId=REMOTE), t)['role'] for t in (o, e, v)])
        for token in (s, r):
            self.assertIn(status('resume', dict(remoteId=REMOTE), token), ('PERMISSION_DENIED', 'NOT_FOUND'))

        delta = change_zip(uid(831))
        def begin(n): return dict(remoteId=REMOTE, changeId=uid(n), sha256=hashlib.sha256(delta).hexdigest(), size=len(delta))
        def outfit(name, previous):
            rows = lambda n: dict(outfit=dict(snapshot()['outfits'][0], name=n), parts=[dict(outfitId=OUTFIT, garmentId=GARMENT)], ideas=[])
            return json.dumps(dict(kind='outfit', previous=rows(previous), **rows(name)))
        def edit(n, base_revision): return dict(remoteId=REMOTE, editId=uid(n), garmentId=GARMENT, baseRevision=base_revision, changes={'size': 'M'})
        def full(n, base_revision):
            data = archive(envelope(base=base_revision, owner=owner.uid))
            return dict(remoteId=REMOTE, archiveId=uid(n), baseRevision=base_revision, sha256=hashlib.sha256(data).hexdigest(), size=len(data))
        # Viewer, stranger and revoked editor: every write path is refused and nothing changes.
        for number, token in enumerate((v, s, r)):
            for name, data in [('begin_change', begin(840 + number)), ('apply_change', dict(remoteId=REMOTE, changeId=uid(850 + number), change=outfit('X', 'Look'))),
                               ('edit_item', edit(860 + number, 1)), ('begin_upload', full(870 + number, 1))]:
                self.assertEqual('PERMISSION_DENIED', status(name, data, token), (name, number))
        self.assertEqual(1, ok('resume', dict(remoteId=REMOTE), o)['revision'])
        # Editor and owner write through every path, in any order, without rebasing by hand.
        self.assertEqual(2, ok('apply_change', dict(remoteId=REMOTE, changeId=uid(880), change=outfit('Editor', 'Look')), e)['revision'])
        self.assertEqual(3, ok('edit_item', edit(881, 2), o)['revision'])
        ticket = ok('begin_change', begin(882), e)
        base.BUCKET.blob(ticket['stagingPath']).upload_from_string(delta, content_type='application/zip')
        garment = dict(snapshot()['garments'][0], id=uid(883), photoId=uid(831), name='Scarf', customCategoryId=None, customColorId=None)
        add = json.dumps(dict(kind='add', garment=garment, customCategory=None, customColor=None, photo=dict(id=uid(831), ext='png')))
        self.assertEqual(4, ok('apply_change', dict(remoteId=REMOTE, changeId=uid(882), change=add), e)['revision'])
        # The viewer becomes an editor: writes at once; demoted back: refused at once.
        member = next(m for m in ok('members', dict(remoteId=REMOTE), o)['members'] if m['uid'] == viewer.uid)
        ok('change_member', dict(remoteId=REMOTE, uid=viewer.uid, role='editor', expectedVersion=member['version'], requestId=uid(890)), o)
        self.assertEqual(5, ok('apply_change', dict(remoteId=REMOTE, changeId=uid(891), change=outfit('Viewer', 'Editor')), v)['revision'])
        ok('change_member', dict(remoteId=REMOTE, uid=viewer.uid, role='viewer', expectedVersion=member['version'] + 1, requestId=uid(892)), o)
        self.assertEqual('PERMISSION_DENIED', status('apply_change', dict(remoteId=REMOTE, changeId=uid(893), change=outfit('Again', 'Viewer')), v))
        self.assertEqual(5, ok('resume', dict(remoteId=REMOTE), v)['revision'])


if __name__ == '__main__': unittest.main(verbosity=2)
