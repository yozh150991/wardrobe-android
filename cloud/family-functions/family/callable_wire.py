"""Decode only numeric request fields defined by the family callable API.

Firebase mobile SDKs encode Long as protobuf Int64Value, even for small
values. Some Python Functions SDKs leave that JSON object in request.data.
Do not weaken integer validation of stored documents or archive manifests.
"""
import re
from .errors import require

_TYPES = {
    'type.googleapis.com/google.protobuf.Int64Value': (-(1 << 63), (1 << 63) - 1),
    'type.googleapis.com/google.protobuf.UInt64Value': (0, (1 << 64) - 1),
}


def callable_integer(value):
    if type(value) is int:
        return value
    require(type(value) is dict and set(value) == {'@type', 'value'},
            'Invalid callable integer', reason='REQUEST_INTEGER_INVALID')
    kind, text = value['@type'], value['value']
    require(type(kind) is str and kind in _TYPES and type(text) is str
            and len(text) <= 20 and re.fullmatch(r'-?(?:0|[1-9][0-9]*)', text) is not None,
            'Invalid callable integer', reason='REQUEST_INTEGER_INVALID')
    number = int(text)
    low, high = _TYPES[kind]
    require(low <= number <= high, 'Callable integer overflow', reason='REQUEST_INTEGER_INVALID')
    return number


def decode_request(method, data):
    if method != 'begin_upload' or type(data) is not dict:
        return data
    result = dict(data)
    for key in ('baseRevision', 'size'):
        if key in result:
            result[key] = callable_integer(result[key])
    return result
