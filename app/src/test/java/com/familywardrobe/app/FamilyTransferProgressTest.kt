package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

class FamilyTransferProgressTest {
    @Test fun deferredReadSurvivesUploadToCommitTransition() {
        var bytes = 4_144_174L
        var total = bytes
        val oldCallback = { (bytes.toFloat() / total).coerceIn(0f, 1f) }
        val callback = requireNotNull(familyUploadProgress(true, bytes, total))
        // Same order as the real failure: compose while uploading, reset counters,
        // then accessibility/drawing invokes the previous indicator's callback.
        bytes = 0L
        total = 0L
        assertTrue("Old callback must reproduce reported NaN", oldCallback().isNaN())
        assertEquals(1f, callback(), 0f)
        assertNull(familyUploadProgress(false, bytes, total))
    }

    @Test fun unknownTotalsAndOtherPhasesAreIndeterminate() {
        for (total in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertNull(familyUploadProgress(true, 0L, total))
            assertNull(familyUploadProgress(true, 10L, total))
        }
        assertNull(familyUploadProgress(false, 50L, 100L))
    }

    @Test fun progressSnapshotsAdvanceWithoutChangingPreviousCallback() {
        val first = requireNotNull(familyUploadProgress(true, 25L, 100L))
        val next = requireNotNull(familyUploadProgress(true, 75L, 100L))
        assertEquals(0.25f, first(), 0f)
        assertEquals(0.75f, next(), 0f)
        assertEquals(0.25f, first(), 0f)
    }

    @Test fun allCounterBoundariesProduceFiniteBoundedProgress() {
        for (total in listOf(1L, 100L, 134_217_728L, Long.MAX_VALUE)) {
            for (bytes in listOf(Long.MIN_VALUE, -1L, 0L, 1L, total / 2, total, Long.MAX_VALUE)) {
                val value = requireNotNull(familyUploadProgress(true, bytes, total))()
                assertTrue("bytes=$bytes total=$total value=$value", value.isFinite() && value in 0f..1f)
                if (bytes <= 0L) assertEquals(0f, value, 0f)
                if (bytes >= total) assertEquals(1f, value, 0f)
            }
        }
    }
}
