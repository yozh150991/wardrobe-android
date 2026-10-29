package com.familywardrobe.app

import java.security.MessageDigest

internal enum class FamilySyncKind { EDIT, ADD, PHOTO, OUTFIT }
internal enum class FamilySyncStatus { READY, RUNNING, RETRY, PAUSED, DONE, CANCELLED }
internal data class FamilySyncOperation(val kind: FamilySyncKind, val id: String, val fingerprint: String, val submitted: Boolean = false) {
    init { require(FamilyRules.validId(id) && FamilyRules.validHash(fingerprint)) }
    fun matches(other: FamilySyncOperation?) = other != null && kind == other.kind && id == other.id && fingerprint == other.fingerprint
    val key get() = "${kind.name}:$id"
}
/** Result of the pre-send check of one approved operation. */
internal sealed interface FamilySyncDecision {
    /** Send this exact operation; it differs from the approved one only by a safe metadata rebase. */
    data class Send(val operation: FamilySyncOperation) : FamilySyncDecision
    /** Leave it in its outbox: its full archive predates this batch's own commits and needs the editor's rebase. */
    data object Defer : FamilySyncDecision
}
/**
 * [owned] — server revisions committed by this approved batch (each is base + 1 of its receipt).
 * [deferred] — "KIND:id" of operations left in their outbox because only this batch's own commits made
 * their full archive stale; later operations of the same kind wait behind them (FIFO).
 * [attempts]/[attemptCursor] — background runs started without progress, including runs Android stopped.
 */
