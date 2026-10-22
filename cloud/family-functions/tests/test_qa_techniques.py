"""QA regression suite for the family backend (QA-REPORT-0.14.13.md).

Every test name starts with its test-design technique:
  ep_  equivalence classes        bva_ boundary values        st_  state transitions
  pw_  pairwise / decision table  eg_  error guessing
Open bugs are @unittest.expectedFailure: the run stays green while the bug reproduces and turns red
(unexpected success) as soon as it is fixed, so the marker is removed together with the fix.
"""
import ast
import copy
import hashlib
import itertools
import json
import unittest
from pathlib import Path

from family.callable_wire import callable_integer
from family.errors import FamilyError
from family.membership import email, email_key, INVITE_TTL
from family.schema import label_key, text_length, valid_date, validate_snapshot, visible_name
from family.service import Actor, FamilyService, MAX_UPLOADS_PER_DAY, UPLOAD_TTL_MS
from fixtures import REMOTE, WARDROBE, GARMENT, OUTFIT, archive, envelope, snapshot, uid
from test_family import MemoryArchives
from test_membership import TransactionStore

EMOJI = '\U0001F600'


def all_pairs(*sizes):
    """Deterministic greedy all-pairs design; coverage is verified before returning."""
    uncovered = {(a, b, va, vb) for a, b in itertools.combinations(range(len(sizes)), 2)
                 for va in range(sizes[a]) for vb in range(sizes[b])}
    rows = []
    while uncovered:
        a, b, va, vb = min(uncovered)
        row = [None] * len(sizes); row[a], row[b] = va, vb
        for p in range(len(sizes)):
            if row[p] is not None: continue
            def gain(v):
                return sum(((min(p, q), max(p, q), v if p < q else row[q], row[q] if p < q else v) in uncovered)
                           for q in range(len(sizes)) if q != p and row[q] is not None)
            row[p] = max(range(sizes[p]), key=lambda v: (gain(v), -v))
        uncovered -= {(a, b, row[a], row[b]) for a, b in itertools.combinations(range(len(sizes)), 2)}
        rows.append(tuple(row))
    for a, b in itertools.combinations(range(len(sizes)), 2):
        assert {(r[a], r[b]) for r in rows} == {(x, y) for x in range(sizes[a]) for y in range(sizes[b])}
    return rows


class Base(unittest.TestCase):
    def setUp(self):
        self.store = TransactionStore(); self.blobs = MemoryArchives(); self.now = 1800000000000
        self.service = FamilyService(self.store, self.blobs, lambda: self.now)
        self.owner = Actor('owner', True, 'owner@example.com')
        self.editor = Actor('editor', True, 'editor@example.com')
        self.viewer = Actor('viewer', True, 'viewer@example.com')
        self.user = Actor('member', True, 'member@example.com')
        self.stranger = Actor('stranger', True, 'stranger@example.com')
        self.service.create(self.owner, dict(remoteId=REMOTE, wardrobeId=WARDROBE))

    def error(self, code, call, reason=None):
        with self.assertRaises(FamilyError) as caught: call()
        self.assertEqual(code, caught.exception.code, str(caught.exception))
        if reason: self.assertEqual(reason, caught.exception.reason)

    def seed_member(self, actor, role, active=True):
        self.store.seed(f'familyWardrobes/{REMOTE}/members/{actor.uid}',
                        dict(uid=actor.uid, email=actor.email, role=role, active=active, expiresAt=self.now + 10**9, version=1))

    def publish_initial(self):
        data = archive(envelope())
        request = dict(remoteId=REMOTE, archiveId=uid(700), baseRevision=0, sha256=hashlib.sha256(data).hexdigest(), size=len(data))
        upload = self.service.begin_upload(self.owner, request); self.blobs.data[upload['stagingPath']] = data
        return self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(700)))

    def snapshot_with(self, kind, field, value, index=0):
        s = snapshot(); s[kind][index][field] = value
        if kind == 'garments':
            s['purchaseUndos'][0]['garmentJson'] = json.dumps(s['garments'][0])
        return s

    def accepted(self, s):
        try:
            validate_snapshot(s, WARDROBE); return True
        except FamilyError:
            return False

    def invite(self, actor=None, **changes):
        request = dict(remoteId=REMOTE, inviteId=uid(900), email=self.user.email, role='viewer', name='Family') | changes
        return self.service.invite(actor or self.owner, request)

    def respond(self, action='accept', actor=None, invite=900):
        return self.service.respond_invite(actor or self.user, dict(inviteId=uid(invite), action=action))


