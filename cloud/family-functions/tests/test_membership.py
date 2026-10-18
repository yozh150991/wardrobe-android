import copy
import unittest
from concurrent.futures import ThreadPoolExecutor
from family.errors import FamilyError
from family.service import Actor, FamilyService
from family.membership import INVITE_TTL, email, email_key
from family.callable_wire import decode_request
from test_family import MemoryStore, MemoryArchives
from fixtures import REMOTE, WARDROBE, uid


class TransactionStore(MemoryStore):
    """Same API, plus Firestore's prohibition on reads after writes."""
    def run(self, callback):
        def strict(tx):
            class Transaction:
                written = False
                def get(self, path):
                    assert not self.written, 'Read after transaction write'
                    return tx.get(path)
                def owned_roots(self, uid, limit):
                    assert not self.written, 'Query after transaction write'
                    return tx.owned_roots(uid, limit)
                def set(self, path, value):
                    self.written = True; tx.set(path, value)
            return callback(Transaction())
        return super().run(strict)


class MembershipTests(unittest.TestCase):
    def setUp(self):
        self.store = TransactionStore(); self.now = 1800000000000
        self.service = FamilyService(self.store, MemoryArchives(), lambda: self.now)
        self.owner = Actor('owner', True, 'owner@example.com')
        self.user = Actor('member', True, 'member@example.com')
        self.other = Actor('other', True, 'other@example.com')
        self.service.create(self.owner, dict(remoteId=REMOTE, wardrobeId=WARDROBE))
        self.request = dict(remoteId=REMOTE, inviteId=uid(900), email=self.user.email, role='viewer', name='Family')
    def error(self, code, call):
        with self.assertRaises(FamilyError) as caught: call()
        self.assertEqual(code, caught.exception.code)
    def invite(self, **changes): return self.service.invite(self.owner, self.request | changes)
    def respond(self, action='accept', actor=None, id_=None):
        return self.service.respond_invite(actor or self.user, dict(inviteId=id_ or self.request['inviteId'], action=action))
    def change(self, role, version=1, request=901, actor=None):
        return self.service.change_member(actor or self.owner, dict(remoteId=REMOTE, uid=self.user.uid, role=role, expectedVersion=version, requestId=uid(request)))
    def member(self): return self.store.data[f'familyWardrobes/{REMOTE}/members/{self.user.uid}']
    def test_discovery_finds_pre_index_owner_and_accepted_member(self):
        self.assertEqual('owner', self.service.directory(self.owner, {})['wardrobes'][0]['role'])
        self.assertEqual([], self.service.directory(self.user, {})['wardrobes'])
        self.invite(); self.respond()
        result = self.service.directory(self.user, {})['wardrobes'][0]
        self.assertEqual(('Family','viewer',False), (result['name'],result['role'],result['ready']))
        self.assertEqual('viewer', self.service.resume(self.user, dict(remoteId=REMOTE))['role'])
    def test_verified_exact_email_required_no_client_identity_override(self):
        self.invite()
        for actor in [self.other, Actor('member',False,self.user.email)]:
            self.error('permission-denied' if actor == self.other else 'unauthenticated', lambda: self.respond(actor=actor))
        self.error('invalid-argument', lambda: self.service.invitations(self.user, {'email': self.owner.email}))
        self.assertEqual([],self.service.invitations(self.other,{})['invitations'])
        self.assertEqual(1,len(self.service.invitations(self.user,{})['invitations']))
        self.respond(actor=Actor('member',True,' MEMBER@EXAMPLE.COM '))
    def test_invalid_email_role_fields_and_self(self):
        for bad in ['a','a@b','a@b.com\nX','é@example.com', 'a'*255+'@example.com']:
            self.error('invalid-argument', lambda: self.invite(email=bad))
        for role in ['owner','admin','',True]: self.error('invalid-argument', lambda: self.invite(role=role))
        self.error('invalid-argument', lambda: self.invite(email=self.owner.email))
        self.error('invalid-argument', lambda: self.invite(name=''))
        self.error('invalid-argument', lambda: self.invite(name='bad\nname'))
        self.error('invalid-argument', lambda: self.invite(uid='forged'))
    def test_owner_only_management(self):
        self.invite(role='editor'); self.respond()
        for actor in [self.user,self.other]:
            self.error('permission-denied', lambda: self.service.members(actor,dict(remoteId=REMOTE)))
            self.error('permission-denied', lambda: self.service.invite(actor,self.request|{'email':'third@example.com','inviteId':uid(912)}))
            self.error('permission-denied', lambda: self.change('editor', actor=actor))
            self.error('permission-denied', lambda: self.service.cancel_invite(actor,dict(remoteId=REMOTE,inviteId=uid(900))))
    def test_invitation_idempotence_and_collision(self):
        first=self.invite(); self.assertEqual(first,self.invite())
        self.assertEqual(1,self.store.data['familyInviteQuotas/owner']['count'])
        self.error('already-exists',lambda:self.invite(role='editor'))
        self.error('already-exists',lambda:self.invite(inviteId=uid(901)))
        self.respond(); self.assertEqual('accepted',self.invite()['status'])
        self.assertEqual(self.respond(),self.respond())
        self.error('already-exists',lambda:self.invite(inviteId=uid(901)))
    def test_decline_removes_inbox_and_cannot_accept(self):
        self.invite(); self.respond('decline'); self.respond('decline')
        self.assertEqual([],self.service.invitations(self.user,{})['invitations'])
        self.assertEqual([],self.service.members(self.owner,dict(remoteId=REMOTE))['invitations'])
        self.error('failed-precondition',lambda:self.respond())
        self.assertEqual([],self.service.directory(self.user,{})['wardrobes'])
        self.invite(inviteId=uid(901)); self.respond(id_=uid(901))
    def test_expiry_at_boundary_and_replace(self):
        self.invite(); self.now+=INVITE_TTL
        self.error('failed-precondition',lambda:self.respond())
        self.assertEqual([],self.service.invitations(self.user,{})['invitations'])
        self.invite(inviteId=uid(901)); self.respond(id_=uid(901))
    def test_cancel_pending_replay_never_grants(self):
        self.invite(); request=dict(remoteId=REMOTE,inviteId=uid(900))
        self.service.cancel_invite(self.owner,request); self.service.cancel_invite(self.owner,request)
        self.error('failed-precondition',lambda:self.respond())
        self.assertEqual([],self.service.invitations(self.user,{})['invitations'])
        self.assertEqual('revoked',self.invite()['status'])
    def test_cancel_accepted_requires_member_revocation(self):
        self.invite();self.respond()
        self.error('failed-precondition',lambda:self.service.cancel_invite(self.owner,dict(remoteId=REMOTE,inviteId=uid(900))))
        self.assertTrue(self.member()['active'])
    def test_role_cas_and_idempotent_retry(self):
        self.invite();self.respond()
        result=self.change('editor'); self.assertEqual(result,self.change('editor'))
        self.assertEqual(2,self.member()['version'])
        self.assertEqual('editor',self.service.resume(self.user,dict(remoteId=REMOTE))['role'])
        self.error('already-exists',lambda:self.change('viewer'))
        self.error('aborted',lambda:self.change('viewer',request=902))
        self.change('viewer',version=2,request=902)
        self.error('aborted',lambda:self.change('editor'))
        self.respond() # Old acceptance must not reset the current role.
        self.assertEqual('viewer',self.member()['role'])
    def test_revoke_removes_discovery_and_denies_head_and_old_accept(self):
        self.invite();self.respond(); result=self.change('revoked')
        self.assertFalse(result['active']); self.assertEqual(result,self.change('revoked'))
        self.assertEqual([],self.service.directory(self.user,{})['wardrobes'])
        self.assertEqual([],self.service.members(self.owner,dict(remoteId=REMOTE))['members'])
        self.error('permission-denied',lambda:self.service.resume(self.user,dict(remoteId=REMOTE)))
        self.error('failed-precondition',lambda:self.respond())
        self.invite(inviteId=uid(902),role='editor'); self.respond(id_=uid(902))
        self.assertEqual('editor',self.member()['role']);self.assertEqual(3,self.member()['version'])
        self.error('aborted',lambda:self.change('revoked'))
        self.error('failed-precondition',lambda:self.respond())
    def test_uid_bound_after_accept_even_if_same_email(self):
        self.invite(); self.respond()
        self.error('failed-precondition', lambda:self.respond(actor=Actor('different',True,self.user.email)))
        self.assertEqual([],self.service.directory(Actor('different',True,self.user.email),{})['wardrobes'])
    def test_leave_cannot_remove_owner_or_other_member(self):
        request=dict(remoteId=REMOTE,requestId=uid(910))
        self.error('permission-denied',lambda:self.service.leave(self.owner,request))
        self.error('permission-denied',lambda:self.service.leave(self.other,request))
        self.invite();self.respond()
        first=self.service.leave(self.user,request);self.assertEqual(first,self.service.leave(self.user,request))
        self.assertFalse(self.member()['active']);self.error('failed-precondition',lambda:self.respond())
    def test_leave_reused_role_request_is_not_falsely_confirmed(self):
        self.invite(); self.respond(); self.change('editor', request=901)
        self.error('already-exists', lambda:self.service.leave(self.user,dict(remoteId=REMOTE,requestId=uid(901))))
        self.assertTrue(self.member()['active'])
    def test_indexes_do_not_grant_access(self):
        self.store.seed('familyDirectories/other',{'entries':{REMOTE:True}})
        self.assertEqual([],self.service.directory(self.other,{})['wardrobes'])
        self.error('permission-denied',lambda:self.service.resume(self.other,dict(remoteId=REMOTE)))
    def test_archive_hides_directory_and_inbox_denies_accept(self):
        self.invite();path=f'familyWardrobes/{REMOTE}'
        self.store.seed(path,self.store.data[path]|{'archived':True})
        self.assertEqual([],self.service.directory(self.owner,{})['wardrobes'])
        self.assertEqual([],self.service.invitations(self.user,{})['invitations'])
        self.error('permission-denied',lambda:self.respond())
    def test_member_listing_current_role_email_and_pending(self):
        self.invite(); result=self.service.members(self.owner,dict(remoteId=REMOTE))
        self.assertEqual(1,len(result['invitations']));self.assertEqual([],result['members'])
        self.respond();self.change('editor')
        result=self.service.members(self.owner,dict(remoteId=REMOTE))
        self.assertEqual([],result['invitations']);self.assertEqual([dict(uid='member',email=self.user.email,role='editor',version=2)],result['members'])
    def test_daily_quota_no_duplicate_charge(self):
        self.invite();self.store.seed('familyInviteQuotas/owner',dict(day=self.now//86400000,count=30))
        self.invite()
        self.error('resource-exhausted',lambda:self.invite(email=self.other.email,inviteId=uid(901)))
        self.now+=86400000;self.invite(email=self.other.email,inviteId=uid(901))
    def test_inbox_capacity_is_bounded_and_expired_entries_pruned(self):
        path=f'familyInboxes/{email_key(self.user.email)}'
        self.store.seed(path,{'entries':{uid(i):{'expiresAt':self.now+1} for i in range(50)}})
        self.error('resource-exhausted',lambda:self.invite())
        self.now+=1;self.invite();self.assertEqual(1,len(self.store.data[path]['entries']))
    def test_directory_and_member_capacity(self):
        self.invite();self.store.seed('familyDirectories/member',{'entries':{uid(i+10000):True for i in range(50)}})
        self.error('resource-exhausted',lambda:self.respond())
        self.assertEqual('pending',self.store.data[f'familyInvites/{uid(900)}']['status'])
        self.store.seed('familyDirectories/member',{'entries':{}})
        path=f'familyAccess/{REMOTE}'; value=self.store.data[path]
        self.store.seed(path,value|{'members':{str(i):True for i in range(50)}})
        self.error('resource-exhausted',lambda:self.respond())
    def test_concurrent_invites_have_one_pending_winner(self):
        def run(number):
            try: self.invite(inviteId=uid(number));return 'ok'
            except FamilyError as e:return e.code
        with ThreadPoolExecutor(max_workers=2) as pool: result=list(pool.map(run,[901,902]))
        self.assertCountEqual(['ok','already-exists'],result)
        self.assertEqual(1,len(self.service.invitations(self.user,{})['invitations']))
    def test_concurrent_accept_and_cancel_is_serializable(self):
        self.invite()
        def run(action):
            try:
                if action=='accept':self.respond()
                else:self.service.cancel_invite(self.owner,dict(remoteId=REMOTE,inviteId=uid(900)))
                return 'ok'
            except FamilyError as e:return e.code
        with ThreadPoolExecutor(max_workers=2) as pool: result=list(pool.map(run,['accept','cancel']))
        self.assertCountEqual(['ok','failed-precondition'],result)
    def test_expected_version_wire_long_and_boolean_denial(self):
        request=decode_request('change_member',{'expectedVersion':{'@type':'type.googleapis.com/google.protobuf.Int64Value','value':'2'}})
        self.assertEqual(2,request['expectedVersion'])
        self.invite();self.respond();self.error('invalid-argument',lambda:self.change('editor',version=True))
    def test_uid_paths_and_request_field_forgery(self):
        self.invite();self.respond()
        request=dict(remoteId=REMOTE,uid='../other',role='editor',expectedVersion=1,requestId=uid(901))
        self.error('invalid-argument',lambda:self.service.change_member(self.owner,request))
        self.error('invalid-argument',lambda:self.service.leave(self.user,dict(remoteId=REMOTE,requestId=uid(902),uid=self.owner.uid)))
        request['uid']='owner';self.error('invalid-argument',lambda:self.service.change_member(self.owner,request))
    def test_email_normalization_does_not_collapse_dots_or_plus_aliases(self):
        self.assertEqual(email_key('A@EXAMPLE.COM'),email_key('a@example.com'))
        self.assertNotEqual(email_key('a.b@example.com'),email_key('ab@example.com'))
        self.assertNotEqual(email_key('a+b@example.com'),email_key('a@example.com'))
