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
suspend fun Repository.recordFeedback(wardrobe: String, ids: List<String>, occasion: String, weather: String, reason: String) = writes.withLock {
    require(occasion in LookRules.OCCASIONS && weather in LookRules.WEATHERS && reason in setOf("dislike", "hot", "occasion"))
    require(ids.isNotEmpty() && ids.size <= WardrobeRules.MAX_PARTS && ids.distinct().size == ids.size)
    db.withTransaction {
        require(ids.all { dao.garment(it)?.wardrobeId == wardrobe })
        val signature = DailyRules.signature(ids)
        val old = dao.allFeedback().find { it.wardrobeId == wardrobe && it.signature == signature && it.weather == weather && it.occasion == occasion && it.reason == reason }
        if (old == null) dao.saveFeedback(OutfitFeedback(wardrobeId = wardrobe, signature = signature, weather = weather, occasion = occasion, reason = reason))
    }
}
