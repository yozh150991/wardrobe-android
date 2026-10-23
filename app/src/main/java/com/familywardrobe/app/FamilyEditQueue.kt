package com.familywardrobe.app

internal val FAMILY_EDIT_LIMITS = linkedMapOf("name" to 120, "size" to 40, "location" to 200, "description" to 1000)
internal fun Garment.familyFields() = linkedMapOf("name" to name, "size" to size, "location" to location, "description" to description)
internal data class FamilyEditDraft(val remoteId: String, val garmentId: String, val editId: String, val baseRevision: Long,
    val original: Map<String, String>, val values: Map<String, String>, val submitted: Boolean = false) {
    init {
        require(FamilyRules.validId(remoteId) && FamilyRules.validId(garmentId) && FamilyRules.validId(editId) && baseRevision > 0)
        require(original.keys == FAMILY_EDIT_LIMITS.keys && values.keys == FAMILY_EDIT_LIMITS.keys)
        for (fields in listOf(original, values)) for ((key, value) in fields) require(value.length <= FAMILY_EDIT_LIMITS.getValue(key))
    }
    val changes get() = values.filter { (key, value) -> original[key] != value }
}

/** Ordered metadata queue: one pending operation per item, bounded across the account. */
internal object FamilyEditQueue {
    const val LIMIT = 100
    fun validate(rows: List<FamilyEditDraft>): List<FamilyEditDraft> = rows.also {
        require(it.size <= LIMIT)
        require(it.map { d -> d.remoteId to d.garmentId }.distinct().size == it.size)
        require(it.map { d -> d.editId }.distinct().size == it.size)
    }
    fun put(rows: List<FamilyEditDraft>, draft: FamilyEditDraft, expected: FamilyEditDraft?): List<FamilyEditDraft> {
        validate(rows)
        val index = rows.indexOfFirst { it.remoteId == draft.remoteId && it.garmentId == draft.garmentId }
        val current = rows.getOrNull(index)
        require(current == expected || current == draft) { "Draft changed" }
        if (index < 0 && rows.size >= LIMIT) throw FamilyTransferException(FamilyProblem.LIMIT)
        return validate(if (index < 0) rows + draft else rows.toMutableList().also { it[index] = draft })
    }
    fun remove(rows: List<FamilyEditDraft>, expected: FamilyEditDraft): List<FamilyEditDraft> {
        validate(rows)
        require(rows.find { it.remoteId == expected.remoteId && it.garmentId == expected.garmentId } == expected)
        return rows.filterNot { it.editId == expected.editId }
    }
    /** Never change a request that may already have reached the server. */
    fun prepare(current: FamilyEditDraft, latest: FamilyEditDraft): FamilyEditDraft {
        require(!current.submitted && !latest.submitted)
        require(current.remoteId == latest.remoteId && current.garmentId == latest.garmentId)
        require(latest.baseRevision >= current.baseRevision)
        if (current.changes.any { (field, local) -> latest.original[field] != current.original[field] && latest.original[field] != local })
            throw FamilyTransferException(FamilyProblem.CONFLICT)
        return latest.copy(values = latest.original + current.changes)
    }
}

internal interface FamilyEditQueuePort {
    fun pending(remote: String): List<FamilyEditDraft>
    fun put(next: FamilyEditDraft, expected: FamilyEditDraft)
    fun remove(expected: FamilyEditDraft)
    suspend fun latest(draft: FamilyEditDraft): FamilyEditDraft
    /** Returns only after validating the exact committed receipt, including its revision. */
    suspend fun publish(draft: FamilyEditDraft)
    fun checkpoint()
    fun progress(draft: FamilyEditDraft, completed: Int, total: Int)
}

/** One explicit send processes a fixed FIFO prefix. Any failure stops before the next item. */
internal class FamilyEditQueueSender(private val port: FamilyEditQueuePort) {
    suspend fun send(remote: String, confirmed: List<FamilyEditDraft>): Int {
        port.checkpoint()
        val selected = confirmed.toList()
        require(selected.isNotEmpty() && selected.all { it.remoteId == remote })
        require(port.pending(remote).take(selected.size) == selected) { "Queue changed" }
        for ((index, selectedDraft) in selected.withIndex()) {
            port.checkpoint()
            require(port.pending(remote).firstOrNull() == selectedDraft) { "Queue changed" }
            var draft = selectedDraft
            port.progress(draft, index, selected.size)
            if (!draft.submitted && draft.changes.isNotEmpty()) {
                val latest = port.latest(draft)
                port.checkpoint()
                val prepared = FamilyEditQueue.prepare(draft, latest)
                port.put(prepared, draft); draft = prepared
                port.progress(draft, index, selected.size)
            }
            if (draft.changes.isNotEmpty() || draft.submitted) {
                port.checkpoint()
                val submitted = draft.copy(submitted = true)
                port.put(submitted, draft); draft = submitted
                port.progress(draft, index, selected.size)
                port.publish(draft)
                port.checkpoint()
            }
            // Empty changes were either never sent or are already present in the latest snapshot.
            port.remove(draft)
            port.progress(draft, index + 1, selected.size)
        }
        return selected.size
    }
}
