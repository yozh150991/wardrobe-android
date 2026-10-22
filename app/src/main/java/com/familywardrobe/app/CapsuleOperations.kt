package com.familywardrobe.app
import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock

suspend fun Repository.saveCapsule(value: Capsule): String = writes.withLock {
    require(value.name.trim().length in 1..80 && TextLimits.hasVisibleText(value.name) && value.kind in setOf("capsule", "trip") && RoadmapRules.period(value.startDate, value.endDate))
    db.withTransaction {
        require(dao.allWardrobes().any { it.id == value.wardrobeId })
        val previous = dao.capsule(value.id); require(previous == null || previous.wardrobeId == value.wardrobeId)
        dao.saveCapsule(value.copy(name = value.name.trim())); value.id
    }
}
suspend fun Repository.removeCapsule(id: String) = writes.withLock { dao.deleteCapsule(id) }
suspend fun Repository.linkCapsuleOutfit(capsuleId: String, outfitId: String, selected: Boolean) = writes.withLock {
    db.withTransaction {
        val capsule = requireNotNull(dao.capsule(capsuleId)); val outfit = requireNotNull(dao.outfit(outfitId))
        require(outfit.wardrobeId == capsule.wardrobeId)
        if (selected) {
            require(dao.outfitIds(outfitId).isNotEmpty() && dao.allIdeas().none { it.outfitId == outfitId })
            dao.saveCapsuleOutfit(CapsuleOutfit(capsuleId, outfitId))
        } else dao.deleteCapsuleOutfit(capsuleId, outfitId)
        reconcileCapsule(capsuleId)
    }
}
suspend fun Repository.manualCapsuleItem(capsuleId: String, garmentId: String, selected: Boolean) = writes.withLock {
    db.withTransaction {
        val capsule = requireNotNull(dao.capsule(capsuleId)); val item = requireNotNull(dao.garment(garmentId))
        require(item.wardrobeId == capsule.wardrobeId)
        val old = dao.allCapsuleItems().find { it.capsuleId == capsuleId && it.garmentId == garmentId }
        if (selected) dao.saveCapsuleItem((old ?: CapsuleItem(capsuleId, garmentId)).copy(manual = true))
        else if (old != null) dao.saveCapsuleItem(old.copy(manual = false))
        reconcileCapsule(capsuleId)
    }
}
suspend fun Repository.packCapsuleItem(capsuleId: String, garmentId: String, packed: Boolean) = writes.withLock {
    db.withTransaction {
        val capsule = requireNotNull(dao.capsule(capsuleId)); val garment = requireNotNull(dao.garment(garmentId))
        require(garment.wardrobeId == capsule.wardrobeId)
        val item = requireNotNull(dao.allCapsuleItems().find { it.capsuleId == capsuleId && it.garmentId == garmentId })
        require(!packed || WardrobeRules.ready(garment.rulePiece()))
        dao.saveCapsuleItem(item.copy(packed = packed))
    }
}
suspend fun Repository.resetPacking(capsuleId: String) = writes.withLock {
    db.withTransaction { requireNotNull(dao.capsule(capsuleId)); dao.allCapsuleItems().filter { it.capsuleId == capsuleId }.forEach { dao.saveCapsuleItem(it.copy(packed = false)) } }
}
/** Called inside the caller's write lock and transaction, including when a linked outfit is edited. */
suspend fun Repository.reconcileCapsule(capsuleId: String) {
    val capsule = requireNotNull(dao.capsule(capsuleId))
    val old = dao.allCapsuleItems().filter { it.capsuleId == capsuleId }
    val outfitIds = dao.allCapsuleOutfits().filter { it.capsuleId == capsuleId }.map { it.outfitId }.toSet()
    val parts = dao.allParts().filter { it.outfitId in outfitIds }.groupBy { it.outfitId }.values.map { rows -> rows.map { it.garmentId } }
    val needed = RoadmapRules.uniquePackingItems(parts, old.filter { it.manual }.map { it.garmentId }).toSet()
    require(needed.all { dao.garment(it)?.wardrobeId == capsule.wardrobeId })
    old.filter { it.garmentId !in needed }.forEach { dao.deleteCapsuleItem(capsuleId, it.garmentId) }
    needed.forEach { id -> if (old.none { it.garmentId == id }) dao.saveCapsuleItem(CapsuleItem(capsuleId, id)) }
}
suspend fun Repository.reconcileOutfitCapsules(outfitId: String) {
    dao.allCapsuleOutfits().filter { it.outfitId == outfitId }.forEach { reconcileCapsule(it.capsuleId) }
}

/** The chosen outfit and capsule link commit together; concurrent packing edits cannot expand the scope. */
suspend fun Repository.saveCapsuleDaily(capsuleId: String, ids: List<String>, name: String, occasion: String, weather: String, packedOnly: Boolean): String = writes.withLock {
    db.withTransaction {
        val capsule = dao.capsule(capsuleId) ?: throw StaleSelectionException()
        val allowed = dao.allCapsuleItems().filter { it.capsuleId == capsuleId && (!packedOnly || it.packed) }.map { it.garmentId }.toSet()
        if (ids.isEmpty() || ids.distinct().size != ids.size || ids.any { it !in allowed }) throw StaleSelectionException()
        val items = ids.map { dao.garment(it) ?: throw StaleSelectionException() }
        if (items.any { it.wardrobeId != capsule.wardrobeId || !WardrobeRules.ready(it.rulePiece()) }) throw StaleSelectionException()
        require(Matching.complete(items) && items.all { a -> items.all { b -> Matching.compatibleSeasons(a.season, b.season) } })
        require(name.trim().length in 1..80 && occasion in LookRules.OCCASIONS && weather in LookRules.WEATHERS)
        val signature = DailyRules.signature(ids)
        val existing = dao.findOutfit(capsule.wardrobeId, signature)
        val outfit = existing ?: Outfit(wardrobeId = capsule.wardrobeId, name = name.trim(), signature = signature, occasion = occasion, weather = weather)
        if (existing == null) { dao.insertOutfit(outfit); dao.insertParts(ids.map { OutfitPart(outfit.id, it) }) }
        require(dao.allIdeas().none { it.outfitId == outfit.id })
        dao.saveCapsuleOutfit(CapsuleOutfit(capsuleId, outfit.id)); reconcileCapsule(capsuleId)
        outfit.id
    }
}