internal data class FamilySyncPlan(val id: String, val remote: String, val operations: List<FamilySyncOperation>,
    val background: Boolean, val cursor: Int = 0, val acknowledged: Boolean = false,
    val status: FamilySyncStatus = FamilySyncStatus.READY, val problem: String = "",
    val owned: List<Long> = emptyList(), val deferred: List<String> = emptyList(),
    val attempts: Int = 0, val attemptCursor: Int = -1) {
    init {
        require(FamilyRules.validId(id) && FamilyRules.validId(remote))
        require(operations.size in 1..130 && operations.map { it.kind to it.id }.distinct().size == operations.size)
        require(cursor in 0..operations.size && (!acknowledged || cursor < operations.size))
        require(status != FamilySyncStatus.DONE || cursor == operations.size)
        require(problem.length <= 32)
        require(owned.size <= operations.size && owned.all { it > 0 } && owned.distinct().size == owned.size)
        require(deferred.distinct().size == deferred.size && deferred.all { it in operations.map(FamilySyncOperation::key) })
        require(attempts in 0..100 && attemptCursor in -1..operations.size)
    }
    val active get() = status in setOf(FamilySyncStatus.READY, FamilySyncStatus.RUNNING, FamilySyncStatus.RETRY)
    val deferredKinds get() = operations.filter { it.key in deferred }.map { it.kind }.toSet()
    /** True when every revision after [base] up to [head] was committed by this batch itself. */
    fun ownsAfter(base: Long, head: Long): Boolean = head > base && head - base <= owned.size && (base + 1..head).all { it in owned }
}
internal class FamilySyncChanged : IllegalStateException("Approved queue changed")
internal object FamilySyncPlanner {
    /**
     * Outfits go first: a full-archive outfit (prepared before the server applied outfit changes) still needs the
     * head it was built on. Additions, photos and outfit changes are server-applied (delta model) and fit any head,
     * and item details follow this batch's own commits safely (audit L-01). A legacy full-archive operation after
     * another commit is deferred like before; only one whole archive can succeed on a given head anyway.
     */
    private val priority = listOf(FamilySyncKind.OUTFIT, FamilySyncKind.ADD, FamilySyncKind.PHOTO, FamilySyncKind.EDIT)
    /** Recover submitted heads first, then stable type priority; never reorder a type's FIFO. */
    fun ordered(queues: Map<FamilySyncKind, List<FamilySyncOperation>>): List<FamilySyncOperation> {
        require(queues.all { (kind, rows) -> rows.all { it.kind == kind } })
        val pending = FamilySyncKind.entries.associateWith { queues[it].orEmpty().toMutableList() }
        val result = mutableListOf<FamilySyncOperation>()
        while (pending.values.any { it.isNotEmpty() }) {
            val head = pending.values.mapNotNull { it.firstOrNull() }
                .minWith(compareBy<FamilySyncOperation> { !it.submitted }.thenBy { priority.indexOf(it.kind) })
            result += head; pending.getValue(head.kind).removeAt(0)
        }
        require(result.size <= 130 && result.map { it.kind to it.id }.distinct().size == result.size)
        return result
    }
    fun unchanged(approved: List<FamilySyncOperation>, actual: List<FamilySyncOperation>) =
        approved.size == actual.size && approved.zip(actual).all { (a, b) -> a.matches(b) }
    fun fingerprint(fields: List<String>): String = MessageDigest.getInstance("SHA-256")
        .digest(fields.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun edit(d: FamilyEditDraft) = FamilySyncOperation(FamilySyncKind.EDIT, d.editId, fingerprint(
        listOf(d.remoteId, d.garmentId, d.editId, d.baseRevision.toString()) + FAMILY_EDIT_LIMITS.keys.flatMap { listOf(d.original.getValue(it), d.values.getValue(it)) }), d.submitted)
    private fun publication(p: FamilyPublication) = listOf(p.identity.remoteId, p.identity.wardrobeId, p.identity.ownerUid,
        p.archiveId, p.baseRevision.toString(), p.hash, p.size.toString())
    fun media(kind: FamilySyncKind, d: FamilyAddDraft): FamilySyncOperation {
        require(kind in setOf(FamilySyncKind.ADD, FamilySyncKind.PHOTO))
        // The change text is part of the approval; a full-archive draft keeps its earlier fingerprint.
        return FamilySyncOperation(kind, d.publication.archiveId, fingerprint(publication(d.publication) + listOf(d.garmentId, d.photoId, d.name) +
            listOfNotNull(d.change)), d.submitted)
    }
    fun outfit(d: FamilyOutfitDraft): FamilySyncOperation {
        val c = d.change
        return FamilySyncOperation(FamilySyncKind.OUTFIT, d.publication.archiveId, fingerprint(publication(d.publication) +
            listOf(c.id, c.creating.toString(), c.name, c.occasion, c.weather, c.source, c.layout, c.background, c.createdAt.toString()) +
            c.pieces.flatMap { listOf(it.key, it.category, it.name) } + listOfNotNull(d.delta)), d.submitted)
    }
}
/**
 * Automatic sending (0.14.23): a per-wardrobe switch, off by default. When it is on, every prepared change is
 * sent in the background as an automatically approved batch of the whole queue. A batch stopped by a problem
 * that needs the user (conflict, access, damaged draft, explicit stop) waits: automatic sending resumes only
 * after the operation it stopped at changed (rebased, discarded or sent by hand) or the user turned the switch
 * on again ([released]). Network trouble, sign-out and quota are retried later on their own.
 */
internal object FamilyAutoSync {
    private val transient = setOf("", "PARTIAL", "RETRY", "RETRY_LIMIT", "SIGNED_OUT", "CHANGED", "LIMIT")
    sealed interface Step {
        /** Nothing queued. */
        data object Idle : Step
        /** A foreground batch is running, or a stopped batch waits for the user. */
        data object Wait : Step
        /** An unfinished background batch (automatic or approved by hand) continues first. */
        data class Continue(val plan: FamilySyncPlan) : Step
        /** A new automatic batch of these operations, in send order. */
        data class Start(val operations: List<FamilySyncOperation>) : Step
    }
    fun decide(plan: FamilySyncPlan?, queue: List<FamilySyncOperation>, released: String = ""): Step = when {
        plan != null && plan.active -> if (plan.background) Step.Continue(plan) else Step.Wait
        queue.isEmpty() -> Step.Idle
        plan != null && plan.id != released && blocked(plan, queue) -> Step.Wait
        else -> Step.Start(queue.take(130))
    }
    /** True while the operation a stopped batch ended at is still queued unchanged and the problem needs the user. */
    fun blocked(plan: FamilySyncPlan, queue: List<FamilySyncOperation>): Boolean {
        if (plan.active || plan.problem in transient) return false
        val stopped = plan.operations.getOrNull(plan.cursor) ?: return false
        return queue.any { stopped.matches(it) }
    }
}
internal interface FamilySyncPort {
    fun plan(): FamilySyncPlan
    fun save(next: FamilySyncPlan, expected: FamilySyncPlan)
    fun pending(kind: FamilySyncKind): List<FamilySyncOperation>
    suspend fun authorize(operation: FamilySyncOperation, plan: FamilySyncPlan): FamilySyncDecision
    fun submitted(operation: FamilySyncOperation)
    /** Returns the committed revision (base + 1 of the validated receipt). */
    suspend fun publish(operation: FamilySyncOperation): Long
    fun remove(operation: FamilySyncOperation)
    fun checkpoint()
}
/** Durable receipt checkpoint bridges two AtomicFiles: acknowledge, remove outbox, advance cursor. */
internal class FamilySyncRunner(private val port: FamilySyncPort) {
    /** [keepGoing] is asked before starting each further operation (a background time budget). */
    suspend fun run(limit: Int = 10, keepGoing: () -> Boolean = { true }): Boolean {
        require(limit in 1..130)
        repeat(limit) { step ->
            port.checkpoint(); var plan = port.plan()
            if (plan.cursor == plan.operations.size) {
                port.save(plan.copy(status = FamilySyncStatus.DONE, problem = finished(plan)), plan); return true
            }
            require(plan.active)
            var operation = plan.operations[plan.cursor]
            if (!plan.acknowledged) {
                if (step > 0 && !keepGoing()) return false
                // A deferred head keeps every later operation of its kind in place (per-kind FIFO).
                if (operation.kind in plan.deferredKinds) { if (advance(plan, deferred = operation)) return true; return@repeat }
                if (!operation.matches(port.pending(operation.kind).firstOrNull())) throw FamilySyncChanged()
                when (val decision = port.authorize(operation, plan)) {
                    FamilySyncDecision.Defer -> { if (advance(plan, deferred = operation)) return true; return@repeat }
                    is FamilySyncDecision.Send -> if (decision.operation != operation) {
                        val next = decision.operation
                        require(next.kind == operation.kind && next.id == operation.id)
                        val rebased = plan.copy(operations = plan.operations.toMutableList().also { it[plan.cursor] = next })
                        port.save(rebased, plan); plan = rebased; operation = next
                    }
                }
                port.checkpoint()
                port.submitted(operation); port.checkpoint()
                val revision = port.publish(operation); port.checkpoint()
                val ack = plan.copy(acknowledged = true, owned = if (revision > 0) (plan.owned + revision).distinct() else plan.owned)
                port.save(ack, plan); plan = ack
            }
            port.checkpoint()
            val rows = port.pending(operation.kind)
            val remaining = rows.find { it.id == operation.id }
            if (remaining != null) {
                if (!operation.matches(rows.firstOrNull())) throw FamilySyncChanged()
                port.remove(operation)
            }
            port.checkpoint()
            if (advance(plan, deferred = null)) return true
        }
        return false
    }
    private fun finished(plan: FamilySyncPlan) = if (plan.deferred.isEmpty()) "" else "PARTIAL"
    /** Moves the cursor; true when the batch is complete. */
    private fun advance(plan: FamilySyncPlan, deferred: FamilySyncOperation?): Boolean {
        val done = plan.cursor + 1 == plan.operations.size
        val list = if (deferred == null) plan.deferred else plan.deferred + deferred.key
        val next = plan.copy(cursor = plan.cursor + 1, acknowledged = false, deferred = list,
            status = if (done) FamilySyncStatus.DONE else FamilySyncStatus.RUNNING, problem = "")
        port.save(if (done) next.copy(problem = finished(next)) else next, plan)
        return done
    }
}
