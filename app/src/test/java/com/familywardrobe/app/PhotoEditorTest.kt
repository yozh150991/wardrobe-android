package com.familywardrobe.app

import org.junit.Test

class PhotoEditorTest {
    @Test fun undoRedoAndLimits() = PhotoEditorCases.history()
    @Test fun fineAndBroadBrushes() = PhotoEditorCases.brush()
    @Test fun onePixelTapsAndRestoration() = PhotoEditorCases.pixels()
}
