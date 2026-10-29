package com.familywardrobe.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Delta model v1 (server feature [FEATURE], cloud/family-functions/family/applying.py).
 *
 * An addition or a photo replacement is sent as a one-photo ZIP plus a small change JSON. The server applies it
 * to whatever shared head is current, so the draft never goes stale: no full archive, no client base revision,
 * no rebase after another member's commit. The JSON rows use exactly the backup/manifest encoding.
 * Drafts of a server without the feature keep the full-archive path ([publishFamilyAddition]).
 */
internal object FamilyChange {
    const val FEATURE = "change1"
    const val ARCHIVE_MISSING = "ARCHIVE_MISSING"
    const val ITEM_MISSING = "CHANGE_ITEM_MISSING"
    const val LABEL_CONFLICT = "CHANGE_LABEL_CONFLICT"
    const val BUSY = "CHANGE_BUSY"
    /** Server limits: MAX_CHANGE_JSON (code points; a Kotlin length is never smaller) and MAX_CHANGE_ARCHIVE. */
    const val MAX_TEXT = 64 * 1024
    const val MAX_ARCHIVE = 12L * 1024 * 1024 + 64 * 1024
    private val HASH = Regex("[0-9a-f]{64}")
    private val EMPTY = listOf("outfits", "ideas", "parts", "wears", "wishes", "plans", "feedback", "capsules",
        "capsuleItems", "capsuleOutfits", "purchaseUndos", "wishEvents")

    fun supported(remote: FamilyRemote) = FEATURE in remote.features

    /** [state] is Backup.encode of the one-item selection (FamilyAddition.select / FamilyNewItem.selection). */
    fun addition(state: JSONObject): String {
        val garments = state.getJSONArray("garments"); require(garments.length() == 1)
        val garment = garments.getJSONObject(0); val photoId = garment.getString("photoId")
        val files = state.getJSONObject("photoFiles")
        require(files.length() == 1 && FamilyRules.validId(photoId))
        val ext = files.getString(photoId).also { require(it in setOf("jpg", "png")) }
        fun label(key: String, reference: String): Any {
            val rows = state.getJSONArray(key); require(rows.length() <= 1)
            val row = if (rows.length() == 0) null else rows.getJSONObject(0)
            require(row?.getString("id") == if (garment.isNull(reference)) null else garment.getString(reference))
            return row ?: JSONObject.NULL
        }
        return JSONObject().put("kind", "add").put("garment", garment)
            .put("customCategory", label("customCategories", "customCategoryId"))
            .put("customColor", label("customColors", "customColorId"))
            .put("photo", JSONObject().put("id", photoId).put("ext", ext)).toString().also { require(it.length <= MAX_TEXT) }
    }

    fun photo(garmentId: String, fingerprint: String, photoId: String, ext: String): String {
        require(FamilyRules.validId(garmentId) && FamilyRules.validId(photoId) && ext in setOf("jpg", "png"))
        require(fingerprint.isEmpty() || HASH.matches(fingerprint))
        return JSONObject().put("kind", "photo").put("garmentId", garmentId).put("fingerprint", fingerprint)
            .put("photo", JSONObject().put("id", photoId).put("ext", ext)).toString()
    }

    /** Kind, item and photo the stored text describes; a draft must agree with its own change. */
    fun check(draft: FamilyAddDraft, kind: String) {
        val text = requireNotNull(draft.change); val change = JSONObject(text)
        require(change.getString("kind") == kind)
        val photo = change.getJSONObject("photo")
        require(photo.getString("id") == draft.photoId && photo.getString("ext") in setOf("jpg", "png"))
        val garment = if (kind == "add") change.getJSONObject("garment").getString("id") else change.getString("garmentId")
        require(garment == draft.garmentId)
    }

