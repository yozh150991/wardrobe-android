package com.familywardrobe.app

import android.os.SystemClock
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

internal data class FamilyReadView(val remote: FamilyRemote, val candidate: FamilyReadCache.Candidate,
    val lease: FamilyReadLease, val sessionId: String) {
    fun readable(now: Long = SystemClock.elapsedRealtime()) = lease.permits(sessionId, now)
}
internal data class FamilyReaderState(val remoteId: String = "", val view: FamilyReadView? = null,
    val running: Boolean = false, val problem: FamilyProblem? = null, val bytes: Long = 0, val total: Long = 0)

internal class FamilyReaderController(private val app: WardrobeApplication, private val session: AccountSession,
    scope: CoroutineScope) {
    private val mutable = MutableStateFlow(FamilyReaderState())
    val state = mutable.asStateFlow()
    private val lock = Mutex()
    private val cache get() = session.familyReadCache
    private fun guard() {
        if (!session.isOpen || app.authBackend.signedInIdentity()?.key != session.identity.key)
            throw CancellationException("Account changed")
    }
    private fun dropView() {
        val old = mutable.value.view
        old?.lease?.clear()
        mutable.value = mutable.value.copy(view = null)
        old?.let { cache.discard(it.candidate) }
    }
    init {
        // Ticks only while a shared view is open: the session outlives the screen (Back keeps the task),
        // and a permanent one-second loop woke the app all day in the background (audit A-06).
        scope.launch(Dispatchers.IO) {
            mutable.map { it.view != null }.distinctUntilChanged().collectLatest { open ->
                while (open && isActive) {
                    delay(1000)
                    lock.withLock {
                        guard()
                        if (mutable.value.view?.readable() == false) {
                            dropView(); mutable.value = mutable.value.copy(problem = FamilyProblem.EXPIRED)
                        }
                    }
                }
            }
        }
    }
    suspend fun open(remoteId: String, refresh: Boolean = false) = withContext(Dispatchers.IO) {
        lock.withLock {
            guard(); require(FamilyRules.validId(remoteId))
            val current = mutable.value
            if (!refresh && current.remoteId == remoteId && current.view?.readable() == true) return@withLock
            // An explicit refresh closes the previous lease; a failed request never extends it.
            dropView()
            mutable.value = FamilyReaderState(remoteId = remoteId, running = true)
            val work = coroutineContext
            val checkpoint = { work.ensureActive(); guard() }
            try {
                val backend = FamilyBackend(session.identity, ::guard)
                val decoder = FamilyEnvelope(Backup(session.context, session.repository, session.photos))
                FamilyReadEngine(session.identity.uid, backend, checkpoint, SystemClock::elapsedRealtime,
                    stage = { remote ->
                        val file = cache.newDownload()
                        try {
                            backend.download(remote, file) { done, total ->
                                checkpoint(); mutable.value = mutable.value.copy(bytes = done, total = total)
                            }
                            checkpoint()
                            try { cache.decode(file, remote, decoder, checkpoint) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { throw FamilyTransferException(FamilyProblem.ARCHIVE, e) }
                        } finally { file.delete() }
                    },
                    publish = { candidate, remote, verifiedAt ->
                        checkpoint()
                        val lease = FamilyReadLease()
                        lease.grant(session.id, FamilyRules.role(session.identity.uid, remote.identity.ownerUid, remote.role, true),
                            verifiedAt, SystemClock.elapsedRealtime())
                        mutable.value = mutable.value.copy(view = FamilyReadView(remote, candidate, lease, session.id))
                    }, discard = cache::discard).run(remoteId)
            } catch (e: CancellationException) {
                dropView(); throw e
            } catch (e: Exception) {
                guard(); dropView()
                mutable.value = mutable.value.copy(problem = when (e) {
                    is FamilyTransferException -> e.problem
                    is FirebaseAuthException -> FamilyProblem.ACCESS
                    is StorageException -> when (e.errorCode) {
                        StorageException.ERROR_NOT_AUTHORIZED, StorageException.ERROR_NOT_AUTHENTICATED -> FamilyProblem.ACCESS
                        StorageException.ERROR_OBJECT_NOT_FOUND -> FamilyProblem.CONFLICT
                        StorageException.ERROR_QUOTA_EXCEEDED -> FamilyProblem.LIMIT
                        else -> FamilyProblem.RETRY
                    }
                    is IllegalArgumentException -> FamilyProblem.ARCHIVE
                    is IllegalStateException -> FamilyProblem.CONFIG
                    else -> FamilyProblem.RETRY
                })
            } finally { mutable.value = mutable.value.copy(running = false) }
        }
    }
    /**
     * FAM-04 (ADR-FAMILY-WARDROBES: owner only): a portable copy of the shared wardrobe as it is on the server now,
     * members' changes included. It is taken from a fresh read under the reader lock, so no refresh can remove the
     * view's files midway. Restoring it adds an independent local wardrobe; membership never travels with it.
     */
    suspend fun exportCopy(remoteId: String, uri: android.net.Uri) {
        open(remoteId, refresh = true)
        withContext(Dispatchers.IO) {
            lock.withLock {
                guard()
                val view = mutable.value.view?.takeIf { it.remote.identity.remoteId == remoteId && readable(it) }
                    ?: throw FamilyTransferException(mutable.value.problem ?: FamilyProblem.RETRY)
                val role = FamilyRules.role(session.identity.uid, view.remote.identity.ownerUid, view.remote.role, true)
                if (!FamilyRules.mayExport(role)) throw FamilyTransferException(FamilyProblem.ACCESS)
                val work = coroutineContext
                Backup(session.context, session.repository, view.candidate.photos)
                    .exportSnapshot(uri, view.candidate.snapshot) { work.ensureActive(); guard() }
            }
        }
    }
    suspend fun invalidate() = withContext(Dispatchers.IO) {
        lock.withLock { guard(); dropView(); mutable.value = FamilyReaderState() }
    }
    fun invalidateSession() {
        val old = mutable.value.view
        old?.lease?.clear()
        old?.candidate?.photos?.clearMemoryCache()
        mutable.value = FamilyReaderState()
        // Files are removed by AccountSession after all ViewModel work has stopped.
    }
    /** Used when the UI resumes: never leave a background-expired picture visible. */
    fun readable(view: FamilyReadView): Boolean = session.isOpen &&
        app.authBackend.signedInIdentity()?.key == session.identity.key && view.readable()
}
