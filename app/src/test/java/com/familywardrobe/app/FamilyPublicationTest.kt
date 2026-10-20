package com.familywardrobe.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FamilyPublicationTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    private val publication = FamilyPublication(FamilyArchiveIdentity(id(1), id(2), "owner"), id(3), 5, "a".repeat(64), 123)
    private class Fake(var committed: Boolean = false, var missing: Boolean = false, var failure: FamilyProblem? = null) : FamilyPublicationTransport {
        val calls = mutableListOf<String>()
        override suspend fun beginPublication(publication: FamilyPublication): FamilyTicket { calls += "begin"; return FamilyTicket("staging", committed) }
        override suspend fun commitPublication(publication: FamilyPublication) {
            calls += "commit"
            failure?.let { throw FamilyTransferException(it) }
            if (missing) throw FamilyTransferException(FamilyProblem.MISSING)
        }
        override suspend fun upload(file: File, path: String, progress: (Long, Long) -> Unit) { calls += "upload"; missing = false; progress(123, 123) }
    }
    @Test fun committedReplayNeverOpensLocalBytes() = runBlocking {
        val fake = Fake(committed = true)
        publishFamilyAddition(publication, fake, { error("Unexpected local read") }) {}
        assertEquals(listOf("begin", "commit"), fake.calls)
    }
    @Test fun lostUploadResponseProbesCommitWithoutUpload() = runBlocking {
        val fake = Fake()
        publishFamilyAddition(publication, fake, { error("Unexpected local read") }) {}
        assertEquals(listOf("begin", "commit"), fake.calls)
    }
    @Test fun missingRemoteBytesUploadsThenCommitsExactlyOnce() = runBlocking {
        val fake = Fake(missing = true); var reads = 0
        publishFamilyAddition(publication, fake, { reads++; File("immutable.zip") }) {}
        assertEquals(1, reads); assertEquals(listOf("begin", "commit", "upload", "commit"), fake.calls)
    }
    @Test fun conflictAndUnknownResponseNeverUploadOrRebuild() = runBlocking {
        for (problem in listOf(FamilyProblem.CONFLICT, FamilyProblem.RETRY, FamilyProblem.ACCESS)) {
            val fake = Fake(failure = problem)
            try { publishFamilyAddition(publication, fake, { error("Unexpected read") }) {}; fail("Error swallowed") }
            catch (e: FamilyTransferException) { assertEquals(problem, e.problem) }
            assertEquals(listOf("begin", "commit"), fake.calls)
        }
    }
    @Test fun cancellationStopsBeforeStorageWrite() = runBlocking {
        val fake = Fake(missing = true)
        try { publishFamilyAddition(publication, fake, { error("Unexpected read") }) { throw kotlinx.coroutines.CancellationException() }; fail("Not cancelled") }
        catch (_: kotlinx.coroutines.CancellationException) {}
        assertEquals(listOf("begin"), fake.calls)
    }
    private fun ticket(): Map<String, Any> = mapOf("archiveId" to publication.archiveId, "baseRevision" to 5.0, "sha256" to publication.hash,
        "size" to 123L, "authorUid" to "editor", "stagingPath" to "familyWardrobes/${id(1)}/staging/editor/${id(3)}.zip", "expiresAt" to 1000L, "status" to "pending")
    @Test fun ticketBindsActorHashPathAndRevision() {
        assertFalse(publication.ticket(ticket(), "editor").committed)
        for (changed in listOf(ticket() + ("authorUid" to "other"), ticket() + ("baseRevision" to 5.5), ticket() + ("size" to "123"), ticket() + ("stagingPath" to "private/path"))) {
            try { publication.ticket(changed, "editor"); fail("Invalid ticket") } catch (_: Exception) {}
        }
    }
    private fun receipt(): Map<String, Any> = mapOf("remoteId" to id(1), "wardrobeId" to id(2), "ownerUid" to "owner", "revision" to 6L,
        "head" to mapOf("revision" to 6L, "baseRevision" to 5L, "archiveId" to id(3), "archive" to FamilyRules.archivePath(id(1), id(3)),
            "sha256" to publication.hash, "size" to 123L, "authorUid" to "editor"))
    @Test fun receiptRequiresExactPublicationButDoesNotRequireCurrentHead() {
        publication.receipt(receipt(), "editor")
        try { publication.receipt(receipt(), "other"); fail("Wrong author") } catch (_: IllegalArgumentException) {}
        try { publication.receipt(receipt() + ("revision" to 7L), "editor"); fail("Wrong revision") } catch (_: IllegalArgumentException) {}
    }
}
