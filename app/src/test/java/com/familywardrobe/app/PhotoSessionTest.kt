package com.familywardrobe.app

import org.junit.Test

class PhotoSessionTest {
    @Test fun softBrushCoverage() = PhotoSessionCases.softBrush()
    @Test fun crossToolUndoAndRedo() = PhotoSessionCases.unifiedHistory()
    @Test fun loupePlacement() = PhotoSessionCases.loupe()
    @Test fun cameraSizedReplay() = PhotoSessionCases.cameraFrame()
}
