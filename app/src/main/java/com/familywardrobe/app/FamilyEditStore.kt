package com.familywardrobe.app

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** v1 is read without rewriting; the next successful mutation atomically writes v2. */
internal object FamilyEditJournal {
    fun decode(text: String, account: String): List<FamilyEditDraft> {
        val json = JSONObject(text); val version = json.getInt("version")
        require(version in 1..2 && json.getString("account") == account)
        val rows = json.getJSONArray("drafts"); require(rows.length() <= FamilyEditQueue.LIMIT)
        return FamilyEditQueue.validate((0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            fun fields(key: String): Map<String, String> {
                val obj = row.getJSONObject(key); require(obj.keys().asSequence().toSet() == FAMILY_EDIT_LIMITS.keys)
                return FAMILY_EDIT_LIMITS.keys.associateWith { obj.getString(it) }
            }
            FamilyEditDraft(row.getString("remoteId"), row.getString("garmentId"), row.getString("editId"), row.getLong("baseRevision"),
                fields("original"), fields("values"), row.getBoolean("submitted"))
        }).also { if (version == 1) require(it.map { d -> d.remoteId }.distinct().size == it.size) }
    }
    fun encode(rows: List<FamilyEditDraft>, account: String): String {
        FamilyEditQueue.validate(rows)
        val array = JSONArray()
        rows.forEach { d -> array.put(JSONObject().put("remoteId", d.remoteId).put("garmentId", d.garmentId).put("editId", d.editId)
            .put("baseRevision", d.baseRevision).put("original", JSONObject(d.original)).put("values", JSONObject(d.values)).put("submitted", d.submitted)) }
        return JSONObject().put("version", 2).put("account", account).put("drafts", array).toString()
    }
}

/** Durable metadata-only FIFO. No photos, private Room or portable backup entries. */
internal class FamilyEditStore(context: AccountContext, private val account: AuthIdentity) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "family-edits/${account.key}/outbox.json"))
    private val lock = locks.computeIfAbsent(file.baseFile.canonicalPath) { Any() }
    companion object { private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>() }
    init { require(context.location.key == account.key) }
    fun list(remote: String): List<FamilyEditDraft> = synchronized(lock) { read().filter { it.remoteId == remote } }
    fun get(remote: String, garment: String? = null): FamilyEditDraft? = synchronized(lock) {
        read().find { it.remoteId == remote && (garment == null || it.garmentId == garment) }
    }
    fun put(draft: FamilyEditDraft, expected: FamilyEditDraft? = null) = synchronized(lock) {
        write(FamilyEditQueue.put(read(), draft, expected))
    }.also { FamilySyncLocks.touched(account.key) }
    fun remove(expected: FamilyEditDraft) = synchronized(lock) { write(FamilyEditQueue.remove(read(), expected)) }
        .also { FamilySyncLocks.touched(account.key) }
    private fun read(): List<FamilyEditDraft> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val bytes = file.openRead().use { input -> java.io.ByteArrayOutputStream().also { PhotoStore.copyLimited(input, it, 1024L * 1024) }.toByteArray() }
        return FamilyEditJournal.decode(bytes.toString(Charsets.UTF_8), account.key)
    }
    private fun write(rows: List<FamilyEditDraft>) {
        val bytes = FamilyEditJournal.encode(rows, account.key).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 1024 * 1024); check(file.baseFile.parentFile!!.mkdirs() || file.baseFile.parentFile!!.isDirectory)
        val output = file.startWrite()
        try { output.write(bytes); output.fd.sync(); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
}
