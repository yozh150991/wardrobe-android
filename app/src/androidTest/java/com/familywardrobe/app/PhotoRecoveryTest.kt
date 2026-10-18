package com.familywardrobe.app

import android.graphics.Bitmap
import android.system.ErrnoException
import android.system.OsConstants
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Prepared device tests; host source checks do not execute these scenarios. */
class PhotoRecoveryTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun account() = AccountContext(base,
        AccountLocation(AuthRules.accountKey("photo-recovery-tests", UUID.randomUUID().toString()), false))

    @Test fun reasonsPersistWithoutPathsAndStayInTheirAccount() {
        val context = account(); val other = account()
        val id = newId(); val photo = newId()
        try {
            val store = PhotoDiagnosticStore(context)
            store.record(id, PhotoAnalysis(photo, cutoutPath = "/private/transient.png",
                cutoutStatus = CutoutStatus.MODEL_NEEDED, labelFailed = true))
            val recovered = PhotoDiagnosticStore(context).reports.value.getValue(id)
            assertEquals(photo, recovered.sourceId)
            assertEquals(CutoutStatus.MODEL_NEEDED, recovered.cutoutStatus)
            assertTrue(recovered.labelFailed)
            assertNull(recovered.cutoutPath)
            assertTrue(PhotoDiagnosticStore(other).reports.value.isEmpty())
            store.retain(emptySet())
            assertTrue(PhotoDiagnosticStore(context).reports.value.isEmpty())
        } finally { context.getSharedPreferences("import-photo-diagnostics", 0).edit().clear().commit() }
    }

    @Test fun unknownOrMalformedOldReportsDoNotInventACause() {
        val context = account(); val id = newId()
        try {
            assertNull(PhotoDiagnosticStore(context).reports.value[id])
            context.getSharedPreferences("import-photo-diagnostics", 0).edit().putString(id, "broken").commit()
            assertNull(PhotoDiagnosticStore(context).reports.value[id])
            assertEquals(PhotoFailureKind.UNKNOWN, photoFailure("", IllegalStateException()).failure)
            assertEquals(PhotoFailureKind.ACCESS, photoFailure("", SecurityException()).failure)
            assertEquals(PhotoFailureKind.MISSING, photoFailure("", FileNotFoundException()).failure)
            assertEquals(PhotoFailureKind.UNREADABLE, photoFailure("", UnreadablePhotoException()).failure)
            assertEquals(PhotoFailureKind.TOO_LARGE, photoFailure("", PhotoTooLargeException()).failure)
            assertEquals(PhotoFailureKind.IO, photoFailure("", IOException()).failure)
            assertEquals(PhotoFailureKind.SPACE, photoFailure("",
                IOException(ErrnoException("write", OsConstants.ENOSPC))).failure)
        } finally { context.getSharedPreferences("import-photo-diagnostics", 0).edit().clear().commit() }
    }

    @Test fun cutoutSuccessAndUnknownCategoryAreSeparateOutcomes() {
        val context = account(); val id = newId()
        try {
            val store = PhotoDiagnosticStore(context)
            store.record(id, PhotoAnalysis(newId(), cutoutStatus = CutoutStatus.READY))
            val restored = PhotoDiagnosticStore(context).reports.value.getValue(id)
            assertEquals(CutoutStatus.READY, restored.cutoutStatus)
            assertNull(restored.category)
            assertFalse(restored.labelFailed)
            assertEquals(PhotoFailureKind.NONE, restored.failure)
        } finally { context.getSharedPreferences("import-photo-diagnostics", 0).edit().clear().commit() }
    }

    @Test fun failedRowCanBeCompletedWithReplacementWithoutDuplicatingOrLosingNextRow() = runBlocking {
        val context = account(); val photos = PhotoStore(context)
        val db = Room.inMemoryDatabaseBuilder(context, WardrobeDatabase::class.java).build()
        val photo = newId()
        try {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            photos.file(photo).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            bitmap.recycle()
            val repo = Repository(db); repo.initialize("Test")
            val rows = repo.enqueueImports(Repository.DEFAULT_WARDROBE, listOf("content://old/missing", "content://next/photo"))
            repo.dao.saveImport(rows[0].copy(status = "failed"))
            val item = Garment(id = rows[0].id, wardrobeId = rows[0].wardrobeId,
                name = "Manually completed", category = "top", color = "blue", photoId = photo)
            repo.saveImported(rows[0].id, item, photos)
            assertEquals(item, repo.dao.garment(item.id))
            assertEquals(rows[1].id, repo.dao.allImports().single().id)
            assertTrue(photos.currentFile(photo).isFile)
            try { repo.saveImported(rows[0].id, item, photos); fail("Duplicate save") }
            catch (_: IllegalArgumentException) { }
            assertEquals(1, repo.dao.allGarments().size)
        } finally { db.close(); photos.remove(setOf(photo)) }
    }

    @Test fun unreadableReplacementKeepsFailedRowAndDoesNotCreateGarment() = runBlocking {
        val context = account(); val photos = PhotoStore(context)
        val db = Room.inMemoryDatabaseBuilder(context, WardrobeDatabase::class.java).build()
        val photo = newId()
        try {
            photos.file(photo).writeText("Not a photograph")
            val repo = Repository(db); repo.initialize("Test")
            val row = repo.enqueueImports(Repository.DEFAULT_WARDROBE, listOf("content://old/missing")).single()
            repo.dao.saveImport(row.copy(status = "failed"))
            val item = Garment(id = row.id, wardrobeId = row.wardrobeId,
                name = "Manual", category = "top", color = "blue", photoId = photo)
            try { repo.saveImported(row.id, item, photos); fail("Invalid photo") }
            catch (_: IllegalArgumentException) { }
            assertEquals("failed", repo.dao.importDraft(row.id)!!.status)
            assertTrue(repo.dao.allGarments().isEmpty())
        } finally { db.close(); photos.remove(setOf(photo)) }
    }
}
