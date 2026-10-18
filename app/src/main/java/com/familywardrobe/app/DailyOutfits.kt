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
    fun rejected(s: Snapshot, wardrobe: String, occasion: String, weather: String): Set<String> =
        s.feedback.filter { it.wardrobeId == wardrobe && it.occasion == occasion && it.weather == weather }.map { it.signature }.toSet()
    fun generate(s: Snapshot, wardrobe: String, occasion: String, weather: String, date: String,
        seed: String = date, additionalRejected: Set<String> = emptySet(), allowed: Set<String>? = null, fixed: Map<String, String> = emptyMap()): List<List<Garment>> {
        val byId = s.garments.associateBy { it.id }
        return DailyRules.generate(items(s, date, allowed), wardrobe, occasion, weather, date, seed,
            rejected(s, wardrobe, occasion, weather) + additionalRejected, fixed, 3).map { ids -> ids.mapNotNull { byId[it] } }
    }
    fun replace(s: Snapshot, wardrobe: String, occasion: String, weather: String, date: String,
        selected: List<Garment>, category: String, additionalRejected: Set<String> = emptySet(), allowed: Set<String>? = null): List<Garment> {
        val byId = s.garments.associateBy { it.id }
        return DailyRules.replace(items(s, date, allowed), wardrobe, occasion, weather, date, selected.map { it.id }, category,
            rejected(s, wardrobe, occasion, weather) + additionalRejected).mapNotNull { byId[it] }
    }
}
