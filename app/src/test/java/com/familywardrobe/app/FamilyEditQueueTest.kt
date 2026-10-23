package com.familywardrobe.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FamilyEditQueueTest {
    private val remote = newId()
    private val fields = linkedMapOf("name" to "Shirt", "size" to "L", "location" to "Shelf", "description" to "")
    private fun draft(garment: String = newId(), remoteId: String = remote) =
        FamilyEditDraft(remoteId, garment, newId(), 4, fields, fields + ("location" to "Hall"))
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    private fun problem(expected: FamilyProblem, block: () -> Unit) {
        assertEquals(expected, (runCatching(block).exceptionOrNull() as? FamilyTransferException)?.problem)
    }
    private inner class Port(initial: List<FamilyEditDraft>) : FamilyEditQueuePort {
        var rows = initial
        var revision = 4L
        val server = initial.associate { it.garmentId to it.original }.toMutableMap()
        val requests = mutableListOf<FamilyEditDraft>()
        val receipts = mutableSetOf<String>()
        var latestCalls = 0
        var lostResponse = false
        var stopped = false
        var afterPublish: (() -> Unit)? = null
        override fun pending(remote: String) = rows.filter { it.remoteId == remote }
        override fun put(next: FamilyEditDraft, expected: FamilyEditDraft) { rows = FamilyEditQueue.put(rows, next, expected) }
        override fun remove(expected: FamilyEditDraft) { rows = FamilyEditQueue.remove(rows, expected) }
        override suspend fun latest(draft: FamilyEditDraft): FamilyEditDraft {
            latestCalls++
            val current = server[draft.garmentId] ?: throw FamilyTransferException(FamilyProblem.MISSING)
            return draft.copy(editId = newId(), baseRevision = revision, original = current, values = current, submitted = false)
        }
        override suspend fun publish(draft: FamilyEditDraft) {
            assertTrue(draft.submitted)
            assertEquals(draft, pending(draft.remoteId).first()) // durable before the network call
            requests += draft
            if (draft.editId !in receipts) {
                if (draft.baseRevision != revision) throw FamilyTransferException(FamilyProblem.CONFLICT)
                server[draft.garmentId] = server.getValue(draft.garmentId) + draft.changes
                revision++; receipts += draft.editId
            }
            afterPublish?.invoke()
            if (lostResponse) { lostResponse = false; throw FamilyTransferException(FamilyProblem.RETRY) }
        }
        override fun checkpoint() { if (stopped) throw CancellationException("Account closed") }
        override fun progress(draft: FamilyEditDraft, completed: Int, total: Int) {}
    }
    @Test fun multipleItemsAndWardrobesRetainInsertionOrder() {
        val a = draft(); val b = draft(); val c = draft(remoteId = newId())
        val rows = listOf(a, b, c)
        val updated = a.copy(values = a.values + ("size" to "M"))
        assertEquals(listOf(updated, b, c), FamilyEditQueue.put(rows, updated, a))
        assertEquals(listOf(a, c), FamilyEditQueue.remove(rows, b))
    }
    @Test fun duplicateItemAndDuplicateRequestIdsAreRejected() {
        val a = draft(); rejects { FamilyEditQueue.validate(listOf(a, draft(a.garmentId))) }
        rejects { FamilyEditQueue.validate(listOf(a, draft().copy(editId = a.editId))) }
        rejects { FamilyEditQueue.put(listOf(a), draft(a.garmentId), null) }
    }
    @Test fun staleMutationCannotReplaceRebasedOperation() {
        val a = draft(); val next = a.copy(editId = newId(), baseRevision = 5)
        rejects { FamilyEditQueue.put(listOf(next), a, a) }
        rejects { FamilyEditQueue.remove(listOf(next), a) }
    }
    @Test fun hundredOperationsAllowedButNextAdditionIsRejectedWithoutDroppingRows() {
        val rows = (1..100).map { draft() }
        assertEquals(100, FamilyEditQueue.validate(rows).size)
        problem(FamilyProblem.LIMIT) { FamilyEditQueue.put(rows, draft(), null) }
        assertEquals(100, FamilyEditQueue.put(rows, rows.first().copy(submitted = true), rows.first()).size)
        rejects { FamilyEditQueue.validate(rows + draft()) }
    }
    @Test fun legacyJournalRetainsSubmittedRequestExactly() {
        val a = draft().copy(submitted = true)
        val legacy = JSONObject(FamilyEditJournal.encode(listOf(a), "account")).put("version", 1).toString()
        assertEquals(listOf(a), FamilyEditJournal.decode(legacy, "account"))
    }
    @Test fun newJournalRoundTripsMultipleItemsAndUtf8Fields() {
        val a = draft().copy(values = fields + ("location" to "Шафа → друга полиця"))
        val rows = listOf(a, draft())
        assertEquals(rows, FamilyEditJournal.decode(FamilyEditJournal.encode(rows, "account"), "account"))
    }
    @Test fun corruptWrongAccountFutureVersionAndInvalidLegacyDuplicatesFailClosed() {
        val json = JSONObject(FamilyEditJournal.encode(listOf(draft(), draft()), "account"))
        rejects { FamilyEditJournal.decode("broken", "account") }
        rejects { FamilyEditJournal.decode(json.toString(), "other") }
        rejects { FamilyEditJournal.decode(json.put("version", 3).toString(), "account") }
        rejects { FamilyEditJournal.decode(json.put("version", 1).toString(), "account") }
    }
    @Test fun rebasePreservesOtherUsersUnchangedFields() {
        val a = draft(); val latest = a.copy(baseRevision = 5, original = fields + ("size" to "M"))
        val result = FamilyEditQueue.prepare(a, latest)
        assertEquals("M", result.values["size"]); assertEquals(mapOf("location" to "Hall"), result.changes)
    }
    @Test fun conflictingFieldStopsBeforeOverwrite() {
        val a = draft(); val latest = a.copy(baseRevision = 5, original = fields + ("location" to "Box"))
        problem(FamilyProblem.CONFLICT) { FamilyEditQueue.prepare(a, latest) }
    }
    @Test fun submittedOrOlderOrForeignSnapshotCannotBeRebased() {
        val a = draft()
        rejects { FamilyEditQueue.prepare(a.copy(submitted = true), a) }
        rejects { FamilyEditQueue.prepare(a, a.copy(baseRevision = 3)) }
        rejects { FamilyEditQueue.prepare(a, draft()) }
        rejects { FamilyEditQueue.prepare(a, a.copy(remoteId = newId())) }
    }
    @Test fun batchSendsFifoAndAdvancesBaseAfterEveryReceipt() = runBlocking {
        val a = draft(); val b = draft(); val port = Port(listOf(a, b))
        assertEquals(2, FamilyEditQueueSender(port).send(remote, true, a))
        assertEquals(listOf(a.garmentId, b.garmentId), port.requests.map { it.garmentId })
        assertEquals(listOf(4L, 5L), port.requests.map { it.baseRevision }); assertTrue(port.rows.isEmpty())
    }
    @Test fun singleSendLeavesTailAndOtherWardrobesIntact() = runBlocking {
        val a = draft(); val b = draft(); val other = draft(remoteId = newId()); val port = Port(listOf(other, a, b))
        assertEquals(1, FamilyEditQueueSender(port).send(remote, false, a))
        assertEquals(listOf(other, b), port.rows)
    }
    @Test fun cannotSendOutOfOrderOrWithStaleHead() = runBlocking {
        val a = draft(); val b = draft(); val port = Port(listOf(a, b))
        assertTrue(runCatching { FamilyEditQueueSender(port).send(remote, false, b) }.isFailure)
        assertTrue(port.requests.isEmpty()); assertEquals(listOf(a, b), port.rows)
    }
    @Test fun lostResponseRetriesSamePayloadBeforeAdvancingTail() = runBlocking {
        val a = draft(); val b = draft(); val port = Port(listOf(a, b)); port.lostResponse = true
        assertTrue(runCatching { FamilyEditQueueSender(port).send(remote, true, a) }.isFailure)
        val uncertain = port.rows.first(); assertTrue(uncertain.submitted)
        assertEquals(b, port.rows.last()); assertEquals(1, port.requests.size)
        FamilyEditQueueSender(port).send(remote, true, uncertain)
        assertEquals(port.requests[0], port.requests[1]); assertEquals(2, port.receipts.size)
        assertEquals(2, port.latestCalls); assertTrue(port.rows.isEmpty())
    }
    @Test fun conflictKeepsHeadAndDoesNotSendTail() = runBlocking {
        val a = draft(); val b = draft(); val port = Port(listOf(a, b)); port.revision++
        port.server[a.garmentId] = fields + ("location" to "Box")
        val error = runCatching { FamilyEditQueueSender(port).send(remote, true, a) }.exceptionOrNull()
        assertEquals(FamilyProblem.CONFLICT, (error as FamilyTransferException).problem)
        assertEquals(listOf(a, b), port.rows); assertTrue(port.requests.isEmpty())
    }
    @Test fun deletedItemStopsQueueWithoutDroppingDraft() = runBlocking {
        val a = draft(); val b = draft(); val port = Port(listOf(a, b)); port.server.remove(a.garmentId)
        val error = runCatching { FamilyEditQueueSender(port).send(remote, true, a) }.exceptionOrNull()
        assertEquals(FamilyProblem.MISSING, (error as FamilyTransferException).problem)
        assertEquals(listOf(a, b), port.rows); assertTrue(port.requests.isEmpty())
    }
    @Test fun alreadyAppliedFieldsNeedNoDuplicateNetworkWrite() = runBlocking {
        val a = draft(); val port = Port(listOf(a)); port.server[a.garmentId] = a.values; port.revision++
        FamilyEditQueueSender(port).send(remote, true, a)
        assertTrue(port.rows.isEmpty()); assertTrue(port.requests.isEmpty())
    }
    @Test fun accountClosureAfterCommitKeepsExactRequestForRecovery() = runBlocking {
        val a = draft(); val b = draft(); val port = Port(listOf(a, b)); port.afterPublish = { port.stopped = true }
        val error = runCatching { FamilyEditQueueSender(port).send(remote, true, a) }.exceptionOrNull()
        assertTrue(error is CancellationException); assertEquals(1, port.requests.size)
        assertEquals(port.requests.single(), port.rows.first()); assertEquals(b, port.rows.last())
    }
    @Test fun operationsAddedDuringSendAreNotIncludedWithoutConfirmation() = runBlocking {
        val a = draft(); val b = draft(); val port = Port(listOf(a)); port.afterPublish = { port.rows = FamilyEditQueue.put(port.rows, b, null) }
        assertEquals(1, FamilyEditQueueSender(port).send(remote, true, a))
        assertEquals(listOf(b), port.rows); assertEquals(1, port.requests.size)
    }
    @Test fun savedNoOpFromLegacyClientDoesNotBlockTail() = runBlocking {
        val a = draft().copy(values = fields); val b = draft(); val port = Port(listOf(a, b))
        assertEquals(2, FamilyEditQueueSender(port).send(remote, true, a))
        assertEquals(listOf(b.garmentId), port.requests.map { it.garmentId }); assertTrue(port.rows.isEmpty())
    }
}
