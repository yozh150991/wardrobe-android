package com.familywardrobe.app

data class LookPiece(val key: String, val category: String, val name: String, val color: String = "",
    val photoId: String = "", val ownedId: String? = null, val wishId: String? = null,
    val kind: String = "idea", val ready: Boolean = true, val season: String = "all", val availability: String = "available", val archived: Boolean = false)

object LookRules {
    val OCCASIONS = listOf("everyday", "work", "occasion", "active_day")
    val WEATHERS = listOf("any_weather", "warm", "cool", "cold", "rain")
    val SOURCES = listOf("owned", "mixed", "inspiration")
    fun owned(g: Garment) = LookPiece("owned:${g.id}", g.category, g.name, g.color, g.photoId,
        ownedId = g.id, kind = "owned", ready = WardrobeRules.ready(g.rulePiece()), season = g.season, availability = g.availability, archived = g.archived)
    fun wanted(w: Wish) = LookPiece("wish:${w.id}", w.category, w.name, w.color, w.photoId, wishId = w.id, kind = w.kind)
    fun candidates(s: Snapshot, wardrobe: String, occasion: String, weather: String, source: String,
        inspiration: List<LookPiece>): List<LookPiece> {
        if (source == "inspiration") {
            val order = InspirationCatalog.forContext(occasion, weather).map { it.key }
            val season = when(weather) { "warm" -> "summer"; "cold" -> "winter"; "any_weather" -> "all"; else -> "midseason" }
            return order.mapNotNull { key -> inspiration.find { it.key == key } } + inspiration.filter {
                it.key == "idea:${it.category}" && InspirationCatalog.validKey(it.key, it.category) && Matching.compatibleSeasons(it.season, season)
            }
        }
        val season = when (weather) { "warm" -> "summer"; "cold" -> "winter"; "cool", "rain" -> "midseason"; else -> "all" }
        val style = when (occasion) { "work", "occasion" -> "formal"; "active_day" -> "sport"; else -> "casual" }
        val available = s.garments.filter { it.wardrobeId == wardrobe && WardrobeRules.ready(it.rulePiece()) &&
            Matching.compatibleSeasons(it.season, season) }.sortedByDescending { if (it.style == style) 1 else 0 }.map(::owned)
        return available + if (source == "mixed") s.wishes.filter { it.wardrobeId == wardrobe && it.ownedId.isBlank() }.map(::wanted) else emptyList()
    }
    fun generate(candidates: List<LookPiece>, selected: List<LookPiece>, locked: Set<String>, weather: String,
        refresh: Boolean): List<LookPiece> {
        val categories = (WardrobeRules.CORE + if (weather in listOf("cold", "cool", "rain")) listOf("outerwear") else emptyList()) +
            selected.map { it.category }
        fun piece(p: LookPiece) = LookSelection.Piece(p.key, p.category, p.season)
        val keys = LookSelection.generate(candidates.map(::piece), selected.map(::piece), locked, categories, refresh)
        return keys.mapNotNull { key -> (selected + candidates).find { it.key == key } }
    }
    fun saved(s: Snapshot, outfit: Outfit): List<LookPiece> = (s.parts.filter { it.outfitId == outfit.id }.mapNotNull { part ->
        s.garments.find { it.id == part.garmentId }?.let(::owned)
    } + s.ideas.filter { it.outfitId == outfit.id }.map { part ->
        s.wishes.find { it.id == part.wishId }?.let(::wanted) ?: LookPiece(part.partId, part.category, part.name,
            part.color, part.photoId, kind = if (part.wishId == null && part.partId.startsWith("wish:")) "missing" else "idea",
            ready = !(part.wishId == null && part.partId.startsWith("wish:")))
    }).sortedBy { Matching.categories.indexOf(it.category) }
    fun choose(selected: List<LookPiece>, next: LookPiece, replacing: String? = null): List<LookPiece> {
        val conflicting = when (next.category) {
            "dress", "jumpsuit" -> WardrobeRules.BASE_CATEGORIES
            "top", "bottom" -> setOf("dress", "jumpsuit")
            else -> emptySet()
        }
        val remaining = selected.filter { it.key != next.key && it.key != replacing && it.category !in conflicting }
            .filter { WardrobeRules.categoryLimit(next.category) > 1 || it.category != next.category }
        return (remaining + next).takeIf { WardrobeRules.validCategories(it.map { p -> p.category }) } ?: selected
    }
    fun status(pieces: List<LookPiece>): Int = when {
        pieces.any { !it.ready || it.kind == "missing" } || pieces.isNotEmpty() && !WardrobeRules.validCategories(pieces.map { it.category }) -> R.string.look_needs_replacement
        !WardrobeRules.completeCategories(pieces.map { it.category }) -> R.string.look_incomplete
        pieces.any { it.ownedId == null } -> R.string.look_planned
        else -> R.string.look_ready
    }
}
