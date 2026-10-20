package com.familywardrobe.app

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Durable account-private bytes; excluded from all portable/private backup manifests. */
internal class FamilyArchive(private val context: AccountContext, private val repo: Repository, private val photos: PhotoStore,
    private val store: FamilyMigrationStore, private val guard: () -> Unit) : FamilyLocalTransfer {
    private val directory = File(context.filesDir,"family-transfer")
    private fun file(r: FamilyMigrationRecord) = File(directory,"${r.remoteId}.zip")
    override fun prepare(id: String) = store.prepare(id)
    override suspend fun revision() = requireNotNull(repo.dao.cloudMeta()).localRevision
    override fun uploaded(record: FamilyMigrationRecord) = store.uploaded(record)
    override fun rotate(record: FamilyMigrationRecord, rebuild: Boolean): FamilyMigrationRecord {
        guard(); val next = store.rotate(record,rebuild)
        if (rebuild) file(record).delete()
        return next
    }
    override suspend fun confirm(record: FamilyMigrationRecord, remote: FamilyRemote) = repo.writes.withLock {
        guard(); coroutineContext.ensureActive(); val h = requireNotNull(remote.head)
        if (revision() != record.sourceRevision) throw FamilyTransferException(FamilyProblem.OLDER)
        store.confirmServer(record,remote.identity,h.path,h.hash,remote.revision,revision())
        file(record).delete(); Unit
    }
    override suspend fun checkedFile(record: FamilyMigrationRecord): File {
        val work = coroutineContext; val checkpoint = { work.ensureActive(); guard() }; checkpoint()
        val target = file(record)
        if (!target.isFile || target.length() != record.archiveSize || digest(target,checkpoint) != record.fingerprint)
            throw FamilyTransferException(FamilyProblem.ARCHIVE)
        return target
    }
    override suspend fun stage(record: FamilyMigrationRecord): FamilyMigrationRecord = repo.writes.withLock {
        val work = coroutineContext; val checkpoint = { work.ensureActive(); guard() }; checkpoint()
        if (record.phase != FamilyMigrationPhase.PREPARED) {
            // Old foundation-only journals have no archive size. They need explicit Prepare again, never silent recreation.
            if (record.archiveSize == 0L) throw FamilyTransferException(FamilyProblem.ARCHIVE)
            return@withLock record
        }
        require(directory.mkdirs() || directory.isDirectory)
        val temporary = File(directory,"${record.remoteId}.tmp")
        try {
            val revision = revision(); val state = WardrobeSnapshots.select(repo.snapshot(),record.wardrobeId)
            BackupStateRules.validate(state)
            val manifest = FamilyEnvelope(Backup(context,repo,photos)).encode(state,record.identity,0).toByteArray(Charsets.UTF_8)
            FamilySnapshotZip.write(temporary, manifest, state, photos, checkpoint)
            val hash = digest(temporary,checkpoint)
            check(temporary.renameTo(file(record)))
            // The bytes are durable before the checkpoint. PREPARED may safely rebuild after a crash.
            checkpoint(); store.staged(record,revision,hash,file(record).length())
        } finally { temporary.delete() }
    }
    companion object {
        internal fun digest(file: File, checkpoint: () -> Unit = {}): String {
            val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(64*1024)
            file.inputStream().use { input -> while (true) {
                checkpoint(); val count = input.read(buffer); if (count < 0) break; hash.update(buffer,0,count)
            } }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
