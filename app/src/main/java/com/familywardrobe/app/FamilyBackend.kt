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
    app: FirebaseApp = FirebaseApp.getInstance()) : FamilyTransport, FamilyReadTransport {
    private val auth = FirebaseAuth.getInstance(app)
    private val functions = FirebaseFunctions.getInstance(app,"europe-west1")
    private val storage = FirebaseStorage.getInstance(app)
    init { require(app.options.projectId == account.project) }
    private fun check() { guard(); if (auth.currentUser?.uid != account.uid) throw CancellationException("Account changed") }
    override suspend fun authenticate() {
        check(); val user = requireNotNull(auth.currentUser); user.reload().await(); check()
        if (!user.isEmailVerified) throw FamilyTransferException(FamilyProblem.EMAIL)
        user.getIdToken(true).await(); check()
    }
    suspend fun sendVerification() { check(); requireNotNull(auth.currentUser).sendEmailVerification().await(); check() }
    private suspend fun call(name: String, data: Map<String, Any>): Map<*, *> {
        check()
        try {
            val result = functions.getHttpsCallable(name).apply { setTimeout(if (name == "family_commit") 540 else 180,TimeUnit.SECONDS) }
                .call(data + ("expectedActorUid" to account.uid)).await().data
            check(); return result as? Map<*, *> ?: throw FamilyTransferException(FamilyProblem.ARCHIVE)
        } catch (e: FirebaseFunctionsException) {
            check(); val reason = (e.details as? Map<*, *>)?.get("reason")
            val problem = when {
                reason in setOf("UPLOAD_EXPIRED", "INVITE_EXPIRED") -> FamilyProblem.EXPIRED
                reason == "INVITE_CLOSED" -> FamilyProblem.CONFLICT
                reason == "EDIT_ITEM_MISSING" -> FamilyProblem.MISSING
                reason == "ARCHIVE_MISSING" -> FamilyProblem.MISSING
                e.code in setOf(FirebaseFunctionsException.Code.UNAUTHENTICATED,FirebaseFunctionsException.Code.PERMISSION_DENIED) -> FamilyProblem.ACCESS
                e.code in setOf(FirebaseFunctionsException.Code.NOT_FOUND,FirebaseFunctionsException.Code.FAILED_PRECONDITION) -> FamilyProblem.CONFIG
                e.code in setOf(FirebaseFunctionsException.Code.ABORTED,FirebaseFunctionsException.Code.ALREADY_EXISTS) -> FamilyProblem.CONFLICT
                e.code == FirebaseFunctionsException.Code.INVALID_ARGUMENT -> FamilyProblem.ARCHIVE
                e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> FamilyProblem.LIMIT
                else -> FamilyProblem.RETRY
            }
            throw FamilyTransferException(problem,e)
        }
    }
    suspend fun accessCall(operation: String, data: Map<String, Any>): Map<*, *> {
        require(operation in setOf("directory", "invitations", "members", "invite", "respond_invite", "cancel_invite", "change_member", "leave", "edit_item"))
        return call("family_$operation", data)
    }
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
