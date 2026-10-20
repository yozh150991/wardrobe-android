package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

/** FAM-03 photo replacement: only the chosen item's photo changes; everything else is preserved. */
class FamilyPhotoChangeTest {
    private val shared = Wardrobe(name = "Family")
    private val item = Garment(wardrobeId = shared.id, name = "Coat", category = "outerwear", color = "black", photoId = newId(), location = "Hall")
    private val other = Garment(wardrobeId = shared.id, name = "Shirt", category = "top", color = "white", photoId = newId())
    private val latest = Snapshot(wardrobes = listOf(shared), garments = listOf(item, other))
    private val fingerprint = "a".repeat(64)

    @Test fun replacesOnlyTheChosenItemsPhoto() {
        val photo = newId()
        val changed = FamilyPhotoChange.apply(latest, item.id, photo, fingerprint)
        assertEquals(item.copy(photoId = photo, photoFingerprint = fingerprint), changed.garments.single { it.id == item.id })
        assertEquals(other, changed.garments.single { it.id == other.id })
        assertEquals(latest.copy(garments = emptyList()), changed.copy(garments = emptyList()))
        assertEquals(setOf(photo, other.photoId), changed.photoIds())
        assertEquals(item, latest.garments.first())
    }

    @Test fun oldPhotoStaysWhileSomethingElseStillUsesIt() {
        val wish = Wish(wardrobeId = shared.id, name = "Coat", category = "outerwear", photoId = item.photoId)
        val changed = FamilyPhotoChange.apply(latest.copy(wishes = listOf(wish)), item.id, newId(), "")
        assertTrue(item.photoId in changed.photoIds())
    }

    @Test fun missingItemIsReportedAsMissing() {
        val failure = runCatching { FamilyPhotoChange.apply(latest, newId(), newId(), fingerprint) }.exceptionOrNull()
        assertEquals(FamilyProblem.MISSING, (failure as FamilyTransferException).problem)
    }

    @Test fun photoIdAlreadyInTheSnapshotOrMalformedInputIsRejected() {
        for (photo in listOf(other.photoId, item.photoId, "not-an-id")) {
            assertTrue(runCatching { FamilyPhotoChange.apply(latest, item.id, photo, fingerprint) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(runCatching { FamilyPhotoChange.apply(latest, item.id, newId(), "XYZ") }.exceptionOrNull() is IllegalArgumentException)
    }
}
