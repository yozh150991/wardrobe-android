package com.familywardrobe.app

import org.json.JSONObject

/** Dedicated wire boundary. The expected identity comes from a server response, never from this file. */
internal class FamilyEnvelope(private val backup: Backup) {
    fun encode(source: Snapshot, identity: FamilyArchiveIdentity, baseRevision: Long): String {
        require(baseRevision >= 0)
        // PERS-02: reasons for turning down outfits are the publisher's own; members never inherit them.
        val state = WardrobeSnapshots.select(source, identity.wardrobeId).copy(feedback = emptyList())
        return JSONObject().put("format", FORMAT).put("version", FamilyRules.WIRE_VERSION)
            .put("remoteId", identity.remoteId).put("wardrobeId", identity.wardrobeId).put("ownerUid", identity.ownerUid)
            .put("baseRevision", baseRevision).put("snapshot", backup.encode(state)).toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST) }
    }

    fun decode(text: String, expected: FamilyArchiveIdentity, baseRevision: Long): Snapshot {
        require(baseRevision >= 0 && text.length <= MAX_MANIFEST && text.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST)
        val json = JSONObject(text)
        require(json.keys().asSequence().toSet() == setOf("format", "version", "remoteId", "wardrobeId", "ownerUid", "baseRevision", "snapshot"))
        require(json.getString("format") == FORMAT && integer(json, "version") == FamilyRules.WIRE_VERSION.toLong())
        require(json.getString("remoteId") == expected.remoteId && json.getString("wardrobeId") == expected.wardrobeId &&
            json.getString("ownerUid") == expected.ownerUid && integer(json, "baseRevision") == baseRevision)
        val payload = json.getJSONObject("snapshot")
        require(integer(payload, "version") == FamilyRules.CONTENT_VERSION.toLong())
        val state = backup.decode(payload)
        require(state.wardrobes.size == 1 && state.wardrobes.single().id == expected.wardrobeId)
        val isolated = WardrobeSnapshots.select(state, expected.wardrobeId)
        val photos = payload.getJSONObject("photoFiles")
        require(photos.keys().asSequence().toSet() == isolated.photoIds())
        require(isolated.photoIds().all { photos.getString(it) in setOf("jpg", "png") })
        return isolated
    }
    private fun integer(json: JSONObject, key: String): Long {
        val value = json.get(key)
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }
    companion object {
        const val FORMAT = "family-wardrobe-scoped"
        const val MAX_MANIFEST = 8 * 1024 * 1024
    }
}
