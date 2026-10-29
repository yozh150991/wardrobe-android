package com.familywardrobe.app

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Outfit changes applied by the server (0.14.22): the draft is only the outfit's own rows, nothing is archived. */
class FamilyOutfitChangeStageTest {
    private fun scenario(block: suspend (AccountContext, AuthIdentity, Repository, PhotoStore) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val account = AuthIdentity("family-outfit-change-test", newId(), "editor@example.test")
        val context = AccountContext(base, AccountLocation(account.key, false)); val name = "family-${newId()}.db"
        val db = Room.databaseBuilder(context, WardrobeDatabase::class.java, name).build()
        try { val repo = Repository(db); repo.initialize("Home"); block(context, account, repo, PhotoStore(context)) }
        finally {
            db.close(); context.deleteDatabase(name); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
            File(context.noBackupFilesDir, "family-outfits/${account.key}").deleteRecursively()
        }
    }
    private val shared = Wardrobe(name = "Family")
    private val top = Garment(wardrobeId = shared.id, name = "Shirt", category = "top", color = "white", photoId = newId())
    private val bottom = Garment(wardrobeId = shared.id, name = "Jeans", category = "bottom", color = "navy", photoId = newId())
    private val look = Outfit(newId(), shared.id, "Look", top.id, 42)
    private val snapshot = Snapshot(wardrobes = listOf(shared), garments = listOf(top, bottom), outfits = listOf(look),
        parts = listOf(OutfitPart(look.id, top.id)))
    private fun view(context: AccountContext, photos: PhotoStore): FamilyReadView {
        val identity = FamilyArchiveIdentity(newId(), shared.id, "owner")
        val remote = FamilyRemote(identity, 4, FamilyHead(4, 3, newId(), FamilyRules.archivePath(identity.remoteId, newId()), "a".repeat(64), 10, "owner"),
            "editor", setOf(FamilyChange.FEATURE, FamilyChange.OUTFIT_FEATURE))
        return FamilyReadView(remote, FamilyReadCache.Candidate(context.filesDir, snapshot, photos), FamilyReadLease(), "session")
    }
    private fun pieces() = listOf(LookPiece("owned:${top.id}", "top", top.name), LookPiece("owned:${bottom.id}", "bottom", bottom.name))

    @Test fun editCarriesItsNewAndPreviousRowsAndNoArchive() = scenario { context, account, repo, photos ->
        val store = FamilyOutfitStore(context, account); val view = view(context, photos)
        val change = FamilyOutfitChange(look.id, false, "Weekend", pieces(), "everyday", "any_weather", "owned", "", "paper")
        val draft = store.stage(view, change, repo, {}, delta = true)
        val text = requireNotNull(draft.delta); val json = JSONObject(text)
        assertEquals("outfit", json.getString("kind"))
        val outfit = json.getJSONObject("outfit")
        assertEquals(listOf("Weekend", 42L, listOf(top.id, bottom.id).sorted().joinToString("|")),
            listOf(outfit.getString("name"), outfit.getLong("createdAt"), outfit.getString("signature")))
        assertEquals(2, json.getJSONArray("parts").length()); assertEquals(0, json.getJSONArray("ideas").length())
        val previous = json.getJSONObject("previous")
        assertEquals("Look", previous.getJSONObject("outfit").getString("name")); assertEquals(1, previous.getJSONArray("parts").length())
        assertTrue(FamilyChange.sameOutfit(previous.toString(), requireNotNull(store.rows(view, snapshot, look.id, repo)).toString()))
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertEquals(bytes.size.toLong(), draft.publication.size)
        assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, draft.publication.hash)
        assertFalse(store.directory(draft.publication.archiveId).exists())
        store.put(draft, null)
        assertEquals(listOf(draft), FamilyOutfitStore(context, account).list(view.remote.identity.remoteId))
        store.remove(draft); assertTrue(store.list(view.remote.identity.remoteId).isEmpty())
    }

    @Test fun newOutfitHasNoPreviousRowsAndTheFormRemembersItsOrigin() = scenario { context, account, repo, photos ->
        val store = FamilyOutfitStore(context, account); val view = view(context, photos)
        val change = FamilyOutfitChange(newId(), true, "New", pieces(), "work", "cool", "owned", "", "sand")
        val draft = store.stage(view, change, repo, {}, delta = true)
        assertTrue(JSONObject(requireNotNull(draft.delta)).isNull("previous"))
        // The same combination as the edited look would be refused before anything is queued.
        val duplicate = snapshot.copy(parts = listOf(OutfitPart(look.id, top.id), OutfitPart(look.id, bottom.id)),
            outfits = listOf(look.copy(signature = listOf(top.id, bottom.id).sorted().joinToString("|"))))
        val stale = FamilyReadView(view.remote, FamilyReadCache.Candidate(context.filesDir, duplicate, photos), FamilyReadLease(), "session")
        try { store.stage(stale, change, repo, {}, delta = true); fail("Duplicate accepted") }
        catch (e: FamilyTransferException) { assertEquals(FamilyProblem.CONFLICT, e.problem) }
        val form = FamilyOutfitForm(view.remote.identity.remoteId, look.id, 4, "{}", requireNotNull(store.rows(view, snapshot, look.id, repo)).toString())
        store.putForm(form); assertEquals(form, FamilyOutfitStore(context, account).form(form.remote))
        // A full archive is still built when the server has no outfit changes (it needs the shared photos).
        for (item in listOf(top, bottom)) {
            val bitmap = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
            try { photos.file(item.photoId, "png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        }
        val legacy = store.stage(view, change, repo, {})
        assertNull(legacy.delta); assertTrue(store.checked(legacy) {}.isFile)
    }
}
