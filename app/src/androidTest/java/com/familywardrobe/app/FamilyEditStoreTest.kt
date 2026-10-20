package com.familywardrobe.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FamilyEditStoreTest {
    private fun scenario(test: (AccountContext, AuthIdentity, FamilyEditStore) -> Unit) {
        val identity = AuthIdentity("family-edit-test", newId(), "editor@example.test")
        val context = AccountContext(InstrumentationRegistry.getInstrumentation().targetContext, AccountLocation(identity.key, false))
        try { test(context, identity, FamilyEditStore(context, identity)) }
        finally { File(context.noBackupFilesDir,"family-edits/${identity.key}").deleteRecursively() }
    }
    private fun draft() = FamilyEditDraft(newId(),newId(),newId(),4,
        mapOf("name" to "Shirt","size" to "L","location" to "Shelf","description" to ""),
        mapOf("name" to "Shirt","size" to "L","location" to "Hall","description" to ""))
    @Test fun restartRetainsExactSubmittedUuidAndChangedFields() = scenario { context, identity, store ->
        val d=draft();store.put(d);val submitted=d.copy(submitted=true);store.put(submitted,d)
        val recovered=FamilyEditStore(context,identity).get(d.remoteId)
        assertEquals(submitted,recovered);assertEquals(mapOf("location" to "Hall"),recovered!!.changes)
        assertTrue(File(context.noBackupFilesDir,"family-edits/${identity.key}/outbox.json").isFile)
    }
    @Test fun separateAccountCannotSeeDraft() = scenario { context, identity, store ->
        val d=draft();store.put(d)
        val other=AuthIdentity(identity.project,newId(),"other@example.test")
        val otherContext=AccountContext(context,AccountLocation(other.key,false))
        assertNull(FamilyEditStore(otherContext,other).get(d.remoteId))
    }
    @Test fun staleUpdateAndRemovalCannotOverwriteRebasedDraft() = scenario { _,_,store ->
        val d=draft();store.put(d);val next=d.copy(editId=newId(),baseRevision=5);store.put(next,d)
        try { store.put(d,d);fail("Stale update") } catch (_: IllegalArgumentException) {}
        try { store.remove(d);fail("Stale removal") } catch (_: IllegalArgumentException) {}
        assertEquals(next,store.get(d.remoteId))
    }
    @Test fun concurrentStoreInstancesPreserveOtherWardrobes() = scenario { context,identity,store ->
        val first=draft();val second=draft();store.put(first);FamilyEditStore(context,identity).put(second)
        assertEquals(first,store.get(first.remoteId));assertEquals(second,store.get(second.remoteId))
    }
    @Test fun corruptJournalFailsWithoutSilentlyDroppingDrafts() = scenario { context,identity,store ->
        val d=draft();store.put(d);File(context.noBackupFilesDir,"family-edits/${identity.key}/outbox.json").writeText("broken")
        try { store.get(d.remoteId);fail("Corruption accepted") } catch (_: org.json.JSONException) {}
    }
}
