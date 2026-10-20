import hashlib
import io
import json
import unittest
import zipfile
from concurrent.futures import ThreadPoolExecutor
from family.service import Actor, FamilyService
from family.errors import FamilyError
from family.callable_wire import decode_request
from test_family import MemoryArchives
from test_membership import TransactionStore
from fixtures import REMOTE, WARDROBE, GARMENT, uid, archive, envelope


class EditingTests(unittest.TestCase):
    def setUp(self):
        self.store=TransactionStore(); self.blobs=MemoryArchives(); self.now=1800000000000
        self.service=FamilyService(self.store,self.blobs,lambda:self.now)
        self.owner=Actor('owner',True);self.editor=Actor('editor',True);self.viewer=Actor('viewer',True)
        self.service.create(self.owner,dict(remoteId=REMOTE,wardrobeId=WARDROBE))
        for actor,role in [(self.editor,'editor'),(self.viewer,'viewer')]:
            self.store.seed(f'familyWardrobes/{REMOTE}/members/{actor.uid}',dict(active=True,role=role,expiresAt=self.now+100000))
        data=archive(envelope()); req=dict(remoteId=REMOTE,archiveId=uid(700),baseRevision=0,sha256=hashlib.sha256(data).hexdigest(),size=len(data))
        upload=self.service.begin_upload(self.owner,req);self.blobs.data[upload['stagingPath']]=data
        self.service.commit(self.owner,dict(remoteId=REMOTE,archiveId=uid(700)))
        self.request=dict(remoteId=REMOTE,editId=uid(701),garmentId=GARMENT,baseRevision=1,changes={'location':'New shelf'})
    def edit(self, actor=None, **kwargs): return self.service.edit_item(actor or self.editor,self.request|kwargs)
    def error(self,code,call):
        with self.assertRaises(FamilyError) as caught: call()
        self.assertEqual(code,caught.exception.code)
    def root(self):return self.store.data[f'familyWardrobes/{REMOTE}']
    def content(self):
        with zipfile.ZipFile(io.BytesIO(self.blobs.data[self.root()['head']['archive']])) as z:
            return json.loads(z.read('manifest.json')), {n:z.read(n) for n in z.namelist() if n!='manifest.json'}
    def test_editor_changes_only_requested_metadata_preserving_photos_and_relations(self):
        before,photos=self.content(); result=self.edit();after,after_photos=self.content()
        self.assertEqual(2,result['revision']);self.assertEqual('committed',result['status'])
        before['baseRevision']=1;before['snapshot']['garments'][0]['location']='New shelf'
        self.assertEqual(before,after);self.assertEqual(photos,after_photos)
        self.assertEqual('owner',self.root()['ownerUid']);self.assertEqual('editor',self.root()['head']['authorUid'])
    def test_owner_can_edit_all_four_fields(self):
        self.edit(self.owner,changes=dict(name='Coat',size='XL',location='Hall',description='Warm\ncoat'))
        item=self.content()[0]['snapshot']['garments'][0]
        self.assertEqual('Coat',item['name']);self.assertEqual('Warm\ncoat',item['description'])
    def test_viewer_stranger_unverified_denied(self):
        for actor,code in [(self.viewer,'permission-denied'),(Actor('other',True),'permission-denied'),(Actor('editor',False),'unauthenticated')]:
            self.error(code,lambda:self.edit(actor))
        self.assertEqual(1,self.root()['revision'])
    def test_protected_fields_and_empty_patch_denied(self):
        for changes in [{},{'photoId':uid(999)},{'wardrobeId':uid(9)},{'availability':'available'},{'ownerUid':'editor'},{'priceMinor':0}]:
            self.error('invalid-argument',lambda:self.edit(changes=changes))
    def test_field_types_lengths_and_name(self):
        for changes in [{'name':''},{'name':' '},{'size':None},{'size':41*'a'},{'location':201*'a'},{'description':1001*'a'},{'name':'bad\x00'}]:
            self.error('invalid-argument',lambda:self.edit(changes=changes))
    def test_utf16_limits_match_android(self):
        self.edit(changes={'size':'😀'*20})
        self.error('invalid-argument',lambda:self.edit(editId=uid(702),baseRevision=2,changes={'size':'😀'*21}))
    def test_request_numbers_and_paths_strict(self):
        for changes in [{'baseRevision':True},{'baseRevision':1.5},{'baseRevision':0},{'editId':'../x'},{'garmentId':'../x'},{'remoteId':'../x'},{'actorUid':'owner'}]:
            self.error('invalid-argument',lambda:self.edit(**changes))
    def test_missing_item_is_not_added(self):
        self.error('failed-precondition',lambda:self.edit(garmentId=uid(800)))
        self.assertEqual(1,self.root()['revision'])
    def test_stale_revision_does_not_overwrite(self):
        self.edit(); self.error('aborted',lambda:self.edit(editId=uid(702),changes={'name':'Stale'}))
        self.assertEqual('Shirt',self.content()[0]['snapshot']['garments'][0]['name'])
    def test_lost_reply_replay_returns_same_receipt_once(self):
        first=self.edit();self.assertEqual(first,self.edit());self.assertEqual(2,self.root()['revision'])
        self.assertEqual(1,self.store.data['familyQuotas/editor']['uploads'])
    def test_replay_after_later_commit_does_not_revert(self):
        first=self.edit(); self.edit(editId=uid(702),baseRevision=2,changes={'name':'Later'})
        self.assertEqual(first,self.edit());self.assertEqual(3,self.root()['revision'])
        self.assertEqual('Later',self.content()[0]['snapshot']['garments'][0]['name'])
    def test_same_uuid_different_binding_or_actor_rejected(self):
        self.edit();self.error('already-exists',lambda:self.edit(changes={'name':'Changed'}))
        self.error('already-exists',lambda:self.edit(self.owner))
    def test_revoke_or_downgrade_between_validation_and_publish(self):
        def revoke():self.store.seed(f'familyWardrobes/{REMOTE}/members/editor',dict(active=True,role='viewer',expiresAt=self.now+100000))
        self.blobs.after_put=revoke
        self.error('permission-denied',lambda:self.edit());self.assertEqual(1,self.root()['revision'])
    def test_expiry_before_publish(self):
        self.blobs.after_put=lambda:setattr(self,'now',self.now+100000)
        self.error('permission-denied',lambda:self.edit());self.assertEqual(1,self.root()['revision'])
    def test_replay_rechecks_access(self):
        self.edit();self.store.seed(f'familyWardrobes/{REMOTE}/members/editor',dict(active=False,role='editor',expiresAt=self.now+100000))
        self.error('permission-denied',lambda:self.edit())
    def test_parallel_writers_single_winner(self):
        def work(n):
            try:self.edit(editId=uid(n));return 'ok'
            except FamilyError as e:return e.code
        with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(work,[701,702]))
        self.assertCountEqual(['ok','aborted'],results);self.assertEqual(2,self.root()['revision'])
    def test_parallel_same_request_is_idempotent(self):
        with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda _:self.edit(),[1,2]))
        self.assertEqual(results[0],results[1]);self.assertEqual(2,self.root()['revision'])
    def test_retry_after_storage_failure_keeps_request_binding(self):
        original=self.blobs.put_once
        self.blobs.put_once=lambda *args:(_ for _ in ()).throw(OSError('network'))
        with self.assertRaises(OSError):self.edit()
        self.blobs.put_once=original
        self.assertEqual(2,self.edit()['revision']);self.assertEqual(1,self.store.data['familyQuotas/editor']['uploads'])
    def test_corrupt_source_cannot_publish(self):
        path=self.root()['head']['archive'];self.blobs.data[path]=b'x'*self.root()['head']['size']
        self.error('invalid-argument',lambda:self.edit());self.assertEqual(1,self.root()['revision'])
    def test_quota_and_replay(self):
        self.edit();self.store.seed('familyQuotas/editor',dict(roots=0,day=self.now//86400000,uploads=120))
        self.edit();self.error('resource-exhausted',lambda:self.edit(editId=uid(702),baseRevision=2))
    def test_explicit_rebase_preserves_other_fields(self):
        self.edit(changes={'name':'Other writer'})
        self.error('aborted',lambda:self.edit(editId=uid(702)))
        self.edit(editId=uid(703),baseRevision=2)
        item=self.content()[0]['snapshot']['garments'][0]
        self.assertEqual(('Other writer','New shelf'),(item['name'],item['location']))
    def test_full_snapshot_writer_wins_before_edit_publish(self):
        payload=archive(envelope(base=1)); request=dict(remoteId=REMOTE,archiveId=uid(800),baseRevision=1,sha256=hashlib.sha256(payload).hexdigest(),size=len(payload))
        upload=self.service.begin_upload(self.owner,request);self.blobs.data[upload['stagingPath']]=payload
        def commit_other():
            self.blobs.after_put=None
            self.service.commit(self.owner,dict(remoteId=REMOTE,archiveId=uid(800)))
        self.blobs.after_put=commit_other
        self.error('aborted',lambda:self.edit())
        self.assertEqual(uid(800),self.root()['head']['archiveId'])
    def test_callable_long_is_decoded(self):
        request=self.request|{'baseRevision':{'@type':'type.googleapis.com/google.protobuf.Int64Value','value':'1'}}
        self.assertEqual(2,self.service.edit_item(self.editor,decode_request('edit_item',request))['revision'])
