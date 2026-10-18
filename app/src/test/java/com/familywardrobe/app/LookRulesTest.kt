package com.familywardrobe.app
import org.junit.Assert.*
import org.junit.Test

class LookRulesTest {
    private fun garment(id: String, category: String, availability: String = "available", wardrobe: String = "home", season: String = "all") =
        Garment(id = id, wardrobeId = wardrobe, name = id, category = category, color = "white", photoId = newId(), availability = availability, season = season)
    @Test fun candidatesRespectAccountCollectionAvailabilityWeatherAndOwnership() {
        val s = Snapshot(garments = listOf(garment("a","top"),garment("wash","bottom","laundry"),garment("other","shoes",wardrobe="other"),garment("winter","bottom",season="winter")),
            wishes = listOf(Wish(wardrobeId="home",name="Wanted shoes",category="shoes")))
        val owned=LookRules.candidates(s,"home","everyday","warm","owned",emptyList())
        assertEquals(listOf("owned:a"),owned.map { it.key })
        val mixed=LookRules.candidates(s,"home","everyday","warm","mixed",emptyList())
        assertEquals(2,mixed.size);assertTrue(mixed.any { it.kind=="need" })
    }
    @Test fun refreshKeepsLockedIdsAndReplacesOnlyUnlockedCategories() {
        val top=LookRules.owned(garment("top","top"));val bottom=LookRules.owned(garment("bottom","bottom"));val shoes=LookRules.owned(garment("shoes","shoes"))
        val alternative=LookRules.owned(garment("alternative","bottom"))
        val result=LookRules.generate(listOf(top,bottom,shoes,alternative),listOf(top,bottom,shoes),setOf(top.key,shoes.key),"any_weather",true)
        assertEquals(setOf(top.key,alternative.key,shoes.key),result.map { it.key }.toSet())
    }
    @Test fun inspirationNeverPretendsToBeOwned() {
        val idea=LookPiece("idea:top","top","Idea")
        assertEquals(listOf(idea),LookRules.candidates(Snapshot(garments=listOf(garment("owned","top"))),"home","everyday","warm","inspiration",listOf(idea)))
        assertNull(idea.ownedId);assertEquals("idea",idea.kind)
    }
    @Test fun partialAndPlannedStatusesRemainDistinct() {
        val top=LookRules.owned(garment("top","top"));val bottom=LookRules.owned(garment("bottom","bottom"));val shoes=LookRules.wanted(Wish(wardrobeId="home",name="Shoes",category="shoes"))
        assertEquals(R.string.look_incomplete,LookRules.status(listOf(top)))
        assertEquals(R.string.look_planned,LookRules.status(listOf(top,bottom,shoes)))
    }
}
