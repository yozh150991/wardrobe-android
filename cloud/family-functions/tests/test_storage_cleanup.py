"""Audit B-01: staged copies and superseded snapshots are removed; edits store photos without recompression."""
import hashlib
import io
import unittest
import zipfile
from family.errors import FamilyError
from family.service import Actor, FamilyService, STAGING_PURGE_AGE_MS
from test_family import MemoryArchives
from test_membership import TransactionStore
from fixtures import REMOTE, WARDROBE, GARMENT, uid, archive, envelope


class StorageCleanupTests(unittest.TestCase):
    def setUp(self):
        self.store = TransactionStore(); self.blobs = MemoryArchives(); self.now = 1800000000000
        self.service = FamilyService(self.store, self.blobs, lambda: self.now)
        self.owner = Actor('owner', True); self.editor = Actor('editor', True)
        self.service.create(self.owner, dict(remoteId=REMOTE, wardrobeId=WARDROBE))
        self.store.seed(f'familyWardrobes/{REMOTE}/members/editor', dict(active=True, role='editor', expiresAt=self.now + 10**9))

    def root(self): return self.store.data[f'familyWardrobes/{REMOTE}']
    def snapshot_paths(self): return sorted(p for p in self.blobs.data if '/snapshots/' in p)
    def staging_paths(self): return sorted(p for p in self.blobs.data if '/staging/' in p)

    def begin(self, number, base, actor=None):
        data = archive(envelope(base))
        request = dict(remoteId=REMOTE, archiveId=uid(number), baseRevision=base, sha256=hashlib.sha256(data).hexdigest(), size=len(data))
        upload = self.service.begin_upload(actor or self.owner, request)
        self.blobs.data[upload['stagingPath']] = data
        return upload

    def commit(self, number, base, actor=None):
        self.begin(number, base, actor)
        return self.service.commit(actor or self.owner, dict(remoteId=REMOTE, archiveId=uid(number)))

    def test_commit_removes_its_staged_copy_and_replay_still_returns_the_receipt(self):
        result = self.commit(100, 0)
        self.assertEqual([], self.staging_paths())
        self.assertEqual(result, self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(100))))

    def test_only_current_and_previous_snapshots_are_kept(self):
        for revision in range(4):
            self.commit(100 + revision, revision)
        root = self.root()
        self.assertEqual(4, root['revision'])
        self.assertEqual(sorted([root['head']['archive'], root['previousArchive']]), self.snapshot_paths())

    def test_edits_retire_snapshots_and_store_photos_without_recompression(self):
        self.commit(100, 0)
        for number, base in [(201, 1), (202, 2), (203, 3)]:
            self.service.edit_item(self.editor, dict(remoteId=REMOTE, editId=uid(number), garmentId=GARMENT,
                                                     baseRevision=base, changes={'location': f'Shelf {number}'}))
        root = self.root(); self.assertEqual(4, root['revision'])
        self.assertEqual(sorted([root['head']['archive'], root['previousArchive']]), self.snapshot_paths())
        with zipfile.ZipFile(io.BytesIO(self.blobs.data[root['head']['archive']])) as z:
            methods = {i.filename: i.compress_type for i in z.infolist()}
        self.assertEqual(zipfile.ZIP_DEFLATED, methods.pop('manifest.json'))
        self.assertTrue(methods and set(methods.values()) == {zipfile.ZIP_STORED})

    def test_edit_rejects_a_head_whose_bytes_do_not_match_its_checksum(self):
        self.commit(100, 0)
        path = self.root()['head']['archive']; self.blobs.data[path] = self.blobs.data[path][:-1] + b'x'
        with self.assertRaises(FamilyError):
            self.service.edit_item(self.editor, dict(remoteId=REMOTE, editId=uid(201), garmentId=GARMENT, baseRevision=1, changes={'size': 'M'}))

    def test_final_copy_of_a_commit_that_lost_the_race_is_removed(self):
        self.begin(100, 0); self.begin(101, 0, self.editor)
        def competitor():
            self.blobs.after_put = None
            self.service.commit(self.editor, dict(remoteId=REMOTE, archiveId=uid(101)))
        self.blobs.after_put = competitor
        with self.assertRaises(FamilyError) as caught:
            self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(100)))
        self.assertEqual('aborted', caught.exception.code)
        self.assertEqual([self.root()['head']['archive']], self.snapshot_paths())

    def test_missing_staged_copy_after_a_concurrent_commit_returns_that_receipt(self):
        self.begin(100, 0)
        read = self.blobs.read; calls = []
        def racing_read(path, size):
            if not calls:
                calls.append(path); self.blobs.read = read
                expected = self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(100)))
                calls.append(expected)
                raise FamilyError('failed-precondition', 'Archive has not been uploaded', 'ARCHIVE_MISSING')
            return read(path, size)
        self.blobs.read = racing_read
        result = self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(100)))
        self.assertEqual(calls[1], result)

    def test_new_upload_purges_only_this_authors_abandoned_staging(self):
        self.commit(100, 0)
        prefix = f'familyWardrobes/{REMOTE}/staging'
        old_own, fresh_own, old_other = f'{prefix}/owner/{uid(300)}.zip', f'{prefix}/owner/{uid(301)}.zip', f'{prefix}/editor/{uid(302)}.zip'
        for path, age in [(old_own, STAGING_PURGE_AGE_MS + 1), (fresh_own, 1000), (old_other, STAGING_PURGE_AGE_MS + 1)]:
            self.blobs.data[path] = b'zip'; self.blobs.created[path] = self.now - age
        upload = self.begin(400, 1)
        self.assertNotIn(old_own, self.blobs.data)
        self.assertIn(fresh_own, self.blobs.data); self.assertIn(old_other, self.blobs.data); self.assertIn(upload['stagingPath'], self.blobs.data)


if __name__ == '__main__':
    unittest.main()
