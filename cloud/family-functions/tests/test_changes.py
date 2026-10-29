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
from fixtures import REMOTE, WARDROBE, GARMENT, PHOTO, CATEGORY, OUTFIT, WISH, CAPSULE, uid, archive, envelope, snapshot


def png(color=(10, 120, 200, 255)):
    p = io.BytesIO(); Image.new('RGBA', (4, 3), color).save(p, format='PNG'); return p.getvalue()


def change_zip(photo_id, ext='png', extras=None, data=None):
    target = io.BytesIO()
    with zipfile.ZipFile(target, 'w') as z:
        z.writestr(f'photos/{photo_id}.{ext}', data if data is not None else png())
        for name, value in (extras or {}).items(): z.writestr(name, value)
    return target.getvalue()


class Helpers:
    def root(self): return self.store.data[f'familyWardrobes/{REMOTE}']
    def head(self):
        with zipfile.ZipFile(io.BytesIO(self.blobs.data[self.root()['head']['archive']])) as z:
            return json.loads(z.read('manifest.json'))['snapshot'], {i.filename: i for i in z.infolist()}
    def error(self, code, call, reason=None):
        with self.assertRaises(FamilyError) as caught: call()
        self.assertEqual(code, caught.exception.code)
        if reason: self.assertEqual(reason, caught.exception.reason)
    def stage(self, change_id, photo, actor=None, payload=None):
        payload = payload or change_zip(photo)
        ticket = self.service.begin_change(actor or self.editor, dict(remoteId=REMOTE, changeId=change_id,
                                                                     sha256=hashlib.sha256(payload).hexdigest(), size=len(payload)))
        self.blobs.data[ticket['stagingPath']] = payload
        return ticket
    def apply(self, change_id, change, actor=None):
        return self.service.apply_change(actor or self.editor, dict(remoteId=REMOTE, changeId=change_id, change=json.dumps(change)))


class ChangeTests(Helpers, unittest.TestCase):
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

    def garment(self, number, photo, **fields):
        g = copy.deepcopy(snapshot()['garments'][0])
        g.update(id=uid(number), photoId=photo, name=f'Item {number}', customCategoryId=None, customColorId=None, photoFingerprint='')
        g.update(fields); return g

    def add(self, number, photo, category=None, color=None, category_field=None, **fields):
        if category_field: fields['category'] = category_field
        return dict(kind='add', garment=self.garment(number, photo, **fields), customCategory=category, customColor=color,
                    photo=dict(id=photo, ext='png'))

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



G2 = uid(30)


