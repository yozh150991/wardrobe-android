package com.familywardrobe.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class PhotoRegionPersistenceTest {
    private val photos = PhotoStore(ApplicationProvider.getApplicationContext<Context>())
    private val owned = mutableSetOf<String>()
    private fun fixture(): String {
        val id = newId(); owned += id
        val pixels = IntArray(7 * 5) { 0xffeeeeee.toInt() }
        for (y in 1..3) for (x in 2..4) pixels[y * 7 + x] = 0xff326591.toInt()
        val bitmap = Bitmap.createBitmap(pixels, 7, 5, Bitmap.Config.ARGB_8888)
        try { photos.file(id, "png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return id
    }
    private fun history() = PhotoEditHistory().append("{\"region\":[0,0,0.1]}")
        .append("{\"rotate\":1}").append("{\"crop\":[0,0.1,1,0.9]}")
    @After fun cleanup() = runBlocking { photos.remove(owned); photos.clearMemoryCache(); Unit }

    @Test fun regionHistorySurvivesSerializationAndUndoRedo() = runBlocking {
        val initial = fixture()
        val saved = decodePhotoHistory(encodePhotoHistory(history().undo()))
        assertTrue(saved.canRedo())
        val expected = renderPhotoSession(photos, initial, history())
        val actual = renderPhotoSession(photos, initial, saved.redo())
        try { assertArrayEquals(expected.frame.pixels, actual.frame.pixels); assertArrayEquals(expected.frame.original, actual.frame.original) }
        finally { expected.bitmap.recycle(); actual.bitmap.recycle() }
    }
    @Test fun regionPreviewAndPngMatchWithoutChangingSource() = runBlocking {
        val initial = fixture(); val input = photos.currentFile(initial).readBytes()
        val preview = renderPhotoSession(photos, initial, history())
        val id = photos.editSession(initial, history()); owned += id
        val decoded = requireNotNull(BitmapFactory.decodeFile(photos.currentFile(id).path))
        try {
            val expected = IntArray(decoded.width * decoded.height); val actual = IntArray(expected.size)
            preview.bitmap.getPixels(expected, 0, decoded.width, 0, 0, decoded.width, decoded.height)
            decoded.getPixels(actual, 0, decoded.width, 0, 0, decoded.width, decoded.height)
            assertArrayEquals(expected, actual); assertArrayEquals(input, photos.currentFile(initial).readBytes())
        } finally { preview.bitmap.recycle(); decoded.recycle() }
    }
    @Test fun alphaOverlayRespectsBitmapStride() {
        val pixels = IntArray(7 * 3) { if (it % 7 < 2) 0xffeeeeee.toInt() else 0xff326591.toInt() }
        val selected = PhotoRegionRules.select(pixels, 7, 3, 0f, 0f, 0f) {}
        val mask = Bitmap.createBitmap(7, 3, Bitmap.Config.ALPHA_8)
        try {
            val expected = selected.alphaMask(mask.rowBytes) {}
            mask.copyPixelsFromBuffer(ByteBuffer.wrap(expected))
            val actual = ByteBuffer.allocate(mask.rowBytes * mask.height)
            mask.copyPixelsToBuffer(actual)
            assertArrayEquals(expected, actual.array())
        } finally { mask.recycle() }
    }
    @Test fun fullRegionResultCannotLeaveAnEmptySavedFile() = runBlocking {
        val initial = fixture()
        val files = photos.directory.listFiles().orEmpty().map { it.name }.toSet()
        try { val id = photos.editSession(initial, PhotoEditHistory().append("{\"region\":[0,0,1]}")); owned += id; fail("Empty result accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(files, photos.directory.listFiles().orEmpty().map { it.name }.toSet())
    }
}
