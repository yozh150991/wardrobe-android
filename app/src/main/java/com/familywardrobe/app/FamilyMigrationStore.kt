package com.familywardrobe.app

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Local checkpoint only: excluded from portable backup and private account cloud snapshots.
 * It cannot grant membership. The transport must verify the server head before confirmServer.
 * A damaged journal fails closed instead of allocating another remote wardrobe.
 */
internal class FamilyMigrationStore(context: AccountContext, private val account: AuthIdentity) {
    private val file = AtomicFile(File(context.filesDir, "family-migration-v1.json"))
    private val lock = locks.computeIfAbsent(file.baseFile.canonicalPath) { Any() }
    init { require(context.location.key == account.key) }

    fun records(): List<FamilyMigrationRecord> = synchronized(lock) { read() }
    fun prepare(wardrobeId: String): FamilyMigrationRecord = synchronized(lock) {
        val rows = read()
        rows.find { it.wardrobeId == wardrobeId } ?: FamilyMigrationRecord(wardrobeId, newId(), account.uid, newId()).also { write(rows + it) }
    }
    fun staged(expected: FamilyMigrationRecord, revision: Long, hash: String, size: Long = 0): FamilyMigrationRecord = update(expected) { current ->
        require(revision >= 0 && FamilyRules.validHash(hash))
        if (current.phase == FamilyMigrationPhase.PREPARED) current.copy(phase = FamilyMigrationPhase.STAGED, sourceRevision = revision, fingerprint = hash, archiveSize = size)
        else current.also { require(it.sourceRevision == revision && it.fingerprint == hash && it.archiveSize == size) }
    }
    fun uploaded(expected: FamilyMigrationRecord): FamilyMigrationRecord = update(expected) { current ->
        require(expected.phase != FamilyMigrationPhase.PREPARED && current.phase != FamilyMigrationPhase.PREPARED)
        if (current.phase == FamilyMigrationPhase.STAGED) current.copy(phase = FamilyMigrationPhase.UPLOADED) else current
    }
    fun confirmServer(expected: FamilyMigrationRecord, identity: FamilyArchiveIdentity, path: String,
        hash: String, serverRevision: Long, currentLocalRevision: Long): FamilyMigrationRecord = update(expected) { current ->
        require(expected.phase in setOf(FamilyMigrationPhase.UPLOADED, FamilyMigrationPhase.VERIFIED))
        require(current.phase in setOf(FamilyMigrationPhase.UPLOADED, FamilyMigrationPhase.VERIFIED))
        require(identity == current.identity && path == current.path && hash == current.fingerprint && serverRevision == 1L)
        require(currentLocalRevision == current.sourceRevision) { "Local data changed during family migration" }
        current.copy(phase = FamilyMigrationPhase.VERIFIED, remoteRevision = serverRevision)
    }
    /** The caller must first confirm an empty server head; never allocate another family root. */
    fun rotate(expected: FamilyMigrationRecord, rebuild: Boolean): FamilyMigrationRecord = update(expected) { current ->
        require(current.phase != FamilyMigrationPhase.VERIFIED)
        if (rebuild || current.phase == FamilyMigrationPhase.PREPARED)
            FamilyMigrationRecord(current.wardrobeId, current.remoteId, current.ownerUid, newId())
        else current.copy(archiveId = newId(), phase = FamilyMigrationPhase.STAGED)
    }
    private fun update(expected: FamilyMigrationRecord, change: (FamilyMigrationRecord) -> FamilyMigrationRecord): FamilyMigrationRecord = synchronized(lock) {
        val rows = read(); val current = requireNotNull(rows.find { it.wardrobeId == expected.wardrobeId })
        // A late callback may repeat a completed transition, but cannot refer to another upload.
        require(current.remoteId == expected.remoteId && current.archiveId == expected.archiveId && current.ownerUid == expected.ownerUid)
        if (expected.phase != FamilyMigrationPhase.PREPARED) require(current.sourceRevision == expected.sourceRevision && current.fingerprint == expected.fingerprint && current.archiveSize == expected.archiveSize)
        val next = change(current)
        if (next != current) write(rows.map { if (it.wardrobeId == current.wardrobeId) next else it })
        next
    }
    private fun read(): List<FamilyMigrationRecord> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val bytes = file.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            PhotoStore.copyLimited(input, output, MAX_BYTES.toLong()); output.toByteArray()
        }
        require(bytes.size <= MAX_BYTES)
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.getInt("version") == 1 && json.getString("account") == account.key)
        val rows = json.getJSONArray("records"); require(rows.length() <= 1000)
        return (0 until rows.length()).map { index -> rows.getJSONObject(index).let { r ->
            FamilyMigrationRecord(r.getString("wardrobeId"), r.getString("remoteId"), r.getString("ownerUid"), r.getString("archiveId"),
                FamilyMigrationPhase.valueOf(r.getString("phase")), r.getLong("sourceRevision"), r.getString("fingerprint"), r.getLong("remoteRevision"), r.optLong("archiveSize", 0))
        } }.also { values ->
            require(values.all { it.ownerUid == account.uid })
            require(values.map { it.wardrobeId }.distinct().size == values.size && values.map { it.remoteId }.distinct().size == values.size &&
                values.map { it.archiveId }.distinct().size == values.size)
        }
    }
    private fun write(values: List<FamilyMigrationRecord>) {
        require(values.size <= 1000)
        val rows = JSONArray()
        values.forEach { r -> rows.put(JSONObject().put("wardrobeId", r.wardrobeId).put("remoteId", r.remoteId).put("ownerUid", r.ownerUid)
            .put("archiveId", r.archiveId).put("phase", r.phase.name).put("sourceRevision", r.sourceRevision)
            .put("fingerprint", r.fingerprint).put("remoteRevision", r.remoteRevision).put("archiveSize", r.archiveSize)) }
        val bytes = JSONObject().put("version", 1).put("account", account.key).put("records", rows).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        val stream = file.startWrite()
        try { stream.write(bytes); stream.fd.sync(); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    companion object {
        private const val MAX_BYTES = 1024 * 1024
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
