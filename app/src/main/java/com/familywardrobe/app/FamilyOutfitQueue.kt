package com.familywardrobe.app

import org.json.JSONArray
import org.json.JSONObject

internal data class FamilyOutfitJournal(val form: FamilyOutfitForm? = null, val drafts: List<FamilyOutfitDraft> = emptyList())

/** One autosaved editor plus FIFO publications, promoted in a single atomic journal write. */
internal object FamilyOutfitQueue {
    const val LIMIT = 10
    const val MAX_BYTES = 512L * 1024 * 1024
    const val MAX_JOURNAL = 512 * 1024
    // LookPiece display details are resolved from the shared snapshot and are not journal fields.
    private fun canonical(d: FamilyOutfitDraft?) = d?.copy(change = d.change.copy(pieces = d.change.pieces.map {
        LookPiece(it.key, it.category, it.name)
    }))
    fun same(a: FamilyOutfitDraft?, b: FamilyOutfitDraft?) = canonical(a) == canonical(b)
    fun validate(state: FamilyOutfitJournal, remote: String): FamilyOutfitJournal = state.also {
        require(FamilyRules.validId(remote))
        val rows = it.drafts
        require(rows.size <= LIMIT && rows.sumOf { d -> d.publication.size } <= MAX_BYTES)
        require(rows.all { d -> d.publication.identity.remoteId == remote && FamilyRules.validId(d.change.id) })
        require(rows.map { d -> d.publication.identity }.distinct().size <= 1)
        require(rows.map { d -> d.change.id }.distinct().size == rows.size)
        require(rows.map { d -> d.publication.archiveId }.distinct().size == rows.size)
        it.form?.let { f ->
            require(f.remote == remote && f.base > 0 && (f.outfit.isEmpty() || FamilyRules.validId(f.outfit)))
            require(f.payload.length <= 40_000)
            if (f.payload.isNotEmpty()) JSONObject(f.payload)
            require(f.outfit.isEmpty() || rows.none { d -> d.change.id == f.outfit })
        }
    }
    fun put(state: FamilyOutfitJournal, next: FamilyOutfitDraft, expected: FamilyOutfitDraft?, preparedForm: FamilyOutfitForm? = null): FamilyOutfitJournal {
        val remote = next.publication.identity.remoteId; validate(state, remote)
        val index = state.drafts.indexOfFirst { it.change.id == next.change.id }
        require(same(state.drafts.getOrNull(index), expected)) { "Draft changed" }
        require(expected == null || expected.change.id == next.change.id)
        require(preparedForm == null || state.form == preparedForm) { "Form changed" }
        if (preparedForm != null) require(expected == null && preparedForm.remote == remote &&
            (!next.change.creating || preparedForm.outfit.isEmpty()))
        val rows = if (index < 0) state.drafts + next else state.drafts.toMutableList().also { it[index] = next }
        if (rows.size > LIMIT || rows.sumOf { it.publication.size } > MAX_BYTES) throw FamilyTransferException(FamilyProblem.LIMIT)
        return validate(FamilyOutfitJournal(if (preparedForm == null) state.form else null, rows), remote)
    }
    fun remove(state: FamilyOutfitJournal, expected: FamilyOutfitDraft): FamilyOutfitJournal {
        validate(state, expected.publication.identity.remoteId)
        require(same(state.drafts.find { it.change.id == expected.change.id }, expected)) { "Draft changed" }
        return state.copy(drafts = state.drafts.filterNot { it.change.id == expected.change.id })
    }
    fun requireHead(rows: List<FamilyOutfitDraft>, expected: FamilyOutfitDraft) {
        validate(FamilyOutfitJournal(drafts = rows), expected.publication.identity.remoteId)
        require(same(rows.firstOrNull(), expected)) { "Queue changed" }
    }
    fun checkEnvelope(j: JSONObject, version: Int, account: String, remote: String) {
        require(j.getInt("version") == version && j.getString("account") == account && j.getString("remote") == remote)
    }
    fun readForm(j: JSONObject, remote: String) = FamilyOutfitForm(remote, j.getString("outfit"), j.getLong("base"), j.getString("payload"))
    fun readDraft(j: JSONObject, remote: String): FamilyOutfitDraft {
        val p = FamilyPublication(FamilyArchiveIdentity(remote, j.getString("wardrobe"), j.getString("owner")),
            j.getString("archive"), j.getLong("base"), j.getString("hash"), j.getLong("size"))
        val c = j.getJSONObject("change"); val pieces = c.getJSONArray("pieces")
        return FamilyOutfitDraft(p, FamilyOutfitChange(c.getString("id"), c.getBoolean("creating"), c.getString("name"),
            (0 until pieces.length()).map { pieces.getJSONObject(it).let { piece ->
                LookPiece(piece.getString("key"), piece.getString("category"), piece.getString("name"))
            } }, c.getString("occasion"), c.getString("weather"), c.getString("source"), c.getString("layout"),
            c.getString("background"), c.getLong("createdAt")), j.getBoolean("submitted"))
    }
    private fun row(d: FamilyOutfitDraft): JSONObject {
        val p = d.publication; val c = d.change
        val change = JSONObject().put("id", c.id).put("creating", c.creating).put("name", c.name)
            .put("occasion", c.occasion).put("weather", c.weather).put("source", c.source).put("layout", c.layout)
            .put("background", c.background).put("createdAt", c.createdAt).put("pieces", JSONArray().apply {
                c.pieces.forEach { put(JSONObject().put("key", it.key).put("category", it.category).put("name", it.name)) }
            })
        return JSONObject().put("wardrobe", p.identity.wardrobeId).put("owner", p.identity.ownerUid)
            .put("archive", p.archiveId).put("base", p.baseRevision).put("hash", p.hash).put("size", p.size)
            .put("submitted", d.submitted).put("change", change)
    }
    fun encode(state: FamilyOutfitJournal, account: String, remote: String): JSONObject {
        validate(state, remote)
        return JSONObject().put("version", 2).put("account", account).put("remote", remote)
            .put("form", state.form?.let { JSONObject().put("outfit", it.outfit).put("base", it.base).put("payload", it.payload) } ?: JSONObject.NULL)
            .put("drafts", JSONArray().apply { state.drafts.forEach { put(row(it)) } })
    }
    fun decode(j: JSONObject, account: String, remote: String): FamilyOutfitJournal {
        checkEnvelope(j, 2, account, remote); require(j.has("form"))
        val rows = j.getJSONArray("drafts"); require(rows.length() <= LIMIT)
        return validate(FamilyOutfitJournal(if (j.isNull("form")) null else readForm(j.getJSONObject("form"), remote),
            (0 until rows.length()).map { readDraft(rows.getJSONObject(it), remote) }), remote)
    }
}
