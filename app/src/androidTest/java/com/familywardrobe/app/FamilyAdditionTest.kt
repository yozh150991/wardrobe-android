package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

class FamilyAdditionTest {
    private val own = Wardrobe(name = "Private")
    private val shared = Wardrobe(name = "Family")
    private val category = CustomCategory(wardrobeId = own.id, name = "Shirts", nameKey = "shirts", baseCategory = "top")
    private val color = CustomColor(wardrobeId = own.id, name = "Ink", nameKey = "ink", baseColor = "black")
    private val item = Garment(wardrobeId = own.id, name = "Shirt", category = "top", color = "black", photoId = newId(), customCategoryId = category.id, customColorId = color.id)
    private val source = Snapshot(wardrobes = listOf(own), garments = listOf(item), customCategories = listOf(category), customColors = listOf(color),
        wears = listOf(Wear(item.id, "2026-10-19", "manual")))
    @Test fun selectionCopiesOnlyOneItemAndRemapsAllPrivateIds() {
        val selected = FamilyAddition.select(source, item.id, shared); val copy = selected.garments.single()
        assertNotEquals(item.id, copy.id); assertNotEquals(item.photoId, copy.photoId); assertEquals(shared.id, copy.wardrobeId)
        assertNotEquals(item.customCategoryId, copy.customCategoryId); assertNotEquals(item.customColorId, copy.customColorId)
        assertEquals(1, selected.customCategories.size); assertEquals(1, selected.customColors.size)
        assertTrue(selected.wears.isEmpty()); assertTrue(selected.outfits.isEmpty()); assertTrue(selected.wishEvents.isEmpty())
        assertEquals(item, source.garments.single())
    }
    @Test fun mergeAndRebasePreserveOtherMembersDataAndReuseMatchingCustomLabels() {
        val selection = FamilyAddition.select(source, item.id, shared)
        val sharedCategory = category.copy(id = newId(), wardrobeId = shared.id)
        val sharedColor = color.copy(id = newId(), wardrobeId = shared.id)
        val other = item.copy(id = newId(), wardrobeId = shared.id, photoId = newId(), location = "Other edit", customCategoryId = sharedCategory.id, customColorId = sharedColor.id)
        val latest = Snapshot(wardrobes = listOf(shared), garments = listOf(other), customCategories = listOf(sharedCategory), customColors = listOf(sharedColor))
        val merged = FamilyAddition.merge(latest, selection)
        assertEquals(latest, merged.copy(garments = merged.garments.take(1)))
        assertEquals(other, merged.garments.first()); assertEquals(sharedCategory.id, merged.garments.last().customCategoryId)
        val recovered = FamilyAddition.recover(merged, selection.garments.single().id)
        assertEquals(selection.garments.single().photoId, recovered.garments.single().photoId)
        assertEquals(selection.garments.single().id, recovered.garments.single().id)
    }
    @Test fun existingItemIdentityCannotBeAddedTwice() {
        val selection = FamilyAddition.select(source, item.id, shared)
        val merged = FamilyAddition.merge(Snapshot(wardrobes = listOf(shared)), selection)
        try { FamilyAddition.merge(merged, selection); fail("Duplicate accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun sameCustomNameWithDifferentBaseIsRejected() {
        val selection = FamilyAddition.select(source, item.id, shared)
        val latest = Snapshot(wardrobes = listOf(shared), customCategories = listOf(category.copy(id = newId(), wardrobeId = shared.id, baseCategory = "shoes")))
        try { FamilyAddition.merge(latest, selection); fail("Category silently changed") } catch (_: IllegalArgumentException) {}
    }
}
