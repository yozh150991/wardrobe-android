package com.familywardrobe.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FamilySyncStoreTest {
    private fun scenario(test: (AccountContext, AuthIdentity, FamilySyncStore) -> Unit) {
        val identity = AuthIdentity("family-sync-test", newId(), "editor@example.test")
        val context = AccountContext(InstrumentationRegistry.getInstrumentation().targetContext, AccountLocation(identity.key, false))
        try { test(context, identity, FamilySyncStore(context, identity)) }
        finally { for (folder in listOf("family-sync", "family-edits", "family-additions", "family-photo-replacements", "family-outfits"))
            File(context.noBackupFilesDir, "$folder/${identity.key}").deleteRecursively() }
    }
    private fun plan(remote: String = newId()) = FamilySyncPlan(newId(), remote,
        listOf(FamilySyncOperation(FamilySyncKind.EDIT, newId(), "a".repeat(64))), true)
    @Test fun restartPreservesConsentAndAcknowledgedCursor() = scenario { context, identity, store ->
        val p = plan().copy(acknowledged = true, status = FamilySyncStatus.RUNNING); store.put(p, null)
        assertEquals(p, FamilySyncStore(context, identity).get(p.remote))
    }
    @Test fun cancelledPlanRejectsLateWorkerWrite() = scenario { _, _, store ->
        val p = plan(); store.put(p, null); store.stop(p.remote)
        assertTrue(runCatching { store.put(p.copy(acknowledged = true), p) }.isFailure)
        assertEquals(FamilySyncStatus.CANCELLED, store.get(p.remote)?.status)
        assertFalse(requireNotNull(store.get(p.remote)).background)
    }
    @Test fun signOutPausesAllWardrobesAndDoesNotResetFinishedPlan() = scenario { context, identity, store ->
        val a = plan(); val b = plan(); val done = plan().copy(cursor = 1, status = FamilySyncStatus.DONE)
        listOf(a, b, done).forEach { store.put(it, null) }; store.pauseAll()
        val restarted = FamilySyncStore(context, identity)
        for (p in listOf(a, b)) { assertEquals(FamilySyncStatus.PAUSED, restarted.get(p.remote)?.status); assertFalse(requireNotNull(restarted.get(p.remote)).background) }
        assertEquals(done, restarted.get(done.remote))
    }
    @Test fun anotherAccountCannotReadOrChangeConsent() = scenario { context, _, store ->
        val p = plan(); store.put(p, null)
        val other = AuthIdentity("family-sync-test", newId(), "other@example.test")
        val isolated = AccountContext(InstrumentationRegistry.getInstrumentation().targetContext, AccountLocation(other.key, false))
        val separate = FamilySyncStore(isolated, other); assertNull(separate.get(p.remote)); separate.pauseAll()
        assertEquals(p, store.get(p.remote)); assertTrue(context.location.key != other.key)
    }
    @Test fun corruptJournalCannotBeSilentlyReplaced() = scenario { context, identity, store ->
        val p = plan(); store.put(p, null)
        File(context.noBackupFilesDir, "family-sync/${identity.key}/${p.remote}.json").writeText("broken")
        assertTrue(runCatching { store.get(p.remote) }.isFailure)
        assertTrue(runCatching { store.put(p, null) }.isFailure)
    }
    @Test fun allFourOutboxesParticipateAndOutfitFormSurvivesRemoval() = scenario { context, identity, _ ->
        val shared = FamilyArchiveIdentity(newId(), newId(), "owner"); val remote = shared.remoteId
        val fields = linkedMapOf("name" to "Shirt", "size" to "L", "location" to "Shelf", "description" to "")
        FamilyEditStore(context, identity).put(FamilyEditDraft(remote, newId(), newId(), 4, fields, fields + ("size" to "M")))
        fun media() = FamilyAddDraft(FamilyPublication(shared, newId(), 4, "a".repeat(64), 100), newId(), newId(), "Shirt")
        FamilyAddStore(context, identity).put(media(), null)
        FamilyAddStore(context, identity, "family-photo-replacements").put(media(), null)
        val outfit = FamilyOutfitDraft(FamilyPublication(shared, newId(), 4, "a".repeat(64), 100),
            FamilyOutfitChange(newId(), true, "Outfit", listOf(LookPiece("idea:top", "top", "Top")), "everyday", "any_weather", "inspiration", "[]", "paper"))
        val outfits = FamilyOutfitStore(context, identity); outfits.put(outfit, null)
        val form = FamilyOutfitForm(remote, "", 4, "{}"); outfits.putForm(form)
        val operations = FamilySyncOperations(context, identity, remote)
        // Whole-archive operations first, item details last (audit L-01).
        assertEquals(listOf(FamilySyncKind.ADD, FamilySyncKind.PHOTO, FamilySyncKind.OUTFIT, FamilySyncKind.EDIT), operations.snapshot().map { it.kind })
        val selected = operations.snapshot().first { it.kind == FamilySyncKind.OUTFIT }; operations.submitted(selected); operations.remove(selected)
        assertEquals(form, outfits.form(remote)); assertEquals(3, operations.snapshot().size)
    }
}
