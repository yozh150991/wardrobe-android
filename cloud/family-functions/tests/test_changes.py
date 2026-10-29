"""Delta model v1: additions and photo replacements applied by the server to the current head."""
import copy
import hashlib
import io
import json
import unittest
import zipfile
from PIL import Image
from family.errors import FamilyError
from family.service import Actor, FamilyService, MAX_UPLOADS_PER_DAY, UPLOAD_TTL_MS
from test_family import MemoryArchives
from test_membership import TransactionStore
from fixtures import REMOTE, WARDROBE, GARMENT, PHOTO, CATEGORY, uid, archive, envelope, snapshot


def png(color=(10, 120, 200, 255)):
    p = io.BytesIO(); Image.new('RGBA', (4, 3), color).save(p, format='PNG'); return p.getvalue()


def change_zip(photo_id, ext='png', extras=None, data=None):
    target = io.BytesIO()
    with zipfile.ZipFile(target, 'w') as z:
        z.writestr(f'photos/{photo_id}.{ext}', data if data is not None else png())
        for name, value in (extras or {}).items(): z.writestr(name, value)
    return target.getvalue()


class ChangeTests(unittest.TestCase):
    def setUp(self):
        self.store = TransactionStore(); self.blobs = MemoryArchives(); self.now = 1800000000000
        self.service = FamilyService(self.store, self.blobs, lambda: self.now)
        self.owner = Actor('owner', True); self.editor = Actor('editor', True); self.viewer = Actor('viewer', True)
        self.service.create(self.owner, dict(remoteId=REMOTE, wardrobeId=WARDROBE))
        for actor, role in [(self.editor, 'editor'), (self.viewer, 'viewer')]:
            self.store.seed(f'familyWardrobes/{REMOTE}/members/{actor.uid}', dict(active=True, role=role, expiresAt=self.now + 10**9))
        data = archive(envelope())
        upload = self.service.begin_upload(self.owner, dict(remoteId=REMOTE, archiveId=uid(700), baseRevision=0,
                                                            sha256=hashlib.sha256(data).hexdigest(), size=len(data)))
        self.blobs.data[upload['stagingPath']] = data
        self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(700)))

    def root(self): return self.store.data[f'familyWardrobes/{REMOTE}']
    def head(self):
        with zipfile.ZipFile(io.BytesIO(self.blobs.data[self.root()['head']['archive']])) as z:
            return json.loads(z.read('manifest.json'))['snapshot'], {i.filename: i for i in z.infolist()}
    def error(self, code, call, reason=None):
        with self.assertRaises(FamilyError) as caught: call()
        self.assertEqual(code, caught.exception.code)
        if reason: self.assertEqual(reason, caught.exception.reason)

    def garment(self, number, photo, **fields):
        g = copy.deepcopy(snapshot()['garments'][0])
        g.update(id=uid(number), photoId=photo, name=f'Item {number}', customCategoryId=None, customColorId=None, photoFingerprint='')
        g.update(fields); return g

    def add(self, number, photo, category=None, color=None, category_field=None, **fields):
        if category_field: fields['category'] = category_field
        return dict(kind='add', garment=self.garment(number, photo, **fields), customCategory=category, customColor=color,
                    photo=dict(id=photo, ext='png'))

    def stage(self, change_id, photo, actor=None, payload=None):
        payload = payload or change_zip(photo)
        ticket = self.service.begin_change(actor or self.editor, dict(remoteId=REMOTE, changeId=change_id,
                                                                     sha256=hashlib.sha256(payload).hexdigest(), size=len(payload)))
        self.blobs.data[ticket['stagingPath']] = payload
        return ticket

    def apply(self, change_id, change, actor=None):
        return self.service.apply_change(actor or self.editor, dict(remoteId=REMOTE, changeId=change_id, change=json.dumps(change)))

    def test_head_advertises_server_side_changes(self):
        self.assertIn('change1', self.service.resume(self.viewer, dict(remoteId=REMOTE))['features'])

    def test_new_item_with_new_label_and_reused_label_by_name(self):
        label = dict(id=uid(510), wardrobeId=WARDROBE, name='Knits', nameKey='knits', baseCategory='top')
        ticket = self.stage(uid(500), uid(501))
        receipt = self.apply(uid(500), self.add(502, uid(501), category=label, customCategoryId=uid(510)))
        self.assertEqual((2, 'committed', uid(500)), (receipt['revision'], receipt['status'], receipt['changeId']))
        self.assertNotIn(ticket['stagingPath'], self.blobs.data)
        state, entries = self.head()
        self.assertEqual(uid(510), next(g for g in state['garments'] if g['id'] == uid(502))['customCategoryId'])
        self.assertEqual('png', state['photoFiles'][uid(501)])
        self.assertEqual(zipfile.ZIP_STORED, entries[f'photos/{uid(501)}.png'].compress_type)
        same_name = dict(id=uid(520), wardrobeId=WARDROBE, name='My tops', nameKey='my tops', baseCategory='top')
        self.stage(uid(503), uid(504))
        self.apply(uid(503), self.add(505, uid(504), category=same_name, customCategoryId=uid(520)))
        state, _ = self.head()
        self.assertEqual(CATEGORY, next(g for g in state['garments'] if g['id'] == uid(505))['customCategoryId'])
        self.assertEqual(1, sum(1 for r in state['customCategories'] if r['nameKey'] == 'my tops'))

    def test_label_with_same_name_but_other_base_is_a_conflict(self):
        clash = dict(id=uid(520), wardrobeId=WARDROBE, name='My tops', nameKey='my tops', baseCategory='shoes')
        self.stage(uid(500), uid(501))
        change = self.add(502, uid(501), category=clash, category_field='shoes', customCategoryId=uid(520))
        self.error('failed-precondition', lambda: self.apply(uid(500), change), 'CHANGE_LABEL_CONFLICT')
        self.assertEqual(1, self.root()['revision'])

    def test_ids_already_in_the_head_are_refused(self):
        self.stage(uid(500), uid(501))
        self.error('already-exists', lambda: self.apply(uid(500), self.add(502, uid(501), id=GARMENT)))
        self.stage(uid(510), PHOTO)
        self.error('already-exists', lambda: self.apply(uid(510), dict(kind='photo', garmentId=GARMENT, fingerprint='',
                   photo=dict(id=PHOTO, ext='png'))))

    def test_expired_change_and_daily_quota(self):
        self.stage(uid(500), uid(501))
        self.now += UPLOAD_TTL_MS + 1
        self.error('failed-precondition', lambda: self.apply(uid(500), self.add(502, uid(501))), 'UPLOAD_EXPIRED')
        self.store.seed('familyQuotas/editor', dict(roots=0, day=self.now // 86400000, uploads=MAX_UPLOADS_PER_DAY))
        self.error('resource-exhausted', lambda: self.stage(uid(510), uid(511)))

    def test_a_commit_after_staging_does_not_conflict(self):
        self.stage(uid(500), uid(501))
        self.service.edit_item(self.owner, dict(remoteId=REMOTE, editId=uid(600), garmentId=GARMENT, baseRevision=1, changes={'size': 'M'}))
        receipt = self.apply(uid(500), self.add(502, uid(501)))
        self.assertEqual(3, receipt['revision'])
        state, _ = self.head()
        self.assertEqual('M', next(g for g in state['garments'] if g['id'] == GARMENT)['size'])
        self.assertTrue(any(g['id'] == uid(502) for g in state['garments']))

    def test_concurrent_commit_during_apply_is_retried_on_the_newer_head(self):
        self.stage(uid(500), uid(501))
        def competitor():
            self.blobs.after_put = None
            self.service.edit_item(self.owner, dict(remoteId=REMOTE, editId=uid(600), garmentId=GARMENT, baseRevision=1, changes={'size': 'S'}))
        self.blobs.after_put = competitor
        receipt = self.apply(uid(500), self.add(502, uid(501)))
        self.assertEqual(3, receipt['revision'])
        state, _ = self.head()
        self.assertEqual('S', next(g for g in state['garments'] if g['id'] == GARMENT)['size'])
        root = self.root()
        self.assertEqual(sorted([root['head']['archive'], root['previousArchive']]), sorted(p for p in self.blobs.data if '/snapshots/' in p))

    def test_replay_returns_the_receipt_and_another_payload_is_refused(self):
        self.stage(uid(500), uid(501))
        change = self.add(502, uid(501))
        receipt = self.apply(uid(500), change)
        self.assertEqual(receipt, self.apply(uid(500), change))
        self.assertEqual(2, self.root()['revision'])
        self.error('already-exists', lambda: self.apply(uid(500), self.add(503, uid(501))))

    def test_photo_replacement_keeps_still_referenced_photo_and_drops_unreferenced(self):
        self.stage(uid(500), uid(501))
        self.apply(uid(500), dict(kind='photo', garmentId=GARMENT, fingerprint='a' * 64, photo=dict(id=uid(501), ext='png')))
        state, entries = self.head()
        g = next(g for g in state['garments'] if g['id'] == GARMENT)
        self.assertEqual((uid(501), 'a' * 64), (g['photoId'], g['photoFingerprint']))
        self.assertIn(PHOTO, state['photoFiles'])  # the wish and history still show the old photo
        self.stage(uid(502), uid(503))
        self.apply(uid(502), dict(kind='photo', garmentId=GARMENT, fingerprint='', photo=dict(id=uid(503), ext='png')))
        state, entries = self.head()
        self.assertNotIn(uid(501), state['photoFiles']); self.assertNotIn(f'photos/{uid(501)}.png', entries)

    def test_missing_item_and_viewer_are_refused(self):
        self.stage(uid(500), uid(501))
        self.error('failed-precondition', lambda: self.apply(uid(500), dict(kind='photo', garmentId=uid(999), fingerprint='',
                   photo=dict(id=uid(501), ext='png'))), 'CHANGE_ITEM_MISSING')
        payload = change_zip(uid(511))
        self.error('permission-denied', lambda: self.service.begin_change(self.viewer, dict(remoteId=REMOTE, changeId=uid(510),
                   sha256=hashlib.sha256(payload).hexdigest(), size=len(payload))))

    def test_staged_archive_must_hold_exactly_the_announced_photo(self):
        for number, payload in [(500, change_zip(uid(501), extras={'manifest.json': '{}'})), (510, change_zip(uid(599)))]:
            self.stage(uid(number), uid(number + 1), payload=payload)
            self.error('invalid-argument', lambda: self.apply(uid(number), self.add(number + 2, uid(number + 1))))
        self.stage(uid(520), uid(521), payload=change_zip(uid(521), data=b'not an image'))
        self.error('invalid-argument', lambda: self.apply(uid(520), self.add(522, uid(521))))
        self.assertEqual(1, self.root()['revision'])

    def test_full_archive_endpoints_refuse_change_records(self):
        self.stage(uid(500), uid(501))
        payload = change_zip(uid(501))
        self.error('already-exists', lambda: self.service.begin_upload(self.editor, dict(remoteId=REMOTE, archiveId=uid(500), baseRevision=1,
                   sha256=hashlib.sha256(payload).hexdigest(), size=len(payload))))
        self.error('permission-denied', lambda: self.service.commit(self.editor, dict(remoteId=REMOTE, archiveId=uid(500))))

    def test_repeated_begin_refreshes_the_revision_for_storage_rules(self):
        first = self.stage(uid(500), uid(501))
        self.service.edit_item(self.owner, dict(remoteId=REMOTE, editId=uid(600), garmentId=GARMENT, baseRevision=1, changes={'size': 'M'}))
        again = self.stage(uid(500), uid(501))
        self.assertEqual((1, 2), (first['baseRevision'], again['baseRevision']))
        self.assertEqual(3, self.apply(uid(500), self.add(502, uid(501)))['revision'])

    def test_android_long_size_is_decoded(self):
        from family.callable_wire import decode_request
        payload = change_zip(uid(501))
        wrapped = {'@type': 'type.googleapis.com/google.protobuf.Int64Value', 'value': str(len(payload))}
        request = decode_request('begin_change', dict(remoteId=REMOTE, changeId=uid(500), sha256=hashlib.sha256(payload).hexdigest(), size=wrapped))
        self.assertEqual(len(payload), self.service.begin_change(self.editor, request)['size'])

    def test_empty_wardrobe_cannot_take_changes(self):
        other = 'aaaaaaaa-0000-4000-8000-000000000001'
        self.service.create(self.owner, dict(remoteId=other, wardrobeId=WARDROBE))
        payload = change_zip(uid(501))
        self.error('failed-precondition', lambda: self.service.begin_change(self.owner, dict(remoteId=other, changeId=uid(500),
                   sha256=hashlib.sha256(payload).hexdigest(), size=len(payload))))


if __name__ == '__main__':
    unittest.main()
