package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

class MatchingTest {
    private fun item(id: String, category: String, wardrobe: String = "home", color: String = "beige",
        season: String = "all", style: String = "casual", available: String = "available") =
        Garment(id = id, wardrobeId = wardrobe, name = id, category = category, color = color,
            photoId = "photo", season = season, style = style, availability = available)

    @Test fun keepsSelectedItemAndNeverMixesWardrobes() {
        val base = item("base", "bottom")
        val all = listOf(base, item("top", "top"), item("shoes", "shoes"), item("foreign", "top", wardrobe = "other"))
        assertEquals(setOf("base", "top", "shoes"), Matching.build(base, all).toSet())
    }
    @Test fun unavailableAndArchivedItemsAreExcluded() {
        val base = item("base", "bottom")
        val all = listOf(base, item("wash", "top", available = "laundry"), item("archived", "top").copy(archived = true), item("shoes", "shoes"))
        assertEquals(setOf("base", "shoes"), Matching.build(base, all).toSet())
    }
    @Test fun incompleteWardrobeDoesNotInventItems() {
        val base = item("only", "bottom")
        assertEquals(listOf("only"), Matching.build(base, listOf(base)))
    }
    @Test fun allSeasonBaseCannotCombineWinterTopWithSummerShoes() {
        val base = item("base", "bottom")
        val all = listOf(base, item("top", "top", season = "winter"), item("shoes", "shoes", season = "summer"))
        assertEquals(2, Matching.build(base, all).size)
    }
    @Test fun replacementChecksOtherSelectedPieces() {
        val base = item("base", "bottom")
        val summerShoes = item("shoes", "shoes", season = "summer")
        val winterTop = item("winter", "top", season = "winter")
        val summerTop = item("summer", "top", season = "summer")
        assertEquals(listOf("summer"), Matching.alternatives(base, listOf(winterTop, summerTop), listOf(base, summerShoes), "top").map { it.id })
    }
    @Test fun sameStyleRanksAboveMismatchedStyleWithSameColor() {
        val base = item("base", "bottom")
        val casual = item("casual", "top", color = "white")
        val sports = item("sports", "top", style = "sport")
        assertTrue(Matching.score(base, casual) > Matching.score(base, sports))
    }
    @Test fun archivedBaseCannotStartSuggestion() {
        val base = item("base", "bottom").copy(archived = true)
        assertTrue(Matching.build(base, listOf(base)).isEmpty())
    }
    @Test fun findsCompleteSetEvenWhenTopRankedTopCannotMatchShoes() {
        val base = item("base", "bottom")
        val all = listOf(base, item("winter", "top", color = "white", season = "winter"),
            item("summer", "top", color = "other", season = "summer"), item("shoes", "shoes", season = "summer"))
        assertEquals(setOf("base", "summer", "shoes"), Matching.build(base, all).toSet())
    }
}
