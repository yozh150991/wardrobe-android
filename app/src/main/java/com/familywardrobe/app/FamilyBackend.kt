package com.familywardrobe.app

import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.concurrent.TimeUnit

/** Family metadata is callable-only; expectedActorUid binds asynchronous SDK authentication to its originating account. */
internal class FamilyBackend(private val account: AuthIdentity, private val guard: () -> Unit,
    app: FirebaseApp = FirebaseApp.getInstance()) : FamilyTransport, FamilyReadTransport, FamilyPublicationTransport, FamilyChangeTransport {
    private val auth = FirebaseAuth.getInstance(app)
    private val functions = FirebaseFunctions.getInstance(app,"europe-west1")
    private val storage = FirebaseStorage.getInstance(app).also(StorageLimits::configure)
    init { require(app.options.projectId == account.project) }
    private fun check() { guard(); if (auth.currentUser?.uid != account.uid) throw CancellationException("Account changed") }
    override suspend fun authenticate() {
        // Signed out meanwhile: stop like any account change instead of reporting a damaged archive.
        check(); val user = auth.currentUser ?: throw CancellationException("Account changed"); user.reload().await(); check()
        if (!user.isEmailVerified) throw FamilyTransferException(FamilyProblem.EMAIL)
        user.getIdToken(true).await(); check()
    }
    suspend fun sendVerification() { check(); (auth.currentUser ?: throw CancellationException("Account changed")).sendEmailVerification().await(); check() }
    private suspend fun call(name: String, data: Map<String, Any>): Map<*, *> {
        check()
        try {
            val result = functions.getHttpsCallable(name).apply { setTimeout(if (name in LONG_CALLS) 540 else 180,TimeUnit.SECONDS) }
                .call(data + ("expectedActorUid" to account.uid)).await().data
            check(); return result as? Map<*, *> ?: throw FamilyTransferException(FamilyProblem.RETRY)
        } catch (e: FirebaseFunctionsException) {
            check(); val reason = (e.details as? Map<*, *>)?.get("reason")
            val problem = when {
                reason in setOf("UPLOAD_EXPIRED", "INVITE_EXPIRED") -> FamilyProblem.EXPIRED
                reason == "INVITE_CLOSED" -> FamilyProblem.CONFLICT
                reason == "EDIT_ITEM_MISSING" -> FamilyProblem.MISSING
                reason == "ARCHIVE_MISSING" -> FamilyProblem.MISSING
                reason == FamilyChange.ITEM_MISSING -> FamilyProblem.MISSING
                reason in setOf(FamilyChange.LABEL_CONFLICT, FamilyChange.OUTFIT_CHANGED, FamilyChange.OUTFIT_DUPLICATE) -> FamilyProblem.CONFLICT
                // Another member's commit won the race several times in a row: nothing is wrong with the change.
                reason == FamilyChange.BUSY -> FamilyProblem.RETRY
                // Membership changed meanwhile: refresh and choose again. The caller reads [reason] for the details.
                reason in setOf(FamilyOwnership.OWNER_NOT_EDITOR, FamilyOwnership.OWNER_CHOICE_REQUIRED) -> FamilyProblem.CONFLICT
                reason == FamilyOwnership.REAUTH_REQUIRED -> FamilyProblem.ACCESS
                e.code in setOf(FirebaseFunctionsException.Code.UNAUTHENTICATED,FirebaseFunctionsException.Code.PERMISSION_DENIED) -> FamilyProblem.ACCESS
                e.code in setOf(FirebaseFunctionsException.Code.NOT_FOUND,FirebaseFunctionsException.Code.FAILED_PRECONDITION) -> FamilyProblem.CONFIG
                e.code in setOf(FirebaseFunctionsException.Code.ABORTED,FirebaseFunctionsException.Code.ALREADY_EXISTS) -> FamilyProblem.CONFLICT
                e.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT -> FamilyProblem.ARCHIVE
                e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> FamilyProblem.LIMIT
                else -> FamilyProblem.RETRY
            }
            throw FamilyTransferException(problem, e, reason as? String)
        }
    }
    private companion object {
        /** A transfer rewrites the whole head archive; a deletion may transfer or delete up to 100 wardrobes. */
        val LONG_CALLS = setOf("family_commit", "family_edit_item", "family_apply_change", "family_transfer_owner", "family_delete_account")
    }
    suspend fun accessCall(operation: String, data: Map<String, Any>): Map<*, *> {
        require(operation in setOf("directory", "invitations", "members", "invite", "respond_invite", "cancel_invite", "change_member", "leave",
            "edit_item", "transfer_owner", "delete_account"))
        return call("family_$operation", data)
    }
    override suspend fun beginChange(draft: FamilyAddDraft): FamilyTicket =
        FamilyChange.ticket(call("family_begin_change", FamilyChange.beginRequest(draft)), draft, account.uid)
    override suspend fun applyChange(remoteId: String, changeId: String, text: String): Long =
        FamilyChange.receipt(call("family_apply_change", FamilyChange.applyRequest(remoteId, changeId, text)), remoteId, changeId)
    override suspend fun create(record: FamilyMigrationRecord) = FamilyWire.remote(call("family_create",mapOf("remoteId" to record.remoteId,"wardrobeId" to record.wardrobeId)))
    override suspend fun resume(record: FamilyMigrationRecord) = FamilyWire.remote(call("family_resume",mapOf("remoteId" to record.remoteId)))
    override suspend fun readHead(remoteId: String): FamilyRemote {
        require(FamilyRules.validId(remoteId))
        return FamilyWire.remote(call("family_resume", mapOf("remoteId" to remoteId)))
    }
    override suspend fun download(remote: FamilyRemote, target: File, progress: (Long, Long) -> Unit) {
        check(); val head = requireNotNull(remote.head)
        require(head.path == FamilyRules.archivePath(remote.identity.remoteId, head.archiveId) && head.size in 1..FamilyWire.MAX_ARCHIVE)
        val work = coroutineContext
        val task = storage.reference.child(head.path).getStream { _, stream ->
            try {
                stream.use { input ->
                    work.ensureActive(); check()
                    target.outputStream().use { output ->
                        val count = FamilyCacheZip.copy(input, output, head.size) { work.ensureActive(); check() }
                        require(count == head.size)
                    }
                }
            } finally { if (!work.isActive) target.delete() }
        }
        val listener = OnProgressListener<StreamDownloadTask.TaskSnapshot> {
            try { check(); work.ensureActive(); progress(it.bytesTransferred, head.size) }
            catch (_: CancellationException) { task.cancel() }
        }
        task.addOnProgressListener(listener)
        try { task.await(); check(); work.ensureActive() }
        catch (e: CancellationException) {
            task.cancel()
            throw e
        } finally { task.removeOnProgressListener(listener) }
    }
    override suspend fun beginPublication(publication: FamilyPublication): FamilyTicket =
        publication.ticket(call("family_begin_upload", publication.request()), account.uid)
    override suspend fun commitPublication(publication: FamilyPublication) {
        publication.receipt(call("family_commit", mapOf("remoteId" to publication.identity.remoteId,
            "archiveId" to publication.archiveId)), account.uid)
    }
    override suspend fun begin(record: FamilyMigrationRecord) = FamilyWire.ticket(call("family_begin_upload",mapOf("remoteId" to record.remoteId,
        "archiveId" to record.archiveId,"baseRevision" to 0,"sha256" to record.fingerprint,"size" to record.archiveSize)),record)
    override suspend fun upload(file: File, path: String, progress: (Long,Long) -> Unit) {
        check(); require(path.matches(Regex("familyWardrobes/[a-f0-9-]{36}/staging/${Regex.escape(account.uid)}/[a-f0-9-]{36}\\.zip")))
        val task = storage.reference.child(path).putFile(Uri.fromFile(file),StorageMetadata.Builder().setContentType("application/zip").build())
        val listener = OnProgressListener<UploadTask.TaskSnapshot> {
            try { check(); progress(it.bytesTransferred,it.totalByteCount) } catch (_: CancellationException) { task.cancel() }
        }
        task.addOnProgressListener(listener)
        try { task.await(); check() } catch (e: CancellationException) { task.cancel(); throw e }
        finally { task.removeOnProgressListener(listener) }
    }
    override suspend fun commit(record: FamilyMigrationRecord) {
        call("family_commit",mapOf("remoteId" to record.remoteId,"archiveId" to record.archiveId))
        // A replay may return a historical result. Only a fresh create/resume head is acknowledged.
    }
}

/**
 * Without limits the SDK keeps retrying a lost connection for about ten minutes while the family account lock is
 * held, and every shared editor waits silently (audit A-04). A failed transfer is retried by the user or
 * WorkManager; a committed upload is confirmed by its receipt, so a shorter window loses nothing.
 */
internal object StorageLimits {
    fun configure(storage: FirebaseStorage) {
        storage.maxUploadRetryTimeMillis = 120_000L
        storage.maxDownloadRetryTimeMillis = 120_000L
        storage.maxOperationRetryTimeMillis = 60_000L
    }
}
