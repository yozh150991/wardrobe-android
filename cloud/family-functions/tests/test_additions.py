"""Later-revision item/photo publication through the existing production protocol."""
import copy
import hashlib
import io
import json
import unittest
import zipfile
from family.errors import FamilyError
from family.service import Actor, FamilyService, UPLOAD_TTL_MS
from fixtures import REMOTE, WARDROBE, PHOTO, GARMENT, uid, archive, envelope
from test_family import MemoryArchives
from test_membership import TransactionStore


def addition(source, base=1):
    env = copy.deepcopy(source); env['baseRevision'] = base
    item = copy.deepcopy(env['snapshot']['garments'][0]); item.update(id=uid(910), photoId=uid(911), name='Shared copy')
    env['snapshot']['garments'].append(item); env['snapshot']['photoFiles'][uid(911)] = 'png'
    with zipfile.ZipFile(io.BytesIO(archive())) as z: photo=z.read(f'photos/{PHOTO}.png')
    return archive(env, extras={f'photos/{uid(911)}.png':photo})


class AdditionTests(unittest.TestCase):
    def setUp(self):
        self.now=1800000000000; self.store=TransactionStore(); self.blobs=MemoryArchives()
        self.service=FamilyService(self.store,self.blobs,lambda:self.now)
        self.owner=Actor('owner',True); self.editor=Actor('editor',True); self.viewer=Actor('viewer',True)
        self.service.create(self.owner,dict(remoteId=REMOTE,wardrobeId=WARDROBE))
        self.member(self.editor,'editor'); self.member(self.viewer,'viewer')
        request,_=self.begin(archive(envelope()),0,uid(900),self.owner)
        self.service.commit(self.owner,request)
    def member(self,actor,role,active=True):
        self.store.seed(f'familyWardrobes/{REMOTE}/members/{actor.uid}',dict(active=active,role=role,expiresAt=self.now+2*UPLOAD_TTL_MS))
    def begin(self,payload,base=1,id=None,actor=None):
        request=dict(remoteId=REMOTE,archiveId=id or uid(901),baseRevision=base,sha256=hashlib.sha256(payload).hexdigest(),size=len(payload))
        ticket=self.service.begin_upload(actor or self.editor,request)
        self.blobs.data[ticket['stagingPath']]=payload
        return dict(remoteId=REMOTE,archiveId=request['archiveId']),request
    def error(self,code,fn):
        with self.assertRaises(FamilyError) as caught: fn()
        self.assertEqual(code,caught.exception.code)
    def current(self):
        root=self.store.data[f'familyWardrobes/{REMOTE}']
        with zipfile.ZipFile(io.BytesIO(self.blobs.data[root['head']['archive']])) as z:
            return json.loads(z.read('manifest.json')), {n:z.read(n) for n in z.namelist() if n!='manifest.json'}
    def test_editor_addition_preserves_every_existing_entity_and_photo(self):
        before,photos=self.current(); req,_=self.begin(addition(before)); receipt=self.service.commit(self.editor,req)
        after,new_photos=self.current(); self.assertEqual(2,receipt['revision'])
        expected=copy.deepcopy(before);expected['baseRevision']=1
        expected['snapshot']['garments']=after['snapshot']['garments'];expected['snapshot']['photoFiles']=after['snapshot']['photoFiles']
        self.assertEqual(expected,after);self.assertEqual(2,len(after['snapshot']['garments']))
        self.assertEqual(before['snapshot']['garments'][0],after['snapshot']['garments'][0])
        for name,photo in photos.items():self.assertEqual(photo,new_photos[name])
        self.assertEqual(2,len(new_photos));self.assertEqual('editor',receipt['head']['authorUid'])
    def test_lost_commit_response_replays_after_later_edit_without_duplicate(self):
        req,full=self.begin(addition(self.current()[0])); first=self.service.commit(self.editor,req)
        self.service.edit_item(self.owner,dict(remoteId=REMOTE,editId=uid(902),garmentId=GARMENT,baseRevision=2,changes={'location':'Changed later'}))
        self.assertEqual('committed',self.service.begin_upload(self.editor,full)['status'])
        self.assertEqual(first,self.service.commit(self.editor,req))
        self.assertEqual(2,len(self.current()[0]['snapshot']['garments']))
        self.assertEqual('Changed later',self.current()[0]['snapshot']['garments'][0]['location'])
    def test_stale_addition_rejected_and_explicit_rebase_preserves_other_edit(self):
        req,_=self.begin(addition(self.current()[0]))
        self.service.edit_item(self.owner,dict(remoteId=REMOTE,editId=uid(902),garmentId=GARMENT,baseRevision=1,changes={'location':'Other member'}))
        self.error('aborted',lambda:self.service.commit(self.editor,req))
        rebased,_=self.begin(addition(self.current()[0],2),2,uid(903))
        self.service.commit(self.editor,rebased); self.assertEqual('Other member',self.current()[0]['snapshot']['garments'][0]['location'])
    def test_viewer_stranger_unverified_cannot_stage_addition(self):
        for actor in [self.viewer,Actor('stranger',True),Actor('editor',False)]:
            self.error('permission-denied' if actor.email_verified else 'unauthenticated',lambda:self.begin(addition(self.current()[0]),actor=actor))
    def test_revocation_during_final_archive_write_does_not_publish(self):
        req,_=self.begin(addition(self.current()[0]))
        self.blobs.after_put=lambda:self.member(self.editor,'editor',False)
        self.error('permission-denied',lambda:self.service.commit(self.editor,req))
        self.assertEqual(1,len(self.current()[0]['snapshot']['garments']))
    def test_role_downgrade_blocks_pending_and_receipt_replay(self):
        req,_=self.begin(addition(self.current()[0]));self.member(self.editor,'viewer')
        self.error('permission-denied',lambda:self.service.commit(self.editor,req))
        self.member(self.editor,'editor');self.service.commit(self.editor,req);self.member(self.editor,'viewer')
        self.error('permission-denied',lambda:self.service.commit(self.editor,req))
    def test_expired_pending_requires_new_archive_id_but_same_item_identity(self):
        payload=addition(self.current()[0]);req,full=self.begin(payload);self.now+=UPLOAD_TTL_MS+1
        self.error('failed-precondition',lambda:self.service.begin_upload(self.editor,full))
        fresh,_=self.begin(payload,id=uid(904));self.service.commit(self.editor,fresh)
        self.assertEqual(uid(910),self.current()[0]['snapshot']['garments'][1]['id'])
    def test_existing_upload_uuid_cannot_change_photo_bytes_or_actor(self):
        payload=addition(self.current()[0]);req,full=self.begin(payload)
        self.error('already-exists',lambda:self.service.begin_upload(self.editor,full|{'sha256':'f'*64}))
        self.error('already-exists',lambda:self.service.begin_upload(self.owner,full))
    def test_missing_added_photo_and_unreferenced_private_photo_rejected(self):
        payload=addition(self.current()[0]); buf=io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(payload)) as z,zipfile.ZipFile(buf,'w') as out:
            for n in z.namelist():
                if uid(911) not in n:out.writestr(n,z.read(n))
        req,_=self.begin(buf.getvalue());self.error('invalid-argument',lambda:self.service.commit(self.editor,req))
        payload=archive(envelope(1),extras={f'photos/{uid(999)}.png':b'private-unreferenced'})
        req,_=self.begin(payload,id=uid(905));self.error('invalid-argument',lambda:self.service.commit(self.editor,req))
    def test_foreign_wardrobe_reference_in_added_item_rejected(self):
        env=self.current()[0];env['snapshot']['garments'][0]['wardrobeId']=uid(999)
        req,_=self.begin(addition(env));self.error('invalid-argument',lambda:self.service.commit(self.editor,req))
    def test_competing_full_snapshot_commit_wins_without_added_item(self):
        first,_=self.begin(addition(self.current()[0])); second,_=self.begin(archive(envelope(1)),id=uid(906),actor=self.owner)
        self.service.commit(self.owner,second);self.error('aborted',lambda:self.service.commit(self.editor,first))
        self.assertEqual(1,len(self.current()[0]['snapshot']['garments']))
    def test_parallel_same_uuid_commit_returns_same_receipt(self):
        from concurrent.futures import ThreadPoolExecutor
        request,_=self.begin(addition(self.current()[0]))
        with ThreadPoolExecutor(2) as pool:results=list(pool.map(lambda _:self.service.commit(self.editor,request),range(2)))
        self.assertEqual(results[0],results[1]);self.assertEqual(2,len(self.current()[0]['snapshot']['garments']))

if __name__=='__main__':unittest.main()
