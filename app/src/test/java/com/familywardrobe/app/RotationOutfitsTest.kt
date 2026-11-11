package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** ROT-01 on the app's own entry point: what is suggested, why, and what is never suggested. */
class RotationOutfitsTest {
    private val today = "2026-11-03"
    private val longAgo = LocalDate.parse("2025-01-01").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val lastWeek = LocalDate.parse("2026-10-27").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun garment(id: String, category: String, season: String = "all", availability: String = "available",
        archived: Boolean = false, excluded: Boolean = false, added: Long = longAgo) =
        Garment(id = id, wardrobeId = "home", name = "Річ $id", category = category, color = "navy", photoId = "", season = season,
            availability = availability, archived = archived, suggestionExcluded = excluded, createdAt = added)
    private val base = Snapshot(wardrobes = listOf(Wardrobe(id = "home", name = "Дім")),
        garments = listOf(garment("old-top", "top"), garment("top", "top"), garment("bottom", "bottom"), garment("shoes", "shoes"),
            garment("coat", "outerwear", season = "winter"), garment("new-top", "top", added = lastWeek)),
        // Everything but the forgotten top and the coat was worn recently.
        wears = listOf(Wear("old-top", "2026-06-01", "item:old-top"), Wear("top", "2026-11-01", "item:top"),
            Wear("bottom", "2026-11-01", "item:bottom"), Wear("shoes", "2026-11-02", "item:shoes"),
            // A wear dated after today does not count as the last one.
            Wear("old-top", "2026-11-10", "item:old-top")))

    @Test fun anItemNotWornFor90DaysGetsAWholeOutfitForToday() {
        val look = RotationOutfits.suggest(base, "home", "everyday", "warm", today)!!
        assertEquals("old-top", look.anchor.id)
        assertTrue(look.look.map { it.id }.containsAll(listOf("old-top", "bottom", "shoes")))
        assertEquals("2026-06-01", look.lastWear); assertEquals(155L, look.idleDays); assertTrue(look.forgotten)
        assertNull(look.problem)
        // The winter coat waits for cold weather; in the cold it is a candidate too.
        assertEquals(listOf("old-top"), RotationOutfits.candidates(base, "home", "everyday", "warm", today))
        assertEquals(setOf("old-top", "coat"), RotationOutfits.candidates(base, "home", "everyday", "cold", today).toSet())
        // A new item is not forgotten: it counts from the day it was added.
        assertFalse("new-top" in RotationOutfits.candidates(base, "home", "everyday", "cold", today))
    }

    @Test fun neverSuggestsWhatCannotOrShouldNotBeWorn() {
        for (change in listOf<(Garment) -> Garment>({ it.copy(availability = "laundry") }, { it.copy(availability = "repair") },
            { it.copy(availability = "lent") }, { it.copy(archived = true) }, { it.copy(suggestionExcluded = true) })) {
            val s = base.copy(garments = base.garments.map { if (it.id == "old-top") change(it) else it })
            assertNull(RotationOutfits.suggest(s, "home", "everyday", "warm", today))
        }
        // «Незручно» (PERS-01) keeps it out of the suggestion.
        val uncomfortable = base.copy(feedback = listOf(OutfitFeedback(wardrobeId = "home", signature = "old-top",
            weather = "warm", occasion = "work", reason = PreferenceRules.DISLIKE)))
        assertNull(RotationOutfits.suggest(uncomfortable, "home", "everyday", "warm", today))
        // A hidden combination stays hidden: the only outfit with it was turned down.
        val hidden = base.copy(feedback = listOf(OutfitFeedback(wardrobeId = "home", signature = DailyRules.signature(listOf("old-top", "bottom", "shoes")),
            weather = "warm", occasion = "everyday", reason = PreferenceRules.DISLIKE)))
        assertNull(RotationOutfits.suggest(hidden, "home", "everyday", "warm", today))
        // Another wardrobe's items never appear.
        assertNull(RotationOutfits.suggest(base.copy(wardrobes = base.wardrobes + Wardrobe(id = "child", name = "Син")), "child", "everyday", "warm", today))
    }

