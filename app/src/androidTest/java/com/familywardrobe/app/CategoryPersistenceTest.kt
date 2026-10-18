package com.familywardrobe.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Native migration/backup/cloud acceptance. Must be run on Android; host checks do not run this. */
class CategoryPersistenceTest {
    private fun scenario(block: suspend (AccountContext, Repository, PhotoStore) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = AccountContext(base, AccountLocation(AuthRules.accountKey("category-tests", newId()), false))
        val name = "categories-${newId()}.db"
        val db = Room.databaseBuilder(context, WardrobeDatabase::class.java, name).build()
        val photos = PhotoStore(context)
        try { val repo = Repository(db); repo.initialize("Home"); block(context, repo, photos) }
        finally { db.close(); context.deleteDatabase(name); photos.clearMemoryCache(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively() }
    }
    private fun item(photos: PhotoStore, custom: CustomCategory): Garment {
        val photo = newId()
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff224466.toInt()) }
        try { photos.file(photo, "png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return Garment(wardrobeId = custom.wardrobeId, name = "Trail shoes", category = custom.baseCategory,
            customCategoryId = custom.id, subcategory = "Трейлові кросівки", photoId = photo, color = "navy")
    }
    @Test fun categoriesDeduplicateWithinWardrobeAndKeepMatchingType() = scenario { _, repo, photos ->
        val a = repo.addCustomCategory(Repository.DEFAULT_WARDROBE, "  Для\u00a0бігу ", "shoes")
        val duplicate = repo.addCustomCategory(a.wardrobeId, "ДЛЯ БІГУ", "shoes")
        assertEquals(a.id, duplicate.id); assertEquals("Для бігу", a.name)
        repo.addWardrobe("Other")
        val other = repo.dao.allWardrobes().single { it.id != a.wardrobeId }
        val b = repo.addCustomCategory(other.id, "Для бігу", "shoes")
        assertNotEquals(a.id, b.id)
        val garment = item(photos, a); repo.save(garment)
        assertEquals("shoes", garment.rulePiece().category)
        assertEquals(listOf(garment.subcategory), subcategoryOptions(repo.dao.allGarments(), a.wardrobeId, a.id))
        assertTrue(subcategoryOptions(repo.dao.allGarments(), other.id, b.id).isEmpty())
        var rejected = false
        try { repo.save(garment.copy(customCategoryId = b.id)) } catch (_: Exception) { rejected = true }
        assertTrue(rejected); assertEquals(garment, repo.dao.garment(garment.id))
        BackupStateRules.validate(repo.snapshot())
    }
    @Test fun backupRestoreRemapsCategoryAndPurchaseReceiptTogether() = scenario { context, repo, photos ->
        val custom = repo.addCustomCategory(Repository.DEFAULT_WARDROBE, "Для бігу", "shoes")
        val garment = item(photos, custom)
        val wish = Wish(id = garment.id, wardrobeId = custom.wardrobeId, name = "Shoes", category = "shoes", kind = "product")
        repo.dao.insertWish(wish); repo.purchaseWish(wish.id, garment)
        val backup = Backup(context, repo, photos)
        val archive = File(context.cacheDir, "categories.zip")
        backup.export(Uri.fromFile(archive))
        val restoredId = requireNotNull(backup.restore(Uri.fromFile(archive)))
        val state = repo.snapshot()
        val restored = state.garments.single { it.wardrobeId == restoredId }
        val category = requireNotNull(state.customCategory(restored))
        assertNotEquals(custom.id, category.id); assertEquals(custom.name, category.name)
        assertEquals(restoredId, category.wardrobeId); assertEquals(garment.subcategory, restored.subcategory)
        val restoredWish = state.wishes.single { it.wardrobeId == restoredId }
        val receipt = state.purchaseUndos.single { it.wishId == restoredWish.id }
        assertEquals(restored, GarmentReceipt.read(receipt.garmentJson))
        assertNull(purchaseUndoReason(restoredWish, receipt, state))
        BackupStateRules.validate(state)
    }
    @Test fun cloudSnapshotTransfersEmptyCategoriesAndStableReferences() = scenario { a, ar, ap ->
        val category = ar.addCustomCategory(Repository.DEFAULT_WARDROBE, "Running", "shoes")
        ar.addCustomCategory(Repository.DEFAULT_WARDROBE, "Travel", "top")
        ar.save(item(ap, category))
        val archive = File(a.cacheDir, "cloud.zip"); val backup = Backup(a, ar, ap)
        backup.export(Uri.fromFile(archive)); val v = backup.verify(Uri.fromFile(archive))
        // Separate account-scoped context and Room DB; only explicit snapshot application transfers records.
        scenario { b, br, bp ->
            assertTrue(br.dao.allCustomCategories().isEmpty())
            val head = CloudHead(1, "users/test/snapshots/${newId()}.zip", v.fingerprint, v.wardrobes, v.garments, v.outfits)
            CloudArchive(b, br, bp).apply(archive, head, br.dao.cloudMeta()!!.localRevision) {}
            assertEquals(ar.snapshot(), br.snapshot()); assertFalse(br.dao.cloudMeta()!!.dirty)
        }
    }
    @Test fun legacyV8ManifestAndReceiptDefaultToNoCustomCategory() = scenario { context, repo, photos ->
        val custom = repo.addCustomCategory(Repository.DEFAULT_WARDROBE, "Running", "shoes")
        val garment = item(photos, custom).copy(customCategoryId = null)
        repo.save(garment)
        val backup = Backup(context, repo, photos)
        val legacy = backup.encode(repo.snapshot()).put("version", 8)
        legacy.remove("customCategories")
        legacy.getJSONArray("garments").getJSONObject(0).remove("customCategoryId")
        val restored = backup.decode(legacy)
        assertTrue(restored.customCategories.isEmpty()); assertNull(restored.garments.single().customCategoryId)
        val receipt = JSONObject(GarmentReceipt.write(garment)).apply { remove("customCategoryId") }
        assertEquals(garment, GarmentReceipt.read(receipt.toString()))
    }
    @Test fun malformedCategoryReferenceFailsBeforeRestoreWrites() = scenario { context, repo, photos ->
        val category = repo.addCustomCategory(Repository.DEFAULT_WARDROBE, "Running", "shoes")
        repo.save(item(photos, category))
        val before = repo.snapshot(); val backup = Backup(context, repo, photos)
        val json = backup.encode(before)
        json.getJSONArray("customCategories").getJSONObject(0).put("baseCategory", "top")
        var rejected = false
        try { backup.decode(json) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected); assertEquals(before, repo.snapshot())
    }
    @Test fun actualRoomUpgradeFromV8KeepsExistingItemAndValidatesCurrentSchema() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "category-upgrade-${newId()}.db"
        val ward = newId(); val garment = newId(); val photo = newId()
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("schema-2.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(base)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i); val table = e.getString("tableName")
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = e.getJSONArray("indices")
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                    WardrobeDatabase.ALL_MIGRATIONS.filter { it.startVersion in 2..7 }.forEach { it.migrate(db) }
                    db.execSQL("INSERT INTO wardrobes(id,name,createdAt,usesDefaultName,noRepeatDays) VALUES (?, 'Legacy', 1, 0, 0)", arrayOf(ward))
                    db.execSQL("INSERT INTO garments(id,wardrobeId,name,category,color,size,location,description,photoId,season,style,availability,favorite,rarelyWorn,archived,createdAt,subcategory) VALUES (?,?,'Legacy shoes','shoes','navy','','','',?,'all','casual','available',0,0,0,1,'Trail')", arrayOf(garment, ward, photo))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture only")
            }).build())
        var db: WardrobeDatabase? = null
        try {
            helper.writableDatabase; helper.close()
            val upgraded = Room.databaseBuilder(base, WardrobeDatabase::class.java, name)
                .addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build(); db = upgraded
            val repo = Repository(upgraded); repo.initialize("Home")
            val restored = requireNotNull(repo.dao.garment(garment))
            assertEquals("Trail", restored.subcategory); assertNull(restored.customCategoryId)
            val custom = repo.addCustomCategory(ward, "Trail gear", "shoes")
            repo.save(restored.copy(customCategoryId = custom.id))
            assertEquals(custom.id, repo.dao.garment(garment)!!.customCategoryId)
        } finally { helper.close(); db?.close(); base.deleteDatabase(name) }
    }
}
