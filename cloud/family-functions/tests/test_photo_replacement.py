"""FAM-03 photo replacement (0.14.10): the Android client publishes a full scoped snapshot in
which one existing item points to a new photo, through the existing begin_upload/commit flow."""
import copy
import hashlib
import io
import json
import unittest
import zipfile
from PIL import Image
from family.errors import FamilyError
from family.service import Actor, FamilyService, UPLOAD_TTL_MS
from fixtures import REMOTE, WARDROBE, PHOTO, GARMENT, uid, archive, envelope
from test_family import MemoryArchives
from test_membership import TransactionStore

NEW = uid(920)


def jpeg():
    out = io.BytesIO(); Image.new('RGB', (5, 6), (10, 120, 200)).save(out, format='JPEG'); return out.getvalue()


def zipped(env, photos):
    target = io.BytesIO()
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('manifest.json', json.dumps(env))
        for name, data in photos.items(): z.writestr(f'photos/{name}', data)
    return target.getvalue()


class PhotoReplacementTests(unittest.TestCase):
    def setUp(self):
        self.now = 1800000000000; self.store = TransactionStore(); self.blobs = MemoryArchives()
        self.service = FamilyService(self.store, self.blobs, lambda: self.now)
        self.owner = Actor('owner', True); self.editor = Actor('editor', True); self.viewer = Actor('viewer', True)
        self.service.create(self.owner, dict(remoteId=REMOTE, wardrobeId=WARDROBE))
        for actor, role in ((self.editor, 'editor'), (self.viewer, 'viewer')):
            self.store.seed(f'familyWardrobes/{REMOTE}/members/{actor.uid}', dict(active=True, role=role, expiresAt=self.now + 2*UPLOAD_TTL_MS))
        request, _ = self.begin(archive(envelope()), 0, uid(900), self.owner)
        self.service.commit(self.owner, request)

    def begin(self, payload, base=1, id=None, actor=None):
        request = dict(remoteId=REMOTE, archiveId=id or uid(901), baseRevision=base, sha256=hashlib.sha256(payload).hexdigest(), size=len(payload))
        ticket = self.service.begin_upload(actor or self.editor, request)
        self.blobs.data[ticket['stagingPath']] = payload
        return dict(remoteId=REMOTE, archiveId=request['archiveId']), request

    def current(self):
        root = self.store.data[f'familyWardrobes/{REMOTE}']
        with zipfile.ZipFile(io.BytesIO(self.blobs.data[root['head']['archive']])) as z:
            return json.loads(z.read('manifest.json')), {n: z.read(n) for n in z.namelist() if n != 'manifest.json'}

    def replacement(self, before, photos, base=1, keep_old=True):
        env = copy.deepcopy(before); env['baseRevision'] = base
        item = next(g for g in env['snapshot']['garments'] if g['id'] == GARMENT)
        item.update(photoId=NEW, photoFingerprint=hashlib.sha256(jpeg()).hexdigest())
        files = {NEW: 'jpg'}; entries = {f'{NEW}.jpg': jpeg()}
        if keep_old:  # the fixture's wish still references the old photo
            files[PHOTO] = 'png'; entries[f'{PHOTO}.png'] = photos[f'photos/{PHOTO}.png']
        env['snapshot']['photoFiles'] = files
        return zipped(env, entries)

    def error(self, code, fn):
        with self.assertRaises(FamilyError) as caught: fn()
        self.assertEqual(code, caught.exception.code)

    def test_editor_replaces_one_photo_and_everything_else_is_unchanged(self):
        before, photos = self.current()
        request, _ = self.begin(self.replacement(before, photos))
        receipt = self.service.commit(self.editor, request)
        after, new_photos = self.current()
        self.assertEqual(2, receipt['revision']); self.assertEqual('editor', receipt['head']['authorUid'])
        changed = next(g for g in after['snapshot']['garments'] if g['id'] == GARMENT)
        self.assertEqual(NEW, changed['photoId'])
        expected = copy.deepcopy(before); expected['baseRevision'] = 1
        original = next(g for g in expected['snapshot']['garments'] if g['id'] == GARMENT)
        original.update(photoId=NEW, photoFingerprint=changed['photoFingerprint'])
        expected['snapshot']['photoFiles'] = {NEW: 'jpg', PHOTO: 'png'}
        self.assertEqual(expected, after)
        self.assertEqual(photos[f'photos/{PHOTO}.png'], new_photos[f'photos/{PHOTO}.png'])
        self.assertEqual(jpeg(), new_photos[f'photos/{NEW}.jpg'])

    def test_photo_still_referenced_elsewhere_cannot_be_dropped(self):
        before, photos = self.current()
        request, _ = self.begin(self.replacement(before, photos, keep_old=False))
        self.error('invalid-argument', lambda: self.service.commit(self.editor, request))
        self.assertEqual(1, self.store.data[f'familyWardrobes/{REMOTE}']['revision'])

    def test_viewer_cannot_publish_a_replacement(self):
        before, photos = self.current(); payload = self.replacement(before, photos)
        self.error('permission-denied', lambda: self.begin(payload, actor=self.viewer))

    def test_replacement_on_a_stale_base_is_a_conflict(self):
        before, photos = self.current()
        self.service.edit_item(self.owner, dict(remoteId=REMOTE, editId=uid(902), garmentId=GARMENT, baseRevision=1, changes={'location': 'Moved'}))
        self.error('aborted', lambda: self.begin(self.replacement(before, photos)))
        self.assertEqual('Moved', next(g for g in self.current()[0]['snapshot']['garments'] if g['id'] == GARMENT)['location'])


if __name__ == '__main__':
    unittest.main()
