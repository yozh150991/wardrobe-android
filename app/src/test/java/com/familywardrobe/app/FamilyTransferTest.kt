package com.familywardrobe.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Production coordinator under lost replies, process restarts, expiry, conflicts and account changes. */
class FamilyTransferTest {
    private val ward = "00000000-0000-0000-0000-000000000001"
    private val remoteId = "00000000-0000-0000-0000-000000000002"
    private val archiveId = "00000000-0000-0000-0000-000000000003"
    private inner class Local : FamilyLocalTransfer {
        var record = FamilyMigrationRecord(ward,remoteId,"owner",archiveId)
        var rev = 7L; var stages = 0; var rotations = 0; var confirms = 0
        val file = File.createTempFile("family", ".zip").apply { writeText("archive") }
        override fun prepare(id: String) = record.also { require(id == ward) }
        override suspend fun stage(record: FamilyMigrationRecord): FamilyMigrationRecord {
            stages++
            if (this.record.phase == FamilyMigrationPhase.PREPARED) this.record = this.record.copy(
                phase = FamilyMigrationPhase.STAGED,sourceRevision = rev,fingerprint = "ab".repeat(32),archiveSize = 7)
            return this.record
        }
        override suspend fun checkedFile(record: FamilyMigrationRecord) = file.also { if (!it.exists()) throw FamilyTransferException(FamilyProblem.ARCHIVE) }
        override suspend fun revision() = rev
        override fun uploaded(record: FamilyMigrationRecord): FamilyMigrationRecord {
            require(record.archiveId == this.record.archiveId)
            if (this.record.phase == FamilyMigrationPhase.STAGED) this.record = this.record.copy(phase = FamilyMigrationPhase.UPLOADED)
            return this.record
        }
        override suspend fun confirm(record: FamilyMigrationRecord, remote: FamilyRemote) {
            if (rev != record.sourceRevision) throw FamilyTransferException(FamilyProblem.OLDER)
            confirms++; this.record = record.copy(phase = FamilyMigrationPhase.VERIFIED,remoteRevision = remote.revision)
        }
        override fun rotate(record: FamilyMigrationRecord, rebuild: Boolean): FamilyMigrationRecord {
            rotations++; val id = "00000000-0000-0000-0000-000000000004"
            this.record = if (rebuild) FamilyMigrationRecord(ward,remoteId,"owner",id)
            else record.copy(archiveId = id,phase = FamilyMigrationPhase.STAGED)
            return this.record
        }
    }
    private inner class Remote(private val local: Local) : FamilyTransport {
        var current = FamilyRemote(local.record.identity,0,null,"owner")
        var creates = 0; var uploads = 0; var resumes = 0; var blob = false
        var loseUpload = false; var loseCommit = false; var expire = false; var alwaysExpire = false
        var afterCreate: () -> Unit = {}; var afterUpload: () -> Unit = {}; var afterCommit: () -> Unit = {}
        override suspend fun authenticate() {}
        override suspend fun create(record: FamilyMigrationRecord): FamilyRemote { creates++; afterCreate(); return current }
        override suspend fun resume(record: FamilyMigrationRecord): FamilyRemote { resumes++; return current }
        override suspend fun begin(record: FamilyMigrationRecord): FamilyTicket {
            if (expire || alwaysExpire) { expire = false; throw FamilyTransferException(FamilyProblem.EXPIRED) }
            return FamilyTicket("staging",current.head != null)
        }
        override suspend fun upload(file: File, path: String, progress: (Long,Long) -> Unit) {
            uploads++; check(!blob); blob = true; afterUpload(); progress(7,7)
            if (loseUpload) { loseUpload = false; throw FamilyTransferException(FamilyProblem.RETRY) }
        }
        override suspend fun commit(record: FamilyMigrationRecord) {
            if (!blob) throw FamilyTransferException(FamilyProblem.MISSING)
            current = FamilyRemote(record.identity,1,FamilyHead(1,0,record.archiveId,record.path,record.fingerprint,7,"owner"),"owner")
            afterCommit()
            if (loseCommit) { loseCommit = false; throw FamilyTransferException(FamilyProblem.RETRY) }
        }
    }
    private fun scenario(block: suspend (Local,Remote,FamilyTransferEngine) -> Unit) = runBlocking {
        val l = Local(); val r = Remote(l)
        try { block(l,r,FamilyTransferEngine(l,r,{})) } finally { l.file.delete() }
    }
    private suspend fun failure(problem: FamilyProblem, block: suspend () -> Unit) {
        try { block(); fail("Expected $problem") } catch (e: FamilyTransferException) { assertEquals(problem,e.problem) }
    }
    @Test fun publicationAndReplayRequireCurrentHead() = scenario { l,r,e ->
        e.run(ward); assertEquals(FamilyMigrationPhase.VERIFIED,l.record.phase); assertEquals(1,r.resumes)
        e.run(ward); assertEquals(1,r.uploads); assertEquals(1,l.stages)
    }
    @Test fun verifiedTransferStaysCompleteAfterLocalEdits() = scenario { l,r,e ->
        e.run(ward); assertEquals(1,l.confirms); l.rev++
        e.run(ward); assertEquals(FamilyMigrationPhase.VERIFIED,l.record.phase); assertEquals(1,l.confirms); assertEquals(1,r.uploads)
    }
    @Test fun lostUploadReplyDoesNotOverwriteImmutableBlob() = scenario { l,r,e ->
        r.loseUpload = true; failure(FamilyProblem.RETRY) { e.run(ward) }
        assertEquals(FamilyMigrationPhase.STAGED,l.record.phase)
        FamilyTransferEngine(l,r,{}).run(ward); assertEquals(1,r.uploads); assertEquals(FamilyMigrationPhase.VERIFIED,l.record.phase)
    }
    @Test fun missingLocalZipStillRecoversSuccessfulUpload() = scenario { l,r,e ->
        r.loseUpload = true; failure(FamilyProblem.RETRY) { e.run(ward) }; l.file.delete()
        e.run(ward); assertEquals(1,r.uploads); assertEquals(1,l.confirms)
    }
    @Test fun missingLocalZipBeforeUploadFailsClosed() = scenario { l,r,e ->
        l.stage(l.record); l.file.delete(); failure(FamilyProblem.ARCHIVE) { e.run(ward) }; assertEquals(0,r.uploads)
    }
    @Test fun lostCommitReplyIsAcknowledgedWithoutRestaging() = scenario { l,r,e ->
        r.loseCommit = true; failure(FamilyProblem.RETRY) { e.run(ward) }
        e.run(ward); assertEquals(1,l.stages); assertEquals(1,r.uploads); assertEquals(1,l.confirms)
    }
    @Test fun expiryRenewsArchiveNotFamilyIdentity() = scenario { l,r,e ->
        r.expire = true; e.run(ward); assertEquals(1,l.rotations); assertEquals(remoteId,l.record.remoteId)
        assertNotEquals(archiveId,l.record.archiveId); assertEquals(7L,l.record.sourceRevision); assertEquals(1,r.creates)
    }
    @Test fun repeatedExpiryIsBounded() = scenario { l,r,e ->
        r.alwaysExpire = true; failure(FamilyProblem.EXPIRED) { e.run(ward) }; assertEquals(1,l.rotations); assertEquals(0,r.uploads)
    }
    @Test fun differentHashNeverAcknowledged() = scenario { l,r,e ->
        l.stage(l.record); r.current = FamilyRemote(l.record.identity,1,FamilyHead(1,0,archiveId,l.record.path,"cd".repeat(32),7,"owner"),"owner")
        failure(FamilyProblem.CONFLICT) { e.run(ward) }; assertEquals(0,l.confirms); assertEquals(0,r.uploads)
    }
    @Test fun changedLocalDataAfterCommitNeverClaimedAsSynced() = scenario { l,r,e ->
        r.afterCommit = { l.rev++ }; failure(FamilyProblem.OLDER) { e.run(ward) }
        assertEquals(0,l.confirms); assertEquals(8L,l.rev); assertEquals(1L,r.current.revision)
        failure(FamilyProblem.CONFLICT) { e.restart(ward) }
    }
    @Test fun staleLocalArchiveRequiresExplicitRebuild() = scenario { l,r,e ->
        l.stage(l.record); l.rev++; failure(FamilyProblem.CHANGED) { e.run(ward) }; assertEquals(0,r.uploads)
        e.restart(ward); assertEquals(8L,l.record.sourceRevision); assertEquals(remoteId,l.record.remoteId)
    }
    @Test fun accountSwitchAfterCreatePreventsReadingLocalData() = scenario { l,r,_ ->
        var active = true; r.afterCreate = { active = false }
        val e = FamilyTransferEngine(l,r,{ if(!active) throw CancellationException("Changed") })
        try { e.run(ward); fail() } catch (_: CancellationException) {}
        assertEquals(0,l.stages); assertEquals(0,r.uploads)
    }
    @Test fun accountSwitchDuringUploadPreventsCommit() = scenario { l,r,_ ->
        var active = true; r.afterUpload = { active = false }
        val e = FamilyTransferEngine(l,r,{ if(!active) throw CancellationException("Changed") })
        try { e.run(ward); fail() } catch (_: CancellationException) {}
        assertEquals(FamilyMigrationPhase.STAGED,l.record.phase); assertEquals(0L,r.current.revision)
    }
    @Test fun roleAndOwnerCannotBeAdoptedFromServerResponse() = scenario { l,r,e ->
        r.current = r.current.copy(role="viewer"); failure(FamilyProblem.ACCESS) { e.run(ward) }
        r.current = r.current.copy(role="owner",identity=l.record.identity.copy(ownerUid="other"))
        failure(FamilyProblem.ACCESS) { e.run(ward) }; assertEquals(0,l.stages)
    }
    @Test fun newerHeadAfterCommitIsConflict() = scenario { l,r,e ->
        r.afterCommit = { r.current = r.current.copy(revision=2) }
        failure(FamilyProblem.CONFLICT) { e.run(ward) }; assertEquals(0,l.confirms)
    }
    @Test fun wireRejectsFractionsStringsAndForeignPaths() {
        val base = mapOf("remoteId" to remoteId,"wardrobeId" to ward,"ownerUid" to "owner","revision" to 0,"head" to null,"role" to "owner")
        assertEquals(0L,FamilyWire.remote(base).revision)
        for (value in listOf(0.5,"0",1,Double.NaN)) {
            try { FamilyWire.remote(base+("revision" to value)); fail() } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
        }
        val rec = FamilyMigrationRecord(ward,remoteId,"owner",archiveId,FamilyMigrationPhase.STAGED,7,"ab".repeat(32),archiveSize=7)
        val ticket = mapOf("archiveId" to archiveId,"baseRevision" to 0,"sha256" to rec.fingerprint,"size" to 7,
            "authorUid" to "owner","stagingPath" to "users/other/archive.zip","status" to "pending","expiresAt" to 1)
        try { FamilyWire.ticket(ticket,rec); fail() } catch (_: IllegalArgumentException) {}
    }
}
