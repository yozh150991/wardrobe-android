import assert from 'node:assert/strict';
import { test } from 'node:test';
import { randomUUID } from 'node:crypto';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signInWithEmailAndPassword, signOut, sendPasswordResetEmail, verifyPasswordResetCode, confirmPasswordReset } from 'firebase/auth';
const projectId = 'demo-wardrobe';
const host = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const endpoint = new URL(`http://${host}`);
assert.ok(['127.0.0.1','localhost','[::1]'].includes(endpoint.hostname), 'Auth tests require a local emulator');
function client() {
 const app=initializeApp({projectId,apiKey:'fake-api-key-for-emulator',appId:'1:123:android:tests'},randomUUID());
 const auth=getAuth(app);connectAuthEmulator(auth,endpoint.href,{disableWarnings:true});return {app,auth};
}
test('email/password registration, duplicate refusal, wrong password and independent clients', async () => {
 const a=client(),b=client(), email=`wardrobe-${randomUUID()}@example.test`,password='Wardrobe-test-123';
 try {
  const registered=await createUserWithEmailAndPassword(a.auth,email,password);
  await assert.rejects(createUserWithEmailAndPassword(b.auth,email,password),e=>e.code==='auth/email-already-in-use');
  await assert.rejects(signInWithEmailAndPassword(b.auth,email,'Wrong-password-123'));
  const signed=await signInWithEmailAndPassword(b.auth,email,password);
  assert.equal(signed.user.uid,registered.user.uid);
  await signOut(a.auth);assert.equal(a.auth.currentUser,null);assert.equal(b.auth.currentUser.uid,registered.user.uid);
 } finally { await deleteApp(a.app);await deleteApp(b.app); }
});
test('reset email link accepts a password once and invalidates the old password', async () => {
 const a=client(),email=`wardrobe-${randomUUID()}@example.test`;
 try {
  const user=(await createUserWithEmailAndPassword(a.auth,email,'Wardrobe-before-123')).user;
  await signOut(a.auth);await sendPasswordResetEmail(a.auth,email);
  const response=await fetch(`${endpoint.origin}/emulator/v1/projects/${projectId}/oobCodes`);assert.equal(response.status,200);
  const code=(await response.json()).oobCodes.find(c=>c.email===email&&c.requestType==='PASSWORD_RESET');assert.ok(code);
  assert.equal(new URL(code.oobLink).searchParams.get('mode'),'resetPassword');
  assert.equal(await verifyPasswordResetCode(a.auth,code.oobCode),email);
  await confirmPasswordReset(a.auth,code.oobCode,'Wardrobe-after-456');
  await assert.rejects(verifyPasswordResetCode(a.auth,code.oobCode));
  await assert.rejects(signInWithEmailAndPassword(a.auth,email,'Wardrobe-before-123'));
  const signed=await signInWithEmailAndPassword(a.auth,email,'Wardrobe-after-456');assert.equal(signed.user.uid,user.uid);assert.equal(signed.user.email,email);
 } finally { await deleteApp(a.app); }
});
