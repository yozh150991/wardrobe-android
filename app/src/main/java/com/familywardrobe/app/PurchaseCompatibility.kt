package com.familywardrobe.app

data class PurchaseHints(val subcategory: String = "", val season: String = "", val style: String = "")
data class PurchasePreview(val pieces: List<LookPiece>, val saved: Boolean, val replacements: List<Garment> = emptyList())
data class PurchaseMatch(val garment: Garment, val reasons: List<String>, val eligible: Boolean)
data class PurchaseAssessment(val previews: List<PurchasePreview>, val similar: List<PurchaseMatch>,
    val missingLayouts: List<List<String>>, val partnerCount: Int, val unavailableCount: Int,
    val filteredCount: Int, val candidateSeasonConflict: Boolean, val withoutReplacement: Int)

object PurchaseCompatibility {
    fun analyze(s: Snapshot, wish: Wish, occasion: String, weather: String, date: String, hints: PurchaseHints = PurchaseHints()): PurchaseAssessment {
        if (wish.kind != "product" || wish.ownedId.isNotBlank())
            return PurchaseAssessment(emptyList(), emptyList(), emptyList(), 0, 0, 0, false, 0)
        val local = s.garments.filter { it.wardrobeId == wish.wardrobeId }
        val byId = local.associateBy { it.id }
        val eligible = DailyOutfits.items(s, date).map { it.piece.id }.toSet()
        val usage = DailyOutfits.rawItems(s, date).associateBy { it.piece.id }
        val sources = s.wishes.filter { it.wardrobeId == wish.wardrobeId && it.ownedId.isNotBlank() }.groupBy { it.ownedId }
        val items = local.map { g -> PurchaseAdviceRules.Owned(usage.getValue(g.id), g.name, g.subcategory,
            g.id in eligible, sources[g.id].orEmpty().map { it.link }) }
        val profile = PurchaseAdviceRules.Profile(wish.id, wish.wardrobeId, wish.category, wish.color,
            hints.subcategory, hints.season, hints.style, wish.link)
        val existing = s.outfits.filter { it.wardrobeId == wish.wardrobeId }.map { it.signature }.toSet()
        val rejected = DailyOutfits.rejected(s, wish.wardrobeId, occasion, weather).map { signature ->
            DailyRules.signature(signature.split('|').map { if (it == "wish:${wish.id}") profile.candidateId() else it })
        }.toSet()
        val report = PurchaseAdviceRules.analyze(profile, items, existing, occasion, weather, date, rejected)
        return PurchaseAssessment(report.previews.map { row ->
            val pieces = row.ids.map { id -> if (id == profile.candidateId()) LookRules.wanted(wish).copy(season = hints.season.ifBlank { "all" })
                else LookRules.owned(byId.getValue(id)) }
            PurchasePreview(pieces, row.saved, row.replacements.map { byId.getValue(it.id()) })
        }, report.similar.map { PurchaseMatch(byId.getValue(it.owned.id()), it.reasons, it.owned.eligible) },
            report.missingLayouts, report.partnerCount, report.unavailableCount, report.filteredCount,
            report.candidateSeasonConflict, report.withoutReplacement())
    }
    fun previews(s: Snapshot, wish: Wish, occasion: String, weather: String, date: String): List<PurchasePreview> =
        analyze(s, wish, occasion, weather, date).previews
    fun similar(s: Snapshot, wish: Wish): List<Garment> =
        analyze(s, wish, "everyday", "any_weather", java.time.LocalDate.now().toString()).similar.map { it.garment }
}
