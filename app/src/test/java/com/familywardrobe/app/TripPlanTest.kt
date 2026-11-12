package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

/** TRIP-02: one outfit per trip day from the capsule's items, why a day has none, and nothing recorded as worn. */
class TripPlanTest {
    private val today = "2026-11-12"
    private fun garment(id: String, category: String, color: String = "white", season: String = "all") =
        Garment(id = id, wardrobeId = "home", name = id, category = category, color = color, photoId = "", season = season)
    private val tops = listOf(garment("top1", "top"), garment("top2", "top", "grey"))
    private val rest = listOf(garment("jeans", "bottom", "navy"), garment("shoes", "shoes"), garment("coat", "outerwear", "black"))
    private val other = garment("not-packed", "top", "navy")
    private val trip = Capsule("trip", "home", "Trip", "trip", "2026-11-14", "2026-11-17")
    private fun snapshot(items: List<Garment>, capsule: Capsule = trip) = Snapshot(wardrobes = listOf(Wardrobe(id = "home", name = "Home")),
        garments = items + other, capsules = listOf(capsule), capsuleItems = items.map { CapsuleItem(capsule.id, it.id, packed = false, manual = true) })
    private fun forecast(vararg days: ForecastDay) = Forecast(WeatherCity("City", 50.0, 20.0), System.currentTimeMillis(), today, 10.0, days.toList())

    @Test fun everyDayGetsAnOutfitFromTheTripItemsOnly() {
        val s = snapshot(tops + rest)
        val c = TripPlan.coverage(s, trip, "everyday", today, null)
        assertEquals(listOf("2026-11-14", "2026-11-15", "2026-11-16", "2026-11-17"), c.days.map { it.date })
        assertEquals(4, c.covered); assertEquals(0, c.past); assertFalse(c.truncated)
        assertTrue(c.days.all { day -> day.look.none { it.id == "not-packed" } })
        // Two tops, one pair of trousers and shoes: two different outfits, then they repeat.
        assertEquals(2, c.days.map { day -> DailyRules.signature(day.look.map { it.id }) }.distinct().size)
        assertFalse(c.days[0].repeated || c.days[1].repeated); assertTrue(c.days[2].repeated && c.days[3].repeated)
        // Without a forecast the weather is unknown and said so; nothing is recorded as worn.
        assertTrue(c.days.all { it.temperature == null && it.weather == "any_weather" && it.rain == null })
        assertTrue(s.wears.isEmpty())
    }

    @Test fun theForecastDecidesTheCoatAndDaysBeyondItAreUnknown() {
        val s = snapshot(tops + rest)
        val f = forecast(ForecastDay("2026-11-14", 18.0, 26.0, 10), ForecastDay("2026-11-15", 4.0, 10.0, 80))
        val c = TripPlan.coverage(s, trip, "everyday", today, f)
        assertEquals(listOf(true, false, true, true), c.days.map { day -> day.look.none { it.id == "coat" } })
        assertTrue(c.days[1].look.any { it.id == "coat" })
        assertEquals(80, c.days[1].rain); assertNotNull(c.days[0].temperature); assertNull(c.days[2].temperature)
        // No coat packed: the cold day says so, the outfit is still there.
        val bare = snapshot(tops + rest.filter { it.id != "coat" })
        val cold = TripPlan.coverage(bare, trip, "everyday", today, f).days[1]
        assertTrue(cold.look.isNotEmpty() && cold.noOuterwear)
    }

    @Test fun aDayWithoutAnOutfitSaysWhy() {
        val noShoes = snapshot(tops + rest.filter { it.id != "shoes" })
        val c = TripPlan.coverage(noShoes, trip, "everyday", today, null)
        assertEquals(0, c.covered); assertTrue(c.days.all { it.reason == DailyDiagnostics.Reason.SHOES })
        val empty = snapshot(emptyList())
        assertEquals(DailyDiagnostics.Reason.CAPSULE_EMPTY, TripPlan.coverage(empty, trip, "everyday", today, null).days.first().reason)
    }

    @Test fun datesPastAndLongTrips() {
        val s = snapshot(tops + rest)
        // Started two days ago: today and later only.
        val started = trip.copy(startDate = "2026-11-10", endDate = "2026-11-13")
        val c = TripPlan.coverage(s.copy(capsules = listOf(started)), started, "everyday", today, null)
        assertEquals(listOf("2026-11-12", "2026-11-13"), c.days.map { it.date }); assertEquals(2, c.past)
        // Over: nothing to cover.
        val over = trip.copy(startDate = "2026-11-01", endDate = "2026-11-03")
        val done = TripPlan.coverage(s, over, "everyday", today, null)
        assertTrue(done.days.isEmpty()); assertEquals(3, done.past)
        // Longer than a month: the first 31 days.
        val long = trip.copy(startDate = "2026-12-01", endDate = "2027-02-01")
        val l = TripPlan.coverage(s, long, "everyday", today, null)
        assertEquals(TripPlan.MAX_DAYS, l.days.size); assertTrue(l.truncated)
        // No dates or invalid ones: nothing.
        assertTrue(TripPlan.coverage(s, trip.copy(startDate = "", endDate = ""), "everyday", today, null).days.isEmpty())
        assertTrue(TripPlan.coverage(s, trip.copy(startDate = "2026-11-20", endDate = "2026-11-14"), "everyday", today, null).days.isEmpty())
    }
}
