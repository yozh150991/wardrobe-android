package com.familywardrobe.app

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.File

internal enum class FamilyProblem { EMAIL, ACCESS, CONFIG, CONFLICT, EXPIRED, MISSING, CHANGED, OLDER, ARCHIVE, LIMIT, RETRY }
internal class FamilyTransferException(val problem: FamilyProblem, cause: Throwable? = null) : Exception(problem.name, cause)
internal enum class FamilyStep { CHECKING, PREPARING, UPLOADING, COMMITTING, VERIFYING, DONE }
internal data class FamilyHead(val revision: Long, val base: Long, val archiveId: String, val path: String,
    val hash: String, val size: Long, val author: String)
internal data class FamilyRemote(val identity: FamilyArchiveIdentity, val revision: Long, val head: FamilyHead?, val role: String)
internal data class FamilyTicket(val path: String, val committed: Boolean)
internal interface FamilyTransport {
    suspend fun authenticate()
    suspend fun create(record: FamilyMigrationRecord): FamilyRemote
    suspend fun resume(record: FamilyMigrationRecord): FamilyRemote
    suspend fun begin(record: FamilyMigrationRecord): FamilyTicket
    suspend fun upload(file: File, path: String, progress: (Long, Long) -> Unit)
    suspend fun commit(record: FamilyMigrationRecord)
}
internal interface FamilyLocalTransfer {
    fun prepare(id: String): FamilyMigrationRecord
    suspend fun stage(record: FamilyMigrationRecord): FamilyMigrationRecord
    suspend fun checkedFile(record: FamilyMigrationRecord): File
    suspend fun revision(): Long
    fun uploaded(record: FamilyMigrationRecord): FamilyMigrationRecord
    suspend fun confirm(record: FamilyMigrationRecord, remote: FamilyRemote)
    fun rotate(record: FamilyMigrationRecord, rebuild: Boolean): FamilyMigrationRecord
}

/** Initial publication only. No direct head writes, local content mutation or automatic CAS overwrite. */
internal class FamilyTransferEngine(private val local: FamilyLocalTransfer, private val backend: FamilyTransport,
    private val guard: () -> Unit, private val progress: (FamilyStep, Long, Long) -> Unit = { _, _, _ -> }) {
    private suspend fun checkpoint() { coroutineContext.ensureActive(); guard() }
    private suspend fun checked(remote: FamilyRemote, record: FamilyMigrationRecord): FamilyRemote {
        checkpoint()
        if (remote.identity != record.identity || remote.role != "owner") throw FamilyTransferException(FamilyProblem.ACCESS)
        require(remote.revision >= 0 && (remote.head == null) == (remote.revision == 0L))
        return remote
    }
    private suspend fun acknowledge(remote: FamilyRemote, record: FamilyMigrationRecord): Boolean {
        checked(remote, record)
        val h = remote.head ?: return false
        if (record.phase == FamilyMigrationPhase.PREPARED || remote.revision != 1L || h.revision != 1L || h.base != 0L ||
            h.archiveId != record.archiveId || h.path != record.path || h.hash != record.fingerprint || h.author != record.ownerUid ||
            record.archiveSize > 0 && h.size != record.archiveSize) throw FamilyTransferException(FamilyProblem.CONFLICT)
        checkpoint(); local.confirm(local.uploaded(record), remote)
        checkpoint(); progress(FamilyStep.DONE, 0, 0); return true
    }
    suspend fun run(id: String) {
        checkpoint(); progress(FamilyStep.CHECKING, 0, 0)
        backend.authenticate(); checkpoint()
        var record = local.prepare(id)
        if (acknowledge(backend.create(record), record)) return
        repeat(2) { attempt ->
            try {
                checkpoint(); progress(FamilyStep.PREPARING, 0, 0)
                record = local.stage(record); checkpoint()
                val ticket = backend.begin(record); checkpoint()
                if (!ticket.committed) {
                    // Probe before accessing local ZIP or writing Storage. Covers lost upload replies/process death.
                    progress(FamilyStep.COMMITTING, 0, 0)
                    try { backend.commit(record) }
                    catch (e: FamilyTransferException) {
                        if (e.problem != FamilyProblem.MISSING) throw e
                        checkpoint()
                        if (local.revision() != record.sourceRevision) throw FamilyTransferException(FamilyProblem.CHANGED)
                        val file = local.checkedFile(record); checkpoint()
                        progress(FamilyStep.UPLOADING, 0, record.archiveSize)
                        backend.upload(file, ticket.path) { done, total -> guard(); progress(FamilyStep.UPLOADING, done, total) }
                        checkpoint(); record = local.uploaded(record)
                        progress(FamilyStep.COMMITTING, 0, 0); backend.commit(record)
                    }
                }
                checkpoint(); progress(FamilyStep.VERIFYING, 0, 0)
                if (!acknowledge(backend.resume(record), record)) throw FamilyTransferException(FamilyProblem.RETRY)
                return
            } catch (e: FamilyTransferException) {
                checkpoint()
                if (e.problem != FamilyProblem.EXPIRED || attempt != 0) throw e
                if (acknowledge(backend.resume(record), record)) return
                record = local.rotate(record, false)
            }
        }
    }
    suspend fun restart(id: String) {
        checkpoint(); backend.authenticate(); checkpoint()
        val record = local.prepare(id)
        if (checked(backend.create(record), record).revision != 0L) throw FamilyTransferException(FamilyProblem.CONFLICT)
        checkpoint(); local.rotate(record, true); run(id)
    }
}

