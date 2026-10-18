package com.familywardrobe.app

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

internal data class PhotoReprocessEntry(val itemId: String, val wardrobeId: String, val sourceId: String,
    val candidateId: String = "", val status: String = "pending", val cutout: CutoutStatus = CutoutStatus.FAILED,
    val failure: PhotoFailureKind = PhotoFailureKind.NONE)
internal data class PhotoReprocessState(val token: String = "", val entries: List<PhotoReprocessEntry> = emptyList(),
    val saved: Int = 0, val skipped: Int = 0, val unreadable: Boolean = false, val retiredIds: Set<String> = emptySet()) {
    fun photos() = entries.flatMap { listOf(it.sourceId, it.candidateId) }.filter { it.isNotBlank() }.toSet()
}

/** Account-local unfinished work, excluded from final-photo backup/cloud snapshots. */
internal class PhotoReprocessStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "photo-reprocess.json"))
    val state = MutableStateFlow(read())
    private fun read(): PhotoReprocessState = try {
        val value = file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        require(value.getInt("version") == 1)
        val token = value.getString("token")
        require(token.isEmpty() || PhotoStore.ID.matches(token))
        val rows = value.getJSONArray("entries")
        require(rows.length() <= 500)
        val entries = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            PhotoReprocessEntry(row.getString("item"), row.getString("wardrobe"), row.getString("source"),
                row.getString("candidate"), row.getString("status"), CutoutStatus.valueOf(row.getString("cutout")),
                PhotoFailureKind.valueOf(row.getString("failure"))).also {
                require(listOf(it.itemId, it.wardrobeId, it.sourceId).all(PhotoStore.ID::matches))
                require(it.candidateId.isEmpty() || PhotoStore.ID.matches(it.candidateId))
                require(it.status in setOf("pending", "review", "failed", "conflict"))
                require(it.status != "review" || it.candidateId.isNotEmpty())
            }
        }
        require(entries.map { it.itemId }.distinct().size == entries.size)
        require(entries.drop(1).all { it.candidateId.isEmpty() && it.status == "pending" })
        val retired = value.optJSONArray("retired") ?: JSONArray()
        require(retired.length() <= 10_000)
        val retiredIds = (0 until retired.length()).map { retired.getString(it).also { id -> require(PhotoStore.ID.matches(id)) } }.toSet()
        PhotoReprocessState(token, entries, value.getInt("saved"), value.getInt("skipped"), retiredIds = retiredIds).also {
            require(entries.isEmpty() || token.isNotBlank())
            require(it.saved in 0..500 && it.skipped in 0..500 && it.saved + it.skipped + entries.size <= 500)
        }
    } catch (_: java.io.FileNotFoundException) { PhotoReprocessState() }
    catch (_: Exception) { PhotoReprocessState(unreadable = true) }

    // Call under Repository.writes. Publish only after the atomic file commits successfully.
    fun write(next: PhotoReprocessState) {
        require(next.retiredIds.size <= 10_000)
        val rows = JSONArray()
        next.entries.forEach { rows.put(JSONObject().put("item", it.itemId).put("wardrobe", it.wardrobeId)
            .put("source", it.sourceId).put("candidate", it.candidateId).put("status", it.status)
            .put("cutout", it.cutout.name).put("failure", it.failure.name)) }
        val bytes = JSONObject().put("version", 1).put("token", next.token).put("entries", rows)
            .put("saved", next.saved).put("skipped", next.skipped).put("retired", JSONArray(next.retiredIds.toList()))
            .toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); stream.fd.sync(); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw IOException("Photo queue could not be saved", e) }
        state.value = next
    }
}
