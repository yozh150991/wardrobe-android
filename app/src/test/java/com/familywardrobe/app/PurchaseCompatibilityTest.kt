package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

class PurchaseCompatibilityTest {
    private val date = "2026-10-08"
    private val wish = Wish(id = "wish", wardrobeId = "home", name = "Shoes", category = "shoes", kind = "product", color = "navy", link = "https://shop.example/shoes")
    private fun garment(id: String, category: String) = Garment(id = id, wardrobeId = "home", name = id, category = category, color = "navy", photoId = "")
    private fun snapshot() = Snapshot(wardrobes = listOf(Wardrobe(id = "home", name = "Home")),
        garments = listOf(garment("top", "top"), garment("bottom", "bottom"), garment("owned-shoes", "shoes")), wishes = listOf(wish))
    private fun advice(s: Snapshot, product: Wish = wish, hints: PurchaseHints = PurchaseHints()) =
        PurchaseCompatibility.analyze(s, product, "everyday", "any_weather", date, hints)

    @Test fun mapsCandidateAndRealPartnersWithoutBuyingTheWish() {
        val s = snapshot(); val report = advice(s); val row = report.previews.single()
        assertEquals(wish.id, row.pieces.single { it.wishId != null }.wishId)
        assertEquals(setOf("top", "bottom"), row.pieces.mapNotNull { it.ownedId }.toSet())
        assertEquals("owned-shoes", row.replacements.single().id)
        assertEquals(0, report.withoutReplacement)
        assertEquals("", s.wishes.single().ownedId)
    }
    @Test fun sourceLinkMatchingIsRestrictedToThisWardrobe() {
        val base = snapshot()
        val source = wish.copy(id = "receipt", ownedId = "owned-shoes", link = wish.link + "?utm_source=ad")
        assertTrue(advice(base.copy(wishes = base.wishes + source)).similar.single().reasons.contains("source"))
        assertFalse(advice(base.copy(wishes = base.wishes + source.copy(wardrobeId = "other"))).similar.single().reasons.contains("source"))
        val foreign = garment("foreign-shoes", "shoes").copy(wardrobeId = "other")
        assertFalse(advice(base.copy(garments = base.garments + foreign)).similar.any { it.garment.id == foreign.id })
    }
    @Test fun noRepeatAndArchiveKeepTheirDistinctMeaning() {
        val base = snapshot()
        val s = base.copy(wardrobes = listOf(base.wardrobes.single().copy(noRepeatDays = 2)),
            garments = base.garments.map { if (it.id == "owned-shoes") it.copy(archived = true) else it },
            wears = listOf(Wear("top", date, "item:top")))
        val report = advice(s)
        assertTrue(report.previews.isEmpty())
        assertEquals(1, report.filteredCount)
        assertEquals(1, report.unavailableCount)
        assertTrue(report.similar.single().garment.archived)
    }
    @Test fun needOrAlreadyOwnedProductHasNoPurchaseAdvice() {
        assertTrue(advice(snapshot(), wish.copy(kind = "need")).previews.isEmpty())
        assertTrue(advice(snapshot(), wish.copy(ownedId = "owned-shoes")).similar.isEmpty())
    }
    @Test fun optionalTraitsAreExplicitAndDoNotMutateTheWish() {
        val s = snapshot()
        val unknown = advice(s).similar.single().reasons
        assertFalse(unknown.contains("season")); assertFalse(unknown.contains("style"))
        val known = advice(s, hints = PurchaseHints(season = "summer", style = "casual")).similar.single().reasons
        assertTrue(known.contains("season")); assertTrue(known.contains("style"))
        assertEquals(wish, s.wishes.single())
    }
    @Test fun savedSignatureUsesOwnedIdsAndTheWishKey() {
        val base = snapshot()
        val outfit = Outfit(id = "planned", wardrobeId = "home", name = "Plan", signature = DailyRules.signature(listOf("top", "bottom", "wish:${wish.id}")), source = "mixed")
        assertTrue(advice(base.copy(outfits = listOf(outfit))).previews.single().saved)
        assertFalse(advice(base.copy(outfits = listOf(outfit.copy(wardrobeId = "other")))).previews.single().saved)
    }
}
