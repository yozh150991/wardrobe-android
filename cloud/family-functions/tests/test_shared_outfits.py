"""Shared outfit publications use the existing scoped begin/commit contract, not private data."""
import copy
import unittest
from fixtures import REMOTE, WARDROBE, GARMENT, OUTFIT, CAPSULE, uid
import test_photo_replacement as photo_tests


class SharedOutfitTests(unittest.TestCase):
    setUp = photo_tests.PhotoReplacementTests.setUp
    begin = photo_tests.PhotoReplacementTests.begin
    current = photo_tests.PhotoReplacementTests.current
    error = photo_tests.PhotoReplacementTests.error

    def payload(self, mutate, base=1):
        env, photos = self.current()
        env = copy.deepcopy(env); env['baseRevision'] = base
        mutate(env['snapshot'])
        return photo_tests.zipped(env, {key.removeprefix('photos/'): value for key, value in photos.items()})

    @staticmethod
    def add(s):
        item = copy.deepcopy(s['garments'][0]); item.update(id=uid(930), name='Shoes', category='shoes', customCategoryId=None)
        s['garments'].append(item)
        outfit = copy.deepcopy(s['outfits'][0]); outfit.update(id=uid(931), name='Shared look', signature=uid(930))
        s['outfits'].append(outfit); s['parts'].append(dict(outfitId=uid(931), garmentId=uid(930)))

    def test_editor_creates_shared_outfit_and_retry_is_idempotent(self):
        request, _ = self.begin(self.payload(self.add))
        receipt = self.service.commit(self.editor, request)
        again = self.service.commit(self.editor, request)
        self.assertEqual(receipt, again)
        self.assertEqual(2, len(self.current()[0]['snapshot']['outfits']))
        self.assertEqual(2, receipt['revision'])

    def test_edit_preserves_other_collections_and_photo_bytes(self):
        before, photos = self.current()
        def edit(s):
            s['outfits'][0].update(name='Edited', background='sage', layout='[]')
        request, _ = self.begin(self.payload(edit)); self.service.commit(self.editor, request)
        after, after_photos = self.current()
        expected = copy.deepcopy(before); expected['baseRevision'] = 1; edit(expected['snapshot'])
        self.assertEqual(expected, after); self.assertEqual(photos, after_photos)

    def test_viewer_cannot_create_or_edit_outfit(self):
        self.error('permission-denied', lambda: self.begin(self.payload(self.add), actor=self.viewer))

    def test_revoked_editor_cannot_commit_uploaded_outfit(self):
        request, _ = self.begin(self.payload(self.add))
        self.store.seed(f'familyWardrobes/{REMOTE}/members/editor', dict(active=False, role='editor', expiresAt=self.now + 999999))
        self.error('permission-denied', lambda: self.service.commit(self.editor, request))
        self.assertEqual(1, self.store.data[f'familyWardrobes/{REMOTE}']['revision'])

    def test_concurrent_edit_causes_conflict_without_overwrite(self):
        request, _ = self.begin(self.payload(self.add))
        self.service.edit_item(self.owner, dict(remoteId=REMOTE, editId=uid(940), garmentId=GARMENT, baseRevision=1, changes={'location':'New shelf'}))
        self.error('aborted', lambda: self.service.commit(self.editor, request))
        self.assertEqual('New shelf', self.current()[0]['snapshot']['garments'][0]['location'])
        self.assertEqual(1, len(self.current()[0]['snapshot']['outfits']))

    def test_missing_garment_reference_is_rejected(self):
        def invalid(s): s['parts'][0]['garmentId'] = uid(999); s['outfits'][0]['signature'] = uid(999)
        request, _ = self.begin(self.payload(invalid))
        self.error('invalid-argument', lambda: self.service.commit(self.editor, request))

    def test_edit_with_linked_capsule_requires_reconciled_packing(self):
        def changed(s):
            self.add(s)
            s['parts'][0]['garmentId'] = uid(930); s['outfits'][0]['signature'] = uid(930)
            s['outfits'] = [s['outfits'][0]]; s['parts'] = [s['parts'][0]]
            s['capsuleItems'] = [dict(capsuleId=CAPSULE, garmentId=uid(930), manual=False, packed=False)]
        before = self.current()[0]['snapshot']
        request, _ = self.begin(self.payload(changed)); self.service.commit(self.editor, request)
        after = self.current()[0]['snapshot']
        self.assertEqual(before['wears'], after['wears']); self.assertEqual(before['plans'], after['plans'])
        self.assertEqual(uid(930), after['capsuleItems'][0]['garmentId'])


if __name__ == '__main__': unittest.main()
