package com.familywardrobe.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Native cache/decoder/photo/backup integration. Must run on Android; host tests do not replace this. */
class FamilyReadCacheTest {
    private fun scenario(block: suspend (AccountContext, AuthIdentity, Repository, PhotoStore, FamilyReadCache) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val identity = AuthIdentity("family-read-tests", newId(), "reader@example.test")
        val context = AccountContext(base, AccountLocation(identity.key, false))
        val dbName = "family-read-${newId()}.db"
        val db = Room.databaseBuilder(context, WardrobeDatabase::class.java, dbName).build()
        val photos = PhotoStore(context)
        val cache = FamilyReadCache(context, newId())
        try {
            val repo = Repository(db); repo.initialize("Personal")
            block(context, identity, repo, photos, cache)
        } finally {
            cache.close(); db.close(); context.deleteDatabase(dbName)
            photos.clearMemoryCache(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
        }
    }
    private fun png(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
    private fun archive(cache: FamilyReadCache, text: String, files: Map<String, File>): File {
        val file = cache.newDownload()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            files.forEach { (name, source) -> zip.putNextEntry(ZipEntry(name)); source.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        }
        return file
    }
    private fun remote(identity: FamilyArchiveIdentity, file: File) = FamilyRemote(identity, 1,
        newId().let { id -> FamilyHead(1, 0, id, FamilyRules.archivePath(identity.remoteId, id), FamilyCacheZip.hash(file) {}, file.length(), identity.ownerUid) }, "viewer")

    @Test fun matchingPhotoIdsStayIsolatedAndPrivateBackupNeverIncludesSharedBytes() = scenario { context, account, repo, photos, cache ->
        val wardrobe = repo.dao.allWardrobes().single()
        val photoId = newId(); png(photos.file(photoId, "png"), 0xff00ff00.toInt())
        val garment = Garment(wardrobeId = wardrobe.id, name = "Private", category = "top", color = "olive", photoId = photoId)
        repo.save(garment, photos)
        val before = repo.snapshot(); val privateBytes = photos.currentFile(photoId).readBytes()
        val shared = before.copy(garments = listOf(garment.copy(name = "Shared", color = "red")))
        val identity = FamilyArchiveIdentity(newId(), wardrobe.id, "other-owner")
        val envelope = FamilyEnvelope(Backup(context, repo, photos))
        val red = File(context.cacheDir, "shared.png"); png(red, 0xffff0000.toInt())
        val zip = archive(cache, envelope.encode(shared, identity, 0), mapOf("photos/$photoId.png" to red))
        val candidate = cache.decode(zip, remote(identity, zip), envelope) {}
        assertEquals(shared, candidate.snapshot)
        assertNotEquals(photos.directory.canonicalPath, candidate.photos.directory.canonicalPath)
        assertTrue(candidate.directory.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + "/"))
        assertEquals(before, repo.snapshot()); assertArrayEquals(privateBytes, photos.currentFile(photoId).readBytes())
        assertArrayEquals(red.readBytes(), candidate.photos.currentFile(photoId).readBytes())
        val backupFile = File(context.cacheDir, "private.zip")
        Backup(context, repo, photos).export(Uri.fromFile(backupFile))
        java.util.zip.ZipFile(backupFile).use { backup ->
            assertTrue(backup.entries().asSequence().none { it.name.contains("family-read") })
            assertArrayEquals(privateBytes, backup.getInputStream(backup.getEntry("photos/$photoId.png")).use { it.readBytes() })
        }
        cache.discard(candidate); assertFalse(candidate.directory.exists())
        assertArrayEquals(privateBytes, photos.currentFile(photoId).readBytes())
    }
    @Test fun malformedPhotoAndExtraFilesCannotInstallACandidate() = scenario { context, account, repo, photos, cache ->
        val wardrobe = repo.dao.allWardrobes().single(); val photoId = newId()
        png(photos.file(photoId, "png"), 0xff00ff00.toInt())
        repo.save(Garment(wardrobeId = wardrobe.id, name = "Private", category = "top", color = "olive", photoId = photoId), photos)
        val before = repo.snapshot(); val identity = FamilyArchiveIdentity(newId(), wardrobe.id, account.uid)
        val envelope = FamilyEnvelope(Backup(context, repo, photos)); val text = envelope.encode(before, identity, 0)
        val broken = File(context.cacheDir, "bad.png").apply { writeText("not an image") }
        for (files in listOf(mapOf("photos/$photoId.png" to broken), mapOf("photos/$photoId.png" to photos.currentFile(photoId), "photos/${newId()}.png" to photos.currentFile(photoId)))) {
            val zip = archive(cache, text, files)
            try { cache.decode(zip, remote(identity, zip), envelope) {}; fail("Invalid shared bytes") }
            catch (_: IllegalArgumentException) {}
            assertTrue(zip.parentFile!!.listFiles().orEmpty().none { it.name.startsWith("view-") })
        }
        assertEquals(before, repo.snapshot())
    }
    @Test fun cacheCloseAndNewSessionRemovePreviousBytes() = scenario { context, account, repo, photos, cache ->
        val before = repo.snapshot(); val identity = FamilyArchiveIdentity(newId(), before.wardrobes.single().id, account.uid)
        val envelope = FamilyEnvelope(Backup(context, repo, photos))
        val zip = archive(cache, envelope.encode(before, identity, 0), emptyMap())
        val candidate = cache.decode(zip, remote(identity, zip), envelope) {}
        val restarted = FamilyReadCache(context, newId())
        try { restarted.newDownload(); assertFalse(candidate.directory.exists()); assertFalse(zip.exists()); assertEquals(before, repo.snapshot()) }
        finally { restarted.close() }
    }
}
