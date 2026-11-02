package com.familywardrobe.app

import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

suspend fun Repository.scheduleOutfit(outfitId: String, date: String, notes: String = ""): String = writes.withLock {
    require(RoadmapRules.validDate(date) && notes.length <= 1000)
    db.withTransaction {
        val outfit = requireNotNull(dao.outfit(outfitId))
        val existing = dao.allPlans().find { it.wardrobeId == outfit.wardrobeId && it.outfitId == outfitId && it.date == date }
        if (existing != null) existing.id else DayPlan(wardrobeId = outfit.wardrobeId, outfitId = outfitId, date = date,
            notes = notes.trim()).also { dao.savePlan(it) }.id
    }
}
suspend fun Repository.movePlan(id: String, date: String) = writes.withLock {
    require(RoadmapRules.validDate(date))
    db.withTransaction {
        val plan = requireNotNull(dao.plan(id)); require(!plan.worn)
        require(dao.allPlans().none { it.id != id && it.wardrobeId == plan.wardrobeId && it.outfitId == plan.outfitId && it.date == date })
        dao.savePlan(plan.copy(date = date))
    }
}
suspend fun Repository.removePlan(id: String) = writes.withLock {
    require(dao.plan(id)?.worn != true)
    dao.deletePlan(id)
}
suspend fun Repository.togglePlanWorn(id: String) = writes.withLock {
    db.withTransaction {
        val plan = requireNotNull(dao.plan(id))
        val source = "plan:$id"
        if (plan.worn) {
            dao.clearSourceDay(source, plan.date)
            dao.savePlan(plan.copy(worn = false, wornItemIds = ""))
        } else {
            require(plan.date <= LocalDate.now().toString())
            val outfit = requireNotNull(dao.outfit(plan.outfitId))
            require(outfit.wardrobeId == plan.wardrobeId && dao.allIdeas().none { it.outfitId == outfit.id })
            val ids = dao.outfitIds(outfit.id)
            require(ids.isNotEmpty())
            val items = ids.map { requireNotNull(dao.garment(it)) }
            require(items.all { it.wardrobeId == plan.wardrobeId && WardrobeRules.ready(it.rulePiece()) })
            dao.insertWears(ids.map { Wear(it, plan.date, source) })
            dao.savePlan(plan.copy(worn = true, wornItemIds = DailyRules.signature(ids)))
        }
    }
}
/** An explicit item-day undo may remove a calendar source; reflect the actual remaining wear ledger. */
suspend fun Repository.reconcilePlanWear(date: String) {
    val wears = dao.allWears().filter { it.date == date }
    dao.allPlans().filter { it.date == date && it.worn }.forEach { plan ->
        val ids = wears.filter { it.source == "plan:${plan.id}" }.map { it.garmentId }
        dao.savePlan(plan.copy(worn = ids.isNotEmpty(), wornItemIds = DailyRules.signature(ids)))
    }
}
/** Returns the new row's ID, or null when the same row already existed (so an undo never removes an older one). */
suspend fun Repository.recordFeedback(wardrobe: String, ids: List<String>, occasion: String, weather: String, reason: String): String? = writes.withLock {
    db.withTransaction { insertFeedback(wardrobe, ids, occasion, weather, reason) }
}
private suspend fun Repository.insertFeedback(wardrobe: String, ids: List<String>, occasion: String, weather: String, reason: String): String? {
    require(occasion in LookRules.OCCASIONS && weather in LookRules.WEATHERS && reason in setOf("dislike", "hot", "occasion"))
    require(ids.isNotEmpty() && ids.size <= WardrobeRules.MAX_PARTS && ids.distinct().size == ids.size)
    require(ids.all { dao.garment(it)?.wardrobeId == wardrobe })
    val signature = DailyRules.signature(ids)
    val rows = dao.allFeedback().filter { it.wardrobeId == wardrobe && it.signature == signature && it.reason == reason }
    // An item is uncomfortable once, whatever the context it was noticed in (PreferenceRules counts it once).
    val old = if (ids.size == 1 && reason == PreferenceRules.DISLIKE) rows.firstOrNull()
        else rows.find { it.weather == weather && it.occasion == occasion }
    if (old != null) return null
    return OutfitFeedback(wardrobeId = wardrobe, signature = signature, weather = weather, occasion = occasion, reason = reason)
        .also { dao.saveFeedback(it) }.id
}

/** PERS-01: the reasons offered for a suggested outfit; scope and effect are in [PreferenceRules]. */
enum class FeedbackReason { COMBINATION, UNCOMFORTABLE, FORMAL, TOO_WARM }

/** Records one reason for [outfit] (all its item IDs); [items] are the uncomfortable ones. Returns the new rows for undo. */
suspend fun Repository.recordReason(wardrobe: String, outfit: List<String>, items: List<String>, occasion: String, weather: String,
    reason: FeedbackReason): List<String> = writes.withLock {
    require(reason != FeedbackReason.UNCOMFORTABLE || items.isNotEmpty() && items.distinct().size == items.size && outfit.containsAll(items))
    db.withTransaction {
        when (reason) {
            FeedbackReason.COMBINATION -> listOfNotNull(insertFeedback(wardrobe, outfit, occasion, weather, PreferenceRules.DISLIKE))
            FeedbackReason.UNCOMFORTABLE -> items.mapNotNull { insertFeedback(wardrobe, listOf(it), occasion, weather, PreferenceRules.DISLIKE) }
            FeedbackReason.FORMAL -> listOfNotNull(insertFeedback(wardrobe, outfit, occasion, weather, PreferenceRules.OCCASION))
            FeedbackReason.TOO_WARM -> listOfNotNull(insertFeedback(wardrobe, outfit, occasion, weather, PreferenceRules.HOT))
        }
    }
}

/** Undo of [recordReason]: removes exactly those rows of this wardrobe; a row already gone is not an error. */
suspend fun Repository.forgetFeedback(wardrobe: String, ids: List<String>) = writes.withLock {
    db.withTransaction {
        val owned = dao.allFeedback().filter { it.wardrobeId == wardrobe }.map { it.id }.toSet()
        ids.filter { it in owned }.forEach { dao.deleteFeedback(it) }
    }
}
