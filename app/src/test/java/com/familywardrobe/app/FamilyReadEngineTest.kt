package com.familywardrobe.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FamilyReadEngineTest {
    private val remoteId = "00000000-0000-0000-0000-000000000001"
    private val wardrobeId = "00000000-0000-0000-0000-000000000002"
    private val archiveId = "00000000-0000-0000-0000-000000000003"
    private inner class Fixture(val actor: String = "member", role: String = "viewer") : FamilyReadTransport {
        var remote = FamilyRemote(FamilyArchiveIdentity(remoteId, wardrobeId, "owner"), 1,
            FamilyHead(1, 0, archiveId, FamilyRules.archivePath(remoteId, archiveId), "ab".repeat(32), 10, "owner"), role)
        var reads = 0; var staged = 0; var published = 0; var discarded = 0
        var now = 100L; var active = true; var afterStage: () -> Unit = {}
        var duringSecondRead: () -> Unit = {}; var publishedRole = ""; var publishedAt = -1L
        override suspend fun authenticate() {}
        override suspend fun readHead(remoteId: String): FamilyRemote {
            reads++; if (reads == 2) duringSecondRead(); return remote
        }
        override suspend fun download(remote: FamilyRemote, target: File, progress: (Long, Long) -> Unit) = Unit
        fun engine() = FamilyReadEngine(actor, this, { if (!active) throw CancellationException("Account changed") }, { now },
            stage = { staged++; afterStage(); "candidate" },
            publish = { _, fresh, verifiedAt -> published++; publishedRole = fresh.role; publishedAt = verifiedAt },
            discard = { discarded++ })
        suspend fun rejected(problem: FamilyProblem) {
            try { engine().run(remoteId); fail("Expected failure") }
            catch (e: FamilyTransferException) { assertEquals(problem, e.problem) }
            assertEquals(0, published)
        }
    }
    @Test fun readerPublishesOnlyAfterRecheck() = runBlocking {
        val f = Fixture(); f.engine().run(remoteId)
        assertEquals(2, f.reads); assertEquals(1, f.published); assertEquals(0, f.discarded); assertEquals(100L, f.publishedAt)
    }
    @Test fun ownerAndEditorMayUseReadOnlyCache() = runBlocking {
        for ((actor, role) in listOf("owner" to "owner", "member" to "editor")) {
            val f = Fixture(actor, role); f.engine().run(remoteId); assertEquals(1, f.published)
        }
    }
    @Test fun revokedDuringDownloadDiscardsCandidate() = runBlocking {
        val f = Fixture(); f.duringSecondRead = { throw FamilyTransferException(FamilyProblem.ACCESS) }
        f.rejected(FamilyProblem.ACCESS); assertEquals(1, f.discarded)
    }
    @Test fun changedHeadNeverPublishesStaleSnapshot() = runBlocking {
        val f = Fixture(); f.afterStage = { f.remote = f.remote.copy(revision = 2, head = f.remote.head!!.copy(revision = 2, base = 1)) }
        f.rejected(FamilyProblem.CONFLICT); assertEquals(1, f.discarded)
    }
    @Test fun roleDowngradeUsesFreshReadRole() = runBlocking {
        val f = Fixture(role = "editor"); f.afterStage = { f.remote = f.remote.copy(role = "viewer") }
        f.engine().run(remoteId); assertEquals("viewer", f.publishedRole)
    }
    @Test fun spoofedOwnerNeverReadsArchive() = runBlocking {
        val f = Fixture(role = "owner"); f.rejected(FamilyProblem.ACCESS); assertEquals(0, f.staged)
    }
    @Test fun missingHeadDoesNotStage() = runBlocking {
        val f = Fixture(); f.remote = f.remote.copy(revision = 0, head = null)
        f.rejected(FamilyProblem.MISSING); assertEquals(0, f.staged)
    }
    @Test fun accountSwitchAfterDownloadCannotPublish() = runBlocking {
        val f = Fixture(); f.afterStage = { f.active = false }
        try { f.engine().run(remoteId); fail() } catch (_: CancellationException) {}
        assertEquals(0, f.published); assertEquals(1, f.discarded)
    }
    @Test fun slowVerificationDoesNotRenewAnExpiredLease() = runBlocking {
        val f = Fixture(); f.duringSecondRead = { f.now += FamilyRules.CACHE_LEASE_MS }
        f.rejected(FamilyProblem.EXPIRED); assertEquals(1, f.discarded)
    }
    @Test fun failedRecheckDoesNotExposeDownloadedBytes() = runBlocking {
        val f = Fixture(); f.duringSecondRead = { throw FamilyTransferException(FamilyProblem.RETRY) }
        f.rejected(FamilyProblem.RETRY); assertEquals(1, f.discarded)
    }
    @Test fun changedIdentityCannotPublishEvenWithSameHead() = runBlocking {
        val f = Fixture(); f.afterStage = { f.remote = f.remote.copy(identity = f.remote.identity.copy(wardrobeId = archiveId)) }
        f.rejected(FamilyProblem.CONFLICT); assertEquals(1, f.discarded)
    }
    @Test fun foreignRemoteRejectedBeforeReadingData() = runBlocking {
        val f = Fixture(); f.remote = f.remote.copy(identity = f.remote.identity.copy(remoteId = wardrobeId))
        try { f.engine().run(remoteId); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(0, f.staged); assertEquals(0, f.published)
    }
}
