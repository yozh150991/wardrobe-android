package com.familywardrobe.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android JSON/save-state integration. Gesture and configuration UI acceptance is separate. */
@RunWith(AndroidJUnit4::class)
class PhotoEditorHistoryTest {
    private val initial = "11111111-1111-4111-8111-111111111111"
    private val original = "22222222-2222-4222-8222-222222222222"
    private val stroke = "{\"s\":[[0.1,0.2,0.2,0.3,0.00048828125,0],[0.2,0.3,0.4,0.5,0.00048828125,0]]}"
    private val broad = "{\"s\":[[0.5,0.5,0.6,0.6,0.08,1]]}"

    @Test fun oneUndoRemovesTheWholeStrokeAndRedoRestoresIt() {
        val history = PhotoEditHistory().append(stroke)
        val applied = maskHistoryState(history, initial)
        assertEquals(2, maskStrokes(applied.strokes).size)
        assertEquals("[]", maskHistoryState(history.undo(), initial).strokes)
        assertEquals(applied, maskHistoryState(history.undo().redo(), initial))
    }
    @Test fun savedHistoryKeepsTheRedoBranchAndPerStrokeSize() {
        val history = PhotoEditHistory().append(stroke).append(broad).undo()
        val restored = decodePhotoHistory(encodePhotoHistory(history))
        assertTrue(restored.canRedo())
        val segments = maskStrokes(maskHistoryState(restored.redo(), initial).strokes)
        assertEquals(3, segments.size)
        assertEquals(1 / 2048f, segments.first().radius, 0f)
        assertEquals(.08f, segments.last().radius, 0f)
        assertTrue(segments.last().restore)
    }
    @Test fun sourceResetCanBeUndoneAndRedone() {
        val edited = PhotoEditHistory().append(stroke)
        val reset = edited.append(JSONObject().put("source", original).toString())
        assertEquals(original, maskHistoryState(reset, initial).sourceId)
        assertEquals("[]", maskHistoryState(reset, initial).strokes)
        assertEquals(maskHistoryState(edited, initial), maskHistoryState(reset.undo(), initial))
        assertEquals(original, maskHistoryState(reset.undo().redo(), initial).sourceId)
    }
    @Test fun pendingGestureDoesNotDiscardRedoUntilCommitted() {
        val history = PhotoEditHistory().append(stroke).undo()
        assertEquals(1, maskStrokes(maskHistoryState(history, initial, broad).strokes).size)
        assertTrue(history.canRedo())
        assertEquals("[]", maskHistoryState(history, initial).strokes)
        assertFalse(history.append(broad).canRedo())
    }
    @Test fun corruptSavedEnvelopeFallsBackToEmptyHistory() {
        assertFalse(decodePhotoHistory("invalid").canUndo())
        assertFalse(decodePhotoHistory("{\"steps\":[\"a\"],\"cursor\":2}").canRedo())
    }
}
