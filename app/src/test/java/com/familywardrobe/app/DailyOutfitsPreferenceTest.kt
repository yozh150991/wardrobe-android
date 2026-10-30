package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

/** PERS-02 on the app's own entry point: choices, generalised reasons and the never-empty fallback. */
class DailyOutfitsPreferenceTest {
    private val date = "2026-10-30"
    private fun garment(id: String, category: String, style: String = "casual") =
        Garment(id = id, wardrobeId = "home", name = id, category = category, color = "navy", photoId = "", style = style)
    private val base = Snapshot(wardrobes = listOf(Wardrobe(id = "home", name = "Home")),
        garments = listOf(garment("formal", "top", "formal"), garment("casual", "top"), garment("trousers", "bottom"), garment("shoes", "shoes")))
    private fun reason(ids: List<String>, occasion: String, weather: String, reason: String) =
        OutfitFeedback(wardrobeId = "home", signature = DailyRules.signature(ids), weather = weather, occasion = occasion, reason = reason)

    @Test fun withoutHistoryTheOccasionDecides() {
        assertTrue(DailyOutfits.generate(base, "home", "work", "warm", date).first().any { it.id == "formal" })
        assertTrue(DailyOutfits.generate(base, "home", "everyday", "warm", date).first().any { it.id == "casual" })
        assertEquals("forecast", DailyOutfits.warmth(base, "home").shift)
    }
    @Test fun theChosenStyleLeadsAndCanBeReset() {
        val casualAtWork = PersonalChoices(styles = mapOf("work" to "casual"))
        assertTrue(DailyOutfits.generate(base, "home", "work", "warm", date, choices = casualAtWork).first().any { it.id == "casual" })
        assertEquals(PreferenceProfile.Source.CHOICE, DailyOutfits.style(base, "home", "work", casualAtWork).source)
        assertEquals(PreferenceProfile.Source.DEFAULT, DailyOutfits.style(base, "home", "work", PersonalChoices()).source)
    }
    @Test fun twoTooFormalReasonsMakeWorkCasualByThemselves() {
        val s = base.copy(feedback = listOf(reason(listOf("formal", "trousers", "shoes"), "work", "warm", PreferenceRules.OCCASION),
            reason(listOf("formal", "shoes", "trousers", "casual"), "work", "cold", PreferenceRules.OCCASION)))
        assertEquals(PreferenceProfile.Source.AUTO, DailyOutfits.style(s, "home", "work").source)
        assertTrue(DailyOutfits.generate(s, "home", "work", "warm", date).first().any { it.id == "casual" })
        // The person's choice of formal wins over the reasons.
        val formal = PersonalChoices(styles = mapOf("work" to "formal"))
        assertFalse(DailyOutfits.style(s, "home", "work", formal).avoidFormal)
    }
    @Test fun twoTooWarmReasonsShiftTheForecastOneStep() {
        val s = base.copy(feedback = listOf(reason(listOf("casual", "trousers", "shoes"), "everyday", "cold", PreferenceRules.HOT),
            reason(listOf("formal", "trousers", "shoes"), "work", "cold", PreferenceRules.HOT)))
        val warmth = DailyOutfits.warmth(s, "home")
        assertEquals("lighter", warmth.shift); assertEquals(2, warmth.evidence)
        assertEquals("cool", PreferenceProfile.apply("cold", warmth.shift))
        assertEquals("forecast", DailyOutfits.warmth(s, "home", PersonalChoices(warmth = "forecast")).shift)
    }
    @Test fun hiddenSetsNeverEmptyTheDay() {
        val sets = listOf(listOf("formal", "trousers", "shoes"), listOf("casual", "trousers", "shoes"))
        val s = base.copy(feedback = sets.map { reason(it, "everyday", "warm", PreferenceRules.DISLIKE) })
        assertTrue(DailyOutfits.generate(s, "home", "everyday", "warm", date).isEmpty())
        val day = DailyOutfits.daily(s, "home", "everyday", "warm", date)
        assertTrue(day.hiddenShown); assertEquals(2, day.looks.size)
        // Without hidden sets there is nothing to bring back.
        val plain = DailyOutfits.daily(base, "home", "everyday", "warm", date)
        assertFalse(plain.hiddenShown); assertTrue(plain.looks.isNotEmpty())
    }
    @Test fun anotherWardrobesReasonsDoNotCount() {
        val other = base.copy(wardrobes = base.wardrobes + Wardrobe(id = "child", name = "Child"),
            feedback = listOf(OutfitFeedback(wardrobeId = "child", signature = "a|b", weather = "warm", occasion = "work", reason = PreferenceRules.OCCASION),
                OutfitFeedback(wardrobeId = "child", signature = "c|d", weather = "warm", occasion = "work", reason = PreferenceRules.OCCASION)))
        assertEquals(PreferenceProfile.Source.DEFAULT, DailyOutfits.style(other, "home", "work").source)
    }
}