    /** A backup-format snapshot with the one new item, for Backup.decode; the wardrobe row is a placeholder. */
    fun preview(text: String, wardrobeId: String): JSONObject {
        val change = JSONObject(text); require(change.getString("kind") == "add")
        val photo = change.getJSONObject("photo")
        fun rows(key: String) = JSONArray().apply { if (!change.isNull(key)) put(change.getJSONObject(key)) }
        val state = JSONObject().put("format", "family-wardrobe").put("version", 10)
            .put("photoFiles", JSONObject().put(photo.getString("id"), photo.getString("ext")))
            .put("wardrobes", JSONArray().put(JSONObject().put("id", wardrobeId).put("name", "Shared").put("createdAt", 0)
                .put("usesDefaultName", false).put("noRepeatDays", 0)))
            .put("garments", JSONArray().put(change.getJSONObject("garment")))
            .put("customCategories", rows("customCategory")).put("customColors", rows("customColor"))
        EMPTY.forEach { state.put(it, JSONArray()) }
        return state
    }

    fun beginRequest(draft: FamilyAddDraft): Map<String, Any> {
        requireNotNull(draft.change); val p = draft.publication
        return mapOf("remoteId" to p.identity.remoteId, "changeId" to p.archiveId, "sha256" to p.hash, "size" to p.size)
    }
    fun applyRequest(draft: FamilyAddDraft): Map<String, Any> =
        mapOf("remoteId" to draft.remoteId, "changeId" to draft.publication.archiveId, "change" to requireNotNull(draft.change))

    /** Unlike a full archive, the ticket's baseRevision is just the current head (it lets Storage rules accept the upload). */
    fun ticket(value: Map<*, *>, draft: FamilyAddDraft, actor: String): FamilyTicket {
        val p = draft.publication
        val path = "familyWardrobes/${p.identity.remoteId}/staging/$actor/${p.archiveId}.zip"
        require(value["archiveId"] == p.archiveId && value["sha256"] == p.hash && value["authorUid"] == actor && value["stagingPath"] == path)
        require(number(value["size"]) == p.size && number(value["expiresAt"]) > 0 && number(value["baseRevision"]) >= 1)
        require(value["status"] in setOf("pending", "committed"))
        return FamilyTicket(path, value["status"] == "committed")
    }
    /** The committed revision; a replay returns the original receipt even after later commits. */
    fun receipt(value: Map<*, *>, draft: FamilyAddDraft): Long {
        require(value["status"] == "committed" && value["remoteId"] == draft.remoteId && value["changeId"] == draft.publication.archiveId)
        require(FamilyRules.validId(value["archiveId"] as? String ?: ""))
        return number(value["revision"]).also { require(it >= 1) }
    }
    private fun number(value: Any?): Long {
        val n = value as? Number ?: error("Invalid number"); val v = n.toLong()
        require(n.toDouble().isFinite() && n.toDouble() == v.toDouble() && v in 0..9_007_199_254_740_991L)
        return v
    }

    /**
     * A rejection that retrying cannot fix; only then may a submitted change be discarded. An expired upload
     * or the daily limit is not one: the next begin_change renews the reservation.
     */
    fun definite(e: FamilyTransferException) = e.problem in setOf(FamilyProblem.CONFLICT, FamilyProblem.ARCHIVE) ||
        e.problem == FamilyProblem.MISSING && e.reason == ITEM_MISSING
}

internal interface FamilyChangeTransport {
    suspend fun beginChange(draft: FamilyAddDraft): FamilyTicket
    suspend fun applyChange(draft: FamilyAddDraft): Long
    suspend fun upload(file: File, path: String, progress: (Long, Long) -> Unit)
}

/**
 * Same receipt-first order as [publishFamilyAddition]: apply is tried before the local ZIP is opened, so a lost
 * reply or a process death after the upload never uploads twice. Returns the committed revision.
 */
internal suspend fun publishFamilyChange(draft: FamilyAddDraft, backend: FamilyChangeTransport,
    checkedFile: () -> File, checkpoint: () -> Unit): Long {
    requireNotNull(draft.change)
    val ticket = backend.beginChange(draft); checkpoint()
    val revision = try { backend.applyChange(draft) }
    catch (e: FamilyTransferException) {
        if (ticket.committed || e.reason != FamilyChange.ARCHIVE_MISSING) throw e
        checkpoint(); backend.upload(checkedFile(), ticket.path) { _, _ -> checkpoint() }
        checkpoint(); backend.applyChange(draft)
    }
    checkpoint(); return revision
}
