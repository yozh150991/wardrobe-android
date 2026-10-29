"""Firebase Admin adapters. Admin bypasses Rules: authorization lives in service.py."""
import hashlib
from google.cloud import firestore
from google.cloud.firestore_v1.base_query import FieldFilter
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
            def owned_roots(self, uid, limit):
                query = client.collection("familyWardrobes").where(filter=FieldFilter("ownerUid", "==", uid)).limit(limit)
                return [snapshot.to_dict() for snapshot in query.stream(transaction=self.transaction)]
            def set(self, path, data):
                self.transaction.set(client.document(path), data)
        @firestore.transactional
        def execute(transaction):
            return callback(Tx(transaction))
        return execute(client.transaction(max_attempts=5))

    def delete_tree(self, path):
        """A document and every subcollection below it (account deletion, deleted shared wardrobes)."""
        self.client.recursive_delete(self.client.document(path))


class FirebaseAccounts:
    """Deletes the Firebase Authentication user; an already deleted user is the desired state."""
    def delete(self, uid):
        from firebase_admin import auth
        try:
            auth.delete_user(uid)
        except auth.UserNotFoundError:
            pass


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

    def delete(self, path):
        """Best effort; a missing object is already the desired state."""
        try:
            self.bucket.blob(path).delete(timeout=30)
        except NotFound:
            pass

    def delete_prefix(self, prefix):
        """Every object under prefix, page by page (account deletion, deleted shared wardrobes)."""
        require(prefix.endswith('/') and prefix.count('/') >= 2, 'Invalid prefix')
        for blob in self.bucket.list_blobs(prefix=prefix, timeout=30):
            try:
                blob.delete(timeout=30)
            except NotFound:
                pass

    def purge(self, prefix, older_than_ms, keep=()):
        """Deletes objects under prefix created before older_than_ms (epoch ms); returns how many were removed."""
        removed = 0
        for blob in self.bucket.list_blobs(prefix=prefix, max_results=50, timeout=30):
            created = blob.time_created
            if blob.name in keep or created is None or int(created.timestamp() * 1000) >= older_than_ms:
                continue
            try:
                blob.delete(timeout=30); removed += 1
            except NotFound:
                pass
        return removed
