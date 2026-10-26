package com.familywardrobe.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FamilySyncPlanTest {
    private val remote = newId()
    private fun op(kind: FamilySyncKind = FamilySyncKind.EDIT, sent: Boolean = false) = FamilySyncOperation(kind, newId(), "a".repeat(64), sent)
    private fun plan(rows: List<FamilySyncOperation>) = FamilySyncPlan(newId(), remote, rows, true)
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    private inner class Port(initial: List<FamilySyncOperation>) : FamilySyncPort {
        var stored = plan(initial); private val id = stored.id
        var rows = initial.toMutableList()
        val published = mutableListOf<FamilySyncOperation>(); val receipts = mutableSetOf<String>()
        var failPublish = false; var failAck = false; var failRemove = false; var revoked = false
        var afterPublish: (() -> Unit)? = null
        override fun plan() = stored
        override fun save(next: FamilySyncPlan, expected: FamilySyncPlan) {
            assertEquals(expected, stored); stored = next
            if (next.acknowledged && failAck) { failAck = false; error("Crash after durable ack") }
        }
        override fun pending(kind: FamilySyncKind) = rows.filter { it.kind == kind }
        override suspend fun authorize(operation: FamilySyncOperation) {
            if (revoked) throw FamilyTransferException(FamilyProblem.ACCESS)
        }
        override fun submitted(operation: FamilySyncOperation) {
            val i = rows.indexOfFirst { operation.matches(it) }; require(i >= 0); rows[i] = rows[i].copy(submitted = true)
        }
        override suspend fun publish(operation: FamilySyncOperation) {
            published += operation; receipts += operation.id
            afterPublish?.invoke()
            if (failPublish) { failPublish = false; error("Lost response") }
        }
        override fun remove(operation: FamilySyncOperation) {
            rows.removeAll { operation.matches(it) }
            if (failRemove) { failRemove = false; error("Crash after outbox removal") }
        }
        override fun checkpoint() { if (!stored.active || stored.id != id) throw CancellationException("Stopped") }
    }
    @Test fun stablePriorityAcrossFourKinds() {
        val rows = FamilySyncKind.entries.reversed().map { op(it) }
        assertEquals(FamilySyncKind.entries.toList(), FamilySyncPlanner.ordered(rows.groupBy { it.kind }).map { it.kind })
    }
    @Test fun submittedHeadRecoveredBeforeNewOperations() {
        val edit = op(); val photo = op(FamilySyncKind.PHOTO, true)
        assertEquals(listOf(photo, edit), FamilySyncPlanner.ordered(mapOf(edit.kind to listOf(edit), photo.kind to listOf(photo))))
    }
    @Test fun submittedTailNeverJumpsItsOwnHead() {
        val a = op(FamilySyncKind.ADD); val b = op(FamilySyncKind.ADD, true)
        assertEquals(listOf(a, b), FamilySyncPlanner.ordered(mapOf(a.kind to listOf(a, b))))
    }
    @Test fun wrongKindAndDuplicateOperationRejected() {
        val a = op(); rejects { FamilySyncPlanner.ordered(mapOf(FamilySyncKind.ADD to listOf(a))) }
        rejects { plan(listOf(a, a)) }
    }
    @Test fun emptyAndOversizedPlansRejectedButBoundaryAccepted() {
        rejects { plan(emptyList()) }; assertEquals(130, plan((1..130).map { op() }).operations.size)
        rejects { plan((1..131).map { op() }) }
    }
    @Test fun invalidCursorAcknowledgementAndDoneStateRejected() {
        val p = plan(listOf(op()))
        rejects { p.copy(cursor = -1) }; rejects { p.copy(cursor = 2) }
        rejects { p.copy(cursor = 1, acknowledged = true) }; rejects { p.copy(status = FamilySyncStatus.DONE) }
    }
    @Test fun fingerprintSeparatesAmbiguousFieldsAndUnicode() {
        assertNotEquals(FamilySyncPlanner.fingerprint(listOf("a", "bc")), FamilySyncPlanner.fingerprint(listOf("ab", "c")))
        assertNotEquals(FamilySyncPlanner.fingerprint(listOf("Сукня")), FamilySyncPlanner.fingerprint(listOf("Suknia")))
    }
    @Test fun submittedTransitionKeepsApprovalButPayloadChangeDoesNot() {
        val a = op(); assertTrue(a.matches(a.copy(submitted = true)))
        assertFalse(a.matches(a.copy(fingerprint = "b".repeat(64))))
        assertFalse(a.matches(a.copy(id = newId())))
    }
    @Test fun changedCountOrderOrPayloadRequiresNewApproval() {
        val a = op(); val b = op()
        assertFalse(FamilySyncPlanner.unchanged(listOf(a), listOf(a, b)))
        assertFalse(FamilySyncPlanner.unchanged(listOf(a, b), listOf(b, a)))
        assertFalse(FamilySyncPlanner.unchanged(listOf(a), listOf(a.copy(fingerprint = "b".repeat(64)))))
    }
    @Test fun editApprovalBindsOriginalValuesAndBaseButNotSubmittedFlag() {
        val fields = linkedMapOf("name" to "Shirt", "size" to "L", "location" to "Shelf", "description" to "")
        val d = FamilyEditDraft(remote, newId(), newId(), 4, fields, fields + ("size" to "M"))
        val a = FamilySyncPlanner.edit(d)
        assertTrue(a.matches(FamilySyncPlanner.edit(d.copy(submitted = true))))
        assertFalse(a.matches(FamilySyncPlanner.edit(d.copy(baseRevision = 5))))
        assertFalse(a.matches(FamilySyncPlanner.edit(d.copy(values = fields + ("size" to "S")))))
    }
    @Test fun mediaApprovalBindsExactArchiveAndOperationType() {
        val d = FamilyAddDraft(FamilyPublication(FamilyArchiveIdentity(remote, newId(), "owner"), newId(), 4, "a".repeat(64), 100), newId(), newId(), "Shirt")
        val a = FamilySyncPlanner.media(FamilySyncKind.ADD, d)
        assertFalse(a.matches(FamilySyncPlanner.media(FamilySyncKind.PHOTO, d)))
        assertFalse(a.matches(FamilySyncPlanner.media(FamilySyncKind.ADD, d.copy(publication = d.publication.copy(hash = "b".repeat(64))))))
    }
    @Test fun onlyApprovedPrefixRunsAndNewDraftRemains() = runBlocking {
        val a = op(); val b = op(); val port = Port(listOf(a)); port.rows += b
        assertTrue(FamilySyncRunner(port).run()); assertEquals(listOf(b), port.rows)
        assertEquals(FamilySyncStatus.DONE, port.stored.status)
    }
    @Test fun boundedRunLeavesDurableCursorForNextWorker() = runBlocking {
        val port = Port((1..12).map { op() })
        assertFalse(FamilySyncRunner(port).run()); assertEquals(10, port.stored.cursor)
        assertTrue(FamilySyncRunner(port).run()); assertEquals(12, port.receipts.size)
    }
    @Test fun changedHeadStopsBeforeAnyPublication() = runBlocking {
        val port = Port(listOf(op())); port.rows[0] = port.rows[0].copy(fingerprint = "b".repeat(64))
        rejects { runBlocking { FamilySyncRunner(port).run() } }
        assertTrue(port.published.isEmpty()); assertFalse(port.rows.first().submitted)
    }
    @Test fun revokedAccessKeepsUnsubmittedDraft() = runBlocking {
        val port = Port(listOf(op())); port.revoked = true
        rejects { runBlocking { FamilySyncRunner(port).run() } }
        assertFalse(port.rows.first().submitted); assertEquals(0, port.stored.cursor)
    }
    @Test fun unknownResponseRetriesSameRequestWithoutNewIdentity() = runBlocking {
        val a = op(); val port = Port(listOf(a)); port.failPublish = true
        rejects { runBlocking { FamilySyncRunner(port).run() } }
        assertTrue(port.rows.first().submitted); assertFalse(port.stored.acknowledged)
        assertTrue(FamilySyncRunner(port).run())
        assertEquals(listOf(a.id, a.id), port.published.map { it.id }); assertEquals(1, port.receipts.size)
    }
    @Test fun crashAfterAckResumesCleanupWithoutRepublishing() = runBlocking {
        val port = Port(listOf(op())); port.failAck = true
        rejects { runBlocking { FamilySyncRunner(port).run() } }; assertTrue(port.stored.acknowledged)
        assertTrue(FamilySyncRunner(port).run()); assertEquals(1, port.published.size)
    }
    @Test fun crashAfterOutboxRemovalAdvancesCursorWithoutTouchingNeighbor() = runBlocking {
        val a = op(); val b = op(); val port = Port(listOf(a)); port.rows += b; port.failRemove = true
        rejects { runBlocking { FamilySyncRunner(port).run() } }
        assertTrue(port.stored.acknowledged); assertEquals(listOf(b), port.rows)
        assertTrue(FamilySyncRunner(port).run()); assertEquals(listOf(b), port.rows)
    }
    @Test fun cancelledDuringResponseDoesNotDeleteUnknownOutcome() = runBlocking {
        val port = Port(listOf(op())); port.afterPublish = { port.stored = port.stored.copy(status = FamilySyncStatus.CANCELLED) }
        rejects { runBlocking { FamilySyncRunner(port).run() } }
        assertTrue(port.rows.first().submitted); assertEquals(0, port.stored.cursor)
    }
    @Test fun replacementPlanIsNotAdvancedByOldRunner() = runBlocking {
        val port = Port(listOf(op())); port.afterPublish = { port.stored = port.stored.copy(id = newId()) }
        rejects { runBlocking { FamilySyncRunner(port).run() } }
        assertEquals(0, port.stored.cursor); assertTrue(port.rows.isNotEmpty())
    }
    @Test fun ackCleanupRejectsChangedPublicationWithSameId() = runBlocking {
        val port = Port(listOf(op())); port.stored = port.stored.copy(acknowledged = true)
        port.rows[0] = port.rows[0].copy(fingerprint = "b".repeat(64))
        rejects { runBlocking { FamilySyncRunner(port).run() } }; assertEquals(1, port.rows.size)
    }
    @Test fun journalPreservesReceiptCheckpointAndBackgroundChoice() {
        val p = plan(listOf(op(FamilySyncKind.OUTFIT, true), op())).copy(acknowledged = true, status = FamilySyncStatus.RUNNING)
        assertEquals(p, FamilySyncJournal.decode(FamilySyncJournal.encode(p, "account"), "account", remote))
    }
    @Test fun journalRejectsWrongAccountRemoteUnknownVersionAndCorruption() {
        val text = FamilySyncJournal.encode(plan(listOf(op())), "account")
        rejects { FamilySyncJournal.decode(text, "other", remote) }
        rejects { FamilySyncJournal.decode(text, "account", newId()) }
        rejects { FamilySyncJournal.decode(JSONObject(text).put("version", 2).toString(), "account", remote) }
        rejects { FamilySyncJournal.decode("broken", "account", remote) }
    }
}
