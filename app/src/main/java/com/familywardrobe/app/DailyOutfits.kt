package com.familywardrobe.app

object DailyOutfits {
    fun rawItems(s: Snapshot, date: String): List<DailyRules.Item> {
        val wear = s.wears.filter { it.date <= date }.groupBy { it.garmentId }
        return s.garments.map { g -> DailyRules.Item(g.rulePiece(), wear[g.id]?.map { it.date }?.distinct()?.size ?: 0,
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
    private fun rows(s: Snapshot, wardrobe: String) = s.feedback.filter { it.wardrobeId == wardrobe }
        .map { PreferenceRules.Row(it.signature, it.occasion, it.weather, it.reason) }
    /** PERS-01: whole sets turned down for this context (an aesthetic dislike applies in every context). */
    fun rejected(s: Snapshot, wardrobe: String, occasion: String, weather: String): Set<String> =
        PreferenceRules.rejected(rows(s, wardrobe), occasion, weather)
    /** PERS-01: items suggested less often in this context; never a reason to hide one. */
    fun penalties(s: Snapshot, wardrobe: String, occasion: String, weather: String): Map<String, Int> =
        PreferenceRules.penalties(rows(s, wardrobe), occasion, weather)
    fun generate(s: Snapshot, wardrobe: String, occasion: String, weather: String, date: String,
        seed: String = date, additionalRejected: Set<String> = emptySet(), allowed: Set<String>? = null, fixed: Map<String, String> = emptyMap()): List<List<Garment>> {
        val byId = s.garments.associateBy { it.id }
        return DailyRules.generate(items(s, date, allowed), wardrobe, occasion, weather, date, seed,
            rejected(s, wardrobe, occasion, weather) + additionalRejected, fixed, 3, penalties(s, wardrobe, occasion, weather))
            .map { ids -> ids.mapNotNull { byId[it] } }
    }
    fun replace(s: Snapshot, wardrobe: String, occasion: String, weather: String, date: String,
        selected: List<Garment>, category: String, additionalRejected: Set<String> = emptySet(), allowed: Set<String>? = null): List<Garment> {
        val byId = s.garments.associateBy { it.id }
        return DailyRules.replace(items(s, date, allowed), wardrobe, occasion, weather, date, selected.map { it.id }, category,
            rejected(s, wardrobe, occasion, weather) + additionalRejected, penalties(s, wardrobe, occasion, weather)).mapNotNull { byId[it] }
    }
}
