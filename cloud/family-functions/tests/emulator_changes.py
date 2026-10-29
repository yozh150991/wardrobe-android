"""Delta model v1 against guarded local demo emulators: begin_change / apply_change callables."""
import hashlib
import io
import json
import unittest
import zipfile
import emulator_family as base
from fixtures import REMOTE, WARDROBE, GARMENT, snapshot, uid, archive, envelope
from test_changes import change_zip


class ChangeCallableTests(unittest.TestCase):
    call = base.CallableTests.call
    login = base.CallableTests.login

    def test_editor_adds_an_item_after_another_commit_without_rebase(self):
        base.clear_db()
        for blob in base.BUCKET.list_blobs(prefix='familyWardrobes/'): blob.delete()
        owner, owner_token = self.login(True); member, token = self.login(True)
        def call(name, data, auth):
            result = self.call('family_' + name, data, auth); self.assertNotIn('error', result, result); return result['result']
        call('create', dict(remoteId=REMOTE, wardrobeId=WARDROBE), owner_token)
        payload = archive(envelope(owner=owner.uid))
        upload = call('begin_upload', dict(remoteId=REMOTE, archiveId=uid(800), baseRevision=0,
                                           sha256=hashlib.sha256(payload).hexdigest(), size=len(payload)), owner_token)
        base.BUCKET.blob(upload['stagingPath']).upload_from_string(payload, content_type='application/zip')
        call('commit', dict(remoteId=REMOTE, archiveId=uid(800)), owner_token)
        call('invite', dict(remoteId=REMOTE, inviteId=uid(801), email=member.email, role='editor', name='Family'), owner_token)
        call('respond_invite', dict(inviteId=uid(801), action='accept'), token)
        self.assertIn('change1', call('resume', dict(remoteId=REMOTE), token)['features'])

        delta = change_zip(uid(811))
        # Android sends Kotlin Long as an Int64Value wrapper.
        size = {'@type': 'type.googleapis.com/google.protobuf.Int64Value', 'value': str(len(delta))}
        ticket = call('begin_change', dict(remoteId=REMOTE, changeId=uid(810), sha256=hashlib.sha256(delta).hexdigest(), size=size), token)
        self.assertEqual((1, 'pending'), (ticket['baseRevision'], ticket['status']))
        garment = dict(snapshot()['garments'][0], id=uid(812), photoId=uid(811), name='Scarf', customCategoryId=None, customColorId=None)
        change = json.dumps(dict(kind='add', garment=garment, customCategory=None, customColor=None, photo=dict(id=uid(811), ext='png')))
        request = dict(remoteId=REMOTE, changeId=uid(810), change=change)
        self.assertEqual({'reason': 'ARCHIVE_MISSING'}, self.call('family_apply_change', request, token)['error']['details'])
        # Another member commits first: the change still applies, to the newer head.
        call('edit_item', dict(remoteId=REMOTE, editId=uid(820), garmentId=GARMENT, baseRevision=1, changes={'location': 'Hall'}), owner_token)
        ticket = call('begin_change', dict(remoteId=REMOTE, changeId=uid(810), sha256=hashlib.sha256(delta).hexdigest(), size=len(delta)), token)
        self.assertEqual(2, ticket['baseRevision'])
        base.BUCKET.blob(ticket['stagingPath']).upload_from_string(delta, content_type='application/zip')
        receipt = call('apply_change', request, token)
        self.assertEqual((3, 'committed'), (receipt['revision'], receipt['status']))
        self.assertEqual(receipt, call('apply_change', request, token))
        self.assertIsNone(base.BUCKET.get_blob(ticket['stagingPath']))
        head = call('resume', dict(remoteId=REMOTE), owner_token)['head']
        with zipfile.ZipFile(io.BytesIO(base.BUCKET.blob(head['archive']).download_as_bytes())) as z:
            state = json.loads(z.read('manifest.json'))['snapshot']
            self.assertIn(f'photos/{uid(811)}.png', z.namelist())
        self.assertEqual('Hall', next(g for g in state['garments'] if g['id'] == GARMENT)['location'])
        self.assertTrue(any(g['id'] == uid(812) for g in state['garments']))


if __name__ == '__main__': unittest.main(verbosity=2)