class InputTechniques(Base):
    # ---------- Equivalence classes ----------
    def test_ep_email_classes(self):
        for raw, normal in [('Anna@Example.COM', 'anna@example.com'), (' anna@example.com\n', 'anna@example.com'),
                            ('anna+shop@example.com', 'anna+shop@example.com'),
                            ('ivan@Пошта.УКР', 'ivan@xn--80a1acn3a.xn--j1amh'), ('ivan@xn--80a1acn3a.xn--j1amh', 'ivan@xn--80a1acn3a.xn--j1amh')]:
            self.assertEqual(normal, email(raw))
        self.assertEqual(email_key('ivan@пошта.укр'), email_key('IVAN@xn--80a1acn3a.xn--j1amh'))
        for bad in ['', 'anna', '@example.com', 'anna@', 'анна@example.com', 'anna@localhost', 'an na@example.com',
                    '\u00a0anna@example.com', 'anna@example.c0m', None, 42]:
            self.error('invalid-argument', lambda: email(bad))

    def test_ep_visible_name_classes(self):
        for ok in ['Shirt', 'Сукня', '7', EMOJI, '\U0001F468\u200d\U0001F469', 'e\u0301', ' x ']:
            self.assertTrue(visible_name(ok), ok)
        for bad in ['', ' ', '\u00a0', '\u3000', '\u200b', '\u200d', '\u0301', '\ufe0f', 'a\u202eb', '\u2066x\u2069', '\n\t']:
            self.assertFalse(visible_name(bad), repr(bad))

    def test_ep_label_keys_fold_final_sigma(self):
        self.assertEqual(label_key('οδός'), label_key('οδόσ'))
        self.assertNotEqual(label_key('ink'), label_key('inc'))

    # ---------- Boundary values ----------
    def test_bva_name_limits_per_record_kind(self):
        for kind, limit in [('wardrobes', 80), ('capsules', 80), ('garments', 120), ('outfits', 120), ('wishes', 120)]:
            self.assertTrue(self.accepted(self.snapshot_with(kind, 'name', 'a' * limit)), f'{kind} {limit}')
            self.assertFalse(self.accepted(self.snapshot_with(kind, 'name', 'a' * (limit + 1))), f'{kind} {limit + 1}')
            self.assertTrue(self.accepted(self.snapshot_with(kind, 'name', 'a')), f'{kind} 1')
            self.assertFalse(self.accepted(self.snapshot_with(kind, 'name', '   ')), f'{kind} blank')
        # Emoji count as two UTF-16 units, as on the phone.
        self.assertTrue(self.accepted(self.snapshot_with('garments', 'name', EMOJI * 60)))
        self.assertFalse(self.accepted(self.snapshot_with('garments', 'name', EMOJI * 60 + 'a')))

    def test_bva_custom_label_name(self):
        s = snapshot(); s['customCategories'][0].update(name='a' * 60, nameKey='a' * 60)
        self.assertTrue(self.accepted(s))
        s['customCategories'][0].update(name='a' * 61, nameKey='a' * 61)
        self.assertFalse(self.accepted(s))

    def test_bva_text_fields(self):
        for field, limit in [('size', 40), ('location', 200), ('description', 1000), ('subcategory', 60)]:
            self.assertTrue(self.accepted(self.snapshot_with('garments', field, 'a' * limit)), field)
            self.assertFalse(self.accepted(self.snapshot_with('garments', field, 'a' * (limit + 1))), field)
        for value, ok in [(None, True), (0, True), (99999999999, True), (100000000000, False), (-1, False), (1.0, False), (True, False)]:
            self.assertEqual(ok, self.accepted(self.snapshot_with('garments', 'priceMinor', value)), repr(value))
        for value, ok in [(0, True), (30, True), (31, False), (-1, False)]:
            self.assertEqual(ok, self.accepted(self.snapshot_with('wardrobes', 'noRepeatDays', value)), value)

    def test_bva_dates(self):
        for value, ok in [('2028-02-29', True), ('2026-02-29', False), ('2026-12-31', True), ('2026-13-01', False),
                          ('2026-1-01', False), ('0001-01-01', True), ('9999-12-31', True), ('', False), (None, False)]:
            self.assertEqual(ok, valid_date(value), value)
        s = snapshot(); s['capsules'][0].update(startDate='2026-10-15', endDate='2026-10-15')
        self.assertTrue(self.accepted(s))
        s['capsules'][0].update(startDate='2026-10-16')
        self.assertFalse(self.accepted(s))

    def test_bva_canvas_layout(self):
        def with_layout(**piece):
            p = dict(key='owned:' + GARMENT, x=0.5, y=0.5, scale=1, z=0) | piece
            s = snapshot(); s['outfits'][0]['layout'] = json.dumps([p]); return s
        for field, ok_values, bad_values in [('x', [0, 1, 0.0], [-0.0001, 1.0001]), ('scale', [0.6, 1.8], [0.5999, 1.8001]),
                                             ('z', [0, 11], [12, -1, 1.5])]:
            for v in ok_values: self.assertTrue(self.accepted(with_layout(**{field: v})), (field, v))
            for v in bad_values: self.assertFalse(self.accepted(with_layout(**{field: v})), (field, v))

    def test_bva_callable_integers(self):
        int64 = 'type.googleapis.com/google.protobuf.Int64Value'
        self.assertEqual(2**63 - 1, callable_integer({'@type': int64, 'value': str(2**63 - 1)}))
        self.assertEqual(-2**63, callable_integer({'@type': int64, 'value': str(-2**63)}))
        for bad in [str(2**63), str(-2**63 - 1), '01', '1.0', ' 1', '', '+1']:
            self.error('invalid-argument', lambda: callable_integer({'@type': int64, 'value': bad}))
        self.error('invalid-argument', lambda: callable_integer({'@type': int64, 'value': '1', 'extra': 1}))
        self.error('invalid-argument', lambda: callable_integer(1.0))

    def test_bva_invitations_per_day(self):
        for n in range(30):
            self.service.invite(self.owner, dict(remoteId=REMOTE, inviteId=uid(1000 + n), email=f'u{n}@example.com', role='viewer', name='F'))
        self.error('resource-exhausted', lambda: self.service.invite(
            self.owner, dict(remoteId=REMOTE, inviteId=uid(1100), email='u30@example.com', role='viewer', name='F')))
        self.now += 86400000  # next UTC day resets the quota
        self.service.invite(self.owner, dict(remoteId=REMOTE, inviteId=uid(1101), email='u30@example.com', role='viewer', name='F'))

    def test_bva_uploads_per_day(self):
        data = archive(envelope())
        digest = hashlib.sha256(data).hexdigest()
        for n in range(MAX_UPLOADS_PER_DAY):
            self.service.begin_upload(self.owner, dict(remoteId=REMOTE, archiveId=uid(2000 + n), baseRevision=0, sha256=digest, size=len(data)))
        self.error('resource-exhausted', lambda: self.service.begin_upload(
            self.owner, dict(remoteId=REMOTE, archiveId=uid(2999), baseRevision=0, sha256=digest, size=len(data))))

    def test_bva_invitation_name(self):
        self.invite(name='a' * 80)
        self.error('invalid-argument', lambda: self.invite(inviteId=uid(901), email='x@example.com', name='a' * 81))
        self.error('invalid-argument', lambda: self.invite(inviteId=uid(902), email='y@example.com', name='   '))

    def test_bva_qa01_invitation_name_counts_utf16_units_like_the_phone(self):
        """QA-01 (fixed in 0.14.14): one limit with the phone parser, 80 UTF-16 units."""
        self.invite(name=EMOJI * 40)
        self.error('invalid-argument', lambda: self.invite(inviteId=uid(901), email='x@example.com', name=EMOJI * 41))
        self.error('invalid-argument', lambda: self.invite(inviteId=uid(902), email='y@example.com', name=EMOJI * 80))

    def test_eg_qa01_legacy_names_never_break_lists(self):
        """Invitations stored before the fix are hidden from the lists; a legacy directory name is shown blank."""
        self.invite()
        invite = self.store.data[f'familyInvites/{uid(900)}']
        self.store.seed(f'familyInvites/{uid(900)}', invite | {'name': EMOJI * 80})
        self.assertEqual([], self.service.invitations(self.user, {})['invitations'])
        self.assertEqual([], self.service.members(self.owner, dict(remoteId=REMOTE))['invitations'])
        root = self.store.data[f'familyWardrobes/{REMOTE}']
        self.store.seed(f'familyWardrobes/{REMOTE}', root | {'displayName': EMOJI * 80})
        self.assertEqual('', self.service.directory(self.owner, {})['wardrobes'][0]['name'])

    def test_ep_qa11_rfc_email_rules(self):
        """QA-11 (fixed in 0.14.14): dot-atom local part <= 64, RFC 1035 labels; same rule as AuthRules."""
        for bad in ['a..b@example.com', '.a@example.com', 'a.@example.com', 'a@x.-y.com', 'a@x-.com',
                    'a@' + 'b' * 64 + '.com', 'a' * 65 + '@example.com']:
            self.error('invalid-argument', lambda: email(bad))
        for ok in ['a' * 64 + '@example.com', 'a@' + 'b' * 63 + '.com', 'first.last@example.co.uk', 'a@my-shop.example.com']:
            self.assertEqual(ok, email(ok))