/** Callable numeric values may be Int, Long or integral Double. Never coerce strings/fractions. */
internal object FamilyWire {
    const val MAX_ARCHIVE = 128L * 1024 * 1024
    private fun str(m: Map<*, *>, key: String) = m[key] as? String ?: error("Invalid $key")
    private fun number(m: Map<*, *>, key: String, min: Long = 0, max: Long = 9_007_199_254_740_991L): Long {
        val n = m[key] as? Number ?: error("Invalid $key"); val v = n.toLong()
        require(n.toDouble().isFinite() && n.toDouble() == v.toDouble() && v in min..max); return v
    }
    fun remote(m: Map<*, *>): FamilyRemote {
        val identity = FamilyArchiveIdentity(str(m,"remoteId"), str(m,"wardrobeId"), str(m,"ownerUid"))
        val revision = number(m,"revision"); val role = str(m,"role").also { require(it in setOf("owner","viewer","editor")) }
        val head = if (m["head"] == null) null else {
            val h = m["head"] as? Map<*, *> ?: error("Invalid head")
            val id = str(h,"archiveId").also { require(FamilyRules.validId(it)) }
            FamilyHead(number(h,"revision",1),number(h,"baseRevision"),id,
                str(h,"archive").also { require(it == FamilyRules.archivePath(identity.remoteId,id)) },
                str(h,"sha256").also { require(FamilyRules.validHash(it)) },number(h,"size",1,MAX_ARCHIVE),
                str(h,"authorUid").also { require(FamilyRules.validUid(it)) })
                .also { require(it.revision == revision && it.base == revision - 1) }
        }
        require((head == null) == (revision == 0L)); return FamilyRemote(identity,revision,head,role)
    }
    fun ticket(m: Map<*, *>, r: FamilyMigrationRecord): FamilyTicket {
        require(str(m,"archiveId") == r.archiveId && number(m,"baseRevision") == 0L && str(m,"sha256") == r.fingerprint &&
            number(m,"size") == r.archiveSize && str(m,"authorUid") == r.ownerUid)
        val path = "familyWardrobes/${r.remoteId}/staging/${r.ownerUid}/${r.archiveId}.zip"
        require(str(m,"stagingPath") == path); val status = str(m,"status"); require(status in setOf("pending","committed"))
        number(m,"expiresAt",1); return FamilyTicket(path,status == "committed")
    }
}
