package com.familywardrobe.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

internal data class FamilyAddState(val remoteId: String = "", val localWardrobe: String = "", val wardrobeName: String = "",
    val choices: List<Garment> = emptyList(), val draft: FamilyAddDraft? = null, val preview: PhotoStore? = null,
    val selection: Snapshot? = null, val running: Boolean = false, val problem: FamilyProblem? = null, val recoverable: Boolean = false, val synced: Boolean = false)
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
            guard(); mutable.value = mutable.value.copy(running = true, problem = null, synced = false)
            val work = coroutineContext; val checkpoint = { work.ensureActive(); guard() }
            block(FamilyBackend(session.identity, checkpoint), checkpoint); checkpoint()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            guard(); val problem = (e as? FamilyTransferException)?.problem ?: if (e is IllegalArgumentException) FamilyProblem.ARCHIVE else FamilyProblem.RETRY
            if (problem == FamilyProblem.ACCESS) {
                lease.clear(); mutable.value.preview?.clearMemoryCache(); reader.invalidate()
                mutable.value = FamilyAddState(remoteId = mutable.value.remoteId, localWardrobe = mutable.value.localWardrobe, running = true)
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
    private suspend fun fresh(backend: FamilyBackend): FamilyReadView {
        val remote = mutable.value.remoteId; authorize(backend, remote); reader.open(remote, refresh = true); guard()
        val view = reader.state.value.view?.takeIf { it.remote.identity.remoteId == remote && reader.readable(it) }
            ?: throw FamilyTransferException(reader.state.value.problem ?: FamilyProblem.RETRY)
        if (view.remote.role !in setOf("owner", "editor")) throw FamilyTransferException(FamilyProblem.ACCESS)
        return view
    }
    fun open(remote: String, localWardrobe: String) = run { backend, checkpoint ->
        mutable.value.preview?.clearMemoryCache()
        mutable.value = FamilyAddState(remoteId = remote, localWardrobe = localWardrobe, running = true)
        authorize(backend, remote)
        val draft = store.get(remote)
        // The durable receipt identity stays available even when local ZIP/photo bytes
        // are missing. Retrying may confirm an already committed historical receipt.
        mutable.value = mutable.value.copy(draft = draft)
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
            mutable.value = mutable.value.copy(draft = draft, preview = store.photos(draft.publication.archiveId), selection = selection, recoverable = false)
            if (previous != null) store.directory(previous.publication.archiveId).deleteRecursively()
        } finally { if (!installed) store.directory(draft.publication.archiveId).deleteRecursively() }
    }
    fun prepare(itemId: String) = run { backend, checkpoint ->
        require(mutable.value.draft == null && store.get(mutable.value.remoteId) == null)
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
        catch (e: Exception) { if (store.get(draft.remoteId) != draft) store.directory(draft.publication.archiveId).deleteRecursively(); throw e }
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
        mutable.value = mutable.value.copy(draft = null, preview = null, selection = null, synced = true, recoverable = false)
        reader.open(draft.remoteId, refresh = true)
    }
    fun rebase() = run { backend, checkpoint ->
        val previous = requireNotNull(mutable.value.draft); require(mutable.value.recoverable || !previous.submitted)
        val view = fresh(backend)
        val draft = FamilyAddArchive(session.context, session.repository, store, checkpoint)
            .rebase(previous, view.remote, view.candidate.snapshot, view.candidate.photos)
        try { authorize(backend, draft.remoteId); install(draft, previous, checkpoint) }
        catch (e: Exception) { if (store.get(draft.remoteId) != draft) store.directory(draft.publication.archiveId).deleteRecursively(); throw e }
    }
    fun discard() = run { _, _ ->
        val draft = requireNotNull(mutable.value.draft); require(!draft.submitted || mutable.value.recoverable)
        store.remove(draft); mutable.value.preview?.clearMemoryCache()
        mutable.value = mutable.value.copy(draft = null, preview = null, selection = null, recoverable = false)
    }
    fun invalidateSession() { lease.clear(); mutable.value.preview?.clearMemoryCache(); mutable.value = FamilyAddState() }
}
