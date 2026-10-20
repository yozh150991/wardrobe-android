"""Callable boundary. Not included in the existing private-cloud deployment config."""
import os
import logging
from firebase_admin import auth, firestore, storage
from family.runtime import initialize
from firebase_functions import https_fn, options
from family.errors import FamilyError
from family.callable_wire import decode_request
from family.service import Actor, FamilyService
from family.firebase_adapters import FirestoreStore, StorageArchives

initialize()
options.set_global_options(region='europe-west1', max_instances=2, concurrency=1,
                           memory=options.MemoryOption.MB_512, timeout_sec=180)


def dispatch(request, method):
    if request.auth is None:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, 'Verified sign-in required')
    # Check current revocation/disabled status too; never trust UID/email in request.data.
    token = request.raw_request.headers.get('Authorization', '')
    if not token.startswith('Bearer '):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, 'Verified sign-in required')
    try:
        decoded = auth.verify_id_token(token[7:], check_revoked=True)
    except (ValueError, auth.InvalidIdTokenError, auth.RevokedIdTokenError, auth.UserDisabledError):
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, 'Verified sign-in required')
    actor = Actor(decoded['uid'], decoded.get('email_verified') is True, decoded.get('email', ''))
    data = request.data
    # New clients bind SDK token retrieval to the account that started the request.
    if type(data) is dict and 'expectedActorUid' in data:
        if data['expectedActorUid'] != actor.uid:
            raise https_fn.HttpsError(https_fn.FunctionsErrorCode.UNAUTHENTICATED, 'Account changed')
        data = {k: v for k, v in data.items() if k != 'expectedActorUid'}
    bucket_name = os.environ.get('FAMILY_STORAGE_BUCKET')
    if not bucket_name:
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode.FAILED_PRECONDITION, 'Family backend is not configured')
    service = FamilyService(FirestoreStore(firestore.client()), StorageArchives(storage.bucket(bucket_name)))
    try:
        return getattr(service, method)(actor, decode_request(method, data))
    except FamilyError as exc:
        # Fixed validation messages only; no tokens, account IDs or wardrobe contents.
        logging.warning('family validation: method=%s code=%s reason=%s message=%s',
                        method, exc.code, exc.reason or 'VALIDATION', str(exc))
        raise https_fn.HttpsError(https_fn.FunctionsErrorCode(exc.code), str(exc), details={"reason": exc.reason} if exc.reason else None) from exc


@https_fn.on_call()
def family_create(request: https_fn.CallableRequest):
    return dispatch(request, 'create')


@https_fn.on_call()
def family_resume(request: https_fn.CallableRequest):
    return dispatch(request, 'resume')


@https_fn.on_call()
def family_begin_upload(request: https_fn.CallableRequest):
    return dispatch(request, 'begin_upload')


@https_fn.on_call()
def family_commit(request: https_fn.CallableRequest):
    return dispatch(request, 'commit')


@https_fn.on_call()
def family_directory(request: https_fn.CallableRequest):
    return dispatch(request, 'directory')


@https_fn.on_call()
def family_invitations(request: https_fn.CallableRequest):
    return dispatch(request, 'invitations')


@https_fn.on_call()
def family_members(request: https_fn.CallableRequest):
    return dispatch(request, 'members')


@https_fn.on_call()
def family_invite(request: https_fn.CallableRequest):
    return dispatch(request, 'invite')


@https_fn.on_call()
def family_respond_invite(request: https_fn.CallableRequest):
    return dispatch(request, 'respond_invite')


@https_fn.on_call()
def family_cancel_invite(request: https_fn.CallableRequest):
    return dispatch(request, 'cancel_invite')


@https_fn.on_call()
def family_change_member(request: https_fn.CallableRequest):
    return dispatch(request, 'change_member')


@https_fn.on_call()
def family_leave(request: https_fn.CallableRequest):
    return dispatch(request, 'leave')


@https_fn.on_call()
def family_edit_item(request: https_fn.CallableRequest):
    return dispatch(request, 'edit_item')
