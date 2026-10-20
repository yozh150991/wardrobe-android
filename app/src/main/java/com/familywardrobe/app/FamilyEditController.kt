package com.familywardrobe.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.coroutineContext

internal data class FamilyEditState(val remoteId: String = "", val draft: FamilyEditDraft? = null, val running: Boolean = false,
    val problem: FamilyProblem? = null, val saved: Boolean = false, val synced: Boolean = false,
    val conflict: Boolean = false, val latest: FamilyEditDraft? = null)
internal class FamilyEditController(private val app: WardrobeApplication, private val session: AccountSession,
    private val reader: FamilyReaderController, private val scope: CoroutineScope) {
    private val lease = FamilyReadLease()
    fun readable() = session.isOpen && app.authBackend.signedInIdentity()?.key == session.identity.key && lease.permits(session.id, android.os.SystemClock.elapsedRealtime())
    private val store = FamilyEditStore(session.context, session.identity)
    private val lock = Mutex(); private val mutable = MutableStateFlow(FamilyEditState()); val state = mutable.asStateFlow()
    private fun guard() {
        if (!session.isOpen || app.authBackend.signedInIdentity()?.key != session.identity.key) throw CancellationException("Account changed")
    }
    private fun run(block: suspend (FamilyBackend) -> Unit) {
        scope.launch(Dispatchers.IO) {
            if (!lock.tryLock()) return@launch
            try {
                guard(); mutable.value = mutable.value.copy(running = true, problem = null, saved = false, synced = false)
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
                        store.put(editable, previous); mutable.value = mutable.value.copy(draft = editable)
                    }
                }
                if (problem == FamilyProblem.ACCESS) {
                    lease.clear(); reader.invalidate(); mutable.value = FamilyEditState(remoteId = mutable.value.remoteId, running = true)
                }
                mutable.value = mutable.value.copy(problem = problem, conflict = mutable.value.conflict || problem == FamilyProblem.CONFLICT)
            } finally { if (session.isOpen) mutable.value = mutable.value.copy(running = false); lock.unlock() }
        }
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
        mutable.value = FamilyEditState(remoteId = remote, running = true)
        authorize(backend, remote)
        val pending = store.get(remote)
        val draft = pending ?: garment?.let { fresh(backend, remote, it) }
        mutable.value = mutable.value.copy(draft = draft, saved = pending != null)
    }
    fun save(values: Map<String, String>) = run {
        if (!readable()) throw FamilyTransferException(FamilyProblem.EXPIRED)
        val current = requireNotNull(mutable.value.draft); require(!current.submitted)
        require(values.getValue("name").isNotBlank())
        val updated = current.copy(values = values.toMap()); store.put(updated, store.get(current.remoteId).also { require(it == null || it == current) }); guard()
        mutable.value = mutable.value.copy(draft = updated, saved = true)
    }
    fun sync() = run { backend ->
        var draft = requireNotNull(mutable.value.draft); require(draft.changes.isNotEmpty())
        authorize(backend, draft.remoteId)
        // Persist exact immutable request BEFORE the call. A timeout retries this UUID.
        val previous = draft
        draft = draft.copy(submitted = true); store.put(draft, previous)
        mutable.value = mutable.value.copy(draft = draft, latest = null)
        val result = backend.accessCall("edit_item", mapOf("remoteId" to draft.remoteId, "editId" to draft.editId,
            "garmentId" to draft.garmentId, "baseRevision" to draft.baseRevision, "changes" to draft.changes))
        require(result["status"] == "committed" && result["editId"] == draft.editId && result["remoteId"] == draft.remoteId)
        val revision = result["revision"] as? Number ?: error("Invalid edit response")
        require(revision.toDouble() == (draft.baseRevision + 1).toDouble())
        guard(); store.remove(draft)
        mutable.value = mutable.value.copy(draft = null, synced = true, conflict = false)
        // The receipt confirms the write even if a newer edit has since been committed.
        reader.open(draft.remoteId, refresh = true)
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
        store.put(rebased, current); mutable.value = mutable.value.copy(draft = rebased, latest = null, conflict = false, saved = true)
    }
    fun discard() = run {
        val draft = mutable.value.draft
        require(draft == null || !draft.submitted || mutable.value.conflict)
        if (draft != null && store.get(draft.remoteId)?.editId == draft.editId) store.remove(draft)
        mutable.value = mutable.value.copy(draft = null, latest = null, conflict = false)
    }
    fun invalidateSession() { lease.clear(); mutable.value = FamilyEditState() }
}
