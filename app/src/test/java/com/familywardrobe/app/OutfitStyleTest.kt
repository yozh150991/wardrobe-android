package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

/** STYLE-01/02 on the app's data: the rating of a real outfit and «Добре → Краще» from the same wardrobe only. */
class OutfitStyleTest {
    private val today = "2026-11-03"
    private fun garment(id: String, category: String, color: String, style: String = "casual", excluded: Boolean = false, wardrobe: String = "home") =
        Garment(id = id, wardrobeId = wardrobe, name = "Річ $id", category = category, color = color, photoId = "", style = style,
            suggestionExcluded = excluded)
    private val base = Snapshot(wardrobes = listOf(Wardrobe(id = "home", name = "Дім"), Wardrobe(id = "child", name = "Син")),
        garments = listOf(garment("sweater", "top", "grey"), garment("jeans", "bottom", "navy"), garment("sneakers", "shoes", "white"),
            garment("cords", "bottom", "brown"), garment("ring", "jewelry", "red"), garment("their-cords", "bottom", "brown", wardrobe = "child")))
    private fun look(vararg ids: String) = ids.map { id -> base.garments.first { it.id == id } }

    @Test fun theScreenshotsGoodAndBetter() {
        val good = look("sweater", "jeans", "sneakers")
        val rating = OutfitStyle.rate(good, "any_weather", "casual")
        assertEquals(9, rating.score); assertEquals(StyleRules.CALM, rating.pair)
        val swap = OutfitStyle.better(base, "home", "everyday", "any_weather", today, good)!!
        assertEquals("jeans", swap.old.id); assertEquals("cords", swap.new.id)
        assertEquals(9, swap.before.score); assertEquals(10, swap.after.score); assertEquals(StyleRules.WARM_COOL, swap.after.pair)
        assertEquals("sweater,cords,sneakers", OutfitStyle.replaced("sweater,jeans,sneakers", swap))
        // Colours for the dots: biggest pieces first, each once, no jewellery.
        assertEquals(listOf("grey", "navy", "white"), OutfitStyle.colours(look("ring", "sneakers", "jeans", "sweater")))
    }

    @Test fun onlyWhatTheDailySuggestionsMayUse() {
        val good = look("sweater", "jeans", "sneakers")
        // Excluded from suggestions, marked «Незручно», or hidden as a combination: no «Краще».
        val excluded = base.copy(garments = base.garments.map { if (it.id == "cords") it.copy(suggestionExcluded = true) else it })
        assertNull(OutfitStyle.better(excluded, "home", "everyday", "any_weather", today, good))
        val uncomfortable = base.copy(feedback = listOf(OutfitFeedback(wardrobeId = "home", signature = "cords", weather = "warm", occasion = "work", reason = PreferenceRules.DISLIKE)))
        assertNull(OutfitStyle.better(uncomfortable, "home", "everyday", "any_weather", today, good))
        val hidden = base.copy(feedback = listOf(OutfitFeedback(wardrobeId = "home", signature = DailyRules.signature(listOf("sweater", "cords", "sneakers")),
            weather = "warm", occasion = "everyday", reason = PreferenceRules.DISLIKE)))
        assertNull(OutfitStyle.better(hidden, "home", "everyday", "any_weather", today, good))
        // A pinned piece stays; another wardrobe's trousers are never offered.
        assertNull(OutfitStyle.better(base, "home", "everyday", "any_weather", today, good, fixed = setOf("jeans")))
        val onlyTheirs = base.copy(garments = base.garments.filter { it.id != "cords" })
        assertNull(OutfitStyle.better(onlyTheirs, "home", "everyday", "any_weather", today, good))
        // Inside a capsule only its items count.
        assertNull(OutfitStyle.better(base, "home", "everyday", "any_weather", today, good, allowed = setOf("sweater", "jeans", "sneakers")))
        assertNull(OutfitStyle.better(base, "home", "everyday", "any_weather", today, emptyList()))
    }

    @Test fun thePersonsStyleChoiceCounts() {
        val good = look("sweater", "jeans", "sneakers")
        // At work the usual style is formal; the person may choose casual there (PERS-02).
        assertEquals("formal", OutfitStyle.target(base, "home", "work"))
        assertEquals(2, OutfitStyle.rate(good, "any_weather", OutfitStyle.target(base, "home", "work")).style)
        assertEquals("casual", OutfitStyle.target(base, "home", "work", PersonalChoices(styles = mapOf("work" to "casual"))))
    }

    @Test fun rulesCases() { StyleRulesCases.main(emptyArray()) }
}
