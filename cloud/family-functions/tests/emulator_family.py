"""Real Firestore transactions / Storage generations / callable Auth. Demo-only."""
import hashlib
import json
import os
import secrets
import sys
import unittest
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import firebase_admin
from firebase_admin import auth, firestore, storage
from family.firebase_adapters import FirestoreStore, StorageArchives
from family.service import Actor, FamilyService
from family.errors import FamilyError
from fixtures import *
import test_family as base

PROJECT='demo-wardrobe'
for key in ('FIRESTORE_EMULATOR_HOST','FIREBASE_STORAGE_EMULATOR_HOST','FIREBASE_AUTH_EMULATOR_HOST'):
    if not os.environ.get(key,'').startswith(('127.0.0.1:','localhost:')):
        raise RuntimeError('Local emulators required: '+key)
if os.environ.get('GCLOUD_PROJECT') != PROJECT:
    raise RuntimeError('Only demo-wardrobe is allowed')
from firebase_admin import credentials
from google.auth.credentials import AnonymousCredentials
class EmulatorCredentials(credentials.Base):
    def get_credential(self): return AnonymousCredentials()
firebase_admin.initialize_app(EmulatorCredentials(), options={'projectId':PROJECT,'storageBucket':PROJECT+'.appspot.com'})
DB=firestore.client()
BUCKET=storage.bucket()


def clear_db():
    url=f'http://{os.environ["FIRESTORE_EMULATOR_HOST"]}/emulator/v1/projects/{PROJECT}/databases/(default)/documents'
    urllib.request.urlopen(urllib.request.Request(url,method='DELETE'),timeout=20).read()


class EmulatorStore(FirestoreStore):
    def seed(self,path,value): DB.document(path).set(value)


class FirestoreTests(base.ServiceTests):
    def setUp(self):
        clear_db()
        self.store=EmulatorStore(DB); self.blobs=base.MemoryArchives(); self.now=1800000000000
        self.service=FamilyService(self.store,self.blobs,lambda:self.now)
        self.owner=Actor('owner',True); self.editor=Actor('editor',True); self.viewer=Actor('viewer',True)
        self.service.create(self.owner,dict(remoteId=REMOTE,wardrobeId=WARDROBE))


class StorageTests(unittest.TestCase):
    def test_generation_immutability_and_commit(self):
        clear_db()
        for blob in BUCKET.list_blobs(prefix='familyWardrobes/'): blob.delete()
        service=FamilyService(EmulatorStore(DB),StorageArchives(BUCKET))
        owner=Actor('owner',True)
        service.create(owner,dict(remoteId=REMOTE,wardrobeId=WARDROBE))
        data=archive(); sha=hashlib.sha256(data).hexdigest()
        pending=service.begin_upload(owner,dict(remoteId=REMOTE,archiveId=uid(901),baseRevision=0,sha256=sha,size=len(data)))
        BUCKET.blob(pending['stagingPath']).upload_from_string(data,content_type='application/zip',if_generation_match=0)
        req=dict(remoteId=REMOTE,archiveId=uid(901))
        result=service.commit(owner,req)
        self.assertEqual(1,result['revision'])
        self.assertEqual(result,service.commit(owner,req))
        archives=StorageArchives(BUCKET)
        self.assertEqual(data,archives.read(result['head']['archive'],len(data)))
        final_blob=BUCKET.get_blob(result['head']['archive'])
        self.assertNotIn('firebaseStorageDownloadTokens', final_blob.metadata or {})
        self.assertEqual(str(archives.put_once(result['head']['archive'],data,sha)),result['head']['generation'])
        with self.assertRaises(FamilyError):archives.put_once(result['head']['archive'],b'x'*len(data),'a'*64)


class CallableTests(unittest.TestCase):
    def call(self,name,data,token=None):
        request=urllib.request.Request(f'http://127.0.0.1:5001/{PROJECT}/europe-west1/{name}',
            data=json.dumps({'data':data}).encode(),headers={'Content-Type':'application/json'} | ({'Authorization':'Bearer '+token} if token else {}))
        try:
            with urllib.request.urlopen(request,timeout=60) as r:return json.load(r)
        except urllib.error.HTTPError as e:return json.load(e)
    def login(self,verified):
        import uuid
        email=str(uuid.uuid4())+'@example.test'
        # Fresh credential for the local emulator; no embedded password prefix.
        password=secrets.token_urlsafe(32)
        user=auth.create_user(email=email,password=password,email_verified=verified)
        req=urllib.request.Request('http://'+os.environ['FIREBASE_AUTH_EMULATOR_HOST']+'/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=emulator',
            data=json.dumps({'email':email,'password':password,'returnSecureToken':True}).encode(),headers={'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=20) as r: token=json.load(r)['idToken']
        return user,token
    def test_callable_auth_owner_spoof_and_full_flow(self):
        clear_db()
        for blob in BUCKET.list_blobs(prefix='familyWardrobes/'):blob.delete()
        data=dict(remoteId=REMOTE,wardrobeId=WARDROBE)
        self.assertEqual('UNAUTHENTICATED',self.call('family_create',data)['error']['status'])
        unverified,token=self.login(False)
        self.assertEqual('UNAUTHENTICATED',self.call('family_create',data,token)['error']['status'])
        owner,token=self.login(True)
        self.assertEqual('INVALID_ARGUMENT',self.call('family_create',data|{'ownerUid':'other'},token)['error']['status'])
        self.assertEqual('UNAUTHENTICATED',self.call('family_create',data|{'expectedActorUid':'other'},token)['error']['status'])
        self.assertEqual(owner.uid,self.call('family_create',data|{'expectedActorUid':owner.uid},token)['result']['ownerUid'])
        archive_data=archive(envelope(owner=owner.uid)); sha=hashlib.sha256(archive_data).hexdigest()
        # Match Android Firebase Serializer: Kotlin Long is an Int64Value object.
        mobile_size={'@type':'type.googleapis.com/google.protobuf.Int64Value','value':str(len(archive_data))}
        upload=self.call('family_begin_upload',dict(remoteId=REMOTE,archiveId=uid(902),baseRevision=0,sha256=sha,size=mobile_size),token)['result']
        missing=self.call('family_commit',dict(remoteId=REMOTE,archiveId=uid(902),expectedActorUid=owner.uid),token)
        self.assertEqual({'reason':'ARCHIVE_MISSING'},missing['error']['details'])
        BUCKET.blob(upload['stagingPath']).upload_from_string(archive_data,content_type='application/zip',if_generation_match=0)
        result=self.call('family_commit',dict(remoteId=REMOTE,archiveId=uid(902)),token)
        self.assertEqual(1,result['result']['revision'])
        self.assertEqual(1,self.call('family_resume',dict(remoteId=REMOTE),token)['result']['revision'])
        stranger,stranger_token=self.login(True)
        self.assertEqual('PERMISSION_DENIED',self.call('family_resume',dict(remoteId=REMOTE),stranger_token)['error']['status'])
        auth.update_user(owner.uid,disabled=True)
        self.assertEqual('UNAUTHENTICATED',self.call('family_resume',dict(remoteId=REMOTE),token)['error']['status'])


if __name__=='__main__': unittest.main(verbosity=2)
