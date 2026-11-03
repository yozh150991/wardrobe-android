package com.familywardrobe.app

/** STYLE-02: «Добре → Краще» — one of the person's own items in place of one piece, and both ratings. */
data class BetterSwap(val old: Garment, val new: Garment, val before: StyleRules.Rating, val after: StyleRules.Rating)

/** STYLE-01/02 on the app's data: the rating of an outfit and the best single replacement from the same wardrobe. */
object OutfitStyle {
    /** The style the occasion calls for, with the person's PERS-02 choice. */
    fun target(s: Snapshot, wardrobe: String, occasion: String, choices: PersonalChoices = PersonalChoices()): String =
        DailyOutfits.style(s, wardrobe, occasion, choices).style

    fun rate(look: List<Garment>, weather: String, target: String): StyleRules.Rating =
        StyleRules.rate(look.map { it.rulePiece() }, weather, target)

    /**
     * Candidates are what the daily suggestions may use (ready, not excluded, not held back by the wardrobe's repeat
     * rule, within a capsule when [allowed]), without items marked «Незручно»; hidden combinations stay hidden.
     * [fixed]: pinned pieces (and the item of «Давно не вдягали»), never replaced.
     */
    fun better(s: Snapshot, wardrobe: String, occasion: String, weather: String, date: String, look: List<Garment>,
        fixed: Set<String> = emptySet(), allowed: Set<String>? = null, choices: PersonalChoices = PersonalChoices()): BetterSwap? {
        if (look.isEmpty()) return null
        val uncomfortable = s.feedback.filter { it.wardrobeId == wardrobe && !it.signature.contains('|') && it.reason == PreferenceRules.DISLIKE }
            .map { it.signature }.toSet()
        val candidates = DailyOutfits.items(s, date, allowed).map { it.piece }.filter { it.wardrobe == wardrobe && it.id !in uncomfortable }
        val swap = StyleRules.better(look.map { it.rulePiece() }, candidates, weather, target(s, wardrobe, occasion, choices), fixed,
            DailyOutfits.rejected(s, wardrobe, occasion, weather)) ?: return null
        val byId = s.garments.associateBy { it.id }
        return BetterSwap(byId[swap.oldId] ?: return null, byId[swap.newId] ?: return null, swap.before, swap.after)
    }

    /** The outfit's colours, biggest pieces first, each once (for the colour dots). */
    fun colours(look: List<Garment>): List<String> =
        look.sortedBy { Matching.categories.indexOf(it.category).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .filter { it.category !in setOf("jewelry", "accessory") }.map { it.color }.distinct()

    fun replaced(ids: String, swap: BetterSwap): String = ids.split(',').joinToString(",") { if (it == swap.old.id) swap.new.id else it }
}
