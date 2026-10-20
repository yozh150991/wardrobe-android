package com.familywardrobe.app

import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

internal data class FamilyPhotoState(val remoteId: String = "", val garmentId: String = "", val garmentName: String = "",
    val localWardrobe: String = "", val choices: List<Garment> = emptyList(), val draft: FamilyAddDraft? = null,
    val preview: PhotoStore? = null, val running: Boolean = false, val problem: FamilyProblem? = null,
    val recoverable: Boolean = false, val synced: Boolean = false)

/**
 * FAM-03 photo replacement for one existing shared item: same durable draft → explicit send →
 * receipt-first retry → explicit rebase flow as additions, with its own journal folder.
 */
internal class FamilyPhotoController(private val app: WardrobeApplication, private val session: AccountSession,
    private val reader: FamilyReaderController, private val scope: CoroutineScope) {
    private val lease = FamilyReadLease(); private val lock = Mutex()
    private val store = FamilyAddStore(session.context, session.identity, "family-photo-replacements")
    private val mutable = MutableStateFlow(FamilyPhotoState()); val state = mutable.asStateFlow()
    fun readable() = session.isOpen && app.authBackend.signedInIdentity()?.key == session.identity.key && lease.permits(session.id, android.os.SystemClock.elapsedRealtime())
    private fun guard() {
        if (!session.isOpen || app.authBackend.signedInIdentity()?.key != session.identity.key) throw CancellationException("Account changed")
    }
    /** A camera file inside this account's FileProvider directory; imported and deleted by prepare. */
    fun newCapture(): Uri = session.photos.newCapture()

    // Actions are queued, never dropped (audit 0.14.6 №10).
    private fun run(block: suspend (FamilyBackend, () -> Unit) -> Unit) = scope.launch(Dispatchers.IO) { lock.withLock {
        try {
            guard(); mutable.value = mutable.value.copy(running = true, problem = null, synced = false)
            val work = coroutineContext; val checkpoint = { work.ensureActive(); guard() }
            block(FamilyBackend(session.identity, checkpoint), checkpoint); checkpoint()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            guard(); val problem = (e as? FamilyTransferException)?.problem
                ?: if (e is UnreadablePhotoException || e is IllegalArgumentException) FamilyProblem.ARCHIVE else FamilyProblem.RETRY
            if (problem == FamilyProblem.ACCESS) {
                lease.clear(); mutable.value.preview?.clearMemoryCache(); reader.invalidate()
                mutable.value = FamilyPhotoState(remoteId = mutable.value.remoteId, localWardrobe = mutable.value.localWardrobe, running = true)
            }
            mutable.value = mutable.value.copy(problem = problem)
        } finally { if (session.isOpen) mutable.value = mutable.value.copy(running = false) }
    } }
    private suspend fun authorize(backend: FamilyBackend, remote: String): FamilyRemote {
        backend.authenticate(); val started = android.os.SystemClock.elapsedRealtime()
        return backend.readHead(remote).also {
            if (it.role !in setOf("owner", "editor")) throw FamilyTransferException(FamilyProblem.ACCESS)
            lease.grant(session.id, FamilyRules.role(session.identity.uid, it.identity.ownerUid, it.role, true), started, android.os.SystemClock.elapsedRealtime())
        }
    }
    private suspend fun fresh(backend: FamilyBackend, remote: String): FamilyReadView {
        authorize(backend, remote); reader.open(remote, refresh = true); guard()
        val view = reader.state.value.view?.takeIf { it.remote.identity.remoteId == remote && reader.readable(it) }
            ?: throw FamilyTransferException(reader.state.value.problem ?: FamilyProblem.RETRY)
        if (view.remote.role !in setOf("owner", "editor")) throw FamilyTransferException(FamilyProblem.ACCESS)
        return view
    }
    private fun show(draft: FamilyAddDraft?) {
        mutable.value.preview?.clearMemoryCache()
        // garmentId stays the item the user opened; the dialog explains when the pending draft is for another item.
        mutable.value = mutable.value.copy(draft = draft, preview = draft?.let { store.photos(it.publication.archiveId) })
    }

    /** Opens the pending replacement of this shared wardrobe, or starts one for [garmentId]. */
    fun open(remote: String, garmentId: String, garmentName: String, localWardrobe: String) = run { backend, _ ->
        mutable.value.preview?.clearMemoryCache()
        mutable.value = FamilyPhotoState(remoteId = remote, garmentId = garmentId, garmentName = garmentName,
            localWardrobe = localWardrobe, running = true)
        authorize(backend, remote)
        show(store.get(remote))
        // Short private read only; the photo itself is copied later under a pin, outside the lock.
        val local = session.repository.writes.withLock { session.repository.snapshot() }
        mutable.value = mutable.value.copy(choices = local.garments
            .filter { it.wardrobeId == localWardrobe && !it.archived && it.photoId.isNotBlank() }
            .sortedBy { it.name.lowercase() })
    }

    private suspend fun prepare(backend: FamilyBackend, checkpoint: () -> Unit, obtain: suspend (PhotoStore) -> String) {
        val remote = mutable.value.remoteId; val garment = mutable.value.garmentId
        require(mutable.value.draft == null && store.get(remote) == null)
        val view = fresh(backend, remote)
        val archive = FamilyPhotoArchive(session.context, session.repository, store, checkpoint)
        val draft = archive.stage(view.remote, view.candidate.snapshot, view.candidate.photos, garment, obtain)
        try { authorize(backend, draft.remoteId); store.put(draft, null); show(draft) }
        catch (e: Exception) { if (store.get(draft.remoteId) != draft) store.directory(draft.publication.archiveId).deleteRecursively(); throw e }
    }
    /** New photo from the gallery or the camera, imported as is (rotation and size limits; no cutout). */
    fun prepareFromUri(uri: Uri) = run { backend, checkpoint -> prepare(backend, checkpoint) { photos -> photos.import(uri) } }
    /** The final (already processed) photo of one of the user's private items. */
    fun prepareFromItem(itemId: String) = run { backend, checkpoint ->
        val photoId = session.repository.writes.withLock {
            val item = session.repository.snapshot().garments.single {
                it.id == itemId && it.wardrobeId == mutable.value.localWardrobe && !it.archived && it.photoId.isNotBlank()
            }
            session.photos.pin(setOf(item.photoId)); item.photoId
        }
        try {
            prepare(backend, checkpoint) { photos ->
                newId().also { FamilyAddArchive(session.context, session.repository, store, checkpoint).copyPhoto(session.photos, photoId, photos, it) }
            }
        } finally { session.photos.unpin(setOf(photoId)) }
    }
    fun sync() = run { backend, checkpoint ->
        var draft = requireNotNull(mutable.value.draft)
        authorize(backend, draft.remoteId)
        val previous = draft; draft = draft.copy(submitted = true); store.put(draft, previous)
        mutable.value = mutable.value.copy(draft = draft, recoverable = false)
        try {
            publishFamilyAddition(draft.publication, backend, { store.checked(draft, checkpoint) }, checkpoint)
        } catch (e: FamilyTransferException) {
            // Only a definite pending-upload rejection makes rebase/discard safe.
            if (e.problem in setOf(FamilyProblem.CONFLICT, FamilyProblem.EXPIRED, FamilyProblem.ARCHIVE, FamilyProblem.LIMIT))
                mutable.value = mutable.value.copy(recoverable = true)
            throw e
        }
        checkpoint(); store.remove(draft); mutable.value.preview?.clearMemoryCache()
        mutable.value = mutable.value.copy(draft = null, preview = null, synced = true, recoverable = false)
        reader.open(draft.remoteId, refresh = true)
    }
    fun rebase() = run { backend, checkpoint ->
        val previous = requireNotNull(mutable.value.draft); require(mutable.value.recoverable || !previous.submitted)
        val view = fresh(backend, previous.remoteId)
        val draft = FamilyPhotoArchive(session.context, session.repository, store, checkpoint)
            .rebase(previous, view.remote, view.candidate.snapshot, view.candidate.photos)
        try {
            authorize(backend, draft.remoteId); store.put(draft, previous)
            store.directory(previous.publication.archiveId).deleteRecursively()
            show(draft); mutable.value = mutable.value.copy(recoverable = false)
        } catch (e: Exception) { if (store.get(draft.remoteId) != draft) store.directory(draft.publication.archiveId).deleteRecursively(); throw e }
    }
    fun discard() = run { _, _ ->
        val draft = requireNotNull(mutable.value.draft); require(!draft.submitted || mutable.value.recoverable)
        store.remove(draft); mutable.value.preview?.clearMemoryCache()
        mutable.value = mutable.value.copy(draft = null, preview = null, recoverable = false)
    }
    fun invalidateSession() { lease.clear(); mutable.value.preview?.clearMemoryCache(); mutable.value = FamilyPhotoState() }
}
