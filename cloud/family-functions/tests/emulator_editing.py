"""Live adapters and callable integration against guarded local demo emulators only."""
import hashlib
import unittest
import emulator_family as base
from fixtures import REMOTE, WARDROBE, GARMENT, uid, archive, envelope


class EditCallableTests(unittest.TestCase):
    call=base.CallableTests.call
    login=base.CallableTests.login
    def test_member_edit_retry_and_stale_revision(self):
        base.clear_db()
        for blob in base.BUCKET.list_blobs(prefix='familyWardrobes/'):blob.delete()
        owner,owner_token=self.login(True);member,token=self.login(True)
        def call(name,data,auth):
            result=self.call('family_'+name,data,auth);self.assertNotIn('error',result);return result['result']
        call('create',dict(remoteId=REMOTE,wardrobeId=WARDROBE),owner_token)
        payload=archive(envelope(owner=owner.uid))
        upload=call('begin_upload',dict(remoteId=REMOTE,archiveId=uid(800),baseRevision=0,sha256=hashlib.sha256(payload).hexdigest(),size=len(payload)),owner_token)
        base.BUCKET.blob(upload['stagingPath']).upload_from_string(payload,content_type='application/zip')
        call('commit',dict(remoteId=REMOTE,archiveId=uid(800)),owner_token)
        call('invite',dict(remoteId=REMOTE,inviteId=uid(801),email=member.email,role='viewer',name='Family'),owner_token)
        call('respond_invite',dict(inviteId=uid(801),action='accept'),token)
        request=dict(remoteId=REMOTE,editId=uid(802),garmentId=GARMENT,baseRevision=1,changes={'location':'New shelf'})
        self.assertEqual('PERMISSION_DENIED',self.call('family_edit_item',request,token)['error']['status'])
        call('change_member',dict(remoteId=REMOTE,uid=member.uid,role='editor',expectedVersion=1,requestId=uid(803)),owner_token)
        receipt=call('edit_item',request,token)
        self.assertEqual(2,receipt['revision']);self.assertEqual(receipt,call('edit_item',request,token))
        self.assertEqual('ABORTED',self.call('family_edit_item',request|{'editId':uid(804)},token)['error']['status'])
        self.assertEqual(2,call('resume',dict(remoteId=REMOTE),owner_token)['revision'])


if __name__=='__main__':unittest.main(verbosity=2)
