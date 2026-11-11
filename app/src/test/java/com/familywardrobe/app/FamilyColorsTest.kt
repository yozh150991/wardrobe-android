package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

/** COL-02: the ten new base colours and what an older family server receives instead. */
class FamilyColorsTest {
    private val added = listOf("cream", "camel", "khaki", "light_blue", "green", "burgundy", "pink", "orange", "yellow", "purple")

    @Test fun twentyBasesWithAnOlderBaseForEachNewOne() {
        assertEquals(20, Matching.colors.size); assertEquals("other", Matching.colors.last())
        assertEquals(ColorRules.LEGACY.toSet() + added, Matching.colors.toSet())
        for (colour in ColorRules.LEGACY) assertEquals(colour, ColorRules.legacy(colour))
        for (colour in added) assertTrue(colour, ColorRules.legacy(colour) in ColorRules.LEGACY && ColorRules.legacy(colour) != colour)
        assertEquals(mapOf("cream" to "white", "camel" to "beige", "khaki" to "olive", "light_blue" to "blue", "green" to "olive",
            "burgundy" to "red", "pink" to "red", "orange" to "red", "yellow" to "beige", "purple" to "other"), added.associateWith(ColorRules::legacy))
        assertEquals(ColorRules.LEGACY, FamilyColors.choices(false)); assertEquals(Matching.colors, FamilyColors.choices(true))
    }

    @Test fun anOlderServerGetsOlderBasesAndStaysConsistent() {
        val shade = CustomColor(wardrobeId = "w", name = "Forest", nameKey = "forest", baseColor = "green")
        val s = Snapshot(wardrobes = listOf(Wardrobe(id = "w", name = "Shared")),
            customColors = listOf(shade, CustomColor(wardrobeId = "w", name = "Night", nameKey = "night", baseColor = "navy")),
            garments = listOf(Garment(id = "g1", wardrobeId = "w", name = "Coat", category = "outerwear", color = "green", photoId = "p1", customColorId = shade.id),
                Garment(id = "g2", wardrobeId = "w", name = "Shirt", category = "top", color = "cream", photoId = "p2"),
                Garment(id = "g3", wardrobeId = "w", name = "Jeans", category = "bottom", color = "navy", photoId = "p3")),
            wishes = listOf(Wish(wardrobeId = "w", name = "Scarf", category = "accessory", kind = "product", color = "burgundy"),
                Wish(wardrobeId = "w", name = "Hat", category = "hat", kind = "product", color = "")),
            ideas = listOf(OutfitIdea("o", "i", null, "bag", "Bag", "camel", "")))
        val old = FamilyColors.forServer(s, false)
        assertEquals(listOf("olive", "white", "navy"), old.garments.map { it.color })
        // A shade's base follows its garments, so the snapshot stays valid for every check that compares them.
        assertEquals(listOf("olive", "navy"), old.customColors.map { it.baseColor })
        assertEquals(old.customColors.first().baseColor, old.garments.first { it.customColorId == shade.id }.color)
        assertEquals(listOf("red", ""), old.wishes.map { it.color }); assertEquals("beige", old.ideas.single().color)
        assertTrue(old.garments.all { it.color in ColorRules.LEGACY })
        // Names, ids and everything else are untouched.
        assertEquals(s.garments.map { it.copy(color = "") }, old.garments.map { it.copy(color = "") })
        // A server with "colors2" gets the exact colours.
        assertSame(s, FamilyColors.forServer(s, true))
        assertTrue(FamilyColors.supported(setOf("change1", "colors2"))); assertFalse(FamilyColors.supported(setOf("change1", "outfit1")))
    }
}
