package com.familywardrobe.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** FAM-04: the owner's portable copy of a shared wardrobe, written from a read view's files (not from Room). */
class FamilyExportTest {
    private fun scenario(block: suspend (AccountContext, AuthIdentity, Repository, PhotoStore) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val account = AuthIdentity("family-export-test", newId(), "owner@example.test")
        val context = AccountContext(base, AccountLocation(account.key, false)); val name = "family-${newId()}.db"
        val db = Room.databaseBuilder(context, WardrobeDatabase::class.java, name).build()
        try { val repo = Repository(db); repo.initialize("Home"); block(context, account, repo, PhotoStore(context)) }
        finally {
            db.close(); context.deleteDatabase(name); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
            File(context.noBackupFilesDir, "family-additions/${account.key}").deleteRecursively()
        }
    }
    private fun png(photos: PhotoStore): String {
        val id = newId(); val bitmap = Bitmap.createBitmap(5, 4, Bitmap.Config.ARGB_8888)
        try { photos.file(id, "png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        return id
    }

    @Test fun ownerCopyIsAPortableBackupThatRestoresAsAnIndependentWardrobe() = scenario { context, account, repo, photos ->
        // The shared wardrobe as a read view holds it: its own isolated photo store, never the account's photos.
        val view = FamilyAddStore(context, account).photos(newId())
        val shared = Wardrobe(name = "Family")
        val shirt = Garment(wardrobeId = shared.id, name = "Shirt", category = "top", color = "white", photoId = png(view))
        val jeans = Garment(wardrobeId = shared.id, name = "Jeans", category = "bottom", color = "navy", photoId = png(view), location = "Шафа")
        val look = Outfit(newId(), shared.id, "Weekend", listOf(shirt.id, jeans.id).sorted().joinToString("|"), 3)
        val snapshot = Snapshot(wardrobes = listOf(shared), garments = listOf(shirt, jeans), outfits = listOf(look),
            parts = listOf(OutfitPart(look.id, shirt.id), OutfitPart(look.id, jeans.id)))
        val before = repo.snapshot()
        val file = File(context.cacheDir, "family-copy.zip"); val uri = Uri.fromFile(file)
        Backup(context, repo, view).exportSnapshot(uri, snapshot)
        ZipFile(file).use { zip ->
            assertEquals(setOf("manifest.json") + snapshot.photoIds().map { "photos/$it.png" }, zip.entries().asSequence().map { it.name }.toSet())
            val json = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use { it.readText() })
            assertEquals("family-wardrobe", json.getString("format"))
            for (key in listOf("remoteId", "ownerUid", "members", "baseRevision")) assertFalse(key, json.has(key))
            assertEquals(snapshot, Backup(context, repo, photos).decode(json))
        }
        val backup = Backup(context, repo, photos)
        assertEquals(1, backup.verify(uri).wardrobes)
        val restoredId = requireNotNull(backup.restore(uri)); val after = repo.snapshot()
        assertEquals(before.wardrobes.size + 1, after.wardrobes.size)
        before.wardrobes.forEach { assertEquals(WardrobeSnapshots.select(before, it.id), WardrobeSnapshots.select(after, it.id)) }
        val restored = WardrobeSnapshots.select(after, restoredId)
        assertNotEquals(shared.id, restoredId)
        assertEquals(setOf("Shirt", "Jeans"), restored.garments.map { it.name }.toSet())
        assertTrue(restored.garments.none { it.id in setOf(shirt.id, jeans.id) })
        assertEquals("Шафа", restored.garments.single { it.name == "Jeans" }.location)
        assertEquals(restored.garments.map { it.id }.sorted().joinToString("|"), restored.outfits.single().signature)
        assertTrue(restored.photoIds().all { photos.currentFile(it).isFile })
        // A restore never creates family state: no transfer journal, no sending queue, no read cache.
        assertTrue(FamilyMigrationStore(context, account).records().isEmpty())
        assertTrue(FamilySyncStore(context, account).autoRemotes().isEmpty())
        BackupStateRules.validate(after)
    }

    @Test fun copyNeedsEveryPhotoOfTheView() = scenario { context, account, repo, _ ->
        val view = FamilyAddStore(context, account).photos(newId())
        val shared = Wardrobe(name = "Family")
        val shirt = Garment(wardrobeId = shared.id, name = "Shirt", category = "top", color = "white", photoId = newId())
        val file = File(context.cacheDir, "broken-copy.zip")
        try { Backup(context, repo, view).exportSnapshot(Uri.fromFile(file), Snapshot(wardrobes = listOf(shared), garments = listOf(shirt))); fail("Missing photo exported") }
        catch (_: IllegalArgumentException) {}
        assertTrue(!file.exists() || file.length() == 0L)
    }
}
