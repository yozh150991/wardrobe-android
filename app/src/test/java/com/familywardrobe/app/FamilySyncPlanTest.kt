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
        val published = mutableListOf<FamilySyncOperation>(); val receipts = mutableSetOf<String>(); val receiptOf = mutableMapOf<String, Long>()
        var failPublish = false; var failAck = false; var failRemove = false; var revoked = false
        var afterPublish: (() -> Unit)? = null
        /** Simulated server: every op starts from [base]; [head] advances on each commit or foreign write. */
        var head = 7L; val bases = mutableMapOf<String, Long>()
        /** Server-applied changes (delta model): never stale, committed on whatever head is current. */
        val changes = mutableSetOf<String>()
        fun base(o: FamilySyncOperation) = bases[o.id] ?: 7L
        override fun plan() = stored
        override fun save(next: FamilySyncPlan, expected: FamilySyncPlan) {
            assertEquals(expected, stored); stored = next
            if (next.acknowledged && failAck) { failAck = false; error("Crash after durable ack") }
        }
        override fun pending(kind: FamilySyncKind) = rows.filter { it.kind == kind }
        override suspend fun authorize(operation: FamilySyncOperation, plan: FamilySyncPlan): FamilySyncDecision {
            if (revoked) throw FamilyTransferException(FamilyProblem.ACCESS)
            val current = rows.first { it.id == operation.id }
            val base = base(operation)
            if (current.submitted || base == head || operation.id in changes) return FamilySyncDecision.Send(operation)
            if (!plan.ownsAfter(base, head)) throw FamilyTransferException(FamilyProblem.CONFLICT)
            if (operation.kind != FamilySyncKind.EDIT) return FamilySyncDecision.Defer
            val rebased = operation.copy(fingerprint = "c".repeat(64))
            rows[rows.indexOfFirst { it.id == operation.id }] = rebased; bases[operation.id] = head
            return FamilySyncDecision.Send(rebased)
        }
        override fun submitted(operation: FamilySyncOperation) {
            val i = rows.indexOfFirst { operation.matches(it) }; require(i >= 0); rows[i] = rows[i].copy(submitted = true)
        }
        override suspend fun publish(operation: FamilySyncOperation): Long {
            val revision = if (operation.id in changes) receiptOf[operation.id] ?: (head + 1) else base(operation) + 1
            if (operation.id !in receipts) { require(revision == head + 1) { "CAS" }; head = revision; receiptOf[operation.id] = revision }
            published += operation; receipts += operation.id
            afterPublish?.invoke()
            if (failPublish) { failPublish = false; error("Lost response") }
            return revision
        }
        override fun remove(operation: FamilySyncOperation) {
            rows.removeAll { operation.matches(it) }
            if (failRemove) { failRemove = false; error("Crash after outbox removal") }
        }
        override fun checkpoint() { if (!stored.active || stored.id != id) throw CancellationException("Stopped") }
    }
    @Test fun wholeArchiveOutfitGoesFirstThenChangesThenItemDetails() {
        val rows = FamilySyncKind.entries.reversed().map { op(it) }
        assertEquals(listOf(FamilySyncKind.OUTFIT, FamilySyncKind.ADD, FamilySyncKind.PHOTO, FamilySyncKind.EDIT),
            FamilySyncPlanner.ordered(rows.groupBy { it.kind }).map { it.kind })
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
        // The change text is approved too; a full-archive draft's fingerprint is unchanged by the new field.
        val change = d.copy(change = """{"kind":"photo"}""")
        assertFalse(a.matches(FamilySyncPlanner.media(FamilySyncKind.ADD, change)))
        assertFalse(FamilySyncPlanner.media(FamilySyncKind.ADD, change).matches(FamilySyncPlanner.media(FamilySyncKind.ADD, change.copy(change = """{"kind":"add"}"""))))
        assertEquals(FamilySyncPlanner.fingerprint(listOf(remote, d.publication.identity.wardrobeId, "owner", d.publication.archiveId, "4",
            "a".repeat(64), "100", d.garmentId, d.photoId, "Shirt")), a.fingerprint)
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
    @Test fun editsPreparedOnOneRevisionChainAfterThisBatchsOwnCommits() = runBlocking {
        val edits = (1..4).map { op() }; val port = Port(edits)
        assertTrue(FamilySyncRunner(port).run()); assertEquals(4, port.receipts.size)
        assertEquals(listOf(8L, 9L, 10L, 11L), port.stored.owned); assertEquals("", port.stored.problem)
        assertTrue(port.rows.isEmpty())
    }
    @Test fun firstArchiveThenEditsAreSentAndStaleArchiveStaysQueued() = runBlocking {
        // Full-archive additions (prepared before the server supported changes) behave as before.
        val add1 = op(FamilySyncKind.ADD); val add2 = op(FamilySyncKind.ADD); val outfit = op(FamilySyncKind.OUTFIT); val edit = op()
        val ordered = FamilySyncPlanner.ordered(listOf(add1, add2, outfit, edit).groupBy { it.kind })
        val port = Port(ordered)
        assertTrue(FamilySyncRunner(port).run())
        assertEquals(listOf(outfit.id, edit.id), port.published.map { it.id })
        assertEquals(listOf(add1, add2), port.rows); assertEquals(FamilySyncStatus.DONE, port.stored.status)
        assertEquals("PARTIAL", port.stored.problem)
        assertEquals(setOf(add1.key, add2.key), port.stored.deferred.toSet())
    }
    @Test fun serverAppliedChangesFollowTheOutfitAndEditsChainAfterThem() = runBlocking {
        val outfit = op(FamilySyncKind.OUTFIT); val adds = (1..3).map { op(FamilySyncKind.ADD) }; val photo = op(FamilySyncKind.PHOTO)
        val edits = (1..2).map { op() }
        val port = Port(FamilySyncPlanner.ordered((listOf(outfit, photo) + adds + edits).groupBy { it.kind }))
        port.changes += adds.map { it.id } + photo.id
        assertTrue(FamilySyncRunner(port).run())
        assertEquals((listOf(outfit) + adds + photo + edits).map { it.id }, port.published.map { it.id })
        assertEquals((8L..14L).toList(), port.stored.owned)
        assertEquals("", port.stored.problem); assertTrue(port.stored.deferred.isEmpty()); assertTrue(port.rows.isEmpty())
    }
    @Test fun changeAfterForeignCommitIsSentButFollowingEditStops() = runBlocking {
        val add = op(FamilySyncKind.ADD); val edit = op(); val port = Port(listOf(add, edit)); port.changes += add.id
        port.head = 9 // another member committed after these drafts were prepared on 7
        rejects { runBlocking { FamilySyncRunner(port).run() } }
        assertEquals(listOf(add), port.published); assertEquals(listOf(10L), port.stored.owned); assertEquals(listOf(edit), port.rows)
    }
    @Test fun deferredHeadKeepsLaterOperationsOfItsKind() = runBlocking {
        val a = op(FamilySyncKind.PHOTO); val b = op(FamilySyncKind.PHOTO); val port = Port(listOf(a, b))
        port.head = 8; port.stored = port.stored.copy(owned = listOf(8)); port.bases[b.id] = 8
        assertTrue(FamilySyncRunner(port).run())
        assertTrue(port.published.isEmpty()); assertEquals(listOf(a, b), port.rows)
    }
    @Test fun foreignRevisionStopsTheBatchInsteadOfRebasing() = runBlocking {
        val a = op(); val b = op(); val port = Port(listOf(a, b))
        port.afterPublish = { if (port.published.size == 1) port.head += 1 }
        rejects { runBlocking { FamilySyncRunner(port).run() } }
        assertEquals(listOf(a.id), port.published.map { it.id }); assertEquals(listOf(b), port.rows)
    }
    @Test fun timeBudgetStopsBeforeStartingAnotherOperation() = runBlocking {
        val port = Port((1..3).map { op() }); var calls = 0
        assertFalse(FamilySyncRunner(port).run(10) { calls++ < 0 })
        assertEquals(1, port.stored.cursor); assertEquals(1, port.receipts.size)
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
        val rows = listOf(op(FamilySyncKind.OUTFIT, true), op(FamilySyncKind.ADD), op())
        val p = plan(rows).copy(acknowledged = true, status = FamilySyncStatus.RUNNING, owned = listOf(8, 9),
            deferred = listOf(rows[1].key), attempts = 2, attemptCursor = 0)
        assertEquals(p, FamilySyncJournal.decode(FamilySyncJournal.encode(p, "account"), "account", remote))
    }
    @Test fun versionOneJournalStillOpensWithEmptyProgressFields() {
        val p = plan(listOf(op()))
        val v1 = JSONObject(FamilySyncJournal.encode(p, "account")).put("version", 1)
        listOf("owned", "deferred", "attempts", "attemptCursor").forEach { v1.remove(it) }
        assertEquals(p, FamilySyncJournal.decode(v1.toString(), "account", remote))
    }
    @Test fun invalidProgressFieldsRejected() {
        val a = op(); val p = plan(listOf(a, op()))
        rejects { p.copy(owned = listOf(0)) }; rejects { plan(listOf(a)).copy(owned = listOf(8, 9)) }; rejects { p.copy(owned = listOf(8, 8)) }
        rejects { p.copy(deferred = listOf("EDIT:" + newId())) }; rejects { p.copy(attempts = 101) }
        assertTrue(p.copy(owned = listOf(8, 9)).ownsAfter(7, 9)); assertFalse(p.copy(owned = listOf(9)).ownsAfter(7, 9))
        assertFalse(p.ownsAfter(7, 7))
    }
    @Test fun journalRejectsWrongAccountRemoteUnknownVersionAndCorruption() {
        val text = FamilySyncJournal.encode(plan(listOf(op())), "account")
        rejects { FamilySyncJournal.decode(text, "other", remote) }
        rejects { FamilySyncJournal.decode(text, "account", newId()) }
        rejects { FamilySyncJournal.decode(JSONObject(text).put("version", 3).toString(), "account", remote) }
        rejects { FamilySyncJournal.decode("broken", "account", remote) }
    }
}
