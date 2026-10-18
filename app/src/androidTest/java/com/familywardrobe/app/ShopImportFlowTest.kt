package com.familywardrobe.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Device tests: SharedPreferences durability and identity boundaries, with isolated test storage. */
class ShopImportFlowTest {
    private fun scenario(block: (Context, String, String) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val a = AuthRules.accountKey("shop-test", newId())
        val b = AuthRules.accountKey("shop-test", newId())
        val context = AccountContext(base, AccountLocation(a, false))
        try { block(context, a, b) }
        finally {
            context.getSharedPreferences("shop-share-inbox", 0).edit().clear().commit()
            EditorDraftStore(context).clear()
        }
    }
    private fun share(text: String = "Coat https://shop.example/coat") =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    @Test fun receiptAndAccountBindingSurviveReopening() = scenario { context, a, b ->
        val inbox = SharedImportInbox(context)
        assertTrue(inbox.receive(share(), a))
        val id = inbox.entries.value.single().id
        val reopened = SharedImportInbox(context)
        assertEquals(id, reopened.entries.value.single().id)
        assertNull(reopened.claim(id, b))
        assertEquals(a, reopened.claim(id, a)!!.account)
        assertTrue(reopened.complete(id, a))
        assertTrue(SharedImportInbox(context).entries.value.isEmpty())
        // Activity redelivery after handoff must not create another queue entry.
        assertTrue(reopened.receive(share(), a))
        assertTrue(reopened.entries.value.isEmpty())
        assertTrue(reopened.receive(share(), b))
        assertEquals(b, reopened.entries.value.single().account)
    }
    @Test fun unauthenticatedShareIsClaimedOnlyOnce() = scenario { context, a, b ->
        val inbox = SharedImportInbox(context)
        assertTrue(inbox.receive(share(), ""))
        val id = inbox.entries.value.single().id
        assertEquals(a, inbox.claim(id, a)!!.account)
        assertNull(SharedImportInbox(context).claim(id, b))
        inbox.complete(id, b)
        assertEquals(id, inbox.entries.value.single().id)
    }
    @Test fun repeatedIntakeAndFullQueuePreserveAllPendingLinks() = scenario { context, a, _ ->
        val inbox = SharedImportInbox(context)
        assertTrue(inbox.receive(share(), a)); assertTrue(inbox.receive(share(), a))
        assertEquals(1, inbox.entries.value.size)
        repeat(9) { assertTrue(inbox.receive(share("https://shop.example/item/$it"), a)) }
        val before = inbox.entries.value
        assertFalse(inbox.receive(share("https://shop.example/overflow"), a))
        assertEquals(before, SharedImportInbox(context).entries.value)
    }
    @Test fun malformedShareCannotReplacePendingDraft() = scenario { context, a, _ ->
        val fields = JSONObject().put("id", newId()).put("name", "Unsaved edits").put("photo", newId())
        val draft = JSONObject().put("kind", "garment").put("wardrobe", Repository.DEFAULT_WARDROBE)
            .put("original", "").put("fields", fields)
        assertTrue(EditorDraftStore(context).write(draft))
        val inbox = SharedImportInbox(context)
        assertTrue(inbox.receive(share(), a))
        assertFalse(inbox.receive(share("x".repeat(ShopImportRules.MAX_TEXT + 1)), a))
        assertFalse(inbox.receive(share().setType("image/png"), a))
        assertFalse(inbox.receive(share(), "invalid-account"))
        assertEquals(draft.toString(), EditorDraftStore(context).read()!!.toString())
    }
    @Test fun shopReviewSelectionAndDestinationSurviveDraftReopening() = scenario { context, _, _ ->
        val fields = JSONObject().put("id", newId()).put("input", "https://shop.example/coat")
            .put("sharedText", "Coat https://shop.example/coat").put("shareId", newId())
            .put("results", "[{\"name\":\"Coat\",\"price\":\"69.90\",\"currency\":\"EUR\"}]")
            .put("selected", 0).put("imageIndex", 1).put("photo", newId())
            .put("target", "wish").put("targetWardrobe", newId()).put("needId", newId())
        val draft = JSONObject().put("kind", "shop").put("wardrobe", Repository.DEFAULT_WARDROBE)
            .put("original", "").put("fields", fields)
        assertTrue(EditorDraftStore(context).write(draft))
        assertEquals(draft.toString(), EditorDraftStore(context).read()!!.toString())
    }
}
