"""Regression tests for the QA report of 0.14.12 (BUG-08, BUG-09, BUG-11, BUG-12).

Each test reproduced its defect on 0.14.12 and passes after the fix."""
import copy
import unittest
from family.errors import FamilyError
from family.membership import email, email_key
from family.schema import validate_snapshot, visible_name
from family.editing import validate_edit
from family.service import Actor, FamilyService
from test_family import MemoryArchives
from test_membership import TransactionStore
from fixtures import REMOTE, WARDROBE, GARMENT, CATEGORY, uid, snapshot

RLO = '‮'
ZWJ = '‍'


class EmailTests(unittest.TestCase):
    """BUG-08: addresses on Cyrillic domains (.укр) and their punycode form."""

    def test_punycode_and_idn_domains_are_one_address(self):
        self.assertEqual('olena@sait.xn--j1amh', email('olena@sait.xn--j1amh'))
        self.assertEqual('olena@sait.xn--j1amh', email('Olena@sait.укр'))
        self.assertEqual(email_key('olena@sait.укр'), email_key('OLENA@sait.xn--j1amh'))
        self.assertEqual('ivan@xn--80a1acn3a.xn--j1amh', email(' ivan@пошта.укр '))

    def test_ascii_addresses_keep_their_existing_keys(self):
        # Existing inboxes are keyed by email_key(): ASCII normalization must not change.
        self.assertEqual('a.b+c@example.com', email(' A.B+C@Example.COM '))

    def test_still_invalid(self):
        for bad in ['é@example.com', 'іван@example.com', 'a@b', 'a@example.c', 'a@-example.com',
                    'a@example.com\nX', '@example.com', 'a@', 'a' * 250 + '@example.com', 5, None]:
            with self.assertRaises(FamilyError, msg=repr(bad)):
                email(bad)


class CyrillicInvitationTests(unittest.TestCase):
    def setUp(self):
        self.store = TransactionStore(); self.now = 1800000000000
        self.service = FamilyService(self.store, MemoryArchives(), lambda: self.now)
        self.owner = Actor('owner', True, 'owner@example.com')
        self.olena = Actor('olena', True, 'olena@sait.xn--j1amh')
        self.service.create(self.owner, dict(remoteId=REMOTE, wardrobeId=WARDROBE))
        self.request = dict(remoteId=REMOTE, inviteId=uid(900), email='olena@sait.укр', role='viewer', name='Family')

    def test_bug08_cyrillic_domain_users_can_use_invitations(self):
        self.assertEqual([], self.service.invitations(self.olena, {})['invitations'])
        self.service.invite(self.owner, self.request)
        self.assertEqual(1, len(self.service.invitations(self.olena, {})['invitations']))
        self.assertEqual('accepted', self.service.respond_invite(self.olena, dict(inviteId=uid(900), action='accept'))['status'])

    def test_unaddressable_account_sees_an_empty_list(self):
        odd = Actor('odd', True, 'not-an-address')
        self.assertEqual({'invitations': []}, self.service.invitations(odd, {}))

    def test_bug12_invitation_name_must_be_visible(self):
        for name in [RLO + 'gpj.akuS', ZWJ, ZWJ + ' ' + ZWJ, '️', '́']:
            with self.assertRaises(FamilyError, msg=repr(name)) as caught:
                self.service.invite(self.owner, self.request | {'name': name})
            self.assertEqual('invalid-argument', caught.exception.code)
        self.assertEqual('pending', self.service.invite(self.owner, self.request | {'name': 'Сім’я 👨‍👩‍👧'})['status'])


class LabelKeyTests(unittest.TestCase):
    """BUG-09: Java toLowerCase(ROOT) writes a final sigma where Python lower() does not."""

    def state(self, name, key):
        s = snapshot(); s['customCategories'][0] |= dict(name=name, nameKey=key); return s

    def test_bug09_java_final_sigma_key_is_accepted(self):
        validate_snapshot(self.state('XL-Σ', 'xl-ς'), WARDROBE)  # what the phone writes
        validate_snapshot(self.state('XL-Σ', 'xl-σ'), WARDROBE)  # what the server computes

    def test_other_keys_still_must_match(self):
        for key in ['xl', 'XL-Σ', 'xl-s', 'Xl-ς']:
            with self.assertRaises(FamilyError, msg=key):
                validate_snapshot(self.state('XL-Σ', key), WARDROBE)

    def test_sigma_spellings_are_one_label(self):
        s = self.state('XL-Σ', 'xl-ς')
        s['customCategories'].append(dict(id=uid(50), wardrobeId=WARDROBE, name='xl-σ', nameKey='xl-σ', baseCategory='top'))
        with self.assertRaises(FamilyError):
            validate_snapshot(s, WARDROBE)


class EditTextTests(unittest.TestCase):
    def request(self, **changes):
        return dict(editId=uid(701), garmentId=GARMENT, baseRevision=1, changes=changes)

    def test_bug11_windows_line_breaks_in_description(self):
        text = 'Прати при 30°C\r\nНе сушити'
        validate_edit(self.request(description=text))
        s = snapshot(); s['garments'][0]['description'] = text
        validate_snapshot(s, WARDROBE)  # both paths agree

    def test_other_control_characters_still_rejected(self):
        for bad in ['a\x00b', 'a\x1bb', 'a\x0bb']:
            with self.assertRaises(FamilyError, msg=repr(bad)):
                validate_edit(self.request(description=bad))

    def test_bug12_edited_name_must_be_visible(self):
        for name in [RLO + 'gpj.akuS', 'Sukienka' + '⁦', ZWJ, ZWJ * 3, ' ​ ']:
            with self.assertRaises(FamilyError, msg=repr(name)):
                validate_edit(self.request(name=name))
        validate_edit(self.request(name='Сукня 👩‍🦰'))  # ZWJ inside an emoji is fine


class SnapshotNameTests(unittest.TestCase):
    def test_bug12_snapshot_names_must_be_visible(self):
        for kind, name in [('garments', RLO + 'gpj.akuS'), ('wardrobes', ZWJ), ('capsules', '⁧Trip'), ('outfits', '️')]:
            s = snapshot(); s[kind][0]['name'] = name
            with self.assertRaises(FamilyError, msg=(kind, repr(name))):
                validate_snapshot(s, WARDROBE)

    def test_ordinary_names_unchanged(self):
        s = snapshot(); s['garments'][0]['name'] = 'Сукня вечірня 🇺🇦 ‘П’ятниця’'
        validate_snapshot(s, WARDROBE)
        self.assertTrue(visible_name('a' + ZWJ + 'b'))
        self.assertFalse(visible_name(''))


if __name__ == '__main__':
    unittest.main()
