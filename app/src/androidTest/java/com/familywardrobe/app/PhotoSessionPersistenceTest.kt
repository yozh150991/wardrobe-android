package com.familywardrobe.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoSessionPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val photos = PhotoStore(context)
    private val owned = mutableSetOf<String>()
    private fun fixture(): String {
        val id = newId(); owned += id
        val bitmap = Bitmap.createBitmap(IntArray(16 * 12) { 0xff6b8194.toInt() }, 16, 12, Bitmap.Config.ARGB_8888)
        try { photos.file(id, "png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return id
    }
    private fun stroke() = "{\"s\":[[0.2,0.4,0.8,0.4,0.15,0,0.25,0.4]]}"
    private fun mixed() = PhotoEditHistory().append(stroke()).append("{\"rotate\":1}").append("{\"crop\":[0.1,0.1,0.9,0.9]}")
    @After fun cleanup() = runBlocking { photos.remove(owned); photos.clearMemoryCache(); Unit }

    @Test fun savedEnvelopeRetainsCrossToolRedoAndBrushParameters() {
        val initial = fixture()
        val history = mixed().undo()
        val restored = decodePhotoHistory(encodePhotoHistory(history))
        assertTrue(restored.canRedo())
        assertEquals(3, photoSessionPlan(restored.redo(), initial).actions.size)
        val segment = maskStrokes(JSONObject(restored.steps.first()).getJSONArray("s").toString()).single()
        assertEquals(.25f, segment.hardness, 0f); assertEquals(.4f, segment.strength, 0f)
    }
    @Test fun resetSourceIsUndoableAcrossAllTools() {
        val initial = fixture(); val replacement = fixture()
        val reset = mixed().append(JSONObject().put("source", replacement).toString())
        assertEquals(replacement, photoSessionPlan(reset, initial).sourceId)
        assertTrue(photoSessionPlan(reset, initial).actions.isEmpty())
        assertEquals(initial, photoSessionPlan(reset.undo(), initial).sourceId)
        assertEquals(3, photoSessionPlan(reset.undo(), initial).actions.size)
    }
    @Test fun previewEqualsSavedPngAndInputIsUnchanged() = runBlocking {
        val initial = fixture(); val before = photos.currentFile(initial).readBytes()
        val image = renderPhotoSession(photos, initial, mixed())
        val saved = photos.editSession(initial, mixed()); owned += saved
        val decoded = requireNotNull(BitmapFactory.decodeFile(photos.currentFile(saved).path))
        try {
            assertEquals(image.bitmap.width, decoded.width); assertEquals(image.bitmap.height, decoded.height)
            val expected = IntArray(decoded.width * decoded.height); val actual = IntArray(expected.size)
            image.bitmap.getPixels(expected, 0, decoded.width, 0, 0, decoded.width, decoded.height)
            decoded.getPixels(actual, 0, decoded.width, 0, 0, decoded.width, decoded.height)
            assertArrayEquals(expected, actual)
            assertArrayEquals(before, photos.currentFile(initial).readBytes())
        } finally { image.bitmap.recycle(); decoded.recycle() }
    }
    @Test fun incrementalPreviewMatchesFullReplay() = runBlocking {
        val initial = fixture()
        var history = PhotoEditHistory()
        var previous = renderPhotoSession(photos, initial, history)
        try {
            for (command in mixed().steps) {
                val next = history.append(command)
                val incremental = renderPhotoSession(photos, initial, next, history, previous)
                val full = renderPhotoSession(photos, initial, next)
                try {
                    assertEquals(full.frame.width, incremental.frame.width)
                    assertEquals(full.frame.height, incremental.frame.height)
                    assertArrayEquals(full.frame.pixels, incremental.frame.pixels)
                    assertArrayEquals(full.frame.original, incremental.frame.original)
                } finally { full.bitmap.recycle() }
                previous.bitmap.recycle(); previous = incremental; history = next
            }
        } finally { previous.bitmap.recycle() }
    }
    @Test fun emptyResultCreatesNoFile() = runBlocking {
        val initial = fixture()
        val before = photos.directory.listFiles().orEmpty().map { it.name }.toSet()
        val segments = JSONArray()
        listOf(0.0, .5, 1.0).forEach { y -> segments.put(JSONArray().put(0).put(y).put(1).put(y).put(.5).put(0).put(1).put(1)) }
        val history = PhotoEditHistory().append(JSONObject().put("s", segments).toString())
        try { val id = photos.editSession(initial, history); owned += id; fail("Empty photo accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, photos.directory.listFiles().orEmpty().map { it.name }.toSet())
    }
}