class StateTransitions(Base):
    def test_st_invitation_lifecycle_table(self):
        # pending --accept--> accepted (replay is idempotent)
        self.invite(); self.assertEqual('accepted', self.respond()['status'])
        self.assertEqual('accepted', self.respond()['status'])
        # accepted --decline--> refused
        self.error('failed-precondition', lambda: self.respond('decline'), 'INVITE_CLOSED')
        # accepted member --revoke--> invitation revoked, access gone
        self.service.change_member(self.owner, dict(remoteId=REMOTE, uid=self.user.uid, role='revoked', expectedVersion=1, requestId=uid(950)))
        self.assertEqual('revoked', self.store.data[f'familyInvites/{uid(900)}']['status'])
        self.error('permission-denied', lambda: self.service.resume(self.user, dict(remoteId=REMOTE)))
        self.error('failed-precondition', lambda: self.respond(), 'INVITE_CLOSED')
        # revoked member --new invitation--> pending --accept--> active again with a higher version
        self.invite(inviteId=uid(901)); self.respond(invite=901)
        self.assertEqual(3, self.store.data[f'familyWardrobes/{REMOTE}/members/{self.user.uid}']['version'])

    def test_st_declined_and_cancelled_are_terminal(self):
        self.invite(); self.assertEqual('declined', self.respond('decline')['status'])
        self.error('failed-precondition', lambda: self.respond('accept'), 'INVITE_CLOSED')
        self.invite(inviteId=uid(901))
        self.assertEqual('revoked', self.service.cancel_invite(self.owner, dict(remoteId=REMOTE, inviteId=uid(901)))['status'])
        self.assertEqual('revoked', self.service.cancel_invite(self.owner, dict(remoteId=REMOTE, inviteId=uid(901)))['status'])
        self.error('failed-precondition', lambda: self.respond(invite=901), 'INVITE_CLOSED')
        self.assertEqual([], self.service.invitations(self.user, {})['invitations'])

    def test_st_expiry_boundary(self):
        self.invite()
        self.now += INVITE_TTL - 1
        self.assertEqual(1, len(self.service.invitations(self.user, {})['invitations']))
        self.now += 1
        self.assertEqual([], self.service.invitations(self.user, {})['invitations'])
        self.error('failed-precondition', lambda: self.respond(), 'INVITE_EXPIRED')
        # an expired invitation does not block a new one to the same address
        self.invite(inviteId=uid(901)); self.assertEqual('accepted', self.respond(invite=901)['status'])

    def test_st_role_changes_and_leave(self):
        self.invite(); self.respond()
        change = lambda role, version, request: self.service.change_member(
            self.owner, dict(remoteId=REMOTE, uid=self.user.uid, role=role, expectedVersion=version, requestId=uid(request)))
        self.assertEqual('editor', change('editor', 1, 960)['role'])
        self.assertEqual('editor', change('editor', 1, 960)['role'])          # replay
        self.error('already-exists', lambda: change('viewer', 1, 960))         # same request id, other change
        self.error('aborted', lambda: change('viewer', 1, 961))                # stale version
        self.assertEqual('viewer', change('viewer', 2, 962)['role'])
        left = self.service.leave(self.user, dict(remoteId=REMOTE, requestId=uid(963)))
        self.assertFalse(left['active'])
        self.assertFalse(self.service.leave(self.user, dict(remoteId=REMOTE, requestId=uid(963)))['active'])   # replay
        self.error('aborted', lambda: self.service.leave(self.user, dict(remoteId=REMOTE, requestId=uid(964))))
        self.error('permission-denied', lambda: self.service.leave(self.owner, dict(remoteId=REMOTE, requestId=uid(965))))

    def test_st_upload_lifecycle(self):
        data = archive(envelope()); digest = hashlib.sha256(data).hexdigest()
        request = dict(remoteId=REMOTE, archiveId=uid(700), baseRevision=0, sha256=digest, size=len(data))
        upload = self.service.begin_upload(self.owner, request)
        self.assertEqual('pending', upload['status'])
        self.error('failed-precondition', lambda: self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(700))))  # nothing staged
        self.blobs.data[upload['stagingPath']] = data
        result = self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(700)))
        self.assertEqual(1, result['revision'])
        self.assertEqual(result, self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(700))))   # replay
        self.assertEqual('committed', self.service.begin_upload(self.owner, request)['status'])
        self.error('aborted', lambda: self.service.begin_upload(self.owner, request | dict(archiveId=uid(701))))  # stale base

    def test_st_upload_expiry_boundary(self):
        data = archive(envelope()); digest = hashlib.sha256(data).hexdigest()
        request = dict(remoteId=REMOTE, archiveId=uid(700), baseRevision=0, sha256=digest, size=len(data))
        upload = self.service.begin_upload(self.owner, request); self.blobs.data[upload['stagingPath']] = data
        self.now += UPLOAD_TTL_MS
        self.error('failed-precondition', lambda: self.service.commit(self.owner, dict(remoteId=REMOTE, archiveId=uid(700))), 'UPLOAD_EXPIRED')
        self.error('failed-precondition', lambda: self.service.begin_upload(self.owner, request), 'UPLOAD_EXPIRED')


