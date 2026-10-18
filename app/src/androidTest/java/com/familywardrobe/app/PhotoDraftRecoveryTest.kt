package com.familywardrobe.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Prepared for native acceptance: real account preferences, cleanup references and PNG replay. */
class PhotoDraftRecoveryTest {
    private fun scenario(block: suspend (AccountContext) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = AccountContext(base, AccountLocation(AuthRules.accountKey("tests", newId()), false))
        try { block(context) }
        finally { context.getSharedPreferences("preferences", 0).edit().clear().commit(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively() }
    }
    private fun history() = PhotoEditHistory()
        .append("{\"polygon\":[0,0,0.5,0,0.5,1,0,1],\"mode\":\"protect\"}")
        .append("{\"edges\":[-1,1]}")
        .append("{\"rotate\":1}").undo()
    private fun draft(photo: String, before: String = photo) = JSONObject().put("kind", "garment")
        .put("wardrobe", Repository.DEFAULT_WARDROBE).put("original", "").put("fields", JSONObject()
            .put("id", newId()).put("photo", photo).put("name", "Jacket")
            .put(PhotoSessionDraft.FIELD, PhotoSessionDraft(photo, before, history(), "precision").json()))
    private fun makePhoto(context: AccountContext): String {
        val id = newId(); val photos = PhotoStore(context)
        val bitmap = Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff65839c.toInt()) }
        try { photos.file(id, "png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return id
    }
    @Test fun reopenedStoreKeepsHistoryRedoAndTool() = scenario { context ->
        val photo = makePhoto(context); val value = draft(photo)
        assertTrue(EditorDraftStore(context).write(value))
        val restored = requireNotNull(EditorDraftStore(context).read()).getJSONObject("fields")
        val session = PhotoSessionDraft.decode(restored.getJSONObject(PhotoSessionDraft.FIELD), photo)
        assertArrayEquals(history().steps, session.history.steps); assertEquals(history().cursor, session.history.cursor)
        assertTrue(session.history.canRedo()); assertEquals("precision", session.tool)
        assertEquals("Jacket", restored.getString("name"))
    }
    @Test fun formAutosaveKeepsCommandsAndSourceReplacementClearsThem() = scenario { context ->
        val initial = draft(makePhoto(context)); val next = JSONObject(initial.toString())
        next.getJSONObject("fields").remove(PhotoSessionDraft.FIELD)
        next.getJSONObject("fields").put("name", "Edited name")
        val merged = PhotoSessionDraft.merge(initial, next)
        assertEquals(initial.getJSONObject("fields").getJSONObject(PhotoSessionDraft.FIELD).toString(), merged.getJSONObject("fields").getJSONObject(PhotoSessionDraft.FIELD).toString())
        assertEquals("Edited name", merged.getJSONObject("fields").getString("name"))
        val replacement = JSONObject(merged.toString()); replacement.getJSONObject("fields").put("photo", newId())
        assertFalse(PhotoSessionDraft.merge(merged, replacement).getJSONObject("fields").has(PhotoSessionDraft.FIELD))
        val other = JSONObject(merged.toString()); other.getJSONObject("fields").put("id", newId())
        assertFalse(PhotoSessionDraft.merge(merged, other).getJSONObject("fields").has(PhotoSessionDraft.FIELD))
    }
    @Test fun redoResetSourcesStayProtectedAndCleanupLedgerSurvivesDiscard() = scenario { context ->
        val first = makePhoto(context); val second = makePhoto(context); val value = draft(first, second)
        val edits = history().append(JSONObject().put("source", second).toString()).undo()
        value.getJSONObject("fields").put(PhotoSessionDraft.FIELD, PhotoSessionDraft(first, second, edits, "precision").json())
        assertTrue(EditorDraftStore(context).write(value))
        assertEquals(setOf(first, second), PhotoSessionDraft.referencedPhotos(value.getJSONObject("fields")))
        assertTrue(context.getSharedPreferences("preferences", 0).getStringSet("draftPhotos", emptySet())!!.containsAll(setOf(first, second)))
        assertTrue(EditorDraftStore(context).clear())
        assertNull(EditorDraftStore(context).read())
        assertTrue(context.getSharedPreferences("preferences", 0).getStringSet("draftPhotos", emptySet())!!.containsAll(setOf(first, second)))
    }
    @Test fun separateAccountsNeverRestoreEachOthersPhotoDrafts() = scenario { a ->
        scenario { b ->
            val value = draft(makePhoto(a)); assertTrue(EditorDraftStore(a).write(value))
            assertNull(EditorDraftStore(b).read())
            assertTrue(b.getSharedPreferences("preferences", 0).getStringSet("draftPhotos", emptySet())!!.isEmpty())
        }
    }
    @Test fun invalidRedoIsRejectedWithoutDeletingStoredDraft() = scenario { context ->
        val photo = makePhoto(context); val value = draft(photo)
        value.getJSONObject("fields").getJSONObject(PhotoSessionDraft.FIELD).getJSONObject("history")
            .getJSONArray("steps").put("{\"edges\":[999,0]}")
        assertTrue(EditorDraftStore(context).write(value))
        try { PhotoSessionDraft.decode(value.getJSONObject("fields").getJSONObject(PhotoSessionDraft.FIELD), photo); fail("Invalid redo accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(value.toString(), EditorDraftStore(context).read()!!.toString())
    }
    @Test fun replayAfterReopeningMatchesPreviewAndFinalPng() = scenario { context ->
        val photo = makePhoto(context); val value = draft(photo); val photos = PhotoStore(context)
        val bytes = photos.currentFile(photo).readBytes()
        assertTrue(EditorDraftStore(context).write(value))
        val session = PhotoSessionDraft.decode(EditorDraftStore(context).read()!!.getJSONObject("fields").getJSONObject(PhotoSessionDraft.FIELD), photo)
        val first = renderPhotoSession(photos, photo, session.history)
        val redone = renderPhotoSession(photos, photo, session.history.redo(), session.history, first)
        val full = renderPhotoSession(photos, photo, session.history.redo())
        val output = photos.editSession(photo, session.history.redo())
        val png = requireNotNull(BitmapFactory.decodeFile(photos.currentFile(output).path))
        try {
            assertArrayEquals(full.frame.pixels, redone.frame.pixels); assertArrayEquals(full.frame.protection, redone.frame.protection)
            val expected = IntArray(png.width * png.height); val actual = IntArray(expected.size)
            full.bitmap.getPixels(expected, 0, png.width, 0, 0, png.width, png.height)
            png.getPixels(actual, 0, png.width, 0, 0, png.width, png.height)
            assertArrayEquals(expected, actual); assertArrayEquals(bytes, photos.currentFile(photo).readBytes())
        } finally { first.bitmap.recycle(); redone.bitmap.recycle(); full.bitmap.recycle(); png.recycle() }
    }
    @Test fun activeSessionProtectsSourceUntilAppliedDraftIsCleared() = scenario { context ->
        val db = Room.inMemoryDatabaseBuilder(context, WardrobeDatabase::class.java).build()
        try {
            val repo = Repository(db); repo.initialize("Home"); val photos = PhotoStore(context)
            val photo = makePhoto(context); val before = makePhoto(context); val draft = draft(photo, before)
            val store = EditorDraftStore(context); assertTrue(store.write(draft))
            repo.editorDraftPhotos = { PhotoSessionDraft.referencedPhotos(store.read()?.optJSONObject("fields")) }
            assertTrue(repo.discardPhotos(setOf(photo, before), photos))
            assertTrue(photos.currentFile(photo).isFile); assertTrue(photos.currentFile(before).isFile)
            val final = photos.editSession(photo, history())
            val fields = draft.getJSONObject("fields"); fields.put("photo", final); fields.remove(PhotoSessionDraft.FIELD)
            assertTrue(store.write(draft)); assertTrue(repo.discardPhotos(setOf(photo, before), photos))
            assertFalse(photos.currentFile(photo).exists()); assertFalse(photos.currentFile(before).exists()); assertTrue(photos.currentFile(final).isFile)
        } finally { db.close() }
    }
    @Test fun ownerAndPhotoGuardsRejectStaleEditor() = scenario { context ->
        val photo = makePhoto(context); val value = draft(photo)
        val owner = Repository.DEFAULT_WARDROBE + ":" + value.getJSONObject("fields").getString("id")
        assertTrue(PhotoSessionDraft.owns(value, owner, photo))
        assertFalse(PhotoSessionDraft.owns(value, owner, newId()))
        assertFalse(PhotoSessionDraft.owns(value, newId() + ":" + value.getJSONObject("fields").getString("id"), photo))
    }
}
