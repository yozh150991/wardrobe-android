package com.familywardrobe.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real Android checks; run with connectedDebugAndroidTest. Host checks do not execute this class. */
class ScenarioPersistenceTest {
    private val base: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun context() = AccountContext(base, AccountLocation(AuthRules.accountKey("scenario-tests", newId()), false))
    private fun open(name: String) = Room.databaseBuilder(base, WardrobeDatabase::class.java, name)
        .addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
    private fun photo(photos: PhotoStore): String {
        val id = newId(); val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        try { photos.file(id, "png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return id
    }
    private suspend fun product(repo: Repository, photos: PhotoStore): Wish {
        val need = Wish(wardrobeId = Repository.DEFAULT_WARDROBE, name = "Rain layer", category = "outerwear", needName = "Rain layer",
            needCategory = "outerwear", requirements = "Waterproof", budgetMinor = 9000, budgetCurrency = "EUR",
            photoId = photo(photos), link = "https://example.com/inspiration", inspiration = "Rain")
        repo.saveWish(need, false, photos)
        repo.saveWish(need.copy(name = "Candidate coat", kind = "product", photoId = photo(photos), link = "https://example.com/coat", priceMinor = 7900, currency = "EUR"), true, photos)
        return requireNotNull(repo.dao.wish(need.id))
    }

    @Test fun purchaseUndoAndOutfitReferencesSurviveDatabaseReopen() = runBlocking {
        val c = context(); val name = "scenario-${newId()}.db"; var db = open(name); val photos = PhotoStore(c)
        try {
            var repo = Repository(db); repo.initialize("Home")
            val wish = product(repo, photos)
            val look = repo.saveLook(wish.wardrobeId, listOf(LookRules.wanted(wish)), "Planned coat", "everyday", "rain", "mixed")
            val owned = Garment(id = wish.id, wardrobeId = wish.wardrobeId, name = wish.name, category = wish.category,
                color = "blue", photoId = wish.photoId, priceMinor = 7100, currency = wish.currency)
            repo.purchaseWish(wish.id, owned)
            assertEquals(7100L, repo.dao.allWishEvents().single { it.kind == "purchased" }.priceMinor)
            assertEquals(listOf(owned.id), repo.dao.outfitIds(look))
            db.close(); db = open(name); repo = Repository(db)
            val row = requireNotNull(repo.dao.purchaseUndoRow(wish.id))
            assertEquals(owned, GarmentReceipt.read(row.garmentJson))
            assertTrue(repo.undoPurchase(PurchaseUndo(wish.id, GarmentReceipt.read(row.garmentJson)), photos))
            assertNull(repo.dao.garment(owned.id)); assertNull(repo.dao.purchaseUndoRow(wish.id))
            assertEquals("", repo.dao.wish(wish.id)!!.ownedId)
            assertEquals(wish.id, repo.dao.allIdeas().single { it.outfitId == look }.wishId)
            assertEquals(listOf("candidate", "purchased", "purchase_cancelled"), repo.dao.allWishEvents().map { it.kind })
            assertTrue(photos.currentFile(wish.photoId).isFile)
            BackupStateRules.validate(repo.snapshot())
        } finally { db.close(); base.deleteDatabase(name); c.filesDir.deleteRecursively(); c.cacheDir.deleteRecursively() }
    }

    @Test fun candidateUndoKeepsOriginalNeedAndEveryCandidatePhotoInPortableBackup() = runBlocking {
        val c = context(); val name = "scenario-${newId()}.db"; val db = open(name); val photos = PhotoStore(c)
        try {
            val repo = Repository(db); repo.initialize("Home"); val candidate = product(repo, photos)
            assertTrue(repo.returnToNeed(candidate.id))
            val need = repo.dao.wish(candidate.id)!!
            assertEquals("need", need.kind); assertEquals("Rain layer", need.name); assertEquals("Waterproof", need.requirements)
            assertEquals(9000L, need.budgetMinor); assertEquals("EUR", need.budgetCurrency)
            assertEquals(candidate.needPhotoId, need.photoId); assertEquals(candidate.needLink, need.link)
            assertTrue(photos.currentFile(candidate.photoId).isFile)
            assertTrue(candidate.photoId in repo.snapshot().photoIds())
            val backup = Backup(c, repo, photos); val file = File(c.cacheDir, "history.zip")
            backup.export(Uri.fromFile(file)); val verification = backup.verify(Uri.fromFile(file))
            val restored = backup.restore(Uri.fromFile(file), expectedFingerprint = verification.fingerprint)!!
            val freshWish = repo.dao.allWishes().single { it.wardrobeId == restored }
            assertNotEquals(candidate.id, freshWish.id)
            val events = repo.dao.allWishEvents().filter { it.wishId == freshWish.id }
            assertEquals(setOf("candidate", "candidate_cancelled"), events.map { it.kind }.toSet())
            assertTrue(events.all { photos.currentFile(it.photoId).isFile })
            assertNotEquals(need.photoId, freshWish.photoId)
            BackupStateRules.validate(repo.snapshot())
        } finally { db.close(); base.deleteDatabase(name); c.filesDir.deleteRecursively(); c.cacheDir.deleteRecursively() }
    }

    @Test fun staleUndoNeverDeletesLaterEditsOrRecordedWear() = runBlocking {
        val c = context(); val name = "scenario-${newId()}.db"; val db = open(name); val photos = PhotoStore(c)
        try {
            val repo = Repository(db); repo.initialize("Home"); val wish = product(repo, photos)
            val owned = Garment(id = wish.id, wardrobeId = wish.wardrobeId, name = wish.name, category = wish.category, color = "blue", photoId = wish.photoId)
            val receipt = repo.purchaseWish(wish.id, owned).undo!!
            repo.save(owned.copy(name = "Later edit"))
            assertFalse(repo.undoPurchase(receipt, photos)); assertEquals("Later edit", repo.dao.garment(owned.id)!!.name)
            repo.save(owned); repo.toggleItemWear(owned.id, "2026-10-02")
            assertFalse(repo.undoPurchase(receipt, photos)); assertNotNull(repo.dao.garment(owned.id))
            assertNotNull(repo.dao.purchaseUndoRow(wish.id))
        } finally { db.close(); base.deleteDatabase(name); c.filesDir.deleteRecursively(); c.cacheDir.deleteRecursively() }
    }

    @Test fun cloudApplyKeepsStableIdsAndRejectsDraftsAndStaleLocalVersion() = runBlocking {
        val a = context(); val b = context(); val an = "scenario-${newId()}.db"; val bn = "scenario-${newId()}.db"
        val ad = open(an); val bd = open(bn); val ap = PhotoStore(a); val bp = PhotoStore(b)
        try {
            val ar = Repository(ad); val br = Repository(bd); ar.initialize("Home"); br.initialize("Home")
            product(ar, ap)
            val file = File(a.cacheDir, "cloud.zip"); val backup = Backup(a, ar, ap)
            backup.export(Uri.fromFile(file)); val v = backup.verify(Uri.fromFile(file))
            val remote = CloudHead(1, "users/test/snapshots/${newId()}.zip", v.fingerprint, v.wardrobes, v.garments, v.outfits)
            CloudArchive(b, br, bp).apply(file, remote, br.dao.cloudMeta()!!.localRevision) {}
            assertEquals(ar.snapshot(), br.snapshot()); assertFalse(br.dao.cloudMeta()!!.dirty); assertEquals(1L, br.dao.cloudMeta()!!.baseRevision)
            val before = br.snapshot(); val revision = br.dao.cloudMeta()!!.localRevision
            val draft = JSONObject().put("kind", "wish").put("wardrobe", Repository.DEFAULT_WARDROBE).put("fields", JSONObject().put("name", "Unsaved"))
            assertTrue(EditorDraftStore(b).write(draft))
            try { CloudArchive(b, br, bp).apply(file, remote.copy(revision = 2), revision) {}; fail("Draft must block replacement") } catch (_: CloudDraftException) { }
            assertEquals(before, br.snapshot()); assertTrue(EditorDraftStore(b).clear())
            br.dao.renameWardrobe(Repository.DEFAULT_WARDROBE, "Changed while reviewing")
            val edited = br.snapshot()
            try { CloudArchive(b, br, bp).apply(file, remote.copy(revision = 2), revision) {}; fail("Stale selection must fail") } catch (_: CloudChangedException) { }
            assertEquals(edited, br.snapshot()); assertTrue(br.dao.cloudMeta()!!.dirty)
        } finally { ad.close(); bd.close(); base.deleteDatabase(an); base.deleteDatabase(bn); listOf(a,b).forEach { it.filesDir.deleteRecursively(); it.cacheDir.deleteRecursively() } }
    }
}
