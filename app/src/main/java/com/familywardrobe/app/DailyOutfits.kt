package com.familywardrobe.app

/** A day's variants; [hiddenShown]: every variant was hidden by earlier reasons, so the best hidden ones are shown. */
data class DailyLooks(val looks: List<List<Garment>>, val hiddenShown: Boolean = false)

object DailyOutfits {
    fun rawItems(s: Snapshot, date: String): List<DailyRules.Item> {
        val wear = s.wears.filter { it.date <= date }.groupBy { it.garmentId }
        // GEN-03: only the last 30 days' wear counts against an item; the last wear still drives the repeat rule.
        return s.garments.map { g -> DailyRules.Item(g.rulePiece(), wear[g.id]?.let { rows -> DailyRules.recentWears(rows.map { it.date }, date) } ?: 0,
            g.favorite, wear[g.id]?.maxOfOrNull { it.date }.orEmpty()) }
    }
    fun items(s: Snapshot, date: String, allowed: Set<String>? = null): List<DailyRules.Item> {
        val byId = s.garments.associateBy { it.id }
        val rules = s.wardrobes.associate { it.id to it.noRepeatDays }
        return rawItems(s, date).filter { item ->
            (allowed == null || item.piece.id in allowed) && RecommendationRules.allowed(byId[item.piece.id]?.suggestionExcluded == true,
                item.lastWear, date, rules[item.piece.wardrobe] ?: 0)
        }
    }
    fun rows(s: Snapshot, wardrobe: String) = s.feedback.filter { it.wardrobeId == wardrobe }
        .map { PreferenceRules.Row(it.signature, it.occasion, it.weather, it.reason) }
    /** PERS-01: whole sets turned down for this context (an aesthetic dislike applies in every context). */
    fun rejected(s: Snapshot, wardrobe: String, occasion: String, weather: String): Set<String> =
        PreferenceRules.rejected(rows(s, wardrobe), occasion, weather)
    /** PERS-02: the style favoured for this occasion, where it comes from and how many reasons back it. */
    fun style(s: Snapshot, wardrobe: String, occasion: String, choices: PersonalChoices = PersonalChoices()): PreferenceProfile.Style =
        PreferenceProfile.style(rows(s, wardrobe), occasion, choices.style(occasion))
    /** PERS-02: how the forecast is shifted for this person. */
    fun warmth(s: Snapshot, wardrobe: String, choices: PersonalChoices = PersonalChoices()): PreferenceProfile.Warmth =
        PreferenceProfile.warmth(rows(s, wardrobe), choices.warmth)
    /** Items suggested less often in this context (PERS-01 reasons, PERS-02 style, PERS-03 colours worn less); never a reason to hide one. */
    fun penalties(s: Snapshot, wardrobe: String, occasion: String, weather: String, choices: PersonalChoices = PersonalChoices()): Map<String, Int> =
        PreferenceProfile.penalties(rows(s, wardrobe), style(s, wardrobe, occasion, choices), occasion, weather, pieces(s, wardrobe), choices.avoided)
    /** PERS-03: items of the person's liked colours, suggested a little more often. */
    fun bonus(s: Snapshot, wardrobe: String, choices: PersonalChoices = PersonalChoices()): Map<String, Int> =
        if (choices.liked.isEmpty()) emptyMap() else PreferenceProfile.bonus(pieces(s, wardrobe), choices.liked, choices.avoided)
    private fun pieces(s: Snapshot, wardrobe: String) = s.garments.filter { it.wardrobeId == wardrobe }.map { it.rulePiece() }
    fun generate(s: Snapshot, wardrobe: String, occasion: String, weather: String, date: String,
        seed: String = date, additionalRejected: Set<String> = emptySet(), allowed: Set<String>? = null, fixed: Map<String, String> = emptyMap(),
        choices: PersonalChoices = PersonalChoices(), temperature: Double? = null): List<List<Garment>> =
        daily(s, wardrobe, occasion, weather, date, seed, additionalRejected, allowed, fixed, choices, fallback = false, temperature = temperature).looks
    /**
     * PERS-02: preferences never leave the day empty. Styles and penalties only change scores; when the hidden sets
     * alone hide every possible variant, the best hidden ones are shown and [DailyLooks.hiddenShown] says so.
     * [temperature]: the forecast's temperature behind [weather], or null for a manual choice (WTH-01, see WeatherRules).
     */
    fun daily(s: Snapshot, wardrobe: String, occasion: String, weather: String, date: String,
        seed: String = date, additionalRejected: Set<String> = emptySet(), allowed: Set<String>? = null, fixed: Map<String, String> = emptyMap(),
        choices: PersonalChoices = PersonalChoices(), fallback: Boolean = true, temperature: Double? = null): DailyLooks {
        val byId = s.garments.associateBy { it.id }
        val items = items(s, date, allowed)
        val target = style(s, wardrobe, occasion, choices).style
        val scores = penalties(s, wardrobe, occasion, weather, choices)
        val hidden = rejected(s, wardrobe, occasion, weather)
        val liked = bonus(s, wardrobe, choices)
        fun run(skip: Set<String>) = DailyRules.generate(items, wardrobe, occasion, weather, date, seed,
            skip, fixed, 3, scores, target, liked, temperature).map { ids -> ids.mapNotNull { byId[it] } }
        val looks = run(hidden + additionalRejected)
        if (looks.isNotEmpty() || !fallback || hidden.isEmpty()) return DailyLooks(looks)
        val shown = run(additionalRejected)
        return DailyLooks(shown, hiddenShown = shown.isNotEmpty())
    }
    fun replace(s: Snapshot, wardrobe: String, occasion: String, weather: String, date: String,
        selected: List<Garment>, category: String, additionalRejected: Set<String> = emptySet(), allowed: Set<String>? = null,
        choices: PersonalChoices = PersonalChoices(), temperature: Double? = null): List<Garment> {
        val byId = s.garments.associateBy { it.id }
        return DailyRules.replace(items(s, date, allowed), wardrobe, occasion, weather, date, selected.map { it.id }, category,
            rejected(s, wardrobe, occasion, weather) + additionalRejected, penalties(s, wardrobe, occasion, weather, choices),
            style(s, wardrobe, occasion, choices).style, bonus(s, wardrobe, choices), temperature).mapNotNull { byId[it] }
    }
}
