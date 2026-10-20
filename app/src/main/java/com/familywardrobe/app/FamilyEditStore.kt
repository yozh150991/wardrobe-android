package com.familywardrobe.app

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

internal val FAMILY_EDIT_LIMITS = linkedMapOf("name" to 120, "size" to 40, "location" to 200, "description" to 1000)
internal fun Garment.familyFields() = linkedMapOf("name" to name, "size" to size, "location" to location, "description" to description)
internal data class FamilyEditDraft(val remoteId: String, val garmentId: String, val editId: String, val baseRevision: Long,
    val original: Map<String, String>, val values: Map<String, String>, val submitted: Boolean = false) {
    init {
        require(FamilyRules.validId(remoteId) && FamilyRules.validId(garmentId) && FamilyRules.validId(editId) && baseRevision > 0)
        require(original.keys == FAMILY_EDIT_LIMITS.keys && values.keys == FAMILY_EDIT_LIMITS.keys)
        for (fields in listOf(original, values)) for ((key, value) in fields) require(value.length <= FAMILY_EDIT_LIMITS.getValue(key))
    }
    val changes get() = values.filter { (key, value) -> original[key] != value }
}

/** Durable metadata-only outbox. No photos, private Room or portable backup entries. */
internal class FamilyEditStore(context: AccountContext, private val account: AuthIdentity) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "family-edits/${account.key}/outbox.json"))
    private val lock = locks.computeIfAbsent(file.baseFile.canonicalPath) { Any() }
    companion object { private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>() }
    init { require(context.location.key == account.key) }
    fun get(remote: String): FamilyEditDraft? = synchronized(lock) { read().find { it.remoteId == remote } }
    fun put(draft: FamilyEditDraft, expected: FamilyEditDraft? = null) = synchronized(lock) {
        val rows = read(); val current = rows.find { it.remoteId == draft.remoteId }
        require(current == expected || current == draft) { "Draft changed" }
        write(rows.filterNot { it.remoteId == draft.remoteId } + draft)
    }
    fun remove(expected: FamilyEditDraft) = synchronized(lock) {
        val rows = read(); require(rows.find { it.remoteId == expected.remoteId } == expected)
        write(rows.filterNot { it.remoteId == expected.remoteId })
    }
    private fun read(): List<FamilyEditDraft> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val bytes = file.openRead().use { input -> java.io.ByteArrayOutputStream().also { PhotoStore.copyLimited(input, it, 1024L * 1024) }.toByteArray() }
        val json = JSONObject(bytes.toString(Charsets.UTF_8)); require(json.getInt("version") == 1 && json.getString("account") == account.key)
        val rows = json.getJSONArray("drafts"); require(rows.length() <= 100)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            fun fields(key: String): Map<String, String> {
                val obj = row.getJSONObject(key); require(obj.keys().asSequence().toSet() == FAMILY_EDIT_LIMITS.keys)
                return FAMILY_EDIT_LIMITS.keys.associateWith { obj.getString(it) }
            }
            FamilyEditDraft(row.getString("remoteId"), row.getString("garmentId"), row.getString("editId"), row.getLong("baseRevision"),
                fields("original"), fields("values"), row.getBoolean("submitted"))
        }.also { require(it.map { d -> d.remoteId }.distinct().size == it.size) }
    }
    private fun write(rows: List<FamilyEditDraft>) {
        require(rows.size <= 100)
        val array = org.json.JSONArray()
        rows.forEach { d -> array.put(JSONObject().put("remoteId",d.remoteId).put("garmentId",d.garmentId).put("editId",d.editId)
            .put("baseRevision",d.baseRevision).put("original",JSONObject(d.original)).put("values",JSONObject(d.values)).put("submitted",d.submitted)) }
        val bytes = JSONObject().put("version",1).put("account",account.key).put("drafts",array).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 1024 * 1024); check(file.baseFile.parentFile!!.mkdirs() || file.baseFile.parentFile!!.isDirectory)
        val output = file.startWrite()
        try { output.write(bytes); output.fd.sync(); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
}
