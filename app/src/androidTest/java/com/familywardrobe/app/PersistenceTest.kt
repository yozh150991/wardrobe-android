package com.familywardrobe.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private lateinit var context: Context
    private lateinit var db: WardrobeDatabase
    private lateinit var repo: Repository
    private lateinit var photos: PhotoStore
    private lateinit var dbName: String
    private val createdPhotos = mutableSetOf<String>()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "test-${newId()}.db"
        db = Room.databaseBuilder(context, WardrobeDatabase::class.java, dbName).build()
        repo = Repository(db); photos = PhotoStore(context)
        runBlocking { repo.initialize("My wardrobe") }
    }
    @After fun cleanup() {
        runBlocking { createdPhotos.addAll(repo.dao.allGarments().map { it.photoId }) }
        db.close(); context.deleteDatabase(dbName)
        runBlocking { photos.remove(createdPhotos) }
    }
    private suspend fun garment(category: String): Garment {
        val source = File(context.cacheDir, "test-photo-${newId()}.png")
        val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val photo = photos.import(Uri.fromFile(source)); source.delete(); createdPhotos.add(photo)
        return Garment(wardrobeId = Repository.DEFAULT_WARDROBE, name = category, category = category, color = "beige", photoId = photo)
            .also { repo.save(it) }
    }
    @Test fun databaseAndPrivatePhotoSurviveReopening() = runBlocking {
        val item = garment("top")
        db.close()
        db = Room.databaseBuilder(context, WardrobeDatabase::class.java, dbName).build(); repo = Repository(db)
        assertEquals(item, repo.dao.garment(item.id))
        assertTrue(photos.file(item.photoId).length() > 0)
        assertNotNull(photos.bitmap(item.photoId))
    }
    @Test fun undoOutfitPreservesIndependentAndOverlappingWearRecords() = runBlocking {
        val a = garment("top"); val b = garment("bottom"); val c = garment("shoes"); val d = garment("top")
        val first = repo.saveOutfit(a.wardrobeId, listOf(a.id, b.id, c.id), "First")
        val second = repo.saveOutfit(a.wardrobeId, listOf(d.id, b.id, c.id), "Second")
        val date = "2026-09-21"
        repo.toggleItemWear(a.id, date); repo.toggleOutfitWear(first, date); repo.toggleOutfitWear(second, date)
        repo.toggleOutfitWear(first, date)
        val remaining = repo.dao.allWears()
        assertTrue(remaining.any { it.garmentId == a.id && it.source == "item:${a.id}" })
        assertEquals(3, remaining.count { it.source == "outfit:$second" })
        assertFalse(remaining.any { it.source == "outfit:$first" })
    }
    @Test fun repeatedOutfitSaveReturnsSameIdentity() = runBlocking {
        val ids = listOf(garment("top").id, garment("bottom").id, garment("shoes").id)
        val one = repo.saveOutfit(Repository.DEFAULT_WARDROBE, ids, "One")
        val two = repo.saveOutfit(Repository.DEFAULT_WARDROBE, ids.reversed(), "Two")
        assertEquals(one, two); assertEquals(1, repo.dao.allOutfits().size)
    }
    @Test fun backupRestoresPhotosIntoNewWardrobeWithoutOverwritingOriginal() = runBlocking {
        val original = garment("top")
        val file = File(context.cacheDir, "test-backup-${newId()}.zip")
        try {
            val backup = Backup(context, repo, photos)
            backup.export(Uri.fromFile(file)); backup.restore(Uri.fromFile(file))
            val restored = repo.dao.allGarments().single { it.id != original.id }
            assertNotEquals(original.wardrobeId, restored.wardrobeId)
            assertNotEquals(original.photoId, restored.photoId)
            assertEquals(original, repo.dao.garment(original.id))
            assertArrayEquals(photos.file(original.photoId).readBytes(), photos.file(restored.photoId).readBytes())
        } finally { file.delete() }
    }
    @Test fun invalidBackupDoesNotModifyExistingData() = runBlocking {
        val original = garment("top")
        val file = File(context.cacheDir, "test-invalid-${newId()}.zip").apply { writeText("not a backup") }
        try {
            var rejected = false
            try { Backup(context, repo, photos).restore(Uri.fromFile(file)) } catch (_: Exception) { rejected = true }
            assertTrue(rejected); assertEquals(listOf(original), repo.dao.allGarments())
            assertEquals(1, repo.dao.allWardrobes().size)
        } finally { file.delete() }
    }
    @Test fun deletingWardrobeRemovesItsDependentsAndPrivatePhotosOnly() = runBlocking {
        val a = garment("top"); val b = garment("bottom"); val c = garment("shoes")
        val outfit = repo.saveOutfit(a.wardrobeId, listOf(a.id, b.id, c.id), "Delete me")
        repo.toggleOutfitWear(outfit)
        repo.dao.insertWish(Wish(wardrobeId = a.wardrobeId, name = "Delete wish", category = "top"))
        val keep = Wardrobe(name = "Keep me")
        repo.dao.insertWardrobe(keep)
        val survivor = a.copy(id = newId(), wardrobeId = keep.id)
        repo.save(survivor) // Deliberately share a photo: the remaining reference protects it.
        repo.toggleItemWear(survivor.id)
        val wish = Wish(wardrobeId = keep.id, name = "Keep wish", category = "shoes")
        repo.dao.insertWish(wish)
        val result = repo.deleteWardrobe(Repository.DEFAULT_WARDROBE, photos)
        assertEquals(keep.id, result.nextWardrobeId)
        assertTrue(result.photosRemoved)
        assertEquals(listOf(keep), repo.dao.allWardrobes())
        assertEquals(listOf(survivor), repo.dao.allGarments())
        assertTrue(repo.dao.allOutfits().isEmpty()); assertTrue(repo.dao.allParts().isEmpty())
        assertEquals(listOf(wish), repo.dao.allWishes())
        assertEquals(listOf(survivor.id), repo.dao.allWears().map { it.garmentId })
        assertTrue(photos.file(a.photoId).exists())
        assertFalse(photos.file(b.photoId).exists())
        assertFalse(photos.file(c.photoId).exists())
    }
    @Test fun lastWardrobeCannotBeDeletedAndPhotosRemain() = runBlocking {
        val item = garment("top")
        var rejected = false
        try { repo.deleteWardrobe(item.wardrobeId, photos) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals(item, repo.dao.garment(item.id))
        assertTrue(photos.file(item.photoId).exists())
        assertEquals(1, repo.dao.allWardrobes().size)
    }
    @Test fun failedWardrobeDeletionRollsBackAndKeepsPhotos() = runBlocking {
        val a = garment("top"); val b = garment("bottom"); val c = garment("shoes")
        val outfit = repo.saveOutfit(a.wardrobeId, listOf(a.id, b.id, c.id), "Keep after failure")
        val other = Wardrobe(name = "Other"); repo.dao.insertWardrobe(other)
        // Inject a storage failure after the outfit deletion, during garment deletion.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_delete BEFORE DELETE ON garments BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        var rejected = false
        try { repo.deleteWardrobe(a.wardrobeId, photos) } catch (_: Exception) { rejected = true }
        finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delete") }
        assertTrue(rejected)
        assertEquals(outfit, repo.dao.allOutfits().single().id)
        assertEquals(3, repo.dao.allParts().size)
        assertEquals(3, repo.dao.allGarments().size)
        assertEquals(2, repo.dao.allWardrobes().size)
        assertTrue(photos.file(a.photoId).exists())
    }
    @Test fun renamedDefaultWardrobeKeepsCustomNameAfterReopen() = runBlocking {
        val original = garment("top")
        repo.renameWardrobe(Repository.DEFAULT_WARDROBE, "  Family clothes  ")
        db.close()
        db = Room.databaseBuilder(context, WardrobeDatabase::class.java, dbName).build(); repo = Repository(db)
        val w = repo.dao.allWardrobes().single()
        assertEquals("Family clothes", w.name)
        assertFalse(w.usesDefaultName)
        assertEquals(original, repo.dao.garment(original.id))
    }
    @Test fun repeatedRestoreNamesAreDistinctAndPreserveArchivedState() = runBlocking {
        val original = garment("top")
        repo.update(original.id) { it.copy(archived = true) }
        val file = File(context.cacheDir, "test-repeat-${newId()}.zip")
        try {
            val backup = Backup(context, repo, photos)
            backup.export(Uri.fromFile(file))
            val one = backup.restore(Uri.fromFile(file), "restored", "My wardrobe")
            val two = backup.restore(Uri.fromFile(file), "restored", "My wardrobe")
            assertNotEquals(one, two)
            val names = repo.dao.allWardrobes().map { it.name }
            assertEquals(3, names.toSet().size)
            assertTrue(names.contains("My wardrobe — restored 1"))
            assertTrue(names.contains("My wardrobe — restored 2"))
            assertTrue(repo.dao.allGarments().all { it.archived })
        } finally { file.delete() }
    }
    @Test fun foundCandidateKeepsNeedIdentityHistoryBudgetAndOutfitReference() = runBlocking {
        val need = Wish(wardrobeId = Repository.DEFAULT_WARDROBE, name = "Rain jacket", category = "outerwear", needName = "Rain jacket",
            needCategory = "outerwear", requirements = "Waterproof", budgetMinor = 35000, budgetCurrency = "PLN")
        repo.saveWish(need, false, photos)
        val outfit = repo.saveLook(need.wardrobeId, listOf(LookRules.wanted(need)), "Plan", "everyday", "rain", "mixed")
        val candidate = need.copy(kind = "product", name = "Blue jacket", priceMinor = 8900, currency = "EUR", link = "https://example.org",
            color = "blue", inspiration = "Rainy walks", budgetMinor = 1, budgetCurrency = "USD", requirements = "changed")
        repo.saveWish(candidate, true, photos); repo.saveWish(candidate, true, photos)
        val saved = requireNotNull(repo.dao.wish(need.id))
        assertEquals(1, repo.dao.allWishes().size); assertEquals("product", saved.kind)
        assertEquals("Rain jacket", saved.needName); assertEquals("Waterproof", saved.requirements)
        assertEquals(35000L, saved.budgetMinor); assertEquals("PLN", saved.budgetCurrency)
        assertEquals(8900L, saved.priceMinor); assertEquals("EUR", saved.currency)
        assertEquals(need.id, repo.dao.allIdeas().single { it.outfitId == outfit }.wishId)
    }
    @Test fun backupRoundTripKeepsWishlistPhotoHistoryAndMixedOutfit() = runBlocking {
        val owned = garment("top")
        val product = Wish(wardrobeId = owned.wardrobeId, name = "Shoes", category = "shoes", kind = "product", priceMinor = 22900,
            link = "https://example.org", color = "white", inspiration = "Walking", photoId = owned.photoId,
            needName = "Comfortable shoes", needCategory = "shoes", requirements = "Light", budgetMinor = 35000)
        repo.saveWish(product, false, photos)
        repo.saveLook(owned.wardrobeId, listOf(LookRules.owned(owned), LookRules.wanted(product)), "Mixed", "work", "cool", "mixed")
        val file = File(context.cacheDir, "test-wishlist-${newId()}.zip")
        try {
            val backup = Backup(context, repo, photos); backup.export(Uri.fromFile(file)); val target = requireNotNull(backup.restore(Uri.fromFile(file)))
            val restored = repo.dao.allWishes().single { it.wardrobeId == target }
            createdPhotos.add(restored.photoId)
            assertEquals(product.priceMinor, restored.priceMinor); assertEquals(product.budgetMinor, restored.budgetMinor)
            assertEquals(product.needName, restored.needName); assertEquals(product.inspiration, restored.inspiration)
            assertNotEquals(product.id, restored.id); assertNotEquals(product.photoId, restored.photoId)
            val look = repo.dao.allOutfits().single { it.wardrobeId == target }
            assertEquals("mixed", look.source); assertEquals("cool", look.weather)
            assertEquals(restored.id, repo.dao.allIdeas().single { it.outfitId == look.id }.wishId)
        } finally { file.delete() }
    }
    @Test fun partialOutfitCanBeCompletedWithoutChangingItsId() = runBlocking {
        val top = garment("top"); val bottom = garment("bottom"); val shoes = garment("shoes")
        val id = repo.saveLook(top.wardrobeId, listOf(LookRules.owned(top)), "Partial", "everyday", "any_weather", "owned")
        val complete = repo.saveLook(top.wardrobeId, listOf(top, bottom, shoes).map(LookRules::owned), "Complete", "everyday", "any_weather", "owned", id)
        assertEquals(id, complete); assertEquals(1, repo.dao.allOutfits().size); assertEquals(3, repo.dao.outfitIds(id).size)
    }
    @Test fun plannedOutfitCannotRecordWear() = runBlocking {
        val product = Wish(wardrobeId = Repository.DEFAULT_WARDROBE, name = "Shoes", category = "shoes", kind = "product")
        repo.saveWish(product, false, photos)
        val id = repo.saveLook(product.wardrobeId, listOf(LookRules.wanted(product)), "Plan", "everyday", "any_weather", "mixed")
        var refused = false
        try { repo.toggleOutfitWear(id) } catch (_: IllegalArgumentException) { refused = true }
        assertTrue(refused); assertTrue(repo.dao.allWears().isEmpty())
    }
    @Test fun migrationFromVersionOnePreservesRowsAndMarksOnlyDefaultName() = runBlocking {
        // Use the actual exported v2 schema, never derive an older schema from the current v3 DB.
        val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val schema = org.json.JSONObject(testContext.assets.open("schema-2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        val statements = mutableListOf<String>()
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            statements.add(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
            entity.optJSONArray("indices")?.let { indices -> for (n in 0 until indices.length()) {
                statements.add(indices.getJSONObject(n).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
            } }
        }
        val legacyName = "legacy-${newId()}.db"
        var upgraded: WardrobeDatabase? = null
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(legacyName), null).use { legacy ->
                statements.forEach { sql ->
                    legacy.execSQL(sql.replace(Regex(",\\s*`?usesDefaultName`?\\s+INTEGER\\s+NOT NULL\\s+DEFAULT\\s+0"), ""))
                }
                legacy.execSQL("INSERT INTO wardrobes (id,name,createdAt) VALUES (?,?,?)", arrayOf(Repository.DEFAULT_WARDROBE, "Moja garderoba", 1L))
                legacy.execSQL("INSERT INTO wardrobes (id,name,createdAt) VALUES (?,?,?)", arrayOf(newId(), "Custom", 2L))
                legacy.version = 1
            }
            upgraded = Room.databaseBuilder(context, WardrobeDatabase::class.java, legacyName)
                .addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
            val rows = upgraded.dao().allWardrobes()
            assertEquals(2, rows.size)
            assertTrue(rows.single { it.id == Repository.DEFAULT_WARDROBE }.usesDefaultName)
            assertFalse(rows.single { it.name == "Custom" }.usesDefaultName)
        } finally { upgraded?.close(); context.deleteDatabase(legacyName) }
    }
}
