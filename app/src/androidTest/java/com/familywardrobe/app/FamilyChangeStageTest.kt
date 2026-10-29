package com.familywardrobe.app

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Delta model v1 drafts on real storage: one stored photo entry, change JSON rows from Backup, preview round trip. */
class FamilyChangeStageTest {
    private fun scenario(block: suspend (AccountContext, AuthIdentity, Repository, PhotoStore) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val account = AuthIdentity("family-change-test", newId(), "editor@example.test")
        val context = AccountContext(base, AccountLocation(account.key, false)); val name = "family-${newId()}.db"
        val db = Room.databaseBuilder(context, WardrobeDatabase::class.java, name).build()
        try { val repo = Repository(db); repo.initialize("Home"); block(context, account, repo, PhotoStore(context)) }
        finally {
            db.close(); context.deleteDatabase(name); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
            File(context.noBackupFilesDir, "family-additions/${account.key}").deleteRecursively()
            File(context.noBackupFilesDir, "family-photo-replacements/${account.key}").deleteRecursively()
        }
    }
    private fun png(photos: PhotoStore, id: String = newId()): String {
        val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
        try { photos.file(id, "png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        return id
    }
    private val shared = Wardrobe(name = "Family")
    private val identity get() = FamilyArchiveIdentity(newId(), shared.id, "owner")
    private fun remote(id: FamilyArchiveIdentity) = FamilyRemote(id, 3, FamilyHead(3, 2, newId(), "unused", "a".repeat(64), 10, "owner"), "editor", setOf(FamilyChange.FEATURE))

    @Test fun additionDraftHoldsOnlyTheNewPhotoAndPreviewsTheSelection() = scenario { context, account, repo, photos ->
        val store = FamilyAddStore(context, account)
        val category = CustomCategory(wardrobeId = shared.id, name = "Knits", nameKey = "knits", baseCategory = "top")
        val item = Garment(wardrobeId = shared.id, name = "Jumper", category = "top", color = "white", photoId = newId(),
            location = "Шафа", customCategoryId = category.id)
        val selected = Snapshot(wardrobes = listOf(shared), garments = listOf(item), customCategories = listOf(category))
        val latest = Snapshot(wardrobes = listOf(shared))
        val source = png(photos)
        val draft = FamilyAddArchive(context, repo, store, {}).stageChange(remote(identity), latest, selected, photos, source)
        assertEquals(item.id, draft.garmentId); assertEquals(item.photoId, draft.photoId); assertEquals(3L, draft.publication.baseRevision)
        val zip = File(store.directory(draft.publication.archiveId), "publish.zip")
        assertEquals(draft.publication.size, zip.length()); assertEquals(draft.publication.hash, FamilyArchive.digest(zip))
        ZipFile(zip).use { z ->
            val entries = z.entries().toList()
            assertEquals(listOf("photos/${item.photoId}.png"), entries.map { it.name })
            assertEquals(ZipEntry.STORED, entries.single().method)
            assertArrayEquals(photos.currentFile(source).readBytes(), z.getInputStream(entries.single()).readBytes())
        }
        val change = JSONObject(requireNotNull(draft.change))
        assertEquals("add", change.getString("kind")); assertEquals(category.id, change.getJSONObject("customCategory").getString("id"))
        assertEquals("Шафа", change.getJSONObject("garment").getString("location"))
        store.put(draft, null)
        assertEquals(draft, FamilyAddStore(context, account).get(draft.remoteId))
        val preview = FamilyAddArchive(context, repo, store, {}).selection(draft)
        assertEquals(item, preview.garments.single()); assertEquals(listOf(category), preview.customCategories)
        // A local label clash is reported before anything is sent.
        val clash = latest.copy(customCategories = listOf(category.copy(id = newId(), baseCategory = "shoes")))
        try { FamilyAddArchive(context, repo, store, {}).stageChange(remote(identity), clash, selected, photos, source); fail("Clash accepted") }
        catch (_: IllegalArgumentException) {}
    }

    @Test fun photoReplacementDraftNeedsNoSharedPhotos() = scenario { context, account, repo, photos ->
        val store = FamilyAddStore(context, account, "family-photo-replacements")
        val item = Garment(wardrobeId = shared.id, name = "Coat", category = "outerwear", color = "black", photoId = newId())
        val latest = Snapshot(wardrobes = listOf(shared), garments = listOf(item))
        val source = png(photos)
        val draft = FamilyPhotoArchive(context, repo, store, {}).stage(remote(identity), latest, photos, item.id, true) { target ->
            newId().also { FamilyAddArchive(context, repo, store, {}).copyPhoto(photos, source, target, it) }
        }
        val change = JSONObject(requireNotNull(draft.change))
        assertEquals(setOf("kind", "garmentId", "fingerprint", "photo"), change.keys().asSequence().toSet())
        assertEquals(item.id, change.getString("garmentId")); assertEquals(draft.photoId, change.getJSONObject("photo").getString("id"))
        assertTrue(Regex("[0-9a-f]{64}").matches(change.getString("fingerprint")))
        ZipFile(File(store.directory(draft.publication.archiveId), "publish.zip")).use { z ->
            assertEquals(listOf("photos/${draft.photoId}.png"), z.entries().toList().map { it.name })
        }
        try {
            FamilyPhotoArchive(context, repo, store, {}).stage(remote(identity), latest, photos, newId(), true) { target ->
                newId().also { FamilyAddArchive(context, repo, store, {}).copyPhoto(photos, source, target, it) }
            }
            fail("Missing item accepted")
        } catch (e: FamilyTransferException) { assertEquals(FamilyProblem.MISSING, e.problem) }
    }
}
