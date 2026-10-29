import copy
import hashlib
import io
import json
import threading
import unittest
import zipfile
from concurrent.futures import ThreadPoolExecutor
from family.archive import validate_archive
from family.errors import FamilyError
from family.service import Actor, FamilyService, UPLOAD_TTL_MS
from family.schema import validate_snapshot
from fixtures import *


class MemoryStore:
    def __init__(self):
        self.data = {}; self.lock = threading.RLock()
    def run(self, callback):
        with self.lock:
            data = copy.deepcopy(self.data)
            class Tx:
                def get(self, path): return copy.deepcopy(data.get(path))
                def owned_roots(self, uid, limit):
                    return [copy.deepcopy(v) for k,v in sorted(data.items()) if k.startswith('familyWardrobes/') and k.count('/') == 1 and v['ownerUid'] == uid][:limit]
                def set(self, path, value): data[path] = copy.deepcopy(value)
            result = callback(Tx()); self.data = data
            return result
    def seed(self, path, value):
        self.run(lambda tx: tx.set(path, value))
    def delete_tree(self, path):
        with self.lock:
            self.data = {k: v for k, v in self.data.items() if k != path and not k.startswith(path + '/')}


class MemoryArchives:
    def __init__(self): self.data={}; self.created={}; self.after_put=None; self.lock=threading.Lock()
    def delete(self, path):
        with self.lock: self.data.pop(path, None)
    def delete_prefix(self, prefix):
        with self.lock:
            for p in [p for p in self.data if p.startswith(prefix)]: self.data.pop(p, None)
    def purge(self, prefix, older_than_ms, keep=()):
        # Objects without a recorded creation time (written directly by tests) are never purged.
        with self.lock:
            old=[p for p,t in self.created.items() if p.startswith(prefix) and p not in keep and t < older_than_ms and p in self.data]
            for p in old: self.data.pop(p, None)
        return len(old)
    def read(self, path, size):
        if path not in self.data: raise FamilyError('failed-precondition','Missing archive')
        if len(self.data[path]) != size: raise FamilyError('invalid-argument','Size mismatch')
        return self.data[path]
    def put_once(self, path, data, checksum):
        with self.lock:
            if path in self.data and self.data[path] != data: raise FamilyError('already-exists','Collision')
            self.data[path]=data
        if self.after_put: self.after_put()
        return '1'


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.store=MemoryStore(); self.blobs=MemoryArchives(); self.now=1800000000000
        self.service=FamilyService(self.store,self.blobs,lambda:self.now)
        self.owner=Actor('owner',True); self.editor=Actor('editor',True); self.viewer=Actor('viewer',True)
        self.service.create(self.owner,dict(remoteId=REMOTE,wardrobeId=WARDROBE))
    def member(self, actor, role, active=True, expiry=None):
        self.store.seed(f'familyWardrobes/{REMOTE}/members/{actor.uid}',dict(role=role,active=active,expiresAt=expiry or self.now+100000))
    def pending(self, actor=None, number=100, base=0, data=None):
        actor=actor or self.owner; data=data or archive(envelope(base))
        request=dict(remoteId=REMOTE,archiveId=uid(number),baseRevision=base,sha256=hashlib.sha256(data).hexdigest(),size=len(data))
        result=self.service.begin_upload(actor,request)
        self.blobs.data[result['stagingPath']]=data
        return dict(remoteId=REMOTE,archiveId=uid(number)), request
    def assert_error(self, code, action):
        with self.assertRaises(FamilyError) as ctx: action()
        self.assertEqual(code,ctx.exception.code)
    def test_create_replay_and_collision(self):
        self.assertEqual(0,self.service.create(self.owner,dict(remoteId=REMOTE,wardrobeId=WARDROBE))['revision'])
        self.assert_error('already-exists',lambda:self.service.create(self.editor,dict(remoteId=REMOTE,wardrobeId=WARDROBE)))
        self.assert_error('already-exists',lambda:self.service.create(self.owner,dict(remoteId=REMOTE,wardrobeId=uid(99))))
    def test_unverified_and_forged_identity(self):
        for actor in (None,Actor('owner',False),Actor('../owner',True)):
            self.assert_error('unauthenticated',lambda:self.service.resume(actor,dict(remoteId=REMOTE)))
        self.assert_error('invalid-argument',lambda:self.service.create(self.owner,dict(remoteId=uid(99),wardrobeId=WARDROBE,ownerUid='other')))
    def test_membership_roles_expiry_and_revocation(self):
        for role,active,expiry in [('owner',True,self.now+1),('editor',False,self.now+1),('viewer',True,self.now)]:
            self.member(self.editor,role,active,expiry)
            self.assert_error('permission-denied',lambda:self.service.resume(self.editor,dict(remoteId=REMOTE)))
        self.member(self.viewer,'viewer')
        self.assertEqual('viewer',self.service.resume(self.viewer,dict(remoteId=REMOTE))['role'])
        self.assert_error('permission-denied',lambda:self.pending(self.viewer))
    def test_first_commit_replay_resume_and_new_revision(self):
        req,_=self.pending(); result=self.service.commit(self.owner,req)
        self.assertEqual(1,result['revision']); self.assertEqual(result,self.service.commit(self.owner,req))
        self.assertEqual(1,self.service.resume(self.owner,dict(remoteId=REMOTE))['revision'])
        req2,_=self.pending(number=101,base=1)
        self.assertEqual(2,self.service.commit(self.owner,req2)['revision'])
        self.assertEqual(1,self.service.commit(self.owner,req)['revision'])
    def test_restart_with_same_storage(self):
        req,_=self.pending()
        restarted=FamilyService(self.store,self.blobs,lambda:self.now)
        self.assertEqual(1,restarted.commit(self.owner,req)['revision'])
    def test_editor_cannot_change_owner(self):
        self.member(self.editor,'editor')
        req,_=self.pending(self.editor,data=archive(envelope(owner='editor')))
        self.assert_error('invalid-argument',lambda:self.service.commit(self.editor,req))
        req,_=self.pending(self.editor,number=101)
        self.assertEqual('owner',self.service.commit(self.editor,req)['ownerUid'])
    def test_revoked_during_upload(self):
        self.member(self.editor,'editor'); req,_=self.pending(self.editor)
        self.member(self.editor,'editor',False)
        self.assert_error('permission-denied',lambda:self.service.commit(self.editor,req))
    def test_revoked_after_validation_before_publish(self):
        self.member(self.editor,'editor'); req,_=self.pending(self.editor)
        self.blobs.after_put=lambda:self.member(self.editor,'viewer')
        self.assert_error('permission-denied',lambda:self.service.commit(self.editor,req))
        self.assertEqual(0,self.service.resume(self.owner,dict(remoteId=REMOTE))['revision'])
    def test_expiry_after_validation(self):
        req,_=self.pending()
        self.blobs.after_put=lambda:setattr(self,'now',self.now+UPLOAD_TTL_MS)
        self.assert_error('failed-precondition',lambda:self.service.commit(self.owner,req))
    def test_replay_cannot_bypass_revocation(self):
        self.member(self.editor,'editor'); req,_=self.pending(self.editor); self.service.commit(self.editor,req)
        self.member(self.editor,'editor',False)
        self.assert_error('permission-denied',lambda:self.service.commit(self.editor,req))
    def test_two_writers_single_winner(self):
        a,_=self.pending(number=100); b,_=self.pending(number=101)
        def run(req):
            try: return self.service.commit(self.owner,req)['revision']
            except FamilyError as e: return e.code
        with ThreadPoolExecutor(max_workers=2) as pool: results=list(pool.map(run,[a,b]))
        self.assertCountEqual([1,'aborted'],results)
    def test_same_commit_concurrent(self):
        req,_=self.pending()
        with ThreadPoolExecutor(max_workers=2) as pool: results=list(pool.map(lambda _:self.service.commit(self.owner,req),range(2)))
        self.assertEqual(results[0],results[1]); self.assertEqual(1,results[0]['revision'])
    def test_upload_replay_requires_identical_binding(self):
        req,data=self.pending()
        self.service.begin_upload(self.owner,data)
        self.assert_error('already-exists',lambda:self.service.begin_upload(self.owner,data|{'sha256':'a'*64}))
        self.member(self.editor,'editor')
        self.assert_error('already-exists',lambda:self.service.begin_upload(self.editor,data))
        self.assert_error('permission-denied',lambda:self.service.commit(self.editor,req))
    def test_missing_corrupt_and_path_spoof(self):
        req,request=self.pending(); record=self.service.begin_upload(self.owner,request)
        del self.blobs.data[record['stagingPath']]
        self.assert_error('failed-precondition',lambda:self.service.commit(self.owner,req))
        self.blobs.data[record['stagingPath']]=b'x'*request['size']
        self.assert_error('invalid-argument',lambda:self.service.commit(self.owner,req))
        self.assert_error('invalid-argument',lambda:self.service.commit(self.owner,req|{'archiveId':'../other'}))
        self.assertEqual(0,self.service.resume(self.owner,dict(remoteId=REMOTE))['revision'])
    def test_final_collision_not_overwritten(self):
        req,_=self.pending(); path=f"familyWardrobes/{REMOTE}/snapshots/{req['archiveId']}.zip"
        self.blobs.data[path]=b'other'
        self.assert_error('already-exists',lambda:self.service.commit(self.owner,req))
        self.assertEqual(b'other',self.blobs.data[path])
    def test_archive_then_resume_denied(self):
        path=f'familyWardrobes/{REMOTE}'
        self.store.run(lambda tx:tx.set(path,tx.get(path)|{'archived':True}))
        self.assert_error('permission-denied',lambda:self.service.resume(self.owner,dict(remoteId=REMOTE)))
    def test_quotas_and_replay(self):
        self.store.seed('familyQuotas/owner',dict(roots=50,day=self.now//86400000,uploads=120))
        self.assert_error('resource-exhausted',lambda:self.pending())
        self.assert_error('resource-exhausted',lambda:self.service.create(self.owner,dict(remoteId=uid(800),wardrobeId=WARDROBE)))
        self.service.create(self.owner,dict(remoteId=REMOTE,wardrobeId=WARDROBE))
    def test_strict_numbers(self):
        for bad in (True, -1, 1.0, '0', 9007199254740992):
            self.assert_error('invalid-argument',lambda:self.service.begin_upload(self.owner,dict(remoteId=REMOTE,archiveId=uid(100),
                baseRevision=bad,sha256='a'*64,size=1)))


class ArchiveTests(unittest.TestCase):
    def validate(self,data):
        return validate_archive(data,dict(remoteId=REMOTE,wardrobeId=WARDROBE,ownerUid='owner'),0,hashlib.sha256(data).hexdigest())
    def reject(self,env):
        with self.assertRaises(FamilyError): self.validate(archive(env))
    def test_all_collections_and_500_items(self):
        state=snapshot()
        for n in range(100,600): state['garments'].append(state['garments'][0]|{'id':uid(n)})
        self.assertEqual(501,self.validate(archive(envelope(state=state)))['garments'])
    def test_identity_formats_and_privilege_fields(self):
        for field,value in [('ownerUid','other'),('remoteId',uid(300)),('wardrobeId',uid(300)),('version',True),('baseRevision',True),('format','family-wardrobe'),('role','owner')]:
            self.reject(envelope()|{field:value})
    def test_cross_wardrobe_and_dangling_references(self):
        for kind,field in [('garments','wardrobeId'),('parts','garmentId'),('wears','garmentId'),('plans','outfitId'),
                           ('capsuleItems','capsuleId'),('capsuleOutfits','outfitId'),('wishEvents','wishId'),('garments','customColorId')]:
            with self.subTest(kind=kind,field=field):
                state=snapshot(); state[kind][0][field]=uid(800); self.reject(envelope(state=state))
    def test_duplicate_records_and_unknown_keys(self):
        for kind in FIELDS:
            if kind == 'ideas': continue
            with self.subTest(kind=kind):
                state=snapshot(); state[kind].append(copy.deepcopy(state[kind][0])); self.reject(envelope(state=state))
                state=snapshot(); state[kind][0]['memberUid']='stranger'; self.reject(envelope(state=state))
    def test_receipt_scope_and_photo_refs(self):
        for field,value in [('wardrobeId',uid(800)),('customCategoryId',uid(800)),('photoId',uid(800)),('ownerUid','stranger')]:
            state=snapshot(); g=json.loads(state['purchaseUndos'][0]['garmentJson']); g[field]=value
            state['purchaseUndos'][0]['garmentJson']=json.dumps(g); self.reject(envelope(state=state))
    def test_old_receipt_nullable_fields(self):
        state=snapshot(); g=json.loads(state['purchaseUndos'][0]['garmentJson']); del g['customCategoryId']; del g['customColorId']
        state['purchaseUndos'][0]['garmentJson']=json.dumps(g); self.validate(archive(envelope(state=state)))
    def test_photo_exact_set_format_and_crc(self):
        for data in [archive(extras={'../private.txt':b'x'}),archive(extras={'unused.jpg':b'x'}),archive(photo=b'bad'),archive()[:-30]]:
            with self.subTest(size=len(data)),self.assertRaises(FamilyError):self.validate(data)
        data=bytearray(archive()); data[50]^=1
        with self.assertRaises(FamilyError):self.validate(bytes(data))
    def test_duplicate_json_zip_nan(self):
        for text in ['{"format":1,"format":2}', '{"value":NaN}', '['*2000]:
            with self.assertRaises(FamilyError):self.validate(archive(manifest=text))
        data=io.BytesIO(archive())
        with zipfile.ZipFile(data,'a') as z: z.writestr('manifest.json','{}')
        with self.assertRaises(FamilyError):self.validate(data.getvalue())
    def test_plan_capsule_signature_and_layout_consistency(self):
        for kind,field,value in [('outfits','signature','wrong'),('plans','worn',False),('plans','date','2026-02-30'),
                                 ('feedback','signature',GARMENT+'|'+GARMENT),('customColors','baseColor','red'),
                                 ('customCategories','nameKey','different'),('outfits','layout','[{"key":"other"}]')]:
            state=snapshot();state[kind][0][field]=value;self.reject(envelope(state=state))
        state=snapshot();state['capsuleItems']=[];self.reject(envelope(state=state))
    def test_empty_wardrobe_and_no_photos(self):
        state=snapshot()
        for k in FIELDS:
            if k!='wardrobes':state[k]=[]
        state['photoFiles']={}
        data=io.BytesIO()
        with zipfile.ZipFile(data,'w') as z:z.writestr('manifest.json',json.dumps(envelope(state=state)))
        self.assertEqual(0,self.validate(data.getvalue())['photos'])
    def test_inspiration_and_missing_wish(self):
        state=snapshot();state['ideas']=[dict(outfitId=OUTFIT,partId='idea:top_shirt_white',wishId=None,category='top',name='Example',color='white',photoId='')]
        state['outfits'][0]['signature']='|'.join(sorted([GARMENT,'idea:top_shirt_white']))
        self.validate(archive(envelope(state=state)))
        state['ideas'][0]['wishId']=uid(800); self.reject(envelope(state=state))


if __name__=='__main__':unittest.main()
