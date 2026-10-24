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
        File(context.noBackupFilesDir, "family-outfits/${identity.key}/$remote-draft.json").writeText("broken")
        assertTrue(runCatching { store.draft(remote) }.isFailure)
    }
    @Test fun removingOneDraftPreservesOtherWardrobesAndTheirForms() = scenario { _, _, store ->
        val first = draft(); val second = draft(); store.put(first, null); store.put(second, null)
        val form = FamilyOutfitForm(second.publication.identity.remoteId, "", 5, "{}")
        store.putForm(form); store.remove(first)
        assertNull(store.draft(first.publication.identity.remoteId))
        assertEquals(second, store.draft(second.publication.identity.remoteId)); assertEquals(form, store.form(form.remote))
    }
}
