package com.familywardrobe.app

import java.security.MessageDigest

internal enum class FamilySyncKind { EDIT, ADD, PHOTO, OUTFIT }
internal enum class FamilySyncStatus { READY, RUNNING, RETRY, PAUSED, DONE, CANCELLED }
internal data class FamilySyncOperation(val kind: FamilySyncKind, val id: String, val fingerprint: String, val submitted: Boolean = false) {
    init { require(FamilyRules.validId(id) && FamilyRules.validHash(fingerprint)) }
    fun matches(other: FamilySyncOperation?) = other != null && kind == other.kind && id == other.id && fingerprint == other.fingerprint
}
internal data class FamilySyncPlan(val id: String, val remote: String, val operations: List<FamilySyncOperation>,
    val background: Boolean, val cursor: Int = 0, val acknowledged: Boolean = false,
    val status: FamilySyncStatus = FamilySyncStatus.READY, val problem: String = "") {
    init {
        require(FamilyRules.validId(id) && FamilyRules.validId(remote))
        require(operations.size in 1..130 && operations.map { it.kind to it.id }.distinct().size == operations.size)
        require(cursor in 0..operations.size && (!acknowledged || cursor < operations.size))
        require(status != FamilySyncStatus.DONE || cursor == operations.size)
        require(problem.length <= 32)
    }
    val active get() = status in setOf(FamilySyncStatus.READY, FamilySyncStatus.RUNNING, FamilySyncStatus.RETRY)
}
internal class FamilySyncChanged : IllegalStateException("Approved queue changed")
internal object FamilySyncPlanner {
    /** Recover submitted heads first, then stable type priority; never reorder a type's FIFO. */
    fun ordered(queues: Map<FamilySyncKind, List<FamilySyncOperation>>): List<FamilySyncOperation> {
        require(queues.all { (kind, rows) -> rows.all { it.kind == kind } })
        val pending = FamilySyncKind.entries.associateWith { queues[it].orEmpty().toMutableList() }
        val result = mutableListOf<FamilySyncOperation>()
        while (pending.values.any { it.isNotEmpty() }) {
            val head = pending.values.mapNotNull { it.firstOrNull() }
                .minWith(compareBy<FamilySyncOperation> { !it.submitted }.thenBy { it.kind.ordinal })
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
        return FamilySyncOperation(kind, d.publication.archiveId, fingerprint(publication(d.publication) + listOf(d.garmentId, d.photoId, d.name)), d.submitted)
    }
    fun outfit(d: FamilyOutfitDraft): FamilySyncOperation {
        val c = d.change
        return FamilySyncOperation(FamilySyncKind.OUTFIT, d.publication.archiveId, fingerprint(publication(d.publication) +
            listOf(c.id, c.creating.toString(), c.name, c.occasion, c.weather, c.source, c.layout, c.background, c.createdAt.toString()) +
            c.pieces.flatMap { listOf(it.key, it.category, it.name) }), d.submitted)
    }
}
internal interface FamilySyncPort {
    fun plan(): FamilySyncPlan
    fun save(next: FamilySyncPlan, expected: FamilySyncPlan)
    fun pending(kind: FamilySyncKind): List<FamilySyncOperation>
    suspend fun authorize(operation: FamilySyncOperation)
    fun submitted(operation: FamilySyncOperation)
    suspend fun publish(operation: FamilySyncOperation)
    fun remove(operation: FamilySyncOperation)
    fun checkpoint()
}
/** Durable receipt checkpoint bridges two AtomicFiles: acknowledge, remove outbox, advance cursor. */
internal class FamilySyncRunner(private val port: FamilySyncPort) {
    suspend fun run(limit: Int = 10): Boolean {
        require(limit in 1..130)
        repeat(limit) {
            port.checkpoint(); var plan = port.plan()
            if (plan.cursor == plan.operations.size) {
                port.save(plan.copy(status = FamilySyncStatus.DONE, problem = ""), plan); return true
            }
            require(plan.active)
            val operation = plan.operations[plan.cursor]
            if (!plan.acknowledged) {
                if (!operation.matches(port.pending(operation.kind).firstOrNull())) throw FamilySyncChanged()
                port.authorize(operation); port.checkpoint()
                port.submitted(operation); port.checkpoint()
                port.publish(operation); port.checkpoint()
                val ack = plan.copy(acknowledged = true)
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
            val next = plan.copy(cursor = plan.cursor + 1, acknowledged = false,
                status = if (plan.cursor + 1 == plan.operations.size) FamilySyncStatus.DONE else FamilySyncStatus.RUNNING, problem = "")
            port.save(next, plan)
            if (next.status == FamilySyncStatus.DONE) return true
        }
        return false
    }
}
