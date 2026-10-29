package com.familywardrobe.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FamilyOutfitQueueTest {
    private val identity = FamilyArchiveIdentity(newId(), newId(), "owner")
    private val remote get() = identity.remoteId
    private fun draft(size: Long = 100): FamilyOutfitDraft = FamilyOutfitDraft(
        FamilyPublication(identity, newId(), 5, "a".repeat(64), size),
        FamilyOutfitChange(newId(), true, "Образ Łódź", listOf(LookPiece("idea:top", "top", "Top")),
            "everyday", "any_weather", "inspiration", "[]", "paper", 123))
    private fun state(vararg rows: FamilyOutfitDraft) = FamilyOutfitJournal(drafts = rows.toList())
    private fun rejects(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun appendsAndRebasesInPlace() {
        val a = draft(); val b = draft(); val c = draft()
        val rows = FamilyOutfitQueue.put(state(a, b), c, null)
        val changed = b.copy(publication = b.publication.copy(archiveId = newId(), baseRevision = 7))
        assertEquals(listOf(a, changed, c), FamilyOutfitQueue.put(rows, changed, b).drafts)
    }
    @Test fun promotionAtomicallyConsumesExactFormAndKeepsNeighbors() {
        val a = draft(); val b = draft(); val form = FamilyOutfitForm(remote, "", 5, "{\"name\":\"draft\"}")
        val promoted = FamilyOutfitQueue.put(state(a).copy(form = form), b, null, form)
        assertNull(promoted.form); assertEquals(listOf(a, b), promoted.drafts)
    }
    @Test fun staleFormCannotBeConsumed() {
        val form = FamilyOutfitForm(remote, "", 5, "{}")
        rejects { FamilyOutfitQueue.put(FamilyOutfitJournal(form.copy(payload = "{\"name\":\"new\"}")), draft(), null, form) }
    }
    @Test fun confirmedDuplicateUpdateCanPromoteNewFormToExistingLook() {
        val form = FamilyOutfitForm(remote, "", 5)
        val update = draft().let { it.copy(change = it.change.copy(creating = false)) }
        assertEquals(listOf(update), FamilyOutfitQueue.put(FamilyOutfitJournal(form), update, null, form).drafts)
    }
    @Test fun sendingAndRemovingDraftPreserveUnrelatedEditor() {
        val a = draft(); val form = FamilyOutfitForm(remote, newId(), 5, "{}")
        val sent = a.copy(submitted = true)
        val next = FamilyOutfitQueue.put(state(a).copy(form = form), sent, a)
        assertEquals(form, next.form)
        assertEquals(FamilyOutfitJournal(form), FamilyOutfitQueue.remove(next, sent))
    }
    @Test fun removingMiddlePreservesOtherPublications() {
        val a = draft(); val b = draft(); val c = draft()
        assertEquals(listOf(a, c), FamilyOutfitQueue.remove(state(a, b, c), b).drafts)
    }
    @Test fun tenAllowedEleventhRejectedWithoutConsumingForm() {
        val rows = (1..10).map { draft() }; val form = FamilyOutfitForm(remote, "", 5)
        val journal = FamilyOutfitJournal(form, rows)
        assertEquals(journal, FamilyOutfitQueue.validate(journal, remote))
        val error = runCatching { FamilyOutfitQueue.put(journal, draft(), null, form) }.exceptionOrNull()
        assertEquals(FamilyProblem.LIMIT, (error as FamilyTransferException).problem)
        assertEquals(form, journal.form)
    }
    @Test fun exactArchiveBudgetAndOneByteOver() {
        val rows = (1..4).map { draft(FamilyWire.MAX_ARCHIVE) }
        assertEquals(FamilyOutfitQueue.MAX_BYTES, rows.sumOf { it.publication.size })
        assertEquals(rows, FamilyOutfitQueue.put(FamilyOutfitJournal(drafts = rows.dropLast(1)), rows.last(), null).drafts)
        val error = runCatching { FamilyOutfitQueue.put(FamilyOutfitJournal(drafts = rows), draft(1), null) }.exceptionOrNull()
        assertEquals(FamilyProblem.LIMIT, (error as FamilyTransferException).problem)
    }
    @Test fun duplicateLookAndArchiveRejected() {
        val a = draft(); val b = draft()
        rejects { FamilyOutfitQueue.put(state(a), b.copy(change = b.change.copy(id = a.change.id)), null) }
        rejects { FamilyOutfitQueue.put(state(a), b.copy(publication = a.publication), null) }
    }
    @Test fun differentWardrobeCannotJoinQueue() {
        val a = draft(); val b = draft()
        for (other in listOf(identity.copy(wardrobeId = newId()), identity.copy(remoteId = newId()))) {
            rejects { FamilyOutfitQueue.put(state(a), b.copy(publication = b.publication.copy(identity = other)), null) }
        }
    }
    /** FAM-04 (0.14.25): an ownership transfer changes only the owner; queued outfits stay valid and readable. */
    @Test fun outfitsFromBeforeAnOwnershipTransferStayQueued() {
        val a = draft(); val b = draft().let { it.copy(publication = it.publication.copy(identity = identity.copy(ownerUid = "new-owner"))) }
        val joined = FamilyOutfitQueue.put(state(a), b, null)
        assertEquals(listOf(a, b), joined.drafts)
        val decoded = FamilyOutfitQueue.decode(FamilyOutfitQueue.encode(joined, "account", remote), "account", remote)
        assertTrue(decoded.drafts.zip(joined.drafts).all { (x, y) -> FamilyOutfitQueue.same(x, y) })
    }
    @Test fun staleRebaseRemoveAndSubmittedStateRejected() {
        val a = draft(); val sent = a.copy(submitted = true)
        rejects { FamilyOutfitQueue.put(state(sent), a, a) }
        rejects { FamilyOutfitQueue.remove(state(sent), a) }
        val renamed = a.copy(change = a.change.copy(name = "Changed"))
        rejects { FamilyOutfitQueue.remove(state(renamed), a) }
    }
    @Test fun resolvedDisplayDetailsDoNotBreakJournalCas() {
        val a = draft(); val resolved = a.copy(change = a.change.copy(pieces = a.change.pieces.map { it.copy(color = "red") }))
        assertTrue(FamilyOutfitQueue.same(a, resolved))
        assertEquals(emptyList<FamilyOutfitDraft>(), FamilyOutfitQueue.remove(state(a), resolved).drafts)
    }
    @Test fun onlyExactHeadAndRepeatedTapCannotAdvanceQueue() {
        val a = draft(); val b = draft()
        FamilyOutfitQueue.requireHead(listOf(a, b), a)
        rejects { FamilyOutfitQueue.requireHead(listOf(a, b), b) }
        rejects { FamilyOutfitQueue.requireHead(FamilyOutfitQueue.remove(state(a, b), a).drafts, a) }
    }
    @Test fun codecPreservesOrderFormUnicodeAndExactSubmittedRequest() {
        val a = draft().copy(submitted = true); val b = draft()
        val journal = state(a, b).copy(form = FamilyOutfitForm(remote, "", 5, "{\"name\":\"Сукня\"}"))
        val decoded = FamilyOutfitQueue.decode(FamilyOutfitQueue.encode(journal, "account", remote), "account", remote)
        assertEquals(journal, decoded); assertEquals(a.publication.request(), decoded.drafts.first().publication.request())
    }
    @Test fun legacyPublicationFieldsAreReadableWithoutChangingReceipt() {
        val a = draft().copy(submitted = true)
        val legacy = FamilyOutfitQueue.encode(state(a), "account", remote).getJSONArray("drafts").getJSONObject(0)
            .put("version", 1).put("account", "account").put("remote", remote)
        FamilyOutfitQueue.checkEnvelope(legacy, 1, "account", remote)
        assertEquals(a, FamilyOutfitQueue.readDraft(legacy, remote))
    }
    @Test fun wrongAccountRemoteAndFutureVersionRejected() {
        val json = FamilyOutfitQueue.encode(state(draft()), "account", remote)
        rejects { FamilyOutfitQueue.decode(json, "other", remote) }
        rejects { FamilyOutfitQueue.decode(json, "account", newId()) }
        rejects { FamilyOutfitQueue.decode(JSONObject(json.toString()).put("version", 4), "account", remote) }
    }
    @Test fun invalidOrOversizedEditorPayloadRejected() {
        for (payload in listOf("broken", "{\"name\":\"" + "a".repeat(40_000) + "\"}")) {
            rejects { FamilyOutfitQueue.validate(FamilyOutfitJournal(FamilyOutfitForm(remote, "", 5, payload)), remote) }
        }
    }
    private fun rows(id: String, name: String = "Look") = JSONObject().put("outfit", JSONObject().put("id", id).put("name", name))
        .put("parts", org.json.JSONArray()).put("ideas", org.json.JSONArray())
    @Test fun outfitChangeAndEditorOriginSurviveJournalV3() {
        val created = draft().let { it.copy(delta = FamilyOutfitChangeText(it.change.id, null)) }
        val edited = draft().let { d -> d.copy(change = d.change.copy(creating = false), delta = FamilyOutfitChangeText(d.change.id, rows(d.change.id))) }
        val outfit = newId(); val form = FamilyOutfitForm(remote, outfit, 5, "{}", rows(outfit).toString())
        val journal = FamilyOutfitJournal(form, listOf(created, edited, draft()))
        val json = FamilyOutfitQueue.encode(journal, "account", remote)
        assertEquals(3, json.getInt("version"))
        assertEquals(journal, FamilyOutfitQueue.decode(json, "account", remote))
        // A version 2 journal (no delta/origin fields) still opens.
        val v2 = FamilyOutfitQueue.encode(FamilyOutfitJournal(form.copy(origin = ""), listOf(draft())), "account", remote).put("version", 2)
        v2.getJSONObject("form").remove("origin"); v2.getJSONArray("drafts").getJSONObject(0).remove("delta")
        assertEquals("", FamilyOutfitQueue.decode(v2, "account", remote).form?.origin)
    }
    @Test fun outfitChangeMustDescribeItsOwnLookAndOriginNeedsAnEditedLook() {
        val a = draft()
        rejects { FamilyOutfitQueue.validate(state(a.copy(delta = FamilyOutfitChangeText(newId(), null))), remote) }
        rejects { FamilyOutfitQueue.validate(state(a.copy(delta = FamilyOutfitChangeText(a.change.id, rows(a.change.id)))), remote) }
        rejects { FamilyOutfitQueue.validate(FamilyOutfitJournal(FamilyOutfitForm(remote, "", 5, "{}", "{}")), remote) }
        rejects { a.copy(delta = "") }
    }
    /** A minimal change text: the queue checks only kind, outfit ID and whether it creates or edits. */
    private fun FamilyOutfitChangeText(id: String, previous: JSONObject?) = FamilyChange.outfit(rows(id), previous)
    @Test fun formCannotCompeteWithAlreadyQueuedLook() {
        val a = draft()
        rejects { FamilyOutfitQueue.validate(state(a).copy(form = FamilyOutfitForm(remote, a.change.id, 5)), remote) }
    }
}
