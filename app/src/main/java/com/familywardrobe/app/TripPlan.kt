package com.familywardrobe.app

import java.time.LocalDate

/**
 * TRIP-02 (0.14.41): one day of a trip.
 * [temperature]: the forecast's temperature to dress for (as the person feels it), or null when the day is beyond the
 * forecast — then [weather] is «будь-яка» and nothing pretends to know it. [rain]: the forecast's chance of rain, %.
 * [look]: the suggested outfit from the trip's items, empty with [reason] when there is none. [repeated]: every
 * outfit had already been suggested for an earlier day, so one is worn again. [noOuterwear]: the weather calls for a
 * coat and the trip's items have none that is ready.
 */
data class TripDay(val date: String, val temperature: Double?, val weather: String, val rain: Int?, val look: List<Garment>,
    val reason: DailyDiagnostics.Reason?, val repeated: Boolean, val noOuterwear: Boolean)

/** A trip's days from today to its end, at most [MAX_DAYS]; [past]: the days before today that are left out. */
data class TripCoverage(val days: List<TripDay>, val past: Int, val truncated: Boolean) {
    val covered: Int get() = days.count { it.look.isNotEmpty() }
}

/**
 * TRIP-02: does the trip's packing cover every day? One outfit per day from the capsule's items, chosen by the same
 * rules as «Що вдягнути сьогодні» (style, colours, the person's choices, the forecast's temperature), each day a
 * different outfit while there is one. Nothing is saved and nothing is recorded as worn: it is a check, not a plan.
 * The forecast is the one for the person's own city — the trip's destination is not known to the app.
 */
object TripPlan {
    const val MAX_DAYS = 31
    /** From this chance of rain a day says that the forecast does not make an item waterproof. */
    const val RAIN_NOTE = 50

    fun coverage(s: Snapshot, capsule: Capsule, occasion: String, today: String, forecast: Forecast?, choices: PersonalChoices = PersonalChoices()): TripCoverage {
        if (!RoadmapRules.validDate(capsule.startDate) || !RoadmapRules.validDate(capsule.endDate) || capsule.startDate > capsule.endDate)
            return TripCoverage(emptyList(), 0, false)
        val start = LocalDate.parse(capsule.startDate); val end = LocalDate.parse(capsule.endDate); val now = LocalDate.parse(today)
        val first = if (start.isBefore(now)) now else start
        val past = if (start.isBefore(now)) (minOf(now, end.plusDays(1)).toEpochDay() - start.toEpochDay()).toInt() else 0
        if (first.isAfter(end)) return TripCoverage(emptyList(), past, false)
        val total = (end.toEpochDay() - first.toEpochDay() + 1).toInt()
        val wardrobe = capsule.wardrobeId
        val allowed = s.capsuleItems.filter { it.capsuleId == capsule.id }.map { it.garmentId }.toSet()
        val warmth = DailyOutfits.warmth(s, wardrobe, choices)
        val excluded = s.garments.filter { it.wardrobeId == wardrobe && it.suggestionExcluded }.map { it.id }.toSet()
        val repeatRule = s.wardrobes.find { it.id == wardrobe }?.noRepeatDays ?: 0
        val used = mutableSetOf<String>()
        val days = (0 until minOf(total, MAX_DAYS)).map { offset ->
            val date = first.plusDays(offset.toLong()).toString()
            val temperature = forecast?.temperature(date)?.let { WeatherRules.shifted(it, warmth.shift) }
            val weather = temperature?.let { RoadmapRules.weatherMode(it) } ?: "any_weather"
            val seed = "trip:${capsule.id}:$date"
            fun first(skip: Set<String>) = DailyOutfits.daily(s, wardrobe, occasion, weather, date, seed, skip, allowed,
                choices = choices, temperature = temperature).looks.firstOrNull().orEmpty()
            // A different outfit each day while there is one; then one of the earlier ones is worn again.
            val fresh = first(used)
            val again = if (fresh.isEmpty() && used.isNotEmpty()) first(emptySet()) else emptyList()
            val look = fresh.ifEmpty { again }
            val repeated = fresh.isEmpty() && again.isNotEmpty()
            if (look.isNotEmpty()) used += DailyRules.signature(look.map { it.id })
            val reason = if (look.isNotEmpty()) null else DailyDiagnostics.explain(DailyOutfits.rawItems(s, date), wardrobe, occasion, weather,
                date, seed, allowed, allowed, false, excluded, repeatRule, DailyOutfits.rejected(s, wardrobe, occasion, weather), emptyMap())
            val noOuterwear = WeatherRules.outerwear(weather, temperature) && look.isNotEmpty() && look.none { it.category == "outerwear" }
            TripDay(date, temperature, weather, forecast?.days?.find { it.date == date }?.rain?.takeIf { temperature != null }, look, reason, repeated, noOuterwear)
        }
        return TripCoverage(days, past, total > MAX_DAYS)
    }
}