class Pairwise(Base):
    def test_pw_role_operation_decision_table(self):
        self.publish_initial()
        self.seed_member(self.editor, 'editor'); self.seed_member(self.viewer, 'viewer')
        revoked = Actor('revoked', True, 'revoked@example.com'); self.seed_member(revoked, 'editor', active=False)
        unverified = Actor('editor', False, 'editor@example.com')
        actors = {'owner': self.owner, 'editor': self.editor, 'viewer': self.viewer, 'revoked': revoked,
                  'stranger': self.stranger, 'unverified': unverified}
        allowed = {'resume': {'owner', 'editor', 'viewer'}, 'edit': {'owner', 'editor'}, 'members': {'owner'},
                   'invite': {'owner'}, 'begin': {'owner', 'editor'}}
        counter = itertools.count(3000)
        def call(operation, actor):
            n = next(counter)
            if operation == 'resume': return self.service.resume(actor, dict(remoteId=REMOTE))
            if operation == 'members': return self.service.members(actor, dict(remoteId=REMOTE))
            if operation == 'invite': return self.service.invite(actor, dict(remoteId=REMOTE, inviteId=uid(n), email=f'n{n}@example.com', role='viewer', name='F'))
            revision = self.store.data[f'familyWardrobes/{REMOTE}']['revision']
            if operation == 'edit': return self.service.edit_item(actor, dict(remoteId=REMOTE, editId=uid(n), garmentId=GARMENT,
                                                                               baseRevision=revision, changes={'location': f'L{n}'}))
            data = archive(envelope(revision))
            return self.service.begin_upload(actor, dict(remoteId=REMOTE, archiveId=uid(n), baseRevision=revision,
                                                         sha256=hashlib.sha256(data).hexdigest(), size=len(data)))
        for operation, (name, actor) in itertools.product(allowed, actors.items()):
            with self.subTest(operation=operation, actor=name):
                if name == 'unverified':
                    self.error('unauthenticated', lambda: call(operation, actor))
                elif name in allowed[operation]:
                    call(operation, actor)
                else:
                    self.error('permission-denied', lambda: call(operation, actor))

    def test_pw_invite_request_combinations(self):
        roles = ['viewer', 'editor', 'owner', '']
        emails = ['ok', 'self', 'idn', 'invalid']
        names = ['Family', 'a' * 80, 'a' * 81, '  ', 'Bad\u202ename', 'Tab\tname']
        actors = ['owner', 'editor', 'stranger']
        self.seed_member(self.editor, 'editor')
        rows = all_pairs(len(roles), len(emails), len(names), len(actors))
        self.assertLess(len(rows), len(roles) * len(emails) * len(names) * len(actors))
        for n, (r, e, nm, a) in enumerate(rows):
            address = {'ok': f'p{n}@example.com', 'self': 'owner@example.com', 'idn': f'p{n}@пошта.укр', 'invalid': 'p@'}[emails[e]]
            actor = {'owner': self.owner, 'editor': self.editor, 'stranger': self.stranger}[actors[a]]
            request = dict(remoteId=REMOTE, inviteId=uid(4000 + n), email=address, role=roles[r], name=names[nm])
            # Oracle in the server's validation order: request fields first, then ownership.
            invalid = (emails[e] == 'invalid' or (emails[e] == 'self' and actors[a] == 'owner') or roles[r] not in ('viewer', 'editor')
                       or names[nm] in ('a' * 81, '  ', 'Bad\u202ename', 'Tab\tname'))
            with self.subTest(row=(roles[r], emails[e], names[nm][:8], actors[a])):
                if invalid:
                    self.error('invalid-argument', lambda: self.service.invite(actor, request))
                elif actors[a] != 'owner':
                    self.error('permission-denied', lambda: self.service.invite(actor, request))
                else:
                    self.assertEqual('pending', self.service.invite(actor, request)['status'])


