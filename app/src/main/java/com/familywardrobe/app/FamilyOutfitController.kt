package com.familywardrobe.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

internal data class FamilyOutfitState(val remote: String = "", val form: FamilyOutfitForm? = null,
    val draft: FamilyOutfitDraft? = null, val view: FamilyReadView? = null, val generation: Long = 0,
    val running: Boolean = false, val problem: FamilyProblem? = null, val recoverable: Boolean = false,
    val synced: Boolean = false)

internal class FamilyOutfitController(private val app: WardrobeApplication, private val session: AccountSession,
    private val reader: FamilyReaderController, private val scope: CoroutineScope) {
    private val store = FamilyOutfitStore(session.context, session.identity)
    private val lock = Mutex(); private val lease = FamilyReadLease()
    private val mutable = MutableStateFlow(FamilyOutfitState()); val state = mutable.asStateFlow()
    private fun guard() {
        if (!session.isOpen || app.authBackend.signedInIdentity()?.key != session.identity.key) throw CancellationException("Account changed")
    }
    fun readable(): Boolean = session.isOpen && app.authBackend.signedInIdentity()?.key == session.identity.key &&
        lease.permits(session.id, android.os.SystemClock.elapsedRealtime())
    private fun run(block: suspend (FamilyBackend, () -> Unit) -> Unit) = scope.launch(Dispatchers.IO) { lock.withLock {
        try {
            guard(); mutable.value = mutable.value.copy(running = true, problem = null)
            val work = coroutineContext; val checkpoint = { work.ensureActive(); guard() }
            block(FamilyBackend(session.identity, checkpoint), checkpoint); checkpoint()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            guard(); val problem = (e as? FamilyTransferException)?.problem
                ?: if (e is IllegalArgumentException) FamilyProblem.ARCHIVE else FamilyProblem.RETRY
            if (problem == FamilyProblem.ACCESS) {
                lease.clear(); reader.invalidate()
                mutable.value = FamilyOutfitState(remote = mutable.value.remote, generation = mutable.value.generation + 1, running = true)
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
        return reader.state.value.view?.takeIf { it.remote.identity.remoteId == remote && reader.readable(it) && it.remote.role in setOf("owner", "editor") }
            ?: throw FamilyTransferException(reader.state.value.problem ?: FamilyProblem.RETRY)
    }
    /** An existing form/publication is always resumed, never overwritten by another item selection. */
    fun open(remote: String, outfit: String = "") = run { backend, _ ->
        val generation = mutable.value.generation + 1
        lease.clear()
        mutable.value = FamilyOutfitState(remote = remote, generation = generation, running = true)
        authorize(backend, remote)
        val draft = store.draft(remote)
        if (draft != null) mutable.value = mutable.value.copy(draft = draft)
        else {
            val view = fresh(backend, remote)
            val form = store.form(remote) ?: FamilyOutfitForm(remote, outfit, view.remote.revision).also(store::putForm)
            mutable.value = mutable.value.copy(form = form, view = view,
                problem = if (form.base != view.remote.revision) FamilyProblem.CONFLICT else null)
        }
    }
    /** Editor autosave is serialized with prepare/discard, and late effects from old editors are ignored. */
    fun keep(generation: Long, payload: String) = scope.launch(Dispatchers.IO) { lock.withLock {
        try {
            guard(); val state = mutable.value
            if (state.generation != generation || state.draft != null || state.form == null) return@withLock
            val form = state.form.copy(payload = payload); store.putForm(form)
            mutable.value = state.copy(form = form)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { guard(); mutable.value = mutable.value.copy(problem = FamilyProblem.RETRY) }
    } }
    fun prepare(generation: Long, pieces: List<LookPiece>, name: String, occasion: String, weather: String,
        source: String, target: String, layout: String, background: String) = run { backend, checkpoint ->
        val state = mutable.value; require(state.generation == generation && state.draft == null)
        val form = requireNotNull(state.form)
        val view = fresh(backend, form.remote)
        mutable.value = mutable.value.copy(view = view)
        if (view.remote.revision != form.base) throw FamilyTransferException(FamilyProblem.CONFLICT)
        val change = FamilyOutfitChange(target.ifEmpty { newId() }, target.isEmpty(), name, pieces, occasion, weather, source, layout, background)
        val draft = store.stage(view, change, session.repository, checkpoint)
        try {
            authorize(backend, form.remote); store.put(draft, null)
            // The publication is durable before the editing form is removed.
            mutable.value = mutable.value.copy(draft = draft, form = null, view = null)
            store.clearForm(form.remote)
        } catch (e: Exception) {
            if (store.draft(form.remote)?.publication != draft.publication) store.directory(draft.publication.archiveId).deleteRecursively()
            throw e
        }
    }
    fun sync() = run { backend, checkpoint ->
        var draft = requireNotNull(mutable.value.draft); val remote = draft.publication.identity.remoteId
        authorize(backend, remote)
        val previous = draft; draft = draft.copy(submitted = true); store.put(draft, previous)
        mutable.value = mutable.value.copy(draft = draft, recoverable = false)
        try { publishFamilyAddition(draft.publication, backend, { store.checked(draft, checkpoint) }, checkpoint) }
        catch (e: FamilyTransferException) {
            if (e.problem in setOf(FamilyProblem.CONFLICT, FamilyProblem.EXPIRED, FamilyProblem.ARCHIVE, FamilyProblem.LIMIT))
                mutable.value = mutable.value.copy(recoverable = true)
            throw e
        }
        checkpoint(); store.clearForm(remote); store.remove(draft)
        mutable.value = mutable.value.copy(draft = null, form = null, view = null, synced = true, recoverable = false)
        reader.open(remote, refresh = true)
    }
    /** Explicit confirmation is required before replaying a look change over a newer shared revision. */
    fun rebase() = run { backend, checkpoint ->
        val state = mutable.value; val view = fresh(backend, state.remote)
        val previous = state.draft
        if (previous == null) {
            val form = requireNotNull(state.form).copy(base = view.remote.revision)
            require(form.outfit.isEmpty() || view.candidate.snapshot.outfits.any { it.id == form.outfit })
            store.putForm(form)
            mutable.value = state.copy(form = form, view = view, generation = state.generation + 1, problem = null, running = true)
        } else {
            require(!previous.submitted || state.recoverable)
            require(previous.publication.identity == view.remote.identity)
            val draft = store.stage(view, previous.change, session.repository, checkpoint)
            try {
                authorize(backend, state.remote); store.put(draft, previous)
                store.directory(previous.publication.archiveId).deleteRecursively()
                mutable.value = mutable.value.copy(draft = draft, recoverable = false)
            } catch (e: Exception) {
                if (store.draft(state.remote)?.publication != draft.publication) store.directory(draft.publication.archiveId).deleteRecursively()
                throw e
            }
        }
    }
    fun discard() = run { _, _ ->
        val state = mutable.value
        state.draft?.let { require(!it.submitted || state.recoverable); store.clearForm(state.remote); store.remove(it) }
            ?: store.clearForm(state.remote)
        mutable.value = FamilyOutfitState(remote = state.remote, generation = state.generation + 1, running = true)
    }
    fun invalidateSession() { lease.clear(); mutable.value = FamilyOutfitState(generation = mutable.value.generation + 1) }
}
