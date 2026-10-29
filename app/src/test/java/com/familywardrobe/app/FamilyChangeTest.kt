package com.familywardrobe.app

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Delta model v1 client side: change JSON, wire validation and the receipt-first send order. */
class FamilyChangeTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    private val identity = FamilyArchiveIdentity(id(1), id(2), "owner")
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    // Unit tests compile against Android's org.json API, which has neither keySet() nor similar().
    private fun JSONObject.fieldNames(): Set<String> = keys().asSequence().toSet()
    private fun JSONObject.map(): Map<String, Any?> = fieldNames().associateWith { key -> get(key).let { if (it is JSONObject) it.map() else it } }
    private fun garment(category: String? = null, color: String? = null) = JSONObject().put("id", id(10)).put("wardrobeId", id(2))
        .put("name", "Scarf").put("category", "accessories").put("color", "red").put("size", "").put("location", "")
        .put("description", "").put("photoId", id(11)).put("season", "all").put("style", "casual").put("availability", "available")
        .put("favorite", false).put("rarelyWorn", false).put("archived", false).put("createdAt", 5).put("priceMinor", JSONObject.NULL)
        .put("currency", "PLN").put("subcategory", "").put("suggestionExcluded", false).put("photoFingerprint", "")
        .put("customCategoryId", category ?: JSONObject.NULL).put("customColorId", color ?: JSONObject.NULL)
    /** What Backup.encode produces for a one-item selection. */
    private fun encoded(garment: JSONObject = garment(), categories: JSONArray = JSONArray(), colors: JSONArray = JSONArray()) =
        JSONObject().put("format", "family-wardrobe").put("version", 10).put("photoFiles", JSONObject().put(id(11), "jpg"))
            .put("wardrobes", JSONArray()).put("garments", JSONArray().put(garment))
            .put("customCategories", categories).put("customColors", colors)
    private fun label(n: Int) = JSONObject().put("id", id(n)).put("wardrobeId", id(2)).put("name", "Knits").put("nameKey", "knits").put("baseCategory", "accessories")
    private fun draft(change: String?, garmentId: String = id(10), photoId: String = id(11)) =
        FamilyAddDraft(FamilyPublication(identity, id(20), 4, "a".repeat(64), 1000), garmentId, photoId, "Scarf", change = change)

    @Test fun additionCarriesExactlyTheBackupRowsAndTheOnePhoto() {
        val text = FamilyChange.addition(encoded(garment(category = id(30)), JSONArray().put(label(30))))
        val change = JSONObject(text)
        assertEquals(setOf("kind", "garment", "customCategory", "customColor", "photo"), change.fieldNames())
        assertEquals("add", change.getString("kind"))
        assertEquals(garment(category = id(30)).map(), change.getJSONObject("garment").map())
        assertEquals(label(30).map(), change.getJSONObject("customCategory").map())
        assertTrue(change.isNull("customColor"))
        assertEquals(id(11), change.getJSONObject("photo").getString("id")); assertEquals("jpg", change.getJSONObject("photo").getString("ext"))
        FamilyChange.check(draft(text), "add")
        rejects { FamilyChange.check(draft(text), "photo") }
        rejects { FamilyChange.check(draft(text, garmentId = id(99)), "add") }
        rejects { FamilyChange.check(draft(text, photoId = id(99)), "add") }
    }
    @Test fun additionRejectsLabelsThatTheItemDoesNotReference() {
        rejects { FamilyChange.addition(encoded(garment(), JSONArray().put(label(30)))) }
        rejects { FamilyChange.addition(encoded(garment(category = id(30)))) }
        rejects { FamilyChange.addition(encoded(garment(category = id(31)), JSONArray().put(label(30)))) }
        rejects { FamilyChange.addition(encoded().put("garments", JSONArray().put(garment()).put(garment()))) }
        rejects { FamilyChange.addition(encoded().put("photoFiles", JSONObject().put(id(11), "gif"))) }
    }
    @Test fun photoChangeIsSmallAndStrict() {
        val text = FamilyChange.photo(id(10), "b".repeat(64), id(11), "png")
        val change = JSONObject(text)
        assertEquals(setOf("kind", "garmentId", "fingerprint", "photo"), change.fieldNames())
        FamilyChange.check(draft(text), "photo")
        FamilyChange.photo(id(10), "", id(11), "jpg")
        rejects { FamilyChange.photo(id(10), "B".repeat(64), id(11), "png") }
        rejects { FamilyChange.photo(id(10), "", id(11), "gif") }
        rejects { FamilyChange.photo("x", "", id(11), "png") }
    }
    @Test fun previewIsABackupSnapshotWithOnlyTheNewItem() {
        val text = FamilyChange.addition(encoded(garment(category = id(30)), JSONArray().put(label(30))))
        val state = FamilyChange.preview(text, id(2))
        assertEquals(id(2), state.getJSONArray("wardrobes").getJSONObject(0).getString("id"))
        assertEquals(1, state.getJSONArray("garments").length()); assertEquals(1, state.getJSONArray("customCategories").length())
        assertEquals(0, state.getJSONArray("customColors").length()); assertEquals(0, state.getJSONArray("wishEvents").length())
        assertEquals("jpg", state.getJSONObject("photoFiles").getString(id(11)))
        rejects { FamilyChange.preview(FamilyChange.photo(id(10), "", id(11), "png"), id(2)) }
    }
    @Test fun requestsAndTicketAndReceiptAreBoundToTheDraft() {
        val d = draft(FamilyChange.photo(id(10), "", id(11), "png"))
        assertEquals(mapOf("remoteId" to id(1), "changeId" to id(20), "sha256" to "a".repeat(64), "size" to 1000L), FamilyChange.beginRequest(d))
        assertEquals(mapOf("remoteId" to id(1), "changeId" to id(20), "change" to d.change), FamilyChange.applyRequest(id(1), id(20), d.change!!))
        rejects { FamilyChange.beginRequest(draft(null)) }
        val path = "familyWardrobes/${id(1)}/staging/me/${id(20)}.zip"
        val ticket = mapOf("archiveId" to id(20), "sha256" to "a".repeat(64), "size" to 1000.0, "authorUid" to "me",
            "status" to "pending", "expiresAt" to 5L, "stagingPath" to path, "baseRevision" to 9)
        // The ticket's base revision is the current head, not the draft's.
        assertEquals(FamilyTicket(path, false), FamilyChange.ticket(ticket, d, "me"))
        assertTrue(FamilyChange.ticket(ticket + ("status" to "committed"), d, "me").committed)
        for (bad in listOf("size" to 999, "authorUid" to "other", "stagingPath" to "x", "status" to "open", "baseRevision" to 0, "size" to 1000.5))
            rejects { FamilyChange.ticket(ticket + bad, d, "me") }
        val receipt = mapOf("remoteId" to id(1), "changeId" to id(20), "revision" to 12, "archiveId" to id(21), "status" to "committed")
        assertEquals(12L, FamilyChange.receipt(receipt, id(1), id(20)))
        for (bad in listOf("changeId" to id(22), "remoteId" to id(3), "revision" to 0, "status" to "pending", "archiveId" to "x"))
            rejects { FamilyChange.receipt(receipt + bad, id(1), id(20)) }
    }
    @Test fun serverFeaturesAreOptionalAndBounded() {
        val base = mapOf("remoteId" to id(1), "wardrobeId" to id(2), "ownerUid" to "owner", "revision" to 0, "head" to null, "role" to "editor")
        assertEquals(emptySet<String>(), FamilyWire.remote(base).features)
        assertFalse(FamilyChange.supported(FamilyWire.remote(base)))
        val remote = FamilyWire.remote(base + ("features" to listOf("change1", "future")))
        assertTrue(FamilyChange.supported(remote)); assertEquals(setOf("change1", "future"), remote.features)
        for (bad in listOf("change1", listOf(1), listOf(""), (1..33).map { "f$it" })) rejects { FamilyWire.remote(base + ("features" to bad)) }
    }
    @Test fun onlyRejectionsThatRetryCannotFixAllowDiscard() {
        assertTrue(FamilyChange.definite(FamilyTransferException(FamilyProblem.CONFLICT, reason = FamilyChange.LABEL_CONFLICT)))
        assertTrue(FamilyChange.definite(FamilyTransferException(FamilyProblem.ARCHIVE)))
        assertTrue(FamilyChange.definite(FamilyTransferException(FamilyProblem.MISSING, reason = FamilyChange.ITEM_MISSING)))
        for (e in listOf(FamilyTransferException(FamilyProblem.MISSING, reason = FamilyChange.ARCHIVE_MISSING), FamilyTransferException(FamilyProblem.EXPIRED),
            FamilyTransferException(FamilyProblem.LIMIT), FamilyTransferException(FamilyProblem.RETRY, reason = FamilyChange.BUSY), FamilyTransferException(FamilyProblem.ACCESS)))
            assertFalse(e.problem.name, FamilyChange.definite(e))
    }

    private class Fake(var committed: Boolean = false, var uploaded: Boolean = false, var failure: FamilyTransferException? = null) : FamilyChangeTransport {
        val calls = mutableListOf<String>(); val applied = mutableListOf<String>(); val targets = mutableListOf<Pair<String, String>>()
        override suspend fun beginChange(draft: FamilyAddDraft): FamilyTicket { calls += "begin"; return FamilyTicket("staging", committed) }
        override suspend fun applyChange(remoteId: String, changeId: String, text: String): Long {
            calls += "apply"; failure?.let { throw it }
            targets += remoteId to changeId; applied += text
            if (!committed && !uploaded) throw FamilyTransferException(FamilyProblem.MISSING, reason = FamilyChange.ARCHIVE_MISSING)
            return 12
        }
        override suspend fun upload(file: File, path: String, progress: (Long, Long) -> Unit) { calls += "upload"; uploaded = true }
    }
    private val change = draft(FamilyChange.photo(id(10), "", id(11), "png"))
    @Test fun newChangeUploadsOnceAfterTheProbe() = runBlocking {
        val fake = Fake(); var reads = 0
        assertEquals(12L, publishFamilyChange(change, fake, { reads++; File("change.zip") }) {})
        assertEquals(listOf("begin", "apply", "upload", "apply"), fake.calls); assertEquals(1, reads)
        assertEquals(listOf(id(1) to id(20)), fake.targets.distinct()); assertEquals(listOf(change.change), fake.applied.distinct())
    }
    @Test fun lostReplyOrCommittedReplayNeverOpensLocalBytes() = runBlocking {
        for (fake in listOf(Fake(uploaded = true), Fake(committed = true))) {
            assertEquals(12L, publishFamilyChange(change, fake, { error("Unexpected local read") }) {})
            assertEquals(listOf("begin", "apply"), fake.calls)
        }
    }
    @Test fun itemGoneOrLabelClashIsNotMistakenForAMissingUpload() = runBlocking {
        for (e in listOf(FamilyTransferException(FamilyProblem.MISSING, reason = FamilyChange.ITEM_MISSING),
            FamilyTransferException(FamilyProblem.CONFLICT, reason = FamilyChange.LABEL_CONFLICT), FamilyTransferException(FamilyProblem.RETRY))) {
            val fake = Fake(failure = e)
            try { publishFamilyChange(change, fake, { error("Unexpected read") }) {}; fail("Error swallowed") }
            catch (thrown: FamilyTransferException) { assertSame(e, thrown) }
            assertEquals(listOf("begin", "apply"), fake.calls)
        }
    }
    private fun outfitRow(outfitId: String, name: String = "Look") = JSONObject().put("id", outfitId).put("wardrobeId", id(2)).put("name", name)
        .put("signature", "x").put("createdAt", 7).put("occasion", "everyday").put("weather", "any_weather").put("source", "owned")
        .put("layout", "").put("background", "paper")
    /** Backup.encode output for two outfits, so the codec has to pick the right rows. */
    private fun encodedOutfits() = JSONObject().put("outfits", JSONArray().put(outfitRow(id(40))).put(outfitRow(id(41), "Other")))
        .put("parts", JSONArray().put(JSONObject().put("outfitId", id(40)).put("garmentId", id(10)))
            .put(JSONObject().put("outfitId", id(41)).put("garmentId", id(10))).put(JSONObject().put("outfitId", id(40)).put("garmentId", id(12))))
        .put("ideas", JSONArray().put(JSONObject().put("outfitId", id(40)).put("partId", "idea:top").put("wishId", JSONObject.NULL)
            .put("category", "top").put("name", "Top").put("color", "").put("photoId", "")))
    @Test fun outfitRowsAreTheOutfitsOwnManifestRows() {
        val rows = requireNotNull(FamilyChange.outfitRows(encodedOutfits(), id(40)))
        assertEquals(setOf("outfit", "parts", "ideas"), rows.fieldNames())
        assertEquals(outfitRow(id(40)).map(), rows.getJSONObject("outfit").map())
        assertEquals(2, rows.getJSONArray("parts").length()); assertEquals(1, rows.getJSONArray("ideas").length())
        assertNull(FamilyChange.outfitRows(encodedOutfits(), id(99)))
        val created = JSONObject(FamilyChange.outfit(rows, null))
        assertEquals(setOf("kind", "outfit", "parts", "ideas", "previous"), created.fieldNames())
        assertTrue(created.isNull("previous")); FamilyChange.checkOutfit(created.toString(), id(40), creating = true)
        val edited = FamilyChange.outfit(rows, rows)
        FamilyChange.checkOutfit(edited, id(40), creating = false)
        rejects { FamilyChange.checkOutfit(edited, id(40), creating = true) }
        rejects { FamilyChange.checkOutfit(edited, id(41), creating = false) }
        rejects { FamilyChange.outfit(rows, requireNotNull(FamilyChange.outfitRows(encodedOutfits(), id(41)))) }
        rejects { FamilyChange.outfit(rows.put("outfit", outfitRow(id(40)).put("layout", "x".repeat(FamilyChange.MAX_TEXT))), null) }
    }
    @Test fun sameOutfitIgnoresRowOrderButNotContent() {
        val rows = requireNotNull(FamilyChange.outfitRows(encodedOutfits(), id(40)))
        val parts = rows.getJSONArray("parts")
        val reordered = JSONObject(rows.toString()).put("parts", JSONArray().put(parts.getJSONObject(1)).put(parts.getJSONObject(0)))
        assertTrue(FamilyChange.sameOutfit(rows.toString(), reordered.toString()))
        val renamed = JSONObject(rows.toString()).put("outfit", outfitRow(id(40), "Renamed"))
        assertFalse(FamilyChange.sameOutfit(rows.toString(), renamed.toString()))
        val fewer = JSONObject(rows.toString()).put("parts", JSONArray().put(parts.getJSONObject(0)))
        assertFalse(FamilyChange.sameOutfit(rows.toString(), fewer.toString()))
        val idea = JSONObject(rows.toString()); idea.getJSONArray("ideas").getJSONObject(0).put("name", "Other")
        assertFalse(FamilyChange.sameOutfit(rows.toString(), idea.toString()))
    }
    @Test fun outfitChangeIsOneIdempotentCallWithoutUploadOrBegin() = runBlocking {
        val text = FamilyChange.outfit(requireNotNull(FamilyChange.outfitRows(encodedOutfits(), id(40))), null)
        val draft = FamilyOutfitDraft(FamilyPublication(identity, id(20), 4, "a".repeat(64), text.length.toLong()),
            FamilyOutfitChange(id(40), true, "Look", listOf(LookPiece("idea:top", "top", "Top")), "everyday", "any_weather", "inspiration", "", "paper", 7),
            delta = text)
        val fake = Fake(uploaded = true)
        assertEquals(12L, publishFamilyOutfitChange(draft, fake) {})
        assertEquals(listOf("apply"), fake.calls); assertEquals(listOf(text), fake.applied); assertEquals(listOf(id(1) to id(20)), fake.targets)
        val conflict = Fake(failure = FamilyTransferException(FamilyProblem.CONFLICT, reason = FamilyChange.OUTFIT_CHANGED))
        assertTrue(runCatching { publishFamilyOutfitChange(draft, conflict) {} }.exceptionOrNull() is FamilyTransferException)
        assertTrue(FamilyChange.definite(FamilyTransferException(FamilyProblem.CONFLICT, reason = FamilyChange.OUTFIT_CHANGED)))
        assertTrue(runCatching { publishFamilyOutfitChange(draft.copy(delta = null), fake) {} }.isFailure)
    }
    @Test fun fullArchiveDraftIsNeverSentAsAChange() = runBlocking {
        val fake = Fake()
        assertTrue(runCatching { publishFamilyChange(draft(null), fake, { File("x") }) {} }.isFailure)
        assertTrue(fake.calls.isEmpty())
    }
    @Test fun cancellationStopsBeforeTheUpload() = runBlocking {
        val fake = Fake(); var checks = 0
        try { publishFamilyChange(change, fake, { error("Unexpected read") }) { if (++checks > 1) throw kotlinx.coroutines.CancellationException() }; fail() }
        catch (_: kotlinx.coroutines.CancellationException) {}
        assertEquals(listOf("begin", "apply"), fake.calls)
    }
}
