import { readFile } from 'node:fs/promises';
import { test, before, after, beforeEach } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, serverTimestamp, runTransaction } from 'firebase/firestore';
import { ref, uploadBytes, getBytes, deleteObject } from 'firebase/storage';

let env;
const projectId = 'demo-wardrobe';
const archive = 'users/alice/snapshots/11111111-1111-4111-8111-111111111111.zip';
const nextArchive = 'users/alice/snapshots/22222222-2222-4222-8222-222222222222.zip';
const head = db => doc(db, 'users/alice/state/head');
const value = (revision = 1, path = archive) => ({ revision, archive: path, fingerprint: 'a'.repeat(64), format: 8, wardrobes: 1, garments: 2, outfits: 1, updatedAt: serverTimestamp() });
const zip = new Uint8Array([80, 75, 3, 4]);
before(async () => {
  env = await initializeTestEnvironment({ projectId, firestore: { rules: await readFile('firestore.rules', 'utf8') }, storage: { rules: await readFile('storage.rules', 'utf8') } });
});
beforeEach(async () => { await env.clearFirestore(); await env.clearStorage(); });
after(async () => { await env?.cleanup(); });

test('owner can create/read head; unauthenticated and another UID cannot', async () => {
  const owner = env.authenticatedContext('alice').firestore();
  await assertSucceeds(setDoc(head(owner), value()));
  await assertSucceeds(getDoc(head(owner)));
  for (const context of [env.unauthenticatedContext(), env.authenticatedContext('bob')]) {
    await assertFails(getDoc(head(context.firestore())));
    await assertFails(setDoc(head(context.firestore()), value(2)));
  }
});
test('revision cannot skip, rewind, reuse or delete; malformed archive metadata is denied', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await assertFails(setDoc(head(db), value(2)));
  await assertFails(setDoc(head(db), { ...value(), fingerprint: 'bad' }));
  await assertFails(setDoc(head(db), { ...value(), archive: 'users/bob/snapshots/11111111-1111-4111-8111-111111111111.zip' }));
  await assertFails(setDoc(head(db), { ...value(), format: 7 }));
  await assertFails(setDoc(head(db), { ...value(), garments: -1 }));
  await assertFails(setDoc(head(db), { ...value(), unexpected: true }));
  await assertSucceeds(setDoc(head(db), value()));
  await assertFails(setDoc(head(db), value()));
  await assertFails(setDoc(head(db), value(3)));
  await assertFails(deleteDoc(head(db)));
  await assertSucceeds(setDoc(head(db), value(2, nextArchive)));
});
test('two devices with the same base cannot both commit', async () => {
  const a = env.authenticatedContext('alice').firestore();
  const b = env.authenticatedContext('alice').firestore();
  await setDoc(head(a), value());
  const commit = db => runTransaction(db, async tx => {
    const current = await tx.get(head(db));
    if (current.data().revision !== 1) throw new Error('Conflict');
    tx.set(head(db), value(2, nextArchive));
  });
  const results = await Promise.allSettled([commit(a), commit(b)]);
  if (results.filter(r => r.status === 'fulfilled').length !== 1) throw new Error('Exactly one CAS commit should succeed');
});
test('snapshots are private and immutable; active head is protected from deletion', async () => {
  const c = env.authenticatedContext('alice');
  const storage = c.storage(`gs://${projectId}.appspot.com`);
  const object = ref(storage, archive);
  await assertSucceeds(uploadBytes(object, zip, { contentType: 'application/zip' }));
  await assertSucceeds(getBytes(object));
  await assertFails(uploadBytes(object, zip, { contentType: 'application/zip' }));
  for (const context of [env.unauthenticatedContext(), env.authenticatedContext('bob')]) {
    const other = ref(context.storage(`gs://${projectId}.appspot.com`), archive);
    await assertFails(getBytes(other));
    await assertFails(deleteObject(other));
  }
  await assertFails(uploadBytes(ref(storage, nextArchive), zip, { contentType: 'image/jpeg' }));
  await assertSucceeds(setDoc(head(c.firestore()), value()));
  await assertFails(deleteObject(object));
  await assertSucceeds(uploadBytes(ref(storage, nextArchive), zip, { contentType: 'application/zip' }));
  await assertSucceeds(setDoc(head(c.firestore()), value(2, nextArchive)));
  await assertSucceeds(deleteObject(object));
});

// Prepared for emulator execution: newer archives must never be overwritten by older clients.
test('formats 8 and 9 upgrade to 10; downgrade and unknown format are denied', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await assertFails(setDoc(head(db), { ...value(), format: 11 }));
  await assertSucceeds(setDoc(head(db), value()));
  await assertSucceeds(setDoc(head(db), { ...value(2, nextArchive), format: 9 }));
  await assertFails(setDoc(head(db), value(3)));
  await assertSucceeds(setDoc(head(db), { ...value(3), format: 10 }));
  await assertFails(setDoc(head(db), { ...value(4, nextArchive), format: 9 }));
  await assertFails(setDoc(head(db), value(4, nextArchive)));
  await assertSucceeds(setDoc(head(db), { ...value(4, nextArchive), format: 10 }));
  await assertFails(getDoc(head(env.authenticatedContext('bob').firestore())));
});
