package com.familywardrobe.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FamilyAddStoreTest {
    private fun scenario(test: (AccountContext, AuthIdentity, FamilyAddStore) -> Unit) {
        val identity = AuthIdentity("family-add-test", newId(), "editor@example.test")
        val context = AccountContext(InstrumentationRegistry.getInstrumentation().targetContext, AccountLocation(identity.key, false))
        try { test(context, identity, FamilyAddStore(context, identity)) }
        finally { File(context.noBackupFilesDir, "family-additions/${identity.key}").deleteRecursively() }
    }
    private fun draft() = FamilyAddDraft(FamilyPublication(FamilyArchiveIdentity(newId(), newId(), "owner"), newId(), 5, "a".repeat(64), 100), newId(), newId(), "Shirt")
    @Test fun restartRetainsExactRequestAndAccountCannotReadAnotherDraft() = scenario { context, identity, store ->
        val draft = draft(); store.put(draft, null); val sent = draft.copy(submitted = true); store.put(sent, draft)
        assertEquals(sent, FamilyAddStore(context, identity).get(draft.remoteId))
        val other = AuthIdentity(identity.project, newId(), "other@example.test")
        val isolated = AccountContext(context, AccountLocation(other.key, false))
        assertNull(FamilyAddStore(isolated, other).get(draft.remoteId))
    }
    @Test fun staleRemovalAndRebaseCannotOverwriteCurrentJournal() = scenario { _, _, store ->
        val old = draft(); store.put(old, null)
        val next = old.copy(publication = old.publication.copy(archiveId = newId(), baseRevision = 6)); store.put(next, old)
        try { store.remove(old); fail("Stale removal") } catch (_: IllegalArgumentException) {}
        try { store.put(old, old); fail("Stale rebase") } catch (_: IllegalArgumentException) {}
        assertEquals(next, store.get(old.remoteId))
    }
    @Test fun corruptJournalFailsClosedAndUnchangedArchiveIsRequired() = scenario { context, identity, store ->
        val draft = draft(); store.put(draft, null)
        store.directory(draft.publication.archiveId).mkdirs(); store.archive(draft).writeText("broken")
        try { store.checked(draft) {}; fail("Corruption accepted") } catch (_: IllegalArgumentException) {}
        File(context.noBackupFilesDir, "family-additions/${identity.key}/${draft.remoteId}.json").writeText("broken")
        try { store.get(draft.remoteId); fail("Journal accepted") } catch (_: org.json.JSONException) {}
    }
    @Test fun removalTouchesOnlyMatchingAdditionAndPreservesPrivatePhotos() = scenario { _, _, store ->
        val first = draft(); val second = draft(); store.put(first, null); store.put(second, null)
        val directory = store.directory(first.publication.archiveId); directory.mkdirs(); File(directory, "publish.zip").writeText("test")
        store.remove(first); assertNull(store.get(first.remoteId)); assertEquals(second, store.get(second.remoteId)); assertFalse(directory.exists())
    }
}
