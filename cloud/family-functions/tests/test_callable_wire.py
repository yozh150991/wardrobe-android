import copy
import hashlib
import unittest

from family.callable_wire import callable_integer, decode_request
from family.errors import FamilyError
from family.archive import MAX_ARCHIVE
from family.service import Actor, FamilyService
from family.schema import integer, validate_snapshot
from fixtures import snapshot, archive, envelope, REMOTE, WARDROBE, uid
from test_family import MemoryStore, MemoryArchives


def wrapped(value, unsigned=False):
    return {'@type': 'type.googleapis.com/google.protobuf.' +
            ('UInt64Value' if unsigned else 'Int64Value'), 'value': str(value)}


class CallableWireTests(unittest.TestCase):
    def test_plain_and_wrapped_integer_boundaries(self):
        for value in (0, 1, 128 * 1024 * 1024, -(1 << 63), (1 << 63) - 1):
            self.assertEqual(value, callable_integer(value))
            self.assertEqual(value, callable_integer(wrapped(value)))
        self.assertEqual((1 << 64) - 1, callable_integer(wrapped((1 << 64) - 1, True)))

    def test_malformed_values_are_rejected(self):
        values = [True, False, None, 1.0, '1', [], {}, {'value': '1'},
                  wrapped(1) | {'extra': 'x'}, wrapped(1) | {'@type': []},
                  wrapped(1) | {'@type': 'other'}, wrapped(1) | {'value': 1},
                  wrapped(1) | {'value': '01'}, wrapped(1) | {'value': '1.0'},
                  wrapped(1) | {'value': ' 1'}, wrapped(1) | {'value': '+1'},
                  wrapped(1) | {'value': '١'}, wrapped(1 << 63), wrapped(-(1 << 63) - 1),
                  wrapped(-1, True), wrapped(1 << 64, True), wrapped('9' * 10000)]
        for value in values:
            with self.subTest(value=str(value)[:80]):
                with self.assertRaises(FamilyError) as ctx:
                    callable_integer(value)
                self.assertEqual('invalid-argument', ctx.exception.code)

    def test_decoder_is_scoped_non_mutating_and_idempotent(self):
        data = {'baseRevision': wrapped(0), 'size': wrapped(123), 'extra': wrapped(1)}
        before = copy.deepcopy(data)
        decoded = decode_request('begin_upload', data)
        self.assertEqual(before, data)
        self.assertEqual(123, decoded['size'])
        self.assertEqual(before['extra'], decoded['extra'])
        self.assertEqual(decoded, decode_request('begin_upload', decoded))
        self.assertEqual(before, decode_request('create', data))

    def test_one_item_mobile_request_reproduces_old_failure_then_commits(self):
        # Minimal actual content: one garment/photo, no custom categories or history.
        state = snapshot()
        for key, value in state.items():
            if isinstance(value, list) and key not in ('wardrobes', 'garments'):
                value.clear()
        state['garments'][0].update(customCategoryId=None, customColorId=None)
        data = archive(envelope(state=state))
        store, blobs = MemoryStore(), MemoryArchives()
        service = FamilyService(store, blobs)
        actor = Actor('owner', True)
        service.create(actor, dict(remoteId=REMOTE, wardrobeId=WARDROBE))
        request = dict(remoteId=REMOTE, archiveId=uid(999), baseRevision=0,
                       sha256=hashlib.sha256(data).hexdigest(), size=wrapped(len(data)))
        with self.assertRaises(FamilyError) as old:
            service.begin_upload(actor, request)
        self.assertEqual('invalid-argument', old.exception.code)
        ticket = service.begin_upload(actor, decode_request('begin_upload', request))
        self.assertEqual(len(data), ticket['size'])
        # Retry can use an already-decoded number or wrapped Long for baseRevision too.
        retry = request | {'baseRevision': wrapped(0), 'size': len(data)}
        self.assertEqual(ticket, service.begin_upload(actor, decode_request('begin_upload', retry)))
        blobs.data[ticket['stagingPath']] = data
        commit = dict(remoteId=REMOTE, archiveId=uid(999))
        head = service.commit(actor, commit)
        self.assertEqual(1, head['revision'])
        self.assertEqual(head, service.commit(actor, commit))
        resumed = service.resume(actor, dict(remoteId=REMOTE))
        self.assertEqual(head['head'], resumed['head'])
        self.assertEqual(1, resumed['revision'])
        self.assertEqual('owner', resumed['role'])

    def test_domain_limits_unknown_fields_and_permissions_remain_enforced(self):
        service = FamilyService(MemoryStore(), MemoryArchives())
        actor = Actor('owner', True)
        service.create(actor, dict(remoteId=REMOTE, wardrobeId=WARDROBE))
        valid = dict(remoteId=REMOTE, archiveId=uid(999), baseRevision=wrapped(0),
                     sha256='a' * 64, size=wrapped(10))
        for changes in ({'size': wrapped(0)}, {'size': wrapped(MAX_ARCHIVE + 1)},
                        {'baseRevision': wrapped(-1)}, {'ownerUid': 'other'}):
            with self.subTest(changes=changes), self.assertRaises(FamilyError):
                service.begin_upload(actor, decode_request('begin_upload', valid | changes))
        with self.assertRaises(FamilyError) as denied:
            service.begin_upload(Actor('stranger', True), decode_request('begin_upload', valid))
        self.assertEqual('permission-denied', denied.exception.code)

    def test_archive_and_database_integer_validation_not_relaxed(self):
        self.assertFalse(integer(wrapped(0)))
        state = snapshot()
        state['wardrobes'][0]['createdAt'] = wrapped(0)
        with self.assertRaises(FamilyError):
            validate_snapshot(state, WARDROBE)


if __name__ == '__main__':
    unittest.main()
