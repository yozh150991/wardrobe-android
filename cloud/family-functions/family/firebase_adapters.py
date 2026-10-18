"""Firebase Admin adapters. Admin bypasses Rules: authorization lives in service.py."""
import hashlib
from google.cloud import firestore
from google.api_core.exceptions import NotFound, PreconditionFailed
from .errors import FamilyError, require
from .archive import MAX_ARCHIVE


class FirestoreStore:
    def __init__(self, client):
        self.client = client

    def run(self, callback):
        client = self.client
        class Tx:
            def __init__(self, transaction):
                self.transaction = transaction
            def get(self, path):
                snapshot = client.document(path).get(transaction=self.transaction)
                return snapshot.to_dict() if snapshot.exists else None
            def set(self, path, data):
                self.transaction.set(client.document(path), data)
        @firestore.transactional
        def execute(transaction):
            return callback(Tx(transaction))
        return execute(client.transaction(max_attempts=5))


class StorageArchives:
    def __init__(self, bucket):
        self.bucket = bucket

    def read(self, path, expected_size):
        require(0 < expected_size <= MAX_ARCHIVE)
        blob = self.bucket.blob(path)
        try:
            blob.reload(timeout=30)
            require(blob.size == expected_size and blob.content_type == 'application/zip' and
                    not blob.content_encoding, 'Staged archive metadata mismatch')
            # A changed generation must fail rather than validating different bytes.
            data = blob.download_as_bytes(if_generation_match=int(blob.generation), raw_download=True, timeout=60)
        except NotFound as exc:
            raise FamilyError('failed-precondition', 'Archive has not been uploaded', 'ARCHIVE_MISSING') from exc
        except PreconditionFailed as exc:
            raise FamilyError('aborted', 'Archive changed during validation') from exc
        require(len(data) == expected_size, 'Archive size mismatch')
        return data

    def put_once(self, path, data, checksum):
        blob = self.bucket.blob(path)
        # Fast replay path avoids a redundant upload. The generation precondition
        # below still closes the race between this lookup and the first create.
        if blob.exists(timeout=30):
            blob.reload(timeout=30)
            existing = self.read(path, len(data))
            require(hashlib.sha256(existing).hexdigest() == checksum, 'Archive ID collision', 'already-exists')
            return blob.generation
        blob.metadata = {'sha256':checksum}
        try:
            blob.upload_from_string(data, content_type='application/zip', if_generation_match=0, timeout=60)
        except PreconditionFailed:
            # Metadata alone is not a content integrity proof.
            blob.reload(timeout=30)
            existing = self.read(path, len(data))
            require(hashlib.sha256(existing).hexdigest() == checksum, 'Archive ID collision', 'already-exists')
        return blob.generation
