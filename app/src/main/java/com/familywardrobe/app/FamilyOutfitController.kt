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
    val synced: Boolean = false, val queue: List<FamilyOutfitDraft> = emptyList(), val hasForm: Boolean = false)

internal class FamilyOutfitController(private val app: WardrobeApplication, private val session: AccountSession,
    private val reader: FamilyReaderController, private val scope: CoroutineScope) {
    private val store = FamilyOutfitStore(session.context, session.identity)
    private val lock = FamilySyncLocks.account(session.identity.key); private val lease = FamilyReadLease()
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
    /** A queued look is selected explicitly; the single unfinished editor is preserved separately. */
    fun open(remote: String, outfit: String = "", queued: String? = null, startNew: Boolean = false) = run { backend, _ ->
        val generation = mutable.value.generation + 1
        lease.clear()
        mutable.value = FamilyOutfitState(remote = remote, generation = generation, running = true)
        authorize(backend, remote)
        val rows = store.list(remote); val savedForm = store.form(remote)
        val draft = when {
            queued != null -> requireNotNull(rows.find { it.change.id == queued })
            startNew -> null
            outfit.isNotEmpty() -> rows.find { it.change.id == outfit }
            savedForm != null -> null
            else -> rows.firstOrNull()
        }
        mutable.value = mutable.value.copy(queue = rows, hasForm = savedForm != null, draft = draft)
        if (draft == null) {
            if (savedForm == null && rows.size >= FamilyOutfitQueue.LIMIT) throw FamilyTransferException(FamilyProblem.LIMIT)
            val view = fresh(backend, remote)
            val form = savedForm ?: FamilyOutfitForm(remote, outfit, view.remote.revision).also(store::putForm)
            mutable.value = mutable.value.copy(form = form, hasForm = true, view = view,
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
        if (store.list(form.remote).size >= FamilyOutfitQueue.LIMIT) throw FamilyTransferException(FamilyProblem.LIMIT)
        val view = fresh(backend, form.remote)
        mutable.value = mutable.value.copy(view = view)
        if (view.remote.revision != form.base) throw FamilyTransferException(FamilyProblem.CONFLICT)
        val change = FamilyOutfitChange(target.ifEmpty { newId() }, target.isEmpty(), name, pieces, occasion, weather, source, layout, background)
        val draft = store.stage(view, change, session.repository, checkpoint)
        try {
            authorize(backend, form.remote); store.put(draft, null, preparedForm = form)
            // Publication and editor removal are one atomic journal update.
            mutable.value = mutable.value.copy(draft = draft, form = null, hasForm = false, view = null,
                queue = store.list(form.remote), generation = state.generation + 1)
        } catch (e: Exception) {
            if (store.draft(form.remote, draft.change.id)?.publication != draft.publication) store.directory(draft.publication.archiveId).deleteRecursively()
            throw e
        }
    }
    fun sync(expected: FamilyOutfitDraft? = mutable.value.draft) = run { backend, checkpoint ->
        var draft = requireNotNull(expected); require(FamilyOutfitQueue.same(mutable.value.draft, draft))
        FamilyOutfitQueue.requireHead(store.list(draft.publication.identity.remoteId), draft)
        val remote = draft.publication.identity.remoteId
        authorize(backend, remote)
        val previous = draft; draft = draft.copy(submitted = true); store.put(draft, previous)
        mutable.value = mutable.value.copy(draft = draft, recoverable = false, queue = store.list(remote))
        try { publishFamilyAddition(draft.publication, backend, { store.checked(draft, checkpoint) }, checkpoint) }
        catch (e: FamilyTransferException) {
            if (e.problem in setOf(FamilyProblem.CONFLICT, FamilyProblem.EXPIRED, FamilyProblem.ARCHIVE, FamilyProblem.LIMIT))
                mutable.value = mutable.value.copy(recoverable = true)
            throw e
        }
        checkpoint(); store.remove(draft)
        mutable.value = mutable.value.copy(draft = null, form = null, view = null, synced = true, recoverable = false,
            queue = store.list(remote), hasForm = store.form(remote) != null, generation = mutable.value.generation + 1)
        reader.open(remote, refresh = true)
    }
    /** Explicit confirmation is required before replaying a look change over a newer shared revision. */
    fun rebase(generation: Long = mutable.value.generation) = run { backend, checkpoint ->
        val state = mutable.value; require(state.generation == generation)
        val view = fresh(backend, state.remote)
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
                mutable.value = mutable.value.copy(draft = draft, recoverable = false, queue = store.list(state.remote), generation = state.generation + 1)
            } catch (e: Exception) {
                if (store.draft(state.remote, draft.change.id)?.publication != draft.publication) store.directory(draft.publication.archiveId).deleteRecursively()
                throw e
            }
        }
    }
    fun discard(generation: Long = mutable.value.generation) = run { _, _ ->
        val state = mutable.value; require(state.generation == generation)
        state.draft?.let { require(!it.submitted || state.recoverable); store.remove(it) }
            ?: store.clearForm(state.remote, requireNotNull(state.form))
        mutable.value = FamilyOutfitState(remote = state.remote, generation = state.generation + 1, running = true,
            queue = store.list(state.remote), hasForm = store.form(state.remote) != null)
    }
    fun invalidateSession() { lease.clear(); mutable.value = FamilyOutfitState(generation = mutable.value.generation + 1) }
}
