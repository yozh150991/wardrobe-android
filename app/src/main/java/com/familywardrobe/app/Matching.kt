package com.familywardrobe.app

/** Deterministic, explainable starting rules; no remote service and no invented percentage. */
object Matching {
    val categories = WardrobeRules.CATEGORIES.toList()
    val coreCategories = WardrobeRules.CORE.toList()
    val colors = listOf("white", "black", "grey", "beige", "navy", "brown", "olive", "blue", "red", "other")
    val seasons = listOf("all", "summer", "winter", "midseason")
    val styles = listOf("casual", "sport", "formal")
    val availability = listOf("available", "laundry", "repair", "lent")
    fun compatibleSeasons(a: String, b: String) = WardrobeRules.compatibleSeasons(a, b)
    fun score(a: Garment, b: Garment) = WardrobeRules.score(a.rulePiece(), b.rulePiece())
    fun candidates(base: Garment, all: List<Garment>, category: String): List<Garment> {
        val byId = all.associateBy { it.id }
        return WardrobeRules.candidates(base.rulePiece(), all.map { it.rulePiece() }, category)
            .mapNotNull { byId[it.id] }
    }
    fun build(base: Garment, all: List<Garment>): List<String> =
        WardrobeRules.build(base.rulePiece(), all.map { it.rulePiece() })
    fun complete(chosen: List<Garment>) = WardrobeRules.completeCategories(chosen.map { it.category })
    fun firstOutfit(wardrobe: String, all: List<Garment>): List<String> =
        WardrobeRules.firstOutfit(wardrobe, all.map { it.rulePiece() })
    fun alternatives(base: Garment, all: List<Garment>, chosen: List<Garment>, category: String): List<Garment> =
        candidates(base, all, category).filter { c -> chosen.filter { it.category != category }
            .all { compatibleSeasons(c.season, it.season) } }
}

internal fun Garment.rulePiece() = WardrobeRules.Piece(id, wardrobeId, category, availability, archived, season, style, color)
