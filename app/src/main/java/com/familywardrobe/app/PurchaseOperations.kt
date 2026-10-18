package com.familywardrobe.app

import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock

data class PurchaseUndo(val wishId: String, val garment: Garment)
data class PurchaseReceipt(val garment: Garment, val undo: PurchaseUndo?)

/** The source record is retained as purchase history; only its ownership changes. */
suspend fun Repository.purchaseWish(wishId: String, reviewed: Garment, importedProduct: Wish? = null): PurchaseReceipt = writes.withLock {
    db.withTransaction {
        if (importedProduct != null) {
            validateWish(importedProduct)
            require(importedProduct.id == wishId && importedProduct.wardrobeId == reviewed.wardrobeId &&
                importedProduct.kind == "product" && importedProduct.ownedId.isBlank() && importedProduct.needName.isBlank())
            val previous = dao.wish(wishId)
            if (previous == null) dao.saveWish(importedProduct)
            else require(previous.ownedId == reviewed.id && previous.wardrobeId == reviewed.wardrobeId)
        }
        val wish = requireNotNull(dao.wish(wishId))
        if (wish.ownedId.isNotBlank()) {
            val existing = requireNotNull(dao.garment(wish.ownedId))
            require(existing.wardrobeId == wish.wardrobeId)
            return@withTransaction PurchaseReceipt(existing, null)
        }
        require(wish.kind == "product" && reviewed.id == wish.id && reviewed.wardrobeId == wish.wardrobeId)
        require(reviewed.name.trim().length in 1..120 && reviewed.category in Matching.categories)
        require(reviewed.color in Matching.colors && reviewed.season in Matching.seasons && reviewed.style in Matching.styles)
        require(reviewed.availability == "available" && !reviewed.archived && PhotoStore.ID.matches(reviewed.photoId))
        require(WishRules.validMinor(reviewed.priceMinor) && reviewed.currency in WishRules.CURRENCIES)
        require(dao.garment(reviewed.id) == null) // Never overwrite an existing owned item.
        val ideas = dao.allIdeas().filter { it.wishId == wish.id }
        val allParts = dao.allParts()
        for (idea in ideas) {
            val outfit = requireNotNull(dao.outfit(idea.outfitId))
            require(outfit.wardrobeId == wish.wardrobeId)
            val categories = allParts.filter { it.outfitId == outfit.id }.map { requireNotNull(dao.garment(it.garmentId)).category } +
                dao.allIdeas().filter { it.outfitId == outfit.id && it.partId != idea.partId }.map { it.category }
            require(WardrobeRules.validCategories(categories + reviewed.category))
        }
        dao.saveGarment(reviewed)
        dao.saveWish(wish.copy(ownedId = reviewed.id))
        dao.savePurchaseUndo(PurchaseUndoRow(wish.id, GarmentReceipt.write(reviewed)))
        recordWishEvent(wish.event("purchased").copy(name = reviewed.name, category = reviewed.category,
            priceMinor = reviewed.priceMinor, currency = reviewed.currency, photoId = reviewed.photoId))
        for (idea in ideas) {
            dao.deleteIdea(idea.outfitId, idea.partId)
            dao.insertParts(listOf(OutfitPart(idea.outfitId, reviewed.id)))
            refreshPurchaseSignature(idea.outfitId, mapping = mapOf("wish:${wish.id}" to "owned:${reviewed.id}"))
            reconcileOutfitCapsules(idea.outfitId)
        }
        PurchaseReceipt(reviewed, PurchaseUndo(wish.id, reviewed))
    }
}

/** Refuse stale undo rather than deleting edits or recorded wear. Keep all saved outfits. */
suspend fun Repository.undoPurchase(change: PurchaseUndo, photos: PhotoStore): Boolean = writes.withLock {
    val undone = db.withTransaction {
        val wish = dao.wish(change.wishId) ?: return@withTransaction false
        if (wish.ownedId != change.garment.id || dao.garment(wish.ownedId) != change.garment ||
            dao.allWears().any { it.garmentId == wish.ownedId } ||
            dao.allCapsuleItems().any { it.garmentId == wish.ownedId }) return@withTransaction false
        val affected = dao.allParts().filter { it.garmentId == wish.ownedId }
        for (part in affected) {
            val categories = dao.allParts().filter { it.outfitId == part.outfitId && it.garmentId != part.garmentId }
                .map { requireNotNull(dao.garment(it.garmentId)).category } + dao.allIdeas().filter { it.outfitId == part.outfitId }.map { it.category }
            if (!WardrobeRules.validCategories(categories + wish.category)) return@withTransaction false
        }
        for (part in affected) {
            dao.deletePart(part.outfitId, part.garmentId)
            dao.insertIdeas(listOf(OutfitIdea(part.outfitId, "wish:${wish.id}", wish.id,
                wish.category, wish.name, wish.color, wish.photoId)))
            refreshPurchaseSignature(part.outfitId, planned = true, mapping = mapOf("owned:${change.garment.id}" to "wish:${wish.id}"))
        }
        dao.deletePurchaseUndo(wish.id)
        recordWishEvent(wish.event("purchase_cancelled").copy(name = change.garment.name, category = change.garment.category,
            priceMinor = change.garment.priceMinor, currency = change.garment.currency, photoId = change.garment.photoId))
        dao.saveWish(wish.copy(ownedId = ""))
        dao.allFeedback().filter { change.garment.id in it.signature.split('|') }.forEach { dao.deleteFeedback(it.id) }
        dao.deleteGarment(change.garment.id)
        true
    }
    if (undone) photos.remove(setOf(change.garment.photoId) - referencedPhotos())
    undone
}

private suspend fun Repository.refreshPurchaseSignature(outfitId: String, planned: Boolean = false, mapping: Map<String, String> = emptyMap()) {
    val outfit = requireNotNull(dao.outfit(outfitId))
    val signature = (dao.outfitIds(outfitId) + dao.allIdeas().filter { it.outfitId == outfitId }.map { it.partId })
        .sorted().joinToString("|")
    val duplicate = dao.findOutfit(outfit.wardrobeId, signature)
    require(duplicate == null || duplicate.id == outfitId)
    val layout = OutfitCanvasState.remap(outfit.layout) { mapping[it] ?: it }
    dao.saveOutfitRow(outfit.copy(layout = layout, signature = signature, source = if (planned && outfit.source == "owned") "mixed" else outfit.source))
}
