package com.familywardrobe.app
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorDraftStoreTest {
    @Test fun everyEditorDraftKindSurvivesReopening() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val account = AccountContext(base, AccountLocation(AuthRules.accountKey("draft-test", newId()), false))
        val store = EditorDraftStore(account)
        try { listOf("garment", "wish", "look", "purchase", "import", "shop").forEach { kind ->
            val draft = JSONObject().put("kind", kind).put("wardrobe", Repository.DEFAULT_WARDROBE).put("original", newId())
                .put("fields", JSONObject().put("name", "Reviewed").put("photo", newId()).put("price", "88,00"))
            assertTrue(store.write(draft)); assertEquals(draft.toString(), EditorDraftStore(account).read()?.toString())
        } } finally { store.clear() }
    }
    @Test fun reopeningRestoresDraftOnlyForSameUidAndDiscardRemovesIt() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val a = AccountLocation(AuthRules.accountKey("draft-test", newId()), false)
        val b = AccountLocation(AuthRules.accountKey("draft-test", newId()), false)
        val first = EditorDraftStore(AccountContext(base, a))
        val fields = JSONObject().put("id", newId()).put("name", "Blue jacket").put("category", "outerwear")
            .put("photo", newId()).put("price", "89,00").put("inspiration", "Rain")
        val draft = JSONObject().put("kind", "wish").put("wardrobe", Repository.DEFAULT_WARDROBE).put("original", newId()).put("fields", fields)
        try {
            assertTrue(first.write(draft))
            assertEquals(draft.toString(), EditorDraftStore(AccountContext(base, a)).read()?.toString())
            assertNull(EditorDraftStore(AccountContext(base, b)).read())
            assertTrue(first.clear()); assertNull(EditorDraftStore(AccountContext(base, a)).read())
        } finally { first.clear(); EditorDraftStore(AccountContext(base, b)).clear() }
    }
    @Test fun corruptDraftDoesNotBlockAccountOpening() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val account = AccountContext(base, AccountLocation(AuthRules.accountKey("draft-test", newId()), false))
        val prefs = account.getSharedPreferences("preferences", 0)
        try { prefs.edit().putString("editorDraft", "not-json").commit(); assertNull(EditorDraftStore(account).read()) }
        finally { prefs.edit().clear().commit() }
    }
}
