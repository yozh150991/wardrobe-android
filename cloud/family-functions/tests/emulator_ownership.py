"""FAM-04 ownership transfer and account deletion through callables, local demo emulators only."""
import hashlib
import io
import json
import unittest
import zipfile
from firebase_admin import auth, firestore
import emulator_family as base
from fixtures import REMOTE, WARDROBE, GARMENT, uid, archive, envelope

LONELY = uid(21)


class OwnershipCallableTests(unittest.TestCase):
    call = base.CallableTests.call
    login = base.CallableTests.login

    def test_transfer_then_delete_the_new_owners_account(self):
        base.clear_db()
        for blob in base.BUCKET.list_blobs(prefix='familyWardrobes/'): blob.delete()
        (owner, o), (editor, e), (viewer, v) = [self.login(True) for _ in range(3)]
        def ok(name, data, token):
            result = self.call('family_' + name, data, token); self.assertNotIn('error', result, (name, result)); return result['result']
        for remote in (REMOTE, LONELY):
            ok('create', dict(remoteId=remote, wardrobeId=WARDROBE), o)
            payload = archive(envelope(owner=owner.uid, remote=remote))
            upload = ok('begin_upload', dict(remoteId=remote, archiveId=uid(800 if remote == REMOTE else 801), baseRevision=0,
                                             sha256=hashlib.sha256(payload).hexdigest(), size=len(payload)), o)
            base.BUCKET.blob(upload['stagingPath']).upload_from_string(payload, content_type='application/zip')
            ok('commit', dict(remoteId=remote, archiveId=uid(800 if remote == REMOTE else 801)), o)
        ok('invite', dict(remoteId=REMOTE, inviteId=uid(810), email=editor.email, role='editor', name='Family'), o)
        ok('respond_invite', dict(inviteId=uid(810), action='accept'), e)
        ok('invite', dict(remoteId=LONELY, inviteId=uid(811), email=viewer.email, role='viewer', name='Family'), o)
        ok('respond_invite', dict(inviteId=uid(811), action='accept'), v)

        # The phone sends Kotlin Long as an Int64Value wrapper.
        version = {'@type': 'type.googleapis.com/google.protobuf.Int64Value', 'value': '1'}
        result = ok('transfer_owner', dict(remoteId=REMOTE, uid=editor.uid, expectedVersion=version, requestId=uid(820)), o)
        self.assertEqual((editor.uid, 2), (result['ownerUid'], result['revision']))
        head = ok('resume', dict(remoteId=REMOTE), e)
        self.assertEqual(('owner', editor.uid), (head['role'], head['ownerUid']))
        self.assertEqual('editor', ok('resume', dict(remoteId=REMOTE), o)['role'])
        with zipfile.ZipFile(io.BytesIO(base.BUCKET.blob(head['head']['archive']).download_as_bytes())) as z:
            self.assertEqual(editor.uid, json.loads(z.read('manifest.json'))['ownerUid'])

        # The original owner deletes the account: LONELY has no editor and goes for everyone; REMOTE is left.
        firestore.client().document(f'users/{owner.uid}/state/head').set({'revision': 1})
        base.BUCKET.blob(f'users/{owner.uid}/snapshots/{uid(830)}.zip').upload_from_string(b'zip', content_type='application/zip')
        deleted = ok('delete_account', dict(requestId=uid(840), owners={}), o)
        self.assertEqual({'transferred': [], 'deleted': [LONELY], 'left': [REMOTE]}, deleted)
        with self.assertRaises(auth.UserNotFoundError): auth.get_user(owner.uid)
        self.assertFalse(firestore.client().document(f'users/{owner.uid}/state/head').get().exists)
        self.assertEqual([], list(base.BUCKET.list_blobs(prefix=f'users/{owner.uid}/')))
        self.assertEqual([], list(base.BUCKET.list_blobs(prefix=f'familyWardrobes/{LONELY}/')))
        self.assertEqual('PERMISSION_DENIED', self.call('family_resume', dict(remoteId=LONELY), v)['error']['status'])
        self.assertEqual({}, {m['uid']: m for m in ok('members', dict(remoteId=REMOTE), e)['members']})
        self.assertEqual(3, ok('edit_item', dict(remoteId=REMOTE, editId=uid(850), garmentId=GARMENT, baseRevision=2,
                                                 changes={'size': 'M'}), e)['revision'])


if __name__ == '__main__': unittest.main(verbosity=2)
