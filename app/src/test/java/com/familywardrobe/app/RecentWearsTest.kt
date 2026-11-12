package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

/** GEN-03 and UX-06 on the app's own entry points: recent wear only, and «Ношу рідко» is not forgotten. */
class RecentWearsTest {
    private val today = "2026-11-12"
    private fun garment(id: String, category: String, rare: Boolean = false, favourite: Boolean = false) =
        Garment(id = id, wardrobeId = "home", name = id, category = category, color = "white", photoId = "", rarelyWorn = rare,
            favorite = favourite, createdAt = 0)
    private val home = Wardrobe(id = "home", name = "Home")

    @Test fun onlyTheLast30DaysCountAgainstAnItem() {
        // The favourite was worn on 40 days, all more than a month ago; the new top never.
        val old = (0 until 40).map { Wear("loved", java.time.LocalDate.parse("2026-08-01").plusDays(it.toLong()).toString(), "item:loved") }
        val s = Snapshot(wardrobes = listOf(home), garments = listOf(garment("loved", "top", favourite = true), garment("new", "top"),
            garment("trousers", "bottom"), garment("shoes", "shoes")), wears = old + Wear("loved", "2026-11-20", "item:loved"))
        val items = DailyOutfits.rawItems(s, today).associateBy { it.piece.id }
        assertEquals(0, items.getValue("loved").wears)
        // The last wear (for the repeat rule) is still the real one, never a future day.
        assertEquals("2026-09-09", items.getValue("loved").lastWear)
        assertTrue(DailyOutfits.generate(s, "home", "everyday", "any_weather", today).first().any { it.id == "loved" })
        // Worn on 12 days of the last month: the full penalty, and the new top leads.
        val recent = (0 until 12).map { Wear("loved", java.time.LocalDate.parse(today).minusDays(it.toLong() * 2 + 1).toString(), "item:loved") }
        val busy = s.copy(wears = recent)
        assertEquals(12, DailyOutfits.rawItems(busy, today).single { it.piece.id == "loved" }.wears)
        assertTrue(DailyOutfits.generate(busy, "home", "everyday", "any_weather", today).first().any { it.id == "new" })
    }

    @Test fun rarelyWornIsNotForgotten() {
        val s = Snapshot(wardrobes = listOf(home), garments = listOf(garment("suit", "top", rare = true), garment("tee", "top"),
            garment("trousers", "bottom"), garment("shoes", "shoes")),
            wears = listOf(Wear("trousers", "2026-11-10", "item:trousers"), Wear("shoes", "2026-11-10", "item:shoes")))
        assertEquals(listOf("tee"), RotationOutfits.candidates(s, "home", "everyday", "any_weather", today))
        val usage = WardrobeStats.usage(s, "home", today, false).associateBy { it.garment.id }
        assertFalse(usage.getValue("suit").forgotten); assertTrue(usage.getValue("tee").forgotten)
        // Chosen in the statistics, it still gets an outfit.
        assertEquals("suit", RotationOutfits.suggest(s, "home", "everyday", "any_weather", today, anchor = "suit")!!.anchor.id)
    }
}
