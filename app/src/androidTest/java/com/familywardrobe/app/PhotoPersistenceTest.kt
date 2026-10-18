package com.familywardrobe.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class PhotoPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: WardrobeDatabase
    private lateinit var repo: Repository
    private lateinit var photos: PhotoStore
    private lateinit var dbName: String
    private val photoIds = mutableSetOf<String>()
    private val tempFiles = mutableListOf<File>()
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbName = "photo-test-${newId()}.db"
        db = Room.databaseBuilder(context, WardrobeDatabase::class.java, dbName).build()
        repo = Repository(db); photos = PhotoStore(context)
        runBlocking { repo.initialize("My wardrobe") }
    }
    @After fun cleanup() {
        runBlocking { photoIds.addAll(repo.dao.allGarments().map { it.photoId }); photos.remove(photoIds) }
        db.close(); context.deleteDatabase(dbName); tempFiles.forEach { it.delete() }
    }
    private fun temporary(extension: String) = File(context.cacheDir, "photo-test-${newId()}.$extension").also { tempFiles.add(it) }
    private fun png(cutout: Boolean = false): File {
        val file = temporary("png")
        val bitmap = Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        if (cutout) for (y in 0 until 30) for (x in 0 until 10) bitmap.setPixel(x, y, Color.TRANSPARENT)
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap.recycle()
        return file
    }
    private suspend fun garment(): Garment {
        val id = photos.import(Uri.fromFile(png())).also { photoIds.add(it) }
        return Garment(wardrobeId = Repository.DEFAULT_WARDROBE, name = "Red jacket", category = "top", color = "red", photoId = id)
            .also { repo.save(it) }
    }
    private suspend fun cutout(id: String) = photos.copyVariant(id, png(true)).also { photoIds.add(it) }
    private fun readZip(file: File): MutableMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(file.inputStream()).use { input ->
            while (true) { val entry = input.nextEntry ?: break; entries[entry.name] = input.readBytes(); input.closeEntry() }
        }
        return entries
    }
    private fun writeZip(entries: Map<String, ByteArray>): File = temporary("zip").also { file ->
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, bytes) -> output.putNextEntry(ZipEntry(name)); output.write(bytes); output.closeEntry() }
        }
    }
    @Test fun importKeepsOnePhotoAndDoesNotModifyGallerySource() = runBlocking {
        val source = png(); val bytes = source.readBytes()
        val id = photos.import(Uri.fromFile(source)).also { photoIds.add(it) }
        assertTrue(photos.file(id).exists()); assertFalse(photos.file(id, "original").exists())
        assertFalse(photos.file(id, "png").exists()); assertArrayEquals(bytes, source.readBytes())
    }
    @Test fun cancelCandidateLeavesSavedPhotoUnchanged() = runBlocking {
        val item = garment(); val bytes = photos.currentFile(item.photoId).readBytes()
        val candidate = cutout(item.photoId)
        assertEquals(item, repo.dao.garment(item.id))
        assertTrue(repo.discardPhotos(setOf(candidate), photos))
        assertFalse(photos.currentFile(candidate).exists())
        assertArrayEquals(bytes, photos.currentFile(item.photoId).readBytes())
    }
    @Test fun savingCutoutRemovesOldPhotoAndStoresOnlyPng() = runBlocking {
        val item = garment(); val candidate = cutout(item.photoId)
        assertTrue(repo.save(item.copy(photoId = candidate), photos))
        assertFalse(photos.currentFile(item.photoId).exists())
        assertTrue(photos.file(candidate, "png").exists())
        assertFalse(photos.file(candidate).exists()); assertFalse(photos.file(candidate, "original").exists())
    }
    @Test fun rotationAndCropPreserveTransparencyAndExpectedPixels() = runBlocking {
        val item = garment(); val candidate = cutout(item.photoId)
        val result = photos.edit(candidate, 1, 0f, 0f, .5f, 1f).also { photoIds.add(it) }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(photos.currentFile(result).path))
        // A tiny source keeps its real detail instead of being enlarged to 1024.
        val side = PhotoResolutionRules.canvas(intArrayOf(10, 0, 20, 30), photos.finalResolution, .08f)
        assertEquals(PhotoGeometry.crop(side, side, 0f, 0f, .5f, 1f)[2], bitmap.width); assertEquals(side, bitmap.height)
        // Normalized cutouts keep transparent padding and preserve the garment after rotation.
        assertEquals(0, bitmap.getPixel(0, 0) ushr 24)
        assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        bitmap.recycle()
        assertTrue(photos.currentFile(candidate).exists()) // Still a draft, not committed.
    }
    @Test fun versionEightBackupHasOnlyCurrentPhotoAndRestoresIt() = runBlocking {
        val item = garment(); val candidate = cutout(item.photoId); repo.save(item.copy(photoId = candidate), photos)
        val zip = temporary("zip"); val backup = Backup(context, repo, photos); backup.export(Uri.fromFile(zip))
        val entries = readZip(zip)
        assertEquals(setOf("manifest.json", "photos/$candidate.png"), entries.keys)
        assertEquals(8, JSONObject(String(entries.getValue("manifest.json"), Charsets.UTF_8)).getInt("version"))
        backup.restore(Uri.fromFile(zip))
        val imported = repo.dao.allGarments().single { it.id != item.id }
        assertArrayEquals(photos.currentFile(candidate).readBytes(), photos.currentFile(imported.photoId).readBytes())
        assertFalse(photos.file(imported.photoId, "original").exists()); assertFalse(photos.file(imported.photoId).exists())
    }
    @Test fun legacyBackupsRestoreOnlyTheirDisplayedPhoto() = runBlocking {
        val item = garment(); val backup = Backup(context, repo, photos)
        val current = temporary("zip"); backup.export(Uri.fromFile(current))
        val originalEntries = readZip(current)
        for (version in 1..2) {
            val entries = originalEntries.toMutableMap()
            val metadata = JSONObject(String(entries.getValue("manifest.json"), Charsets.UTF_8)).apply {
                put("version", version); remove("photoFiles")
                if (version == 2) put("cutouts", JSONArray().put(item.photoId))
            }
            entries["manifest.json"] = metadata.toString().toByteArray(Charsets.UTF_8)
            entries["photos/${item.photoId}.original"] = png().readBytes()
            if (version == 2) entries["photos/${item.photoId}.png"] = png(true).readBytes()
            val wardrobe = backup.restore(Uri.fromFile(writeZip(entries)))
            val imported = repo.dao.allGarments().single { it.wardrobeId == wardrobe }
            val extension = if (version == 1) "jpg" else "png"
            assertArrayEquals(entries.getValue("photos/${item.photoId}.$extension"), photos.currentFile(imported.photoId).readBytes())
            assertFalse(photos.file(imported.photoId, "original").exists())
            if (version == 2) assertFalse(photos.file(imported.photoId).exists())
        }
    }
    @Test fun missingPhotoRejectsBackupWithoutChangingDatabaseOrFiles() = runBlocking {
        val item = garment(); val backup = Backup(context, repo, photos); val zip = temporary("zip")
        backup.export(Uri.fromFile(zip)); val entries = readZip(zip)
        entries.remove("photos/${item.photoId}.jpg")
        val before = photos.directory.list()?.toSet(); var rejected = false
        try { backup.restore(Uri.fromFile(writeZip(entries))) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected); assertEquals(listOf(item), repo.dao.allGarments())
        assertEquals(before, photos.directory.list()?.toSet())
    }
    @Test fun invalidCropDoesNotCreateFilesOrChangeSavedItem() = runBlocking {
        val item = garment(); val before = photos.directory.list()?.toSet(); var rejected = false
        try { photos.edit(item.photoId, 1, .8f, 0f, .2f, 1f) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected); assertEquals(item, repo.dao.garment(item.id))
        assertEquals(before, photos.directory.list()?.toSet())
    }
    @Test fun oldOriginalAndHiddenJpegRemovedAfterValidatingCurrentPng() = runBlocking {
        val item = garment(); val bytes = png(true).readBytes()
        photos.file(item.photoId, "original").writeBytes(png().readBytes())
        photos.file(item.photoId, "png").writeBytes(bytes)
        assertTrue(photos.removeLegacyCopies())
        assertFalse(photos.file(item.photoId, "original").exists()); assertFalse(photos.file(item.photoId).exists())
        assertArrayEquals(bytes, photos.currentFile(item.photoId).readBytes())
    }
    @Test fun sharedPhotoRemainsWhileAnotherItemUsesIt() = runBlocking {
        val item = garment(); val another = item.copy(id = newId(), name = "Another")
        repo.save(another); val candidate = cutout(item.photoId)
        repo.save(item.copy(photoId = candidate), photos)
        assertTrue(photos.currentFile(another.photoId).exists())
        assertEquals(another, repo.dao.garment(another.id))
    }
    @Test fun failedDatabaseSaveKeepsPreviousPhoto() = runBlocking {
        val item = garment(); val candidate = cutout(item.photoId)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_photo_save BEFORE INSERT ON garments BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_photo_update BEFORE UPDATE ON garments BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        var rejected = false
        try { repo.save(item.copy(photoId = candidate), photos) } catch (_: Exception) { rejected = true }
        finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_photo_save")
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_photo_update")
        }
        assertTrue(rejected); assertEquals(item, repo.dao.garment(item.id)); assertTrue(photos.currentFile(item.photoId).exists())
    }
    @Test fun croppedPhotoSurvivesDatabaseReopen() = runBlocking {
        val item = garment()
        val next = photos.edit(item.photoId, -1, 0f, 0f, .5f, 1f).also { photoIds.add(it) }
        repo.save(item.copy(photoId = next), photos)
        db.close(); db = Room.databaseBuilder(context, WardrobeDatabase::class.java, dbName).build(); repo = Repository(db)
        val saved = requireNotNull(repo.dao.garment(item.id))
        val bitmap = requireNotNull(PhotoStore(context).bitmap(saved.photoId))
        assertEquals(15, bitmap.width); assertEquals(20, bitmap.height)
        assertFalse(photos.currentFile(item.photoId).exists())
    }
}