class ErrorGuessing(Base):
    def test_eg_crafted_manifests_are_rejected(self):
        bad = []
        s = snapshot(); s['garments'][0]['createdAt'] = True; bad.append(('bool as int', s))
        s = snapshot(); s['garments'][0]['wardrobeId'] = uid(99); bad.append(('foreign wardrobe', s))
        s = snapshot(); s['parts'].append(dict(outfitId=OUTFIT, garmentId=GARMENT)); bad.append(('duplicate part', s))
        s = snapshot(); s['garments'][0]['extra'] = 1; bad.append(('unknown field', s))
        s = snapshot(); s['outfits'][0]['layout'] = '[{"key":"owned:%s","x":NaN,"y":0.5,"scale":1,"z":0}]' % GARMENT; bad.append(('NaN layout', s))
        s = snapshot(); s['wishes'][0]['link'] = 'javascript:alert(1)'; bad.append(('script link', s))
        s = snapshot(); s['wishes'][0]['link'] = 'https://user:pw@shop.pl'; bad.append(('credential link', s))
        s = snapshot(); s['photoFiles'] = {}; bad.append(('photo set mismatch', s))
        s = snapshot(); s['wardrobes'][0]['name'] = 'Home\u202e'; bad.append(('bidi name', s))
        for label, value in bad:
            with self.subTest(label):
                self.assertFalse(self.accepted(value))

    def test_eg_duplicate_json_keys_in_layout(self):
        s = snapshot(); s['outfits'][0]['layout'] = '[{"key":"owned:%s","key":"x","x":0.5,"y":0.5,"scale":1,"z":0}]' % GARMENT
        self.assertFalse(self.accepted(s))

    def test_eg_invite_id_reuse_with_other_payload(self):
        self.invite()
        self.error('already-exists', lambda: self.invite(role='editor'))

    def test_eg_edit_text_controls(self):
        self.publish_initial()
        self.seed_member(self.editor, 'editor')
        def edit(n, changes):
            return self.service.edit_item(self.editor, dict(remoteId=REMOTE, editId=uid(n), garmentId=GARMENT,
                                                            baseRevision=self.store.data[f'familyWardrobes/{REMOTE}']['revision'], changes=changes))
        self.assertEqual('committed', edit(5000, {'description': 'line\r\nnext\ttab'})['status'])
        for n, changes in enumerate([{'name': '\u200b'}, {'name': 'a\u202eb'}, {'description': 'bell\x07'}, {'name': 'a' * 121},
                                     {'photoId': uid(1)}, {}], 5001):
            with self.subTest(changes=changes):
                self.error('invalid-argument', lambda: edit(n, changes))

    def test_eg_qa04_edit_function_has_commit_resources(self):
        """QA-04 (fixed in 0.14.14): family_edit_item reads the head ZIP (<=128 MiB), rewrites it and fully decodes every photo twice
        (validate source + validate result); it used to run with the global 512 MiB / 180 s while family_commit, which
        decodes once, was raised to 1 GiB / 540 s (audit 0.14.7). Both now have the same resources."""
        tree = ast.parse((Path(__file__).resolve().parents[1] / 'main.py').read_text(encoding='utf-8'))
        def resources(name):
            function = next(f for f in tree.body if isinstance(f, ast.FunctionDef) and f.name == name)
            options = {}
            for decorator in function.decorator_list:
                if isinstance(decorator, ast.Call):
                    for keyword in decorator.keywords: options[keyword.arg] = ast.unparse(keyword.value)
            return options
        commit, edit = resources('family_commit'), resources('family_edit_item')
        self.assertIn('memory', commit)  # fixture: commit has explicit resources
        self.assertEqual(commit.get('memory'), edit.get('memory'))
        self.assertEqual(commit.get('timeout_sec'), edit.get('timeout_sec'))


if __name__ == '__main__':
    unittest.main()
