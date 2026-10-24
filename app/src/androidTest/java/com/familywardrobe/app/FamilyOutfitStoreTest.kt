package com.familywardrobe.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FamilyOutfitStoreTest {
    private fun scenario(test: (AccountContext, AuthIdentity, FamilyOutfitStore) -> Unit) {
        val identity = AuthIdentity("family-outfit-test", newId(), "editor@example.test")
        val context = AccountContext(InstrumentationRegistry.getInstrumentation().targetContext, AccountLocation(identity.key, false))
        try { test(context, identity, FamilyOutfitStore(context, identity)) }
        finally { File(context.noBackupFilesDir, "family-outfits/${identity.key}").deleteRecursively() }
    }
    private fun draft(): FamilyOutfitDraft {
        val publication = FamilyPublication(FamilyArchiveIdentity(newId(), newId(), "owner"), newId(), 5, "a".repeat(64), 100)
        val change = FamilyOutfitChange(newId(), true, "Outfit", listOf(LookPiece("idea:top", "top", "Top")),
            "everyday", "any_weather", "inspiration", "[]", "paper", 123)
        return FamilyOutfitDraft(publication, change)
    }
    @Test fun restartKeepsEditorPayloadAndAccountIsolation() = scenario { context, identity, store ->
        val form = FamilyOutfitForm(newId(), "", 7, "{\"name\":\"Shared look\",\"locks\":[],\"stage\":1}")
        store.putForm(form)
        assertEquals(form, FamilyOutfitStore(context, identity).form(form.remote))
        val other = AuthIdentity(identity.project, newId(), "other@example.test")
        val isolated = AccountContext(InstrumentationRegistry.getInstrumentation().targetContext, AccountLocation(other.key, false))
        assertNull(FamilyOutfitStore(isolated, other).form(form.remote))
    }
    @Test fun publicationRoundTripKeepsExactRetryAndSubmittedState() = scenario { context, identity, store ->
        val draft = draft(); store.put(draft, null)
        val sent = draft.copy(submitted = true); store.put(sent, draft)
        assertEquals(sent, FamilyOutfitStore(context, identity).draft(draft.publication.identity.remoteId))
        assertTrue(runCatching { store.put(draft, draft) }.isFailure)
    }
    @Test fun staleRebaseAndRemovalCannotTouchNewPublication() = scenario { _, _, store ->
        val first = draft(); store.put(first, null)
        val next = first.copy(publication = first.publication.copy(archiveId = newId(), baseRevision = 6))
        store.put(next, first)
        assertTrue(runCatching { store.put(first, first) }.isFailure)
        assertTrue(runCatching { store.remove(first) }.isFailure)
        assertEquals(next, store.draft(first.publication.identity.remoteId))
    }
    @Test fun corruptArchiveAndJournalFailClosed() = scenario { context, identity, store ->
        val draft = draft(); val remote = draft.publication.identity.remoteId; store.put(draft, null)
        store.directory(draft.publication.archiveId).mkdirs()
        File(store.directory(draft.publication.archiveId), "publish.zip").writeText("broken")
        assertTrue(runCatching { store.checked(draft) {} }.isFailure)
        File(context.noBackupFilesDir, "family-outfits/${identity.key}/$remote-state.json").writeText("broken")
        assertTrue(runCatching { store.draft(remote) }.isFailure)
    }
    @Test fun removingOneDraftPreservesOtherWardrobesAndTheirForms() = scenario { _, _, store ->
        val first = draft(); val second = draft(); store.put(first, null); store.put(second, null)
        val form = FamilyOutfitForm(second.publication.identity.remoteId, "", 5, "{}")
        store.putForm(form); store.remove(first)
        assertNull(store.draft(first.publication.identity.remoteId))
        assertEquals(second, store.draft(second.publication.identity.remoteId)); assertEquals(form, store.form(form.remote))
    }
    @Test fun severalDraftsAndUnfinishedFormSurviveRestartAndHeadRemoval() = scenario { context, identity, store ->
        val a = draft(); val remote = a.publication.identity.remoteId
        val b = draft().let { it.copy(publication = it.publication.copy(identity = a.publication.identity)) }
        store.put(a, null); store.put(b, null)
        val form = FamilyOutfitForm(remote, "", 5, "{}"); store.putForm(form)
        val restarted = FamilyOutfitStore(context, identity)
        assertEquals(listOf(a, b), restarted.list(remote)); assertEquals(form, restarted.form(remote))
        restarted.remove(a)
        assertEquals(listOf(b), store.list(remote)); assertEquals(form, store.form(remote))
    }
    @Test fun promotionIsAtomicAcrossStoreInstances() = scenario { context, identity, store ->
        val a = draft(); val remote = a.publication.identity.remoteId
        val form = FamilyOutfitForm(remote, "", 5, "{}"); store.putForm(form)
        FamilyOutfitStore(context, identity).put(a, null, form)
        assertNull(store.form(remote)); assertEquals(listOf(a), store.list(remote))
        assertTrue(runCatching { store.put(a, null, form) }.isFailure)
    }
    @Test fun removingTailDeletesOnlyItsArchiveAndKeepsForm() = scenario { _, _, store ->
        val a = draft(); val remote = a.publication.identity.remoteId
        val b = draft().let { it.copy(publication = it.publication.copy(identity = a.publication.identity)) }
        for (d in listOf(a, b)) { store.put(d, null); store.directory(d.publication.archiveId).mkdirs() }
        val form = FamilyOutfitForm(remote, "", 5); store.putForm(form); store.remove(b)
        assertTrue(store.directory(a.publication.archiveId).exists()); assertFalse(store.directory(b.publication.archiveId).exists())
        assertEquals(listOf(a), store.list(remote)); assertEquals(form, store.form(remote))
    }
    @Test fun legacySubmittedDraftMigratesWithoutResurrectingSupersededForm() = scenario { context, identity, store ->
        val a = draft().copy(submitted = true); val remote = a.publication.identity.remoteId
        val root = File(context.noBackupFilesDir, "family-outfits/${identity.key}").apply { mkdirs() }
        val legacy = FamilyOutfitQueue.encode(FamilyOutfitJournal(drafts = listOf(a)), identity.key, remote)
            .getJSONArray("drafts").getJSONObject(0).put("version", 1).put("account", identity.key).put("remote", remote)
        File(root, "$remote-draft.json").writeText(legacy.toString())
        File(root, "$remote-form.json").writeText("broken obsolete form")
        assertEquals(a, store.draft(remote)); assertNull(store.form(remote))
        val b = draft().let { it.copy(publication = it.publication.copy(identity = a.publication.identity)) }
        store.put(b, null)
        assertEquals(listOf(a, b), FamilyOutfitStore(context, identity).list(remote))
        assertFalse(File(root, "$remote-draft.json").exists()); assertFalse(File(root, "$remote-form.json").exists())
    }
    @Test fun legacyFormMigratesAndCorruptV2NeverFallsBack() = scenario { context, identity, store ->
        val form = FamilyOutfitForm(newId(), "", 5, "{}"); val remote = form.remote
        val root = File(context.noBackupFilesDir, "family-outfits/${identity.key}").apply { mkdirs() }
        val legacy = org.json.JSONObject().put("version", 1).put("account", identity.key).put("remote", remote)
            .put("outfit", "").put("base", 5).put("payload", "{}")
        File(root, "$remote-form.json").writeText(legacy.toString())
        assertEquals(form, store.form(remote)); store.putForm(form.copy(base = 6))
        File(root, "$remote-form.json").writeText(legacy.toString())
        File(root, "$remote-state.json").writeText("broken")
        assertTrue(runCatching { store.form(remote) }.isFailure)
    }
}
