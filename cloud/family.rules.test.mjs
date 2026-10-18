import {readFile} from 'node:fs/promises';
import {test,before,after,beforeEach} from 'node:test';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {doc,setDoc,getDoc} from 'firebase/firestore';
import {ref,uploadBytes,getBytes,deleteObject,updateMetadata,listAll} from 'firebase/storage';
let env;
const projectId='demo-wardrobe', remote='00000000-0000-0000-0000-000000000001', archive='00000000-0000-0000-0000-000000000100';
const path=`familyWardrobes/${remote}`, bytes=new Uint8Array([80,75,3,4]);
const stage=`${path}/staging/owner/${archive}.zip`, final=`${path}/snapshots/${archive}.zip`;
const context=(uid,verified=true)=>env.authenticatedContext(uid,{email_verified:verified});
const object=(c,p)=>ref(c.storage(`gs://${projectId}.appspot.com`),p);
async function seedDoc(p,v){await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),p),v));}
async function seedBlob(p){await env.withSecurityRulesDisabled(c=>uploadBytes(object(c,p),bytes,{contentType:'application/zip'}));}
async function member(uid,role,active=true,expiresAt=Date.now()+600000){await seedDoc(`${path}/members/${uid}`,{role,active,expiresAt});}
before(async()=>{env=await initializeTestEnvironment({projectId,firestore:{rules:await readFile('firestore.rules','utf8')},storage:{rules:await readFile('family-storage.rules','utf8')}});});
beforeEach(async()=>{
 await env.clearFirestore();await env.clearStorage();
 await seedDoc(path,{ownerUid:'owner',wardrobeId:remote,revision:0,archived:false,head:null});
 await seedDoc(`${path}/uploads/${archive}`,{authorUid:'owner',status:'pending',expiresAt:Date.now()+600000,baseRevision:0,stagingPath:stage,size:4});
});
after(async()=>await env?.cleanup());
test('reservation permits only exact authenticated immutable staging upload',async()=>{
 const owner=context('owner');
 for(const c of [context('stranger'),context('owner',false),env.unauthenticatedContext()])await assertFails(uploadBytes(object(c,stage),bytes,{contentType:'application/zip'}));
 await assertFails(uploadBytes(object(owner,stage),bytes,{contentType:'image/jpeg'}));
 await assertFails(uploadBytes(object(owner,stage),bytes,{contentType:'application/zip',customMetadata:{unexpected:'forged'}}));
 await assertSucceeds(uploadBytes(object(owner,stage),bytes,{contentType:'application/zip'}));
 await assertFails(uploadBytes(object(owner,stage),bytes,{contentType:'application/zip'}));
 await assertFails(getBytes(object(owner,stage)));await assertFails(deleteObject(object(owner,stage)));
});
test('viewer, revoked, expired and wrong revision cannot upload',async()=>{
 const editorStage=`${path}/staging/editor/${archive}.zip`;
 await seedDoc(`${path}/uploads/${archive}`,{authorUid:'editor',status:'pending',expiresAt:Date.now()+600000,baseRevision:0,stagingPath:editorStage,size:4});
 for(const [role,active,expiresAt] of [['viewer',true,Date.now()+600000],['owner',true,Date.now()+600000],['editor',false,Date.now()+600000],['editor',true,Date.now()-1]]){
  await member('editor',role,active,expiresAt);await assertFails(uploadBytes(object(context('editor'),editorStage),bytes,{contentType:'application/zip'}));
 }
 await member('editor','editor');
 await assertSucceeds(uploadBytes(object(context('editor'),editorStage),bytes,{contentType:'application/zip'}));
});
test('only current head readable; revocation and downgrade take effect',async()=>{
 await seedBlob(final);await seedBlob(final.replace(archive,'00000000-0000-0000-0000-000000000101'));
 await seedDoc(path,{ownerUid:'owner',revision:1,archived:false,head:{archive:final}});
 await member('viewer','viewer');await member('editor','editor');
 for(const uid of ['owner','viewer','editor'])await assertSucceeds(getBytes(object(context(uid),final)));
 for(const c of [context('stranger'),context('owner',false),env.unauthenticatedContext()])await assertFails(getBytes(object(c,final)));
 await member('viewer','viewer',false);await assertFails(getBytes(object(context('viewer'),final)));
 await member('editor','editor',true,Date.now()-1);await assertFails(getBytes(object(context('editor'),final)));
 await assertFails(getBytes(object(context('owner'),final.replace(archive,'00000000-0000-0000-0000-000000000101'))));
 await assertFails(updateMetadata(object(context('owner'),final),{customMetadata:{firebaseStorageDownloadTokens:'forged'}}));
 await assertFails(deleteObject(object(context('owner'),final)));await assertFails(listAll(object(context('owner'),`${path}/snapshots`)));
});
test('root, membership, quota and final object writes are server-only',async()=>{
 for(const p of [path,`${path}/members/stranger`,`${path}/uploads/${archive}`,'familyQuotas/owner']){
  await assertFails(setDoc(doc(context('owner').firestore(),p),{role:'owner'}));
  await assertFails(getDoc(doc(context('owner').firestore(),p)));
 }
 await assertFails(uploadBytes(object(context('owner'),final),bytes,{contentType:'application/zip'}));
});
test('family membership never opens the private account archive',async()=>{
 const privatePath=`users/owner/snapshots/${archive}.zip`;await seedBlob(privatePath);await member('viewer','viewer');
 await assertSucceeds(getBytes(object(context('owner'),privatePath)));
 await assertFails(getBytes(object(context('viewer'),privatePath)));
});

test('expired reservation, stale revision and wrong byte count are denied',async()=>{
 for(const changes of [{expiresAt:Date.now()-1},{baseRevision:1},{size:5},{status:'committed'}]){
  await seedDoc(`${path}/uploads/${archive}`,{authorUid:'owner',status:'pending',expiresAt:Date.now()+600000,baseRevision:0,stagingPath:stage,size:4,...changes});
  await assertFails(uploadBytes(object(context('owner'),stage),bytes,{contentType:'application/zip'}));
 }
 await seedBlob(final);
 await seedDoc(path,{ownerUid:'owner',revision:1,archived:true,head:{archive:final}});
 await assertFails(getBytes(object(context('owner'),final)));
});
