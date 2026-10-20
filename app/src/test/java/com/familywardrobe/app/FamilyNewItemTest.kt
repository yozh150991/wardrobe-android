package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

/** FAM-03: a brand-new shared item (gallery/camera photo) merges like any other addition. */
class FamilyNewItemTest {
    private val shared = Wardrobe(name = "Family")
    private val existing = Garment(wardrobeId = shared.id, name = "Shirt", category = "top", color = "white", photoId = newId(), location = "Shelf")
    private val latest = Snapshot(wardrobes = listOf(shared), garments = listOf(existing))
    private val fields = FamilyNewItem("  Raincoat ", "outerwear", "navy", " M ", " Hall ", " Waterproof ", "midseason", "sport")

    @Test fun newItemGetsFreshIdsTrimmedFieldsAndMergesWithoutTouchingOthers() {
        val selected = fields.selection(shared, "b".repeat(64)); val item = selected.garments.single()
        assertEquals(shared.id, item.wardrobeId); assertEquals("Raincoat", item.name); assertEquals("M", item.size)
        assertEquals("Hall", item.location); assertEquals("Waterproof", item.description)
        assertEquals("outerwear", item.category); assertEquals("navy", item.color); assertEquals("midseason", item.season); assertEquals("sport", item.style)
        assertNotEquals(existing.id, item.id); assertNotEquals(existing.photoId, item.photoId)
        assertNull(item.customCategoryId); assertNull(item.customColorId)
        val merged = FamilyAddition.merge(latest, selected)
        assertEquals(listOf(existing, item), merged.garments)
        assertEquals(setOf(existing.photoId, item.photoId), merged.photoIds())
    }

    @Test fun invalidFieldsAreRejectedBeforeAnyPhotoWork() {
        val invalid = listOf(fields.copy(name = "  "), fields.copy(name = "x".repeat(121)), fields.copy(category = "spaceship"),
            fields.copy(color = "ultraviolet"), fields.copy(size = "x".repeat(41)), fields.copy(location = "x".repeat(201)),
            fields.copy(description = "x".repeat(1001)), fields.copy(season = "monsoon"), fields.copy(style = "x"))
        for (item in invalid) {
            assertFalse(item.valid)
            assertTrue(runCatching { item.selection(shared, "") }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(runCatching { fields.selection(shared, "nothex") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun sharedCustomLabelIsReusedNotDuplicated() {
        val category = CustomCategory(wardrobeId = shared.id, name = "Coats", nameKey = "coats", baseCategory = "outerwear")
        val color = CustomColor(wardrobeId = shared.id, name = "Ink", nameKey = "ink", baseColor = "navy")
        val withLabels = latest.copy(customCategories = listOf(category), customColors = listOf(color))
        val chosen = fields.copy(customCategoryId = category.id, customColorId = color.id)
        assertEquals(listOf(category), chosen.categories(withLabels, shared)); assertEquals(listOf(color), chosen.colors(withLabels, shared))
        val merged = FamilyAddition.merge(withLabels, chosen.selection(shared, "", withLabels))
        val item = merged.garments.last()
        assertEquals(category.id, item.customCategoryId); assertEquals(color.id, item.customColorId)
        assertEquals(listOf(category), merged.customCategories); assertEquals(listOf(color), merged.customColors)
    }

    @Test fun customLabelOfAnotherBaseOrMissingIsRejected() {
        val category = CustomCategory(wardrobeId = shared.id, name = "Shirts", nameKey = "shirts", baseCategory = "top")
        val withLabel = latest.copy(customCategories = listOf(category))
        assertTrue(fields.categories(withLabel, shared).isEmpty())
        assertTrue(runCatching { fields.copy(customCategoryId = category.id).selection(shared, "", withLabel) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { fields.copy(customColorId = newId()).selection(shared, "", withLabel) }.exceptionOrNull() is IllegalArgumentException)
    }
}
