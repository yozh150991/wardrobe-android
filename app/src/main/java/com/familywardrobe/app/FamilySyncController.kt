package com.familywardrobe.app

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.withLock

internal data class FamilySyncState(val remote: String = "", val operations: List<FamilySyncOperation> = emptyList(),
    val labels: Map<String, String> = emptyMap(), val plan: FamilySyncPlan? = null, val busy: Boolean = false, val problem: String = "")
internal class FamilySyncController(private val app: WardrobeApplication, private val session: AccountSession, private val reader: FamilyReaderController, private val scope: CoroutineScope) {
    private val store = FamilySyncStore(session.context, session.identity)
    private val mutable = MutableStateFlow(FamilySyncState()); val state = mutable.asStateFlow()
    private var manual: Job? = null
    private var manualRemote = ""
    private var lastDenied = ""
    private val intents = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
    private fun intent(remote: String) = intents.computeIfAbsent(remote) { java.util.concurrent.atomic.AtomicLong() }
    private fun guard() {
        if (!session.isOpen || app.authBackend.signedInIdentity()?.key != session.identity.key) throw CancellationException("Account changed")
    }
    // Any outbox change refreshes the list too: a draft saved meanwhile must not leave a stale approval (audit L-03).
    init { scope.launch(Dispatchers.IO) { combine(store.changes, FamilySyncLocks.outbox(session.identity.key)) { a, b -> a to b }.collect {
        if (mutable.value.remote.isNotEmpty()) {
            refresh(mutable.value.remote)
            val denied = mutable.value.plan?.takeIf { it.problem == "ACCESS" }?.let { "${it.id}:${it.cursor}" }.orEmpty()
            if (denied.isNotEmpty() && denied != lastDenied) reader.invalidate()
            lastDenied = denied
        }
    } } }
    private fun refresh(remote: String) {
        try {
            guard(); val operations = FamilySyncOperations(session.context, session.identity, remote)
            val rows = operations.snapshot()
            if (mutable.value.remote == remote) mutable.value = mutable.value.copy(operations = rows,
                labels = operations.labels(rows), plan = store.get(remote), problem = "")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (mutable.value.remote == remote) mutable.value = mutable.value.copy(problem = "ARCHIVE") }
    }
    fun open(remote: String) { scope.launch(Dispatchers.IO) {
        guard(); mutable.value = FamilySyncState(remote = remote)
        try { store.get(remote)?.takeIf { it.active && !it.background && (manualRemote != remote || manual?.isActive != true) }
            ?.let { store.failure(remote, it.id, "STOPPED") }
        } catch (e: Exception) { mutable.value = mutable.value.copy(problem = "ARCHIVE") }
        refresh(remote)
    } }
    fun start(remote: String, approved: List<FamilySyncOperation>, background: Boolean) {
        val ticket = intent(remote).incrementAndGet()
        manualRemote = remote
        manual = scope.launch(Dispatchers.IO) {
            var created: String? = null
            val work = coroutineContext
            fun checkpoint() { work.ensureActive(); guard(); if (intent(remote).get() != ticket) throw CancellationException("Stopped") }
            try {
                checkpoint(); mutable.value = mutable.value.copy(busy = true, problem = "")
                val plan = FamilySyncLocks.account(session.identity.key).withLock {
                    checkpoint()
                    val backend = FamilyBackend(session.identity, ::checkpoint)
                    backend.authenticate(); val head = backend.readHead(remote)
                    if (head.role !in setOf("owner", "editor")) throw FamilyTransferException(FamilyProblem.ACCESS)
                    val current = FamilySyncOperations(session.context, session.identity, remote).snapshot()
                    if (!FamilySyncPlanner.unchanged(approved, current)) throw FamilySyncChanged()
                    val previous = store.get(remote); require(previous?.active != true)
                    checkpoint()
                    FamilySyncPlan(newId(), remote, approved.toList(), background).also { store.put(it, previous) }
                }
                created = plan.id
                checkpoint()
                if (background) FamilySyncSchedule.enqueue(session.context, session.identity.key, plan)
                else FamilySyncEngine(app, session.context, session.identity, remote, plan.id, false) { session.isOpen && intent(remote).get() == ticket }.run()
                refresh(remote)
            } catch (e: CancellationException) { created?.let { store.failure(remote, it, "STOPPED") }; throw e }
            catch (e: Exception) {
                created?.let { store.failure(remote, it, "STOPPED") }
                if (e is FamilyTransferException && e.problem == FamilyProblem.ACCESS) reader.invalidate()
                mutable.value = mutable.value.copy(problem = when (e) {
                    is FamilySyncChanged -> "CHANGED"
                    is FamilyTransferException -> e.problem.name
                    else -> "RETRY"
                })
            } finally { if (session.isOpen) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun cancel(remote: String) {
        intent(remote).incrementAndGet()
        scope.launch(Dispatchers.IO) {
            try {
                guard(); store.stop(remote)
                FamilySyncSchedule.cancel(session.context, session.identity.key, remote)
                if (manualRemote == remote) manual?.cancel()
                refresh(remote)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(problem = "ARCHIVE") }
        }
    }
}
