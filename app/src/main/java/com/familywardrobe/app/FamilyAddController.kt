package com.familywardrobe.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

internal data class FamilyAddState(val remoteId: String = "", val localWardrobe: String = "", val wardrobeName: String = "",
    val choices: List<Garment> = emptyList(), val draft: FamilyAddDraft? = null, val preview: PhotoStore? = null,
    val selection: Snapshot? = null, val running: Boolean = false, val problem: FamilyProblem? = null, val recoverable: Boolean = false, val synced: Boolean = false,
    val unreadablePhoto: Boolean = false, val queue: List<FamilyAddDraft> = emptyList(), val generation: Long = 0)
internal class FamilyAddController(private val app: WardrobeApplication, private val session: AccountSession,
    private val reader: FamilyReaderController, private val scope: CoroutineScope) {
    private val lease = FamilyReadLease(); private val lock = Mutex()
    private val store = FamilyAddStore(session.context, session.identity)
    private val mutable = MutableStateFlow(FamilyAddState()); val state = mutable.asStateFlow()
    fun readable() = session.isOpen && app.authBackend.signedInIdentity()?.key == session.identity.key && lease.permits(session.id, android.os.SystemClock.elapsedRealtime())
    private fun guard() {
        if (!session.isOpen || app.authBackend.signedInIdentity()?.key != session.identity.key) throw CancellationException("Account changed")
    }
    // Queue instead of dropping: a tap during another call used to be ignored silently
    // and the dialog then showed stale state (audit 0.14.6 №10 pattern).
    private fun run(block: suspend (FamilyBackend, () -> Unit) -> Unit) = scope.launch(Dispatchers.IO) { lock.withLock {
        try {
            guard(); mutable.value = mutable.value.copy(running = true, problem = null, synced = false, unreadablePhoto = false)
            val work = coroutineContext; val checkpoint = { work.ensureActive(); guard() }
            block(FamilyBackend(session.identity, checkpoint), checkpoint); checkpoint()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            guard(); val problem = (e as? FamilyTransferException)?.problem ?: if (e is IllegalArgumentException) FamilyProblem.ARCHIVE else FamilyProblem.RETRY
            if (problem == FamilyProblem.ACCESS) {
                lease.clear(); mutable.value.preview?.clearMemoryCache(); reader.invalidate()
                mutable.value = FamilyAddState(remoteId = mutable.value.remoteId, localWardrobe = mutable.value.localWardrobe, running = true, generation = mutable.value.generation + 1)
            }
            mutable.value = mutable.value.copy(problem = problem, unreadablePhoto = e is UnreadablePhotoException)
        } finally { if (session.isOpen) mutable.value = mutable.value.copy(running = false) }
    } }
    private suspend fun authorize(backend: FamilyBackend, remote: String): FamilyRemote {
        backend.authenticate(); val started = android.os.SystemClock.elapsedRealtime()
        return backend.readHead(remote).also {
            if (it.role !in setOf("owner", "editor")) throw FamilyTransferException(FamilyProblem.ACCESS)
            lease.grant(session.id, FamilyRules.role(session.identity.uid, it.identity.ownerUid, it.role, true), started, android.os.SystemClock.elapsedRealtime())
        }
    }
    private suspend fun fresh(backend: FamilyBackend): FamilyReadView {
        val remote = mutable.value.remoteId; authorize(backend, remote); reader.open(remote, refresh = true); guard()
        val view = reader.state.value.view?.takeIf { it.remote.identity.remoteId == remote && reader.readable(it) }
            ?: throw FamilyTransferException(reader.state.value.problem ?: FamilyProblem.RETRY)
        if (view.remote.role !in setOf("owner", "editor")) throw FamilyTransferException(FamilyProblem.ACCESS)
        return view
    }
    fun open(remote: String, localWardrobe: String, garmentId: String? = null, startNew: Boolean = false) = run { backend, checkpoint ->
        mutable.value.preview?.clearMemoryCache()
        lease.clear()
        mutable.value = FamilyAddState(remoteId = remote, localWardrobe = localWardrobe, running = true, generation = mutable.value.generation + 1)
        authorize(backend, remote)
        val draft = if (startNew) null else store.get(remote, garmentId)
        require(garmentId == null || draft != null)
        // The durable receipt identity stays available even when local ZIP/photo bytes
        // are missing. Retrying may confirm an already committed historical receipt.
        mutable.value = mutable.value.copy(draft = draft, queue = store.list(remote))
        val local = session.repository.writes.withLock { session.repository.snapshot() }; checkpoint()
        mutable.value = mutable.value.copy(
            choices = local.garments.filter { it.wardrobeId == localWardrobe && !it.archived }.sortedBy { it.name.lowercase() },
            wardrobeName = local.wardrobes.find { it.id == localWardrobe }?.name.orEmpty())
        if (draft != null) {
            val selected = FamilyAddArchive(session.context, session.repository, store, checkpoint).selection(draft)
            mutable.value = mutable.value.copy(selection = selected, preview = store.photos(draft.publication.archiveId))
        }
    }

    private fun install(draft: FamilyAddDraft, previous: FamilyAddDraft?, checkpoint: () -> Unit) {
        var installed = false
        try {
            val selection = FamilyAddArchive(session.context, session.repository, store, checkpoint).selection(draft)
            checkpoint(); store.put(draft, previous); installed = true
            mutable.value.preview?.clearMemoryCache()
            mutable.value = mutable.value.copy(draft = draft, preview = store.photos(draft.publication.archiveId), selection = selection, recoverable = false, queue = store.list(draft.remoteId), generation = mutable.value.generation + 1)
            if (previous != null) store.directory(previous.publication.archiveId).deleteRecursively()
        } finally { if (!installed) store.directory(draft.publication.archiveId).deleteRecursively() }
    }
    private fun canPrepare(generation: Long) {
        require(mutable.value.generation == generation && mutable.value.draft == null)
        if (store.list(mutable.value.remoteId).size >= FamilyMediaQueue.LIMIT) throw FamilyTransferException(FamilyProblem.LIMIT)
    }
    fun prepare(itemId: String, generation: Long = mutable.value.generation) = run { backend, checkpoint ->
        canPrepare(generation)
        val view = fresh(backend); val archive = FamilyAddArchive(session.context, session.repository, store, checkpoint)
        // Hold the private write lock only to read the item and pin its photo. Copying every
        // shared photo and writing the ZIP can take minutes and must not block the user's
        // own edits (audit 0.14.6 №4 pattern); the pin keeps cleanup from deleting the photo.
        val (selected, photoId) = session.repository.writes.withLock {
            val source = session.repository.snapshot()
            val item = source.garments.single { it.id == itemId && it.wardrobeId == mutable.value.localWardrobe && !it.archived }
            session.photos.pin(setOf(item.photoId))
            FamilyAddition.select(source, item.id, view.candidate.snapshot.wardrobes.single()) to item.photoId
        }
        val draft = try {
            archive.stage(view.remote, view.candidate.snapshot, selected, view.candidate.photos, session.photos, photoId)
        } finally { session.photos.unpin(setOf(photoId)) }
        try { authorize(backend, draft.remoteId); install(draft, null, checkpoint) }
        catch (e: Exception) { if (store.get(draft.remoteId, draft.garmentId) != draft) store.directory(draft.publication.archiveId).deleteRecursively(); throw e }
    }
    /** A camera file inside this account's FileProvider directory; imported and deleted by prepareNew. */
    fun newCapture(): android.net.Uri = session.photos.newCapture()
    /**
     * New item directly in the shared wardrobe: the photo is imported as is (rotation and size
     * limits, no cutout) into an isolated directory; nothing is written to the private wardrobe.
     */
    fun prepareNew(fields: FamilyNewItem, uri: android.net.Uri, generation: Long = mutable.value.generation) = run { backend, checkpoint ->
        canPrepare(generation); require(fields.valid)
        val view = fresh(backend); val archive = FamilyAddArchive(session.context, session.repository, store, checkpoint)
        val temporary = store.directory(newId()); val isolated = store.photos(temporary.name)
        try {
            val photoId = isolated.import(uri); checkpoint()
            val selected = fields.selection(view.candidate.snapshot.wardrobes.single(), isolated.fingerprint(photoId), view.candidate.snapshot)
            val draft = archive.stage(view.remote, view.candidate.snapshot, selected, view.candidate.photos, isolated, photoId)
            try { authorize(backend, draft.remoteId); install(draft, null, checkpoint) }
            catch (e: Exception) { if (store.get(draft.remoteId, draft.garmentId) != draft) store.directory(draft.publication.archiveId).deleteRecursively(); throw e }
        } finally { isolated.clearMemoryCache(); temporary.deleteRecursively() }
    }
    fun sync(expected: FamilyAddDraft? = mutable.value.draft) = run { backend, checkpoint ->
        var draft = requireNotNull(expected); require(mutable.value.draft == draft)
        FamilyMediaQueue.requireHead(store.list(draft.remoteId), draft)
        authorize(backend, draft.remoteId)
        val previous = draft; draft = draft.copy(submitted = true); store.put(draft, previous)
        mutable.value = mutable.value.copy(draft = draft, recoverable = false, queue = store.list(draft.remoteId))
        try {
            publishFamilyAddition(draft.publication, backend, { store.checked(draft, checkpoint) }, checkpoint)
        } catch (e: FamilyTransferException) {
            // Only a definite pending-upload rejection makes rebase/discard safe.
            if (e.problem in setOf(FamilyProblem.CONFLICT, FamilyProblem.EXPIRED, FamilyProblem.ARCHIVE, FamilyProblem.LIMIT))
                mutable.value = mutable.value.copy(recoverable = true)
            throw e
        }
        checkpoint(); store.remove(draft); mutable.value.preview?.clearMemoryCache()
        mutable.value = mutable.value.copy(draft = null, preview = null, selection = null, synced = true, recoverable = false, queue = store.list(draft.remoteId), generation = mutable.value.generation + 1)
        reader.open(draft.remoteId, refresh = true)
    }
    fun rebase(expected: FamilyAddDraft? = mutable.value.draft) = run { backend, checkpoint ->
        val previous = requireNotNull(expected); require(mutable.value.draft == previous); require(mutable.value.recoverable || !previous.submitted)
        val view = fresh(backend)
        val draft = FamilyAddArchive(session.context, session.repository, store, checkpoint)
            .rebase(previous, view.remote, view.candidate.snapshot, view.candidate.photos)
        try { authorize(backend, draft.remoteId); install(draft, previous, checkpoint) }
        catch (e: Exception) { if (store.get(draft.remoteId, draft.garmentId) != draft) store.directory(draft.publication.archiveId).deleteRecursively(); throw e }
    }
    fun discard(expected: FamilyAddDraft? = mutable.value.draft) = run { _, _ ->
        val draft = requireNotNull(expected); require(mutable.value.draft == draft); require(!draft.submitted || mutable.value.recoverable)
        store.remove(draft); mutable.value.preview?.clearMemoryCache()
        mutable.value = mutable.value.copy(draft = null, preview = null, selection = null, recoverable = false, queue = store.list(draft.remoteId), generation = mutable.value.generation + 1)
    }
    fun invalidateSession() { lease.clear(); mutable.value.preview?.clearMemoryCache(); mutable.value = FamilyAddState(generation = mutable.value.generation + 1) }
}
