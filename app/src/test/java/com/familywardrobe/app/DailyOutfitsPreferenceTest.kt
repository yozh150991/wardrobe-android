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

    /** WTH-01 / GEN-04: the forecast's temperature reaches the generator, the rating and «Давно не вдягали» through the app's entry points. */
    @Test fun theTemperatureDecidesTheCoatAndTheLayer() {
        val s = base.copy(garments = listOf(garment("top", "top"), garment("trousers", "bottom"), garment("shoes", "shoes"),
            garment("coat", "outerwear"), garment("cardigan", "layer"), garment("tee", "top").copy(season = "summer")))
        fun first(temperature: Double?) = DailyOutfits.generate(s, "home", "everyday", "cool", date, temperature = temperature).first().map { it.id }.toSet()
        // By hand «прохолодно»: the coat, as before. From the forecast: no coat at +21, the coat at +12, a second layer at +5.
        assertTrue("coat" in first(null) && "cardigan" !in first(null))
        assertTrue("coat" !in first(21.0) && "cardigan" !in first(21.0))
        assertTrue("coat" in first(12.0) && "cardigan" !in first(12.0))
        assertEquals(setOf("coat", "cardigan"), DailyOutfits.generate(s, "home", "everyday", "cold", date, temperature = 5.0).first().map { it.id }.toSet() - setOf("top", "trousers", "shoes"))
        // The summer tee is offered at +21 only.
        assertTrue(DailyOutfits.generate(s, "home", "everyday", "cool", date, temperature = 21.0).any { look -> look.any { it.id == "tee" } })
        assertTrue(DailyOutfits.generate(s, "home", "everyday", "cool", date, temperature = 12.0).none { look -> look.any { it.id == "tee" } })
        assertTrue(DailyOutfits.generate(s, "home", "everyday", "cool", date).none { look -> look.any { it.id == "tee" } })
        // The rating and replacing a piece agree with it.
        val plain = s.garments.filter { it.id in setOf("top", "trousers", "shoes") }
        assertEquals(StyleRules.WEATHER_OK, OutfitStyle.rate(plain, "cool", "casual", 21.0).weatherCode)
        assertEquals(StyleRules.NO_OUTERWEAR, OutfitStyle.rate(plain, "cool", "casual", 12.0).weatherCode)
        assertEquals(StyleRules.NO_OUTERWEAR, OutfitStyle.rate(plain, "cool", "casual").weatherCode)
        assertTrue(DailyOutfits.replace(s, "home", "everyday", "cool", date, plain, "top", temperature = 21.0).none { it.id == "coat" })
        assertEquals("tee", RotationOutfits.suggest(s, "home", "everyday", "cool", date, anchor = "tee", temperature = 21.0)!!.also { assertNull(it.problem) }.anchor.id)
        assertEquals(RotationRules.WEATHER, RotationOutfits.suggest(s, "home", "everyday", "cool", date, anchor = "tee", temperature = 12.0)!!.problem)
    }

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
    /** PERS-03: «Мої кольори» on the app's entry point — liked first, «Краще рідше» last but still there, swaps follow. */
    @Test fun thePersonsColoursSteerTheVariants() {
        // Grey and white tops are equal on navy: only the palette decides which leads.
        val s = base.copy(garments = listOf(garment("grey", "top").copy(color = "grey"), garment("white", "top").copy(color = "white"),
            garment("trousers", "bottom"), garment("shoes", "shoes")))
        for ((colour, other) in listOf("grey" to "white", "white" to "grey")) {
            val liked = PersonalChoices(liked = setOf(colour))
            assertEquals(mapOf(colour to PreferenceProfile.PALETTE_LIKED), DailyOutfits.bonus(s, "home", liked))
            assertTrue(DailyOutfits.generate(s, "home", "everyday", "warm", date, choices = liked).first().any { it.id == colour })
            val less = PersonalChoices(avoided = setOf(colour))
            assertEquals(PreferenceProfile.PALETTE_AVOIDED, DailyOutfits.penalties(s, "home", "everyday", "warm", less)[colour])
            val looks = DailyOutfits.generate(s, "home", "everyday", "warm", date, choices = less)
            assertTrue(looks.first().any { it.id == other }); assertTrue(looks.any { look -> look.any { it.id == colour } })
        }
        // Every colour worn less: still suggestions. No palette: nothing changes.
        val all = PersonalChoices(avoided = setOf("grey", "white", "navy"))
        assertTrue(DailyOutfits.generate(s, "home", "everyday", "warm", date, choices = all).isNotEmpty())
        assertTrue(DailyOutfits.bonus(s, "home").isEmpty()); assertTrue(DailyOutfits.penalties(s, "home", "everyday", "warm").isEmpty())
        assertEquals(PersonalChoices.LIKED, PersonalChoices(liked = setOf("grey")).palette("grey"))
        assertEquals(PersonalChoices.NONE, PersonalChoices(liked = setOf("grey")).palette("white"))
        assertEquals(19, PersonalChoices.PALETTE.size); assertFalse("other" in PersonalChoices.PALETTE)
    }
    @Test fun anotherWardrobesReasonsDoNotCount() {
        val other = base.copy(wardrobes = base.wardrobes + Wardrobe(id = "child", name = "Child"),
            feedback = listOf(OutfitFeedback(wardrobeId = "child", signature = "a|b", weather = "warm", occasion = "work", reason = PreferenceRules.OCCASION),
                OutfitFeedback(wardrobeId = "child", signature = "c|d", weather = "warm", occasion = "work", reason = PreferenceRules.OCCASION)))
        assertEquals(PreferenceProfile.Source.DEFAULT, DailyOutfits.style(other, "home", "work").source)
    }
}
