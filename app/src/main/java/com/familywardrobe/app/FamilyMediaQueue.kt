package com.familywardrobe.app

import org.json.JSONArray
import org.json.JSONObject

/** Separate FIFO queues for additions and photo replacements. Archives are never merged implicitly. */
internal object FamilyMediaQueue {
    const val LIMIT = 10
    const val MAX_BYTES = 512L * 1024 * 1024
    /** Ten change JSONs of up to a few KiB each no longer fit the old 64 KiB journal. */
    const val MAX_JOURNAL = 1024 * 1024
    fun validate(rows: List<FamilyAddDraft>): List<FamilyAddDraft> = rows.also {
        require(it.size <= LIMIT && it.sumOf { d -> d.publication.size } <= MAX_BYTES)
        require(it.map { d -> d.publication.identity }.distinct().size <= 1)
        require(it.map { d -> d.garmentId }.distinct().size == it.size)
        require(it.map { d -> d.publication.archiveId }.distinct().size == it.size)
    }
    fun put(rows: List<FamilyAddDraft>, next: FamilyAddDraft, expected: FamilyAddDraft?): List<FamilyAddDraft> {
        validate(rows)
        val index = rows.indexOfFirst { it.garmentId == next.garmentId }
        require(rows.getOrNull(index) == expected || rows.getOrNull(index) == next) { "Draft changed" }
        val updated = if (index < 0) rows + next else rows.toMutableList().also { it[index] = next }
        if (updated.size > LIMIT || updated.sumOf { it.publication.size } > MAX_BYTES) throw FamilyTransferException(FamilyProblem.LIMIT)
        return validate(updated)
    }
    fun remove(rows: List<FamilyAddDraft>, expected: FamilyAddDraft): List<FamilyAddDraft> {
        validate(rows); require(rows.find { it.garmentId == expected.garmentId } == expected) { "Draft changed" }
        return rows.filterNot { it.garmentId == expected.garmentId }
    }
    fun requireHead(rows: List<FamilyAddDraft>, expected: FamilyAddDraft) {
        validate(rows); require(rows.firstOrNull() == expected) { "Queue changed" }
    }
    private fun row(d: FamilyAddDraft): JSONObject {
        val p = d.publication
        return JSONObject().put("wardrobeId", p.identity.wardrobeId).put("ownerUid", p.identity.ownerUid)
            .put("archiveId", p.archiveId).put("baseRevision", p.baseRevision).put("sha256", p.hash).put("size", p.size)
            .put("garmentId", d.garmentId).put("photoId", d.photoId).put("name", d.name).put("submitted", d.submitted)
            .put("change", d.change ?: JSONObject.NULL)
    }
    fun encode(rows: List<FamilyAddDraft>, account: String, remote: String): String {
        validate(rows); require(rows.all { it.remoteId == remote })
        return JSONObject().put("version", 3).put("account", account).put("remoteId", remote)
            .put("drafts", JSONArray().apply { rows.forEach { put(row(it)) } }).toString()
    }
    fun decode(text: String, account: String, remote: String): List<FamilyAddDraft> {
        val j = JSONObject(text); val version = j.getInt("version")
        require(version in 1..3 && j.getString("account") == account && j.getString("remoteId") == remote)
        val array = if (version == 1) JSONArray().put(j) else j.getJSONArray("drafts")
        require(array.length() <= LIMIT)
        return validate((0 until array.length()).map { index ->
            val d = array.getJSONObject(index)
            FamilyAddDraft(FamilyPublication(FamilyArchiveIdentity(remote, d.getString("wardrobeId"), d.getString("ownerUid")),
                d.getString("archiveId"), d.getLong("baseRevision"), d.getString("sha256"), d.getLong("size")),
                d.getString("garmentId"), d.getString("photoId"), d.getString("name"), d.getBoolean("submitted"),
                // v3: the delta change text, or null for a full-archive draft. Older journals have no change.
                if (version < 3 || d.isNull("change")) null else d.getString("change"))
        })
    }
}
