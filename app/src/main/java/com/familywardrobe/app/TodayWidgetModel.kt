package com.familywardrobe.app

/**
 * WID-01: what the «Образ на сьогодні» widget shows. Pure, so every state is covered by JVM tests.
 * Only the active wardrobe's calendar plans for today count (the user's choice); without one the widget offers
 * to pick an outfit in the app and computes nothing in the background.
 */
sealed class TodayWidgetState {
    /** Nobody is signed in, or the account was never opened on this phone: no private data at all. */
    object SignedOut : TodayWidgetState()
    data class NoPlan(val date: String, val wardrobe: String) : TodayWidgetState()
    /**
     * [photoIds]: up to four item photos for the picture; [unavailable]: names of items that cannot be worn now
     * (laundry, repair, lent, archived); [ideas]: the outfit still has wish or idea pieces; [more]: other plans today.
     */
    data class Plan(val date: String, val wardrobe: String, val wardrobeId: String, val planId: String, val outfitId: String, val name: String,
        val photoIds: List<String>, val items: Int, val worn: Boolean, val unavailable: List<String>, val ideas: Boolean,
        val more: Int) : TodayWidgetState() {
        /** «Одягнув сьогодні» needs the same conditions as the calendar's mark: owned, ready items only. */
        val canWear: Boolean get() = !worn && items > 0 && unavailable.isEmpty() && !ideas
    }
}

object TodayWidgetModel {
    const val PHOTOS = 4

    fun state(s: Snapshot, wardrobe: String, today: String): TodayWidgetState {
        // Blank = the default name, which the widget shows in the phone's language.
        val name = s.wardrobes.find { it.id == wardrobe }?.let { if (it.usesDefaultName) "" else it.name }.orEmpty()
        val outfits = s.outfits.filter { it.wardrobeId == wardrobe }.associateBy { it.id }
        // Not yet worn first, then by name: the widget shows the plan most likely to matter now.
        val plans = s.plans.filter { it.wardrobeId == wardrobe && it.date == today && it.outfitId in outfits }
            .sortedWith(compareBy({ it.worn }, { outfits.getValue(it.outfitId).name.lowercase() }, { it.id }))
        val plan = plans.firstOrNull() ?: return TodayWidgetState.NoPlan(today, name)
        val outfit = outfits.getValue(plan.outfitId)
        val garments = s.garments.associateBy { it.id }
        val items = s.parts.filter { it.outfitId == outfit.id }.mapNotNull { garments[it.garmentId] }
            .sortedBy { Matching.categories.indexOf(it.category).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        val ideas = s.ideas.any { it.outfitId == outfit.id }
        return TodayWidgetState.Plan(today, name, wardrobe, plan.id, outfit.id, outfit.name,
            items.map { it.photoId }.filter { it.isNotBlank() }.take(PHOTOS), items.size, plan.worn,
            items.filterNot { WardrobeRules.ready(it.rulePiece()) }.map { it.name }, ideas, plans.size - 1)
    }
}