    @Test fun anotherItemCyclesAndPinsAreKept() {
        val s = base.copy(garments = base.garments + garment("old-shoes", "shoes"))
        val first = RotationOutfits.suggest(s, "home", "everyday", "warm", today)!!
        assertEquals(1, first.others)
        val second = RotationOutfits.suggest(s, "home", "everyday", "warm", today, after = first.anchor.id)!!
        assertNotEquals(first.anchor.id, second.anchor.id)
        assertEquals(first.anchor.id, RotationOutfits.suggest(s, "home", "everyday", "warm", today, after = second.anchor.id)!!.anchor.id)
        // A top pinned by the person wins over the forgotten top (next in turn); the forgotten shoes are suggested instead.
        assertEquals("old-top", second.anchor.id)
        val pinned = RotationOutfits.suggest(s, "home", "everyday", "warm", today, after = "old-shoes", fixed = mapOf("top" to "top"))!!
        assertEquals("old-shoes", pinned.anchor.id); assertTrue(pinned.look.any { it.id == "top" })
    }

    @Test fun anItemChosenInTheStatisticsExplainsWhyItCannotBeWorn() {
        // Chosen explicitly: suggested even if worn recently or marked uncomfortable, replacing a pin of its category.
        val chosen = RotationOutfits.suggest(base, "home", "everyday", "warm", today, anchor = "top", fixed = mapOf("top" to "old-top"))!!
        assertEquals("top", chosen.anchor.id); assertFalse(chosen.forgotten); assertTrue(chosen.look.any { it.id == "top" })
        val laundry = base.copy(garments = base.garments.map { if (it.id == "top") it.copy(availability = "laundry") else it })
        RotationOutfits.suggest(laundry, "home", "everyday", "warm", today, anchor = "top")!!.let {
            assertEquals(RotationRules.UNAVAILABLE, it.problem); assertTrue(it.look.isEmpty())
        }
        val excluded = base.copy(garments = base.garments.map { if (it.id == "top") it.copy(suggestionExcluded = true) else it })
        assertEquals(RotationRules.EXCLUDED, RotationOutfits.suggest(excluded, "home", "everyday", "warm", today, anchor = "top")!!.problem)
        assertEquals(RotationRules.WEATHER, RotationOutfits.suggest(base, "home", "everyday", "warm", today, anchor = "coat")!!.problem)
        val repeat = base.copy(wardrobes = listOf(Wardrobe(id = "home", name = "Дім", noRepeatDays = 7)))
        assertEquals(RotationRules.REPEAT, RotationOutfits.suggest(repeat, "home", "everyday", "warm", today, anchor = "top")!!.problem)
        // Nothing to wear it with: no problem code, an empty outfit.
        val noShoes = base.copy(garments = base.garments.filter { it.category != "shoes" })
        RotationOutfits.suggest(noShoes, "home", "everyday", "warm", today, anchor = "old-top")!!.let {
            assertNull(it.problem); assertTrue(it.look.isEmpty())
        }
        assertNull(RotationOutfits.suggest(base, "home", "everyday", "warm", today, anchor = "missing"))
    }

    @Test fun theStatisticsUseTheSameRule() {
        val usage = WardrobeStats.usage(base, "home", today, false).associateBy { it.garment.id }
        assertTrue(usage.getValue("old-top").forgotten); assertEquals("2026-06-01", usage.getValue("old-top").last)
        assertTrue(usage.getValue("coat").forgotten)
        assertFalse(usage.getValue("new-top").forgotten); assertFalse(usage.getValue("top").forgotten)
        for (row in usage.values) assertEquals(row.garment.id, RotationRules.forgotten(row.last.orEmpty(), RotationOutfits.added(row.garment), today), row.forgotten)
    }

    @Test fun rulesCases() { RotationRulesCases.main(emptyArray()) }
}