class OutfitChangeTests(Helpers, unittest.TestCase):
    """kind 'outfit': no staged file; only the same outfit changed by someone else conflicts."""
    def setUp(self):
        self.store = TransactionStore(); self.blobs = MemoryArchives(); self.now = 1800000000000
        self.service = FamilyService(self.store, self.blobs, lambda: self.now)
        self.owner = Actor('owner', True); self.editor = Actor('editor', True); self.viewer = Actor('viewer', True)
        self.service.create(self.owner, dict(remoteId=REMOTE, wardrobeId=WARDROBE))
        for actor, role in [(self.editor, 'editor'), (self.viewer, 'viewer')]:
            self.store.seed(f'familyWardrobes/{REMOTE}/members/{actor.uid}', dict(active=True, role=role, expiresAt=self.now + 10**9))
        state = snapshot()
        state['garments'].append(dict(state['garments'][0], id=G2, name='Jeans', category='bottom', customCategoryId=None, customColorId=None))
        data = archive(envelope(state=state))
        upload = self.service.begin_upload(self.owner, dict(remoteId=REMOTE, archiveId=uid(700), baseRevision=0,
                                                            sha256=hashlib.sha256(data).hexdigest(), size=len(data)))
        self.blobs.data[upload['stagingPath']] = data
        self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(700)))

    def rows(self, oid=OUTFIT, garments=(GARMENT,), ideas=(), name='Look', created=0, layout=''):
        parts = [dict(outfitId=oid, garmentId=g) for g in garments]
        idea_rows = [dict(outfitId=oid, partId='idea:' + c, wishId=None, category=c, name='Idea', color='', photoId='') for c in ideas]
        signature = '|'.join(sorted(list(garments) + ['idea:' + c for c in ideas]))
        outfit = dict(id=oid, wardrobeId=WARDROBE, name=name, signature=signature, createdAt=created, occasion='everyday',
                      weather='any_weather', source='owned' if not ideas else 'inspiration', layout=layout, background='paper')
        return dict(outfit=outfit, parts=parts, ideas=idea_rows)

    def outfit(self, rows, previous=None):
        return dict(kind='outfit', previous=previous, **rows)

    def current(self, oid=OUTFIT):
        state, _ = self.head()
        return state, next((o for o in state['outfits'] if o['id'] == oid), None)

    def test_new_outfit_after_another_members_commit(self):
        self.service.edit_item(self.owner, dict(remoteId=REMOTE, editId=uid(600), garmentId=GARMENT, baseRevision=1, changes={'size': 'M'}))
        layout = json.dumps([dict(key='owned:' + G2, x=0.5, y=0.5, scale=1, z=0)])
        receipt = self.apply(uid(800), self.outfit(self.rows(uid(801), garments=(G2,), name='Weekend', created=5, layout=layout)))
        self.assertEqual((3, 'committed'), (receipt['revision'], receipt['status']))
        state, outfit = self.current(uid(801))
        self.assertEqual(('Weekend', G2, layout), (outfit['name'], outfit['signature'], outfit['layout']))
        self.assertEqual([G2], [p['garmentId'] for p in state['parts'] if p['outfitId'] == uid(801)])
        self.assertEqual('M', next(g for g in state['garments'] if g['id'] == GARMENT)['size'])
        record = self.store.data[f'familyWardrobes/{REMOTE}/uploads/{uid(800)}']
        self.assertNotIn('stagingPath', record)
        self.assertEqual(receipt, self.apply(uid(800), self.outfit(self.rows(uid(801), garments=(G2,), name='Weekend', created=5, layout=layout))))
        self.assertEqual(3, self.root()['revision'])

    def test_edit_reconciles_linked_capsule_and_keeps_history(self):
        before, _ = self.current()
        changed = self.rows(garments=(G2,), ideas=('top',), name='Look 2')
        self.apply(uid(800), self.outfit(changed, previous=self.rows()))
        state, outfit = self.current()
        self.assertEqual(('Look 2', 0, 'inspiration'), (outfit['name'], outfit['createdAt'], outfit['source']))
        self.assertEqual([dict(capsuleId=CAPSULE, garmentId=G2, manual=False, packed=False)], state['capsuleItems'])
        for kind in ('plans', 'wears', 'wishes', 'wishEvents', 'purchaseUndos'):
            self.assertEqual(before[kind], state[kind])
        self.assertEqual(['idea:top'], [i['partId'] for i in state['ideas'] if i['outfitId'] == OUTFIT])

    def test_manual_and_packed_capsule_items_survive(self):
        state, _ = self.head()
        # Owner packs the item and adds the second one by hand (full archive, as before the delta model).
        state['capsuleItems'] = [dict(capsuleId=CAPSULE, garmentId=GARMENT, manual=False, packed=True),
                                 dict(capsuleId=CAPSULE, garmentId=G2, manual=True, packed=False)]
        data = archive(envelope(base=1, state=state))
        upload = self.service.begin_upload(self.owner, dict(remoteId=REMOTE, archiveId=uid(701), baseRevision=1,
                                                            sha256=hashlib.sha256(data).hexdigest(), size=len(data)))
        self.blobs.data[upload['stagingPath']] = data
        self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(701)))
        self.apply(uid(800), self.outfit(self.rows(garments=(GARMENT, G2)), previous=self.rows()))
        state, _ = self.head()
        self.assertEqual({(GARMENT, False, True), (G2, True, False)},
                         {(r['garmentId'], r['manual'], r['packed']) for r in state['capsuleItems']})

    def test_only_the_same_outfit_changed_by_someone_else_conflicts(self):
        self.apply(uid(800), self.outfit(self.rows(name='Theirs'), previous=self.rows()), actor=self.owner)
        self.error('failed-precondition', lambda: self.apply(uid(810), self.outfit(self.rows(name='Mine'), previous=self.rows())),
                   'CHANGE_OUTFIT_CHANGED')
        self.assertEqual('Theirs', self.current()[1]['name'])
        # Order of parts/ideas in "previous" carries no meaning.
        self.apply(uid(820), self.outfit(self.rows(garments=(G2, GARMENT), name='Mine'), previous=self.rows(name='Theirs')))
        self.assertEqual('Mine', self.current()[1]['name'])

    def test_concurrent_edit_of_the_same_outfit_is_detected_on_retry(self):
        def competitor():
            self.blobs.after_put = None
            self.apply(uid(900), self.outfit(self.rows(name='Theirs'), previous=self.rows()), actor=self.owner)
        self.blobs.after_put = competitor
        self.error('failed-precondition', lambda: self.apply(uid(800), self.outfit(self.rows(name='Mine'), previous=self.rows())),
                   'CHANGE_OUTFIT_CHANGED')
        self.assertEqual((2, 'Theirs'), (self.root()['revision'], self.current()[1]['name']))
        self.assertEqual(sorted([self.root()['head']['archive'], self.root()['previousArchive']]),
                         sorted(p for p in self.blobs.data if '/snapshots/' in p))

    def test_missing_outfit_item_or_owned_wish_and_duplicate_combination(self):
        self.error('failed-precondition', lambda: self.apply(uid(800), self.outfit(self.rows(uid(801)), previous=self.rows(uid(801)))),
                   'CHANGE_ITEM_MISSING')
        self.error('failed-precondition', lambda: self.apply(uid(810), self.outfit(self.rows(uid(811), garments=(uid(999),)))),
                   'CHANGE_ITEM_MISSING')
        wish = self.rows(uid(821), garments=(G2,))
        wish['ideas'] = [dict(outfitId=uid(821), partId='wish:' + WISH, wishId=WISH, category='top', name='Shirt', color='', photoId=PHOTO)]
        wish['outfit'].update(signature='|'.join(sorted([G2, 'wish:' + WISH])), source='mixed')
        self.error('failed-precondition', lambda: self.apply(uid(820), self.outfit(wish)), 'CHANGE_ITEM_MISSING')  # WISH is owned
        self.error('failed-precondition', lambda: self.apply(uid(830), self.outfit(self.rows(uid(831)))), 'CHANGE_OUTFIT_DUPLICATE')
        self.error('already-exists', lambda: self.apply(uid(840), self.outfit(self.rows())))
        self.assertEqual(1, self.root()['revision'])

    def test_malformed_outfit_changes_are_rejected_before_any_write(self):
        good = self.rows(uid(801), garments=(G2,))
        bad = [dict(good, parts=[dict(outfitId=uid(802), garmentId=G2)]),
               dict(good, outfit=dict(good['outfit'], signature=GARMENT)),
               dict(good, parts=[dict(outfitId=uid(801), garmentId=G2)] * 2),
               dict(good, parts=[]),
               dict(good, outfit=dict(good['outfit'], extra=1))]
        for rows in bad:
            with self.subTest(rows=rows):
                self.error('invalid-argument', lambda: self.apply(uid(800), self.outfit(rows)))
        self.error('invalid-argument', lambda: self.apply(uid(800), self.outfit(self.rows(created=9), previous=self.rows())))
        self.error('invalid-argument', lambda: self.apply(uid(800), dict(self.outfit(good), photo=dict(id=uid(5), ext='png'))))
        self.assertNotIn(f'familyWardrobes/{REMOTE}/uploads/{uid(800)}', self.store.data)

    def test_outfit_request_id_cannot_become_a_photo_upload_or_another_change(self):
        self.apply(uid(800), self.outfit(self.rows(uid(801), garments=(G2,))))
        payload = change_zip(uid(811))
        self.error('already-exists', lambda: self.service.begin_change(self.editor, dict(remoteId=REMOTE, changeId=uid(800),
                   sha256=hashlib.sha256(payload).hexdigest(), size=len(payload))))
        self.error('already-exists', lambda: self.apply(uid(800), self.outfit(self.rows(uid(801), garments=(G2,), name='Other'))))
        self.stage(uid(810), uid(811))
        self.error('already-exists', lambda: self.apply(uid(810), self.outfit(self.rows(uid(812), garments=(G2,), ideas=('top',)))))
        self.error('permission-denied', lambda: self.apply(uid(820), self.outfit(self.rows(uid(821), garments=(G2,))), actor=self.viewer))

    def test_outfit_changes_share_the_daily_quota(self):
        self.store.seed('familyQuotas/editor', dict(roots=0, day=self.now // 86400000, uploads=MAX_UPLOADS_PER_DAY))
        self.error('resource-exhausted', lambda: self.apply(uid(800), self.outfit(self.rows(uid(801), garments=(G2,)))))

    def test_head_advertises_outfit_changes(self):
        self.assertIn('outfit1', self.service.resume(self.viewer, dict(remoteId=REMOTE))['features'])



class CompatibilityTests(Helpers, unittest.TestCase):
    """FAM-04: a client without the delta model (full archives, CAS) keeps working next to server-applied changes."""
    setUp = ChangeTests.setUp
    garment = ChangeTests.garment
    add = ChangeTests.add

    def full(self, number, base, state):
        data = archive(envelope(base=base, state=state))
        upload = self.service.begin_upload(self.owner, dict(remoteId=REMOTE, archiveId=uid(number), baseRevision=base,
                                                            sha256=hashlib.sha256(data).hexdigest(), size=len(data)))
        self.blobs.data[upload['stagingPath']] = data
        return self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(number)))

    def test_old_client_commits_on_top_of_server_applied_changes_and_cas_still_holds(self):
        self.stage(uid(500), uid(501))
        self.apply(uid(500), self.add(502, uid(501)))
        state, entries = self.head()
        # An old client downloads this head, edits locally and uploads a full archive on base 2.
        state = copy.deepcopy(state); next(g for g in state['garments'] if g['id'] == GARMENT)['size'] = 'XL'
        photos = {name[len('photos/'):].split('.')[0] for name in entries if name.startswith('photos/')}
        self.assertEqual({PHOTO, uid(501)}, photos)
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w') as z:
            z.writestr('manifest.json', json.dumps(envelope(base=2, state=state)))
            for name in entries:
                if name.startswith('photos/'):
                    with zipfile.ZipFile(io.BytesIO(self.blobs.data[self.root()['head']['archive']])) as head:
                        z.writestr(name, head.read(name))
        payload = data.getvalue()
        upload = self.service.begin_upload(self.owner, dict(remoteId=REMOTE, archiveId=uid(600), baseRevision=2,
                                                            sha256=hashlib.sha256(payload).hexdigest(), size=len(payload)))
        self.blobs.data[upload['stagingPath']] = payload
        self.assertEqual(3, self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(600)))['revision'])
        # A stale full archive is still refused; a change after it still applies.
        self.error('aborted', lambda: self.service.begin_upload(self.owner, dict(remoteId=REMOTE, archiveId=uid(601), baseRevision=2,
                   sha256='a' * 64, size=10)))
        self.stage(uid(510), uid(511))
        self.assertEqual(4, self.apply(uid(510), self.add(512, uid(511)))['revision'])
        state, _ = self.head()
        self.assertEqual('XL', next(g for g in state['garments'] if g['id'] == GARMENT)['size'])
        self.assertEqual({GARMENT, uid(502), uid(512)}, {g['id'] for g in state['garments']})

    def test_head_answer_only_gains_the_features_list(self):
        old_keys = {'remoteId', 'wardrobeId', 'ownerUid', 'revision', 'head', 'role'}
        head = self.service.resume(self.viewer, dict(remoteId=REMOTE))
        self.assertEqual(old_keys | {'features'}, set(head))
        self.stage(uid(500), uid(501)); self.apply(uid(500), self.add(502, uid(501)))
        head = self.service.resume(self.viewer, dict(remoteId=REMOTE))['head']
        # Same head shape and rules an old reader checks: revision chain, author, archive path.
        self.assertEqual((2, 1, 'editor'), (head['revision'], head['baseRevision'], head['authorUid']))
        self.assertTrue(head['archive'].startswith(f'familyWardrobes/{REMOTE}/snapshots/'))

if __name__ == '__main__':
    unittest.main()
