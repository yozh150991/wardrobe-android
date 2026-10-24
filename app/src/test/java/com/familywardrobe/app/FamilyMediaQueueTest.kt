package com.familywardrobe.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FamilyMediaQueueTest {
    private val identity = FamilyArchiveIdentity(newId(), newId(), "owner")
    private fun draft(size: Long = 100) = FamilyAddDraft(FamilyPublication(identity, newId(), 4, "a".repeat(64), size), newId(), newId(), "Shirt")
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun appendingSeveralItemsKeepsInsertionOrder() {
        val a = draft(); val b = draft(); val c = draft()
        assertEquals(listOf(a, b, c), FamilyMediaQueue.put(listOf(a, b), c, null))
    }
    @Test fun markingSubmittedAndRebasingPreservePositionAndNeighbors() {
        val a = draft(); val b = draft(); val c = draft()
        val submitted = b.copy(submitted = true)
        val rows = FamilyMediaQueue.put(listOf(a, b, c), submitted, b)
        val rebased = b.copy(publication = b.publication.copy(archiveId = newId(), baseRevision = 6))
        assertEquals(listOf(a, rebased, c), FamilyMediaQueue.put(rows, rebased, submitted))
    }
    @Test fun removingMiddleDraftDoesNotTouchHeadOrTail() {
        val a = draft(); val b = draft(); val c = draft()
        assertEquals(listOf(a, c), FamilyMediaQueue.remove(listOf(a, b, c), b))
    }
    @Test fun staleMutationCannotTouchNewPublicationForSameItem() {
        val a = draft(); val next = a.copy(publication = a.publication.copy(archiveId = newId()))
        rejects { FamilyMediaQueue.put(listOf(next), a, a) }
        rejects { FamilyMediaQueue.remove(listOf(next), a) }
        assertEquals(listOf(next), FamilyMediaQueue.put(listOf(next), next, next))
    }
    @Test fun oneDraftPerItemAndUniqueArchiveIdentity() {
        val a = draft()
        rejects { FamilyMediaQueue.put(listOf(a), draft().copy(garmentId = a.garmentId), null) }
        rejects { FamilyMediaQueue.validate(listOf(a, draft().copy(publication = a.publication))) }
        rejects { FamilyMediaQueue.validate(listOf(a, a)) }
    }
    @Test fun tenDraftsAllowedEleventhRejected() {
        val rows = (1..10).map { draft() }
        assertEquals(10, FamilyMediaQueue.validate(rows).size)
        val error = runCatching { FamilyMediaQueue.put(rows, draft(), null) }.exceptionOrNull()
        assertEquals(FamilyProblem.LIMIT, (error as FamilyTransferException).problem)
        assertEquals(10, FamilyMediaQueue.put(rows, rows[5].copy(submitted = true), rows[5]).size)
    }
    @Test fun archiveBudgetAllowsExactBoundaryButRejectsOneByteOver() {
        // Each publication is independently capped at 128 MiB; the queue budget spans several archives.
        val rows = (1..4).map { draft(FamilyWire.MAX_ARCHIVE) }
        assertEquals(FamilyMediaQueue.MAX_BYTES, rows.sumOf { it.publication.size })
        assertEquals(rows, FamilyMediaQueue.put(rows.dropLast(1), rows.last(), null))
        val error = runCatching { FamilyMediaQueue.put(rows, draft(1), null) }.exceptionOrNull()
        assertEquals(FamilyProblem.LIMIT, (error as FamilyTransferException).problem)
    }
    @Test fun anotherWardrobeOrOwnerCannotBeCombined() {
        val a = draft()
        for (foreign in listOf(identity.copy(remoteId = newId()), identity.copy(wardrobeId = newId()), identity.copy(ownerUid = "other"))) {
            rejects { FamilyMediaQueue.put(listOf(a), draft().let { it.copy(publication = it.publication.copy(identity = foreign)) }, null) }
        }
    }
    @Test fun onlyExactHeadCanBeSentAndRepeatedTapCannotAdvanceTail() {
        val a = draft(); val b = draft(); val rows = listOf(a, b)
        FamilyMediaQueue.requireHead(rows, a)
        rejects { FamilyMediaQueue.requireHead(rows, b) }
        val remaining = FamilyMediaQueue.remove(rows, a)
        rejects { FamilyMediaQueue.requireHead(remaining, a) }
        FamilyMediaQueue.requireHead(remaining, b)
    }
    @Test fun legacySubmittedRequestSurvivesMigrationByteForByteInItsFields() {
        val a = draft().copy(submitted = true)
        val legacy = JSONObject(FamilyMediaQueue.encode(listOf(a), "account", identity.remoteId)).getJSONArray("drafts").getJSONObject(0)
            .put("version", 1).put("account", "account").put("remoteId", identity.remoteId).toString()
        val recovered = FamilyMediaQueue.decode(legacy, "account", identity.remoteId)
        assertEquals(listOf(a), recovered)
        val b = draft(); val upgraded = FamilyMediaQueue.encode(FamilyMediaQueue.put(recovered, b, null), "account", identity.remoteId)
        assertEquals(listOf(a, b), FamilyMediaQueue.decode(upgraded, "account", identity.remoteId))
        assertEquals(a.publication.request(), FamilyMediaQueue.decode(upgraded, "account", identity.remoteId).first().publication.request())
    }
    @Test fun newJournalPreservesOrderAndUnicode() {
        val rows = listOf(draft().copy(name = "Сукня — Łódź"), draft(), draft().copy(submitted = true))
        assertEquals(rows, FamilyMediaQueue.decode(FamilyMediaQueue.encode(rows, "account", identity.remoteId), "account", identity.remoteId))
    }
    @Test fun wrongAccountRemoteUnknownVersionAndCorruptionFailClosed() {
        val text = FamilyMediaQueue.encode(listOf(draft()), "account", identity.remoteId)
        rejects { FamilyMediaQueue.decode(text, "other", identity.remoteId) }
        rejects { FamilyMediaQueue.decode(text, "account", newId()) }
        rejects { FamilyMediaQueue.decode(JSONObject(text).put("version", 3).toString(), "account", identity.remoteId) }
        rejects { FamilyMediaQueue.decode("broken", "account", identity.remoteId) }
    }
    @Test fun submittedRecoveryKeepsExactPayloadBeforeAnyNewArchive() {
        val a = draft(); val b = draft(); val pending = a.copy(submitted = true)
        val restarted = FamilyMediaQueue.decode(FamilyMediaQueue.encode(listOf(pending, b), "account", identity.remoteId), "account", identity.remoteId)
        FamilyMediaQueue.requireHead(restarted, pending)
        assertEquals(a.publication, restarted.first().publication)
        assertEquals(listOf(b), FamilyMediaQueue.remove(restarted, pending))
    }
    @Test fun newerSnapshotIsNotSilentlySubstitutedForQueuedRequest() {
        val a = draft(); val newer = a.copy(publication = a.publication.copy(baseRevision = 5, archiveId = newId()))
        rejects { FamilyMediaQueue.requireHead(listOf(a), newer) }
        rejects { FamilyMediaQueue.remove(listOf(a), newer) }
    }
}
