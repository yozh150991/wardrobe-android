"""FAM-02 integration, local demo emulators only. Run separately from host tests.

Uses the existing guarded Auth/Firestore/Functions emulator helpers; never
connects to production. No email delivery is configured or performed.
"""
import unittest
import emulator_family as base
from fixtures import REMOTE, WARDROBE, uid


class MembershipCallableTests(unittest.TestCase):
    call = base.CallableTests.call
    login = base.CallableTests.login

    def test_verified_invite_discovery_role_and_revoke(self):
        base.clear_db()
        owner, owner_token = self.login(True)
        member, member_token = self.login(True)
        other, other_token = self.login(True)
        def call(name, data, token):
            result = self.call('family_' + name, data, token)
            self.assertNotIn('error', result)
            return result['result']
        call('create', dict(remoteId=REMOTE, wardrobeId=WARDROBE), owner_token)
        self.assertEqual('owner', call('directory', {}, owner_token)['wardrobes'][0]['role'])
        request = dict(remoteId=REMOTE, inviteId=uid(991), email=member.email, role='viewer', name='Family')
        call('invite', request, owner_token)
        self.assertEqual([], call('invitations', {}, other_token)['invitations'])
        self.assertEqual('PERMISSION_DENIED', self.call('family_respond_invite',dict(inviteId=uid(991),action='accept'),other_token)['error']['status'])
        self.assertEqual(1,len(call('invitations',{},member_token)['invitations']))
        call('respond_invite', dict(inviteId=uid(991), action='accept'), member_token)
        self.assertEqual('viewer',call('directory',{},member_token)['wardrobes'][0]['role'])
        self.assertEqual('viewer',call('resume',dict(remoteId=REMOTE),member_token)['role'])
        self.assertEqual(member.uid,call('members',dict(remoteId=REMOTE),owner_token)['members'][0]['uid'])
        change=dict(remoteId=REMOTE,uid=member.uid,role='editor',expectedVersion={'@type':'type.googleapis.com/google.protobuf.Int64Value','value':'1'},requestId=uid(992))
        self.assertEqual(2,call('change_member',change,owner_token)['version'])
        self.assertEqual('editor',call('resume',dict(remoteId=REMOTE),member_token)['role'])
        call('change_member',change|{'role':'revoked','expectedVersion':2,'requestId':uid(993)},owner_token)
        self.assertEqual([],call('directory',{},member_token)['wardrobes'])
        self.assertEqual('PERMISSION_DENIED',self.call('family_resume',dict(remoteId=REMOTE),member_token)['error']['status'])
        self.assertEqual('FAILED_PRECONDITION',self.call('family_respond_invite',dict(inviteId=uid(991),action='accept'),member_token)['error']['status'])

    def test_actor_binding_and_unverified_inbox(self):
        user,token=self.login(False)
        self.assertEqual('UNAUTHENTICATED',self.call('family_invitations',{},token)['error']['status'])
        user,token=self.login(True)
        self.assertEqual('UNAUTHENTICATED',self.call('family_directory',{'expectedActorUid':'another'},token)['error']['status'])
        self.assertEqual('INVALID_ARGUMENT',self.call('family_invitations',{'email':'forged@example.com'},token)['error']['status'])


if __name__ == '__main__': unittest.main(verbosity=2)
