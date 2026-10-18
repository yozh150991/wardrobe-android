package com.familywardrobe.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PhotoResolutionPersistenceTest {
    private fun scenario(block: suspend (PhotoStore) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = AccountContext(base, AccountLocation(AuthRules.accountKey("tests", newId()), false))
        try { block(PhotoStore(context)) }
        finally {
            context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively()
            base.deleteSharedPreferences("account-${context.location.key}-photo-cleanup")
        }
    }
    private fun photo(photos: PhotoStore, width: Int, height: Int): String {
        val id = newId(); val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff326591.toInt())
        try { photos.file(id, "png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return id
    }
    @Test fun outputUsesAvailableDetailAndSmallPhotosAreNotUpscaled() = scenario { photos ->
        for ((width, height) in listOf(2000 to 1400, 30 to 10)) {
            val source = photo(photos, width, height)
            val before = photos.currentFile(source).readBytes()
            val decoded = photos.analysisBitmap(source)
            val mask = photos.file(newId(), "png")
            val size = PhotoResolutionRules.canvas(intArrayOf(0, 0, decoded.width, decoded.height), photos.finalResolution, .08f)
            try { mask.outputStream().use { check(decoded.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { decoded.recycle() }
            val result = photos.copyVariant(source, mask)
            val saved = requireNotNull(BitmapFactory.decodeFile(photos.currentFile(result).path))
            try {
                assertEquals(size, saved.width); assertEquals(size, saved.height)
                assertEquals(0, saved.getPixel(0, 0) ushr 24)
                assertEquals(0xff326591.toInt(), saved.getPixel(size / 2, size / 2))
                assertArrayEquals(before, photos.currentFile(source).readBytes())
                if (width == 30) assertEquals(35, size)
            } finally { saved.recycle() }
        }
    }
    @Test fun oddLargerSourceUsesBoundedAnalysisAndSeparateSmallPreview() = scenario { photos ->
        val source = photo(photos, 2571, 1631)
        val decoded = photos.analysisBitmap(source)
        try {
            assertTrue(decoded.width <= PhotoResolutionRules.sourceLimit(photos.finalResolution))
            assertTrue(decoded.width.toLong() * decoded.height <= PhotoResolutionRules.pixelLimit(photos.finalResolution))
            assertTrue(PhotoResolutionRules.matchesMask(2571, 1631, decoded.width, decoded.height, photos.finalResolution))
        } finally { decoded.recycle() }
        val card = requireNotNull(photos.bitmap(source))
        val zoom = requireNotNull(photos.bitmap(source, detailed = true))
        assertTrue(card.width <= 768 && card.height <= 768)
        assertEquals(2571, zoom.width); assertEquals(1631, zoom.height)
        assertNotSame(card, zoom)
        photos.clearMemoryCache()
    }
    @Test fun emptyCutoutCannotReplaceOrLeaveCandidateFile() = scenario { photos ->
        val source = photo(photos, 30, 20)
        val mask = photos.file(newId(), "png")
        val empty = Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888)
        try { mask.outputStream().use { check(empty.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { empty.recycle() }
        val candidate = newId()
        try { photos.copyVariant(source, mask, candidate); fail("Empty candidate accepted") }
        catch (_: IllegalArgumentException) { }
        assertFalse(photos.currentFile(candidate).exists()); assertTrue(photos.currentFile(source).exists())
    }
}
