package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

/** WID-01: what the «Образ на сьогодні» widget shows, for every state the user decided on. */
class TodayWidgetModelTest {
    private val today = "2026-11-02"
    private fun garment(id: String, category: String, wardrobe: String = "home", photo: String = "p-$id",
        availability: String = "available", archived: Boolean = false) =
        Garment(id = id, wardrobeId = wardrobe, name = "item $id", category = category, color = "navy", photoId = photo,
            availability = availability, archived = archived)
    private fun outfit(id: String, name: String, wardrobe: String = "home") = Outfit(id = id, wardrobeId = wardrobe, name = name, signature = id)
    private val items = listOf(garment("shoes", "shoes"), garment("bag", "bag"), garment("top", "top"), garment("bottom", "bottom"),
        garment("coat", "outerwear"), garment("away", "top", wardrobe = "child"))
    private val base = Snapshot(
        wardrobes = listOf(Wardrobe(id = "home", name = "Мій гардероб", usesDefaultName = true), Wardrobe(id = "child", name = "Син")),
        garments = items,
        outfits = listOf(outfit("look", "Офіс"), outfit("walk", "Прогулянка"), outfit("kid", "Школа", "child")),
        parts = listOf("shoes", "bag", "top", "bottom", "coat").map { OutfitPart("look", it) } +
            listOf(OutfitPart("walk", "top"), OutfitPart("kid", "away")))
    private fun plan(id: String, outfit: String, wardrobe: String = "home", date: String = today, worn: Boolean = false) =
        DayPlan(id = id, wardrobeId = wardrobe, outfitId = outfit, date = date, worn = worn)

    @Test fun withoutAPlanTheWidgetOffersToPick() {
        // Plans of another day or another wardrobe do not count; the default name stays blank for the phone's language.
        val s = base.copy(plans = listOf(plan("yesterday", "look", date = "2026-11-01"), plan("kid", "kid", "child")))
        assertEquals(TodayWidgetState.NoPlan(today, ""), TodayWidgetModel.state(s, "home", today))
        assertEquals(TodayWidgetState.NoPlan(today, "Син"), TodayWidgetModel.state(base, "child", today))
    }

    @Test fun todaysPlanShowsItsPicturesInCategoryOrder() {
        val state = TodayWidgetModel.state(base.copy(plans = listOf(plan("p", "look"))), "home", today) as TodayWidgetState.Plan
        assertEquals("Офіс", state.name); assertEquals("look", state.outfitId); assertEquals("p", state.planId)
        assertEquals("home", state.wardrobeId); assertEquals(5, state.items)
        // Top, bottom, shoes, outerwear first; at most four photos, the bag does not fit.
        assertEquals(listOf("p-top", "p-bottom", "p-shoes", "p-coat"), state.photoIds)
        assertTrue(state.canWear); assertEquals(0, state.more)
    }

    @Test fun anItemOutOfReachBlocksWearingLikeTheCalendar() {
        val laundry = base.copy(garments = items.map { if (it.id == "top") it.copy(availability = "laundry") else it },
            plans = listOf(plan("p", "look")))
        val state = TodayWidgetModel.state(laundry, "home", today) as TodayWidgetState.Plan
        assertEquals(listOf("item top"), state.unavailable); assertFalse(state.canWear)
        val archived = laundry.copy(garments = items.map { if (it.id == "coat") it.copy(archived = true) else it })
        assertEquals(listOf("item coat"), (TodayWidgetModel.state(archived, "home", today) as TodayWidgetState.Plan).unavailable)
    }

    @Test fun ideasAndAlreadyWornNeverOfferWearing() {
        val ideas = base.copy(plans = listOf(plan("p", "look")),
            ideas = listOf(OutfitIdea("look", "idea", null, "hat", "Капелюх", "black", "")))
        val withIdeas = TodayWidgetModel.state(ideas, "home", today) as TodayWidgetState.Plan
        assertTrue(withIdeas.ideas); assertFalse(withIdeas.canWear)
        val worn = TodayWidgetModel.state(base.copy(plans = listOf(plan("p", "look", worn = true))), "home", today) as TodayWidgetState.Plan
        assertTrue(worn.worn); assertFalse(worn.canWear)
    }

    @Test fun anUnwornPlanComesFirstAndTheRestAreCounted() {
        val s = base.copy(plans = listOf(plan("a", "look", worn = true), plan("b", "walk")))
        val state = TodayWidgetModel.state(s, "home", today) as TodayWidgetState.Plan
        assertEquals("walk", state.outfitId); assertEquals(1, state.more)
        // Same worn state: by name, so the choice is stable between redraws.
        val both = base.copy(plans = listOf(plan("b", "walk"), plan("a", "look")))
        assertEquals("look", (TodayWidgetModel.state(both, "home", today) as TodayWidgetState.Plan).outfitId)
    }

    @Test fun aPlanWithoutItsOutfitOrPhotosIsHandled() {
        // An outfit of another wardrobe referenced by a plan is ignored.
        assertTrue(TodayWidgetModel.state(base.copy(plans = listOf(plan("x", "kid"))), "home", today) is TodayWidgetState.NoPlan)
        val noPhotos = base.copy(garments = items.map { it.copy(photoId = "") }, plans = listOf(plan("p", "walk")))
        val state = TodayWidgetModel.state(noPhotos, "home", today) as TodayWidgetState.Plan
        assertEquals(emptyList<String>(), state.photoIds); assertEquals(1, state.items)
    }

    @Test fun unrelatedChangesKeepTheSameState() {
        // The app redraws the widget only when this value changes.
        val s = base.copy(plans = listOf(plan("p", "look")))
        val other = s.copy(garments = s.garments.map { if (it.id == "away") it.copy(name = "renamed", availability = "lent") else it },
            wishes = emptyList(), plans = s.plans + plan("future", "walk", date = "2026-11-03"))
        assertEquals(TodayWidgetModel.state(s, "home", today), TodayWidgetModel.state(other, "home", today))
        val worn = s.copy(plans = listOf(plan("p", "look", worn = true)))
        assertNotEquals(TodayWidgetModel.state(s, "home", today), TodayWidgetModel.state(worn, "home", today))
        assertNotEquals(TodayWidgetModel.state(s, "home", today), TodayWidgetModel.state(s, "home", "2026-11-03"))
    }
}
