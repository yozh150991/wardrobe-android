package com.familywardrobe.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

internal data class FamilyEditState(val remoteId: String = "", val draft: FamilyEditDraft? = null, val running: Boolean = false,
    val problem: FamilyProblem? = null, val saved: Boolean = false, val synced: Boolean = false,
    val conflict: Boolean = false, val latest: FamilyEditDraft? = null,
    val queue: List<FamilyEditDraft> = emptyList(), val completed: Int = 0, val total: Int = 0, val waiting: Boolean = false)
internal class FamilyEditController(private val app: WardrobeApplication, private val session: AccountSession,
    private val reader: FamilyReaderController, private val scope: CoroutineScope) {
    private val lease = FamilyReadLease()
    fun readable() = session.isOpen && app.authBackend.signedInIdentity()?.key == session.identity.key && lease.permits(session.id, android.os.SystemClock.elapsedRealtime())
    private val store = FamilyEditStore(session.context, session.identity)
    private val lock = FamilySyncLocks.account(session.identity.key); private val mutable = MutableStateFlow(FamilyEditState()); val state = mutable.asStateFlow()
    private fun guard() {
        if (!session.isOpen || app.authBackend.signedInIdentity()?.key != session.identity.key) throw CancellationException("Account changed")
    }
    private val actions = FamilyActions()
    fun cancel() = actions.cancel()
    private fun run(block: suspend (FamilyBackend) -> Unit) {
        // Queue instead of dropping: a tap during another call used to be ignored silently
        // and the dialog then showed the previous item's state (audit 0.14.6 №10 pattern).
        // Progress is visible while waiting for the account lock (audit A-04).
        mutable.value = mutable.value.copy(running = true, waiting = lock.isLocked)
        actions.track(scope.launch(Dispatchers.IO) { lock.withLock {
            try {
                guard(); mutable.value = mutable.value.copy(running = true, waiting = false, problem = null, saved = false, synced = false, completed = 0, total = 0)
                val work = coroutineContext
                val backend = FamilyBackend(session.identity, { work.ensureActive(); guard() })
                block(backend); work.ensureActive(); guard()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                guard(); val problem = (e as? FamilyTransferException)?.problem ?: if (e is IllegalArgumentException) FamilyProblem.ARCHIVE else FamilyProblem.RETRY
                // Only a definite server rejection unlocks a submitted payload. Unknown
                // outcomes and malformed receipts keep the same UUID for safe retry.
                if (e is FamilyTransferException && problem in setOf(FamilyProblem.ARCHIVE, FamilyProblem.MISSING, FamilyProblem.LIMIT)) {
                    mutable.value.draft?.takeIf { it.submitted }?.let { previous ->
                        val editable = previous.copy(editId = newId(), submitted = false)
                        store.put(editable, previous); mutable.value = mutable.value.copy(draft = editable, queue = store.list(previous.remoteId))
                    }
                }
                if (problem == FamilyProblem.ACCESS) {
                    lease.clear(); reader.invalidate(); mutable.value = FamilyEditState(remoteId = mutable.value.remoteId, running = true)
                }
                mutable.value = mutable.value.copy(problem = problem, conflict = mutable.value.conflict || problem == FamilyProblem.CONFLICT)
            }
        } }) { if (session.isOpen) mutable.value = mutable.value.copy(running = false, waiting = false) }
    }
    private suspend fun authorize(backend: FamilyBackend, remoteId: String): FamilyRemote {
        backend.authenticate()
        val started = android.os.SystemClock.elapsedRealtime()
        return backend.readHead(remoteId).also {
            if (it.role !in setOf("owner", "editor")) throw FamilyTransferException(FamilyProblem.ACCESS)
            lease.grant(session.id, FamilyRules.role(session.identity.uid, it.identity.ownerUid, it.role, true), started, android.os.SystemClock.elapsedRealtime())
        }
    }
    private suspend fun fresh(backend: FamilyBackend, remote: String, garment: String): FamilyEditDraft {
        authorize(backend, remote); reader.open(remote, refresh = true); guard()
        val view = reader.state.value.view?.takeIf { it.remote.identity.remoteId == remote && reader.readable(it) }
            ?: throw FamilyTransferException(reader.state.value.problem ?: FamilyProblem.RETRY)
        if (view.remote.role !in setOf("owner", "editor")) throw FamilyTransferException(FamilyProblem.ACCESS)
        val item = view.candidate.snapshot.garments.find { it.id == garment } ?: throw FamilyTransferException(FamilyProblem.MISSING)
        return FamilyEditDraft(remote, garment, newId(), view.remote.revision, item.familyFields(), item.familyFields())
    }
    fun open(remote: String, garment: String? = null) = run { backend ->
        lease.clear()
        mutable.value = FamilyEditState(remoteId = remote, running = true)
        authorize(backend, remote)
        val pending = store.get(remote, garment)
        val draft = pending ?: garment?.let { fresh(backend, remote, it) }
        mutable.value = mutable.value.copy(draft = draft, saved = pending != null, queue = store.list(remote))
    }
    fun save(values: Map<String, String>) = run {
        if (!readable()) throw FamilyTransferException(FamilyProblem.EXPIRED)
        val current = requireNotNull(mutable.value.draft); require(!current.submitted)
        require(TextLimits.hasVisibleText(values.getValue("name")))
        val updated = current.copy(values = values.toMap())
        require(updated.changes.isNotEmpty())
        store.put(updated, store.get(current.remoteId, current.garmentId).also { require(it == null || it == current) }); guard()
        mutable.value = mutable.value.copy(draft = updated, saved = true, queue = store.list(current.remoteId))
    }
    fun sync(all: Boolean = false) {
        // Capture the user's selection before enqueuing work: a double tap must not send the next item.
        val shown = mutable.value
        val confirmed = (if (all) shown.queue else listOfNotNull(shown.draft)).toList()
        run { backend ->
        val remote = shown.remoteId
        val work = coroutineContext
        val sender = FamilyEditQueueSender(object : FamilyEditQueuePort {
            override fun checkpoint() { work.ensureActive(); guard() }
            override fun pending(remote: String) = store.list(remote)
            override fun put(next: FamilyEditDraft, expected: FamilyEditDraft) { checkpoint(); store.put(next, expected) }
            override fun remove(expected: FamilyEditDraft) { checkpoint(); store.remove(expected) }
            override suspend fun latest(draft: FamilyEditDraft) = fresh(backend, draft.remoteId, draft.garmentId)
            override fun progress(draft: FamilyEditDraft, completed: Int, total: Int) {
                checkpoint()
                mutable.value = mutable.value.copy(draft = draft, queue = store.list(remote), completed = completed,
                    total = total, latest = null, conflict = false)
            }
            override suspend fun publish(draft: FamilyEditDraft) {
                authorize(backend, draft.remoteId)
                val result = backend.accessCall("edit_item", mapOf("remoteId" to draft.remoteId, "editId" to draft.editId,
                    "garmentId" to draft.garmentId, "baseRevision" to draft.baseRevision, "changes" to draft.changes))
                require(result["status"] == "committed" && result["editId"] == draft.editId && result["remoteId"] == draft.remoteId)
                val revision = result["revision"] as? Number ?: error("Invalid edit response")
                require(revision.toDouble() == (draft.baseRevision + 1).toDouble())
            }
        })
        sender.send(remote, confirmed)
        val remaining = store.list(remote)
        mutable.value = mutable.value.copy(draft = remaining.firstOrNull(), queue = remaining, synced = true, conflict = false)
        reader.open(remote, refresh = true)
        }
    }
    fun reviewConflict() = run { backend ->
        val current = requireNotNull(mutable.value.draft); require(mutable.value.conflict)
        val latest = fresh(backend, current.remoteId, current.garmentId)
        mutable.value = mutable.value.copy(latest = latest)
    }
    fun keepLocalChanges() = run {
        if (!readable()) throw FamilyTransferException(FamilyProblem.EXPIRED)
        val current = requireNotNull(mutable.value.draft); val latest = requireNotNull(mutable.value.latest)
        require(mutable.value.conflict && current.remoteId == latest.remoteId && current.garmentId == latest.garmentId)
        val rebased = latest.copy(values = latest.values + current.changes)
        store.put(rebased, current); mutable.value = mutable.value.copy(draft = rebased, latest = null, conflict = false, saved = true, queue = store.list(current.remoteId))
    }
    fun discard() = run {
        val draft = mutable.value.draft
        require(draft == null || !draft.submitted || mutable.value.conflict)
        if (draft != null && store.get(draft.remoteId, draft.garmentId)?.editId == draft.editId) store.remove(draft)
        val remaining = store.list(mutable.value.remoteId)
        mutable.value = mutable.value.copy(draft = remaining.firstOrNull(), queue = remaining, latest = null, conflict = false)
    }
    fun invalidateSession() { lease.clear(); mutable.value = FamilyEditState() }
}
