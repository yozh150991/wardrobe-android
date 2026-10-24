package com.familywardrobe.app

import org.junit.Assert.*
import org.junit.Test

class FamilyOutfitChangeTest {
    private val wardrobe = Wardrobe(name = "Shared")
    private val top = Garment(wardrobeId = wardrobe.id, name = "Shirt", category = "top", color = "white", photoId = newId())
    private val bottom = top.copy(id = newId(), name = "Trousers", category = "bottom", photoId = newId())
    private val shoes = top.copy(id = newId(), name = "Shoes", category = "shoes", photoId = newId())
    private val snapshot = Snapshot(wardrobes = listOf(wardrobe), garments = listOf(top, bottom, shoes))
    private fun change(pieces: List<LookPiece> = listOf(top, bottom).map(LookRules::owned), id: String = newId(), creating: Boolean = true) =
        FamilyOutfitChange(id, creating, "Look", pieces, "everyday", "any_weather", "owned", "", "paper", 123L)
    private fun failure(problem: FamilyProblem, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertEquals(problem, (error as? FamilyTransferException)?.problem)
    }

    @Test fun createKeepsAllOtherCollectionsAndPhotoReferences() {
        val command = change(); val result = command.apply(snapshot, wardrobe.id)
        assertEquals(snapshot, result.copy(outfits = emptyList(), parts = emptyList()))
        assertEquals(snapshot.photoIds(), result.photoIds())
        assertEquals(listOf(top.id, bottom.id).sorted().joinToString("|"), result.outfits.single().signature)
        assertEquals(command.id, result.outfits.single().id)
        assertEquals(123L, result.outfits.single().createdAt)
    }
    @Test fun editPreservesIdentityCreationDateAndHistoricalWear() {
        val command = change(); val first = command.apply(snapshot, wardrobe.id)
        val plan = DayPlan(wardrobeId = wardrobe.id, outfitId = command.id, date = "2026-10-23", worn = true, wornItemIds = top.id)
        val before = first.copy(plans = listOf(plan), wears = listOf(Wear(top.id, plan.date, "plan:${plan.id}")))
        val edited = change(listOf(shoes).map(LookRules::owned), command.id, false).copy(name = "Changed", createdAt = 999).apply(before, wardrobe.id)
        assertEquals(command.id, edited.outfits.single().id); assertEquals(123L, edited.outfits.single().createdAt)
        assertEquals(before.plans, edited.plans); assertEquals(before.wears, edited.wears)
        assertEquals(listOf(OutfitPart(command.id, shoes.id)), edited.parts)
    }
    @Test fun capsulePackingKeepsManualExtrasAndRetainedPackedFlags() {
        val command = change(); val first = command.apply(snapshot, wardrobe.id)
        val capsule = Capsule(wardrobeId = wardrobe.id, name = "Trip")
        val before = first.copy(capsules = listOf(capsule), capsuleOutfits = listOf(CapsuleOutfit(capsule.id, command.id)),
            capsuleItems = listOf(CapsuleItem(capsule.id, top.id, packed = true), CapsuleItem(capsule.id, bottom.id, manual = true, packed = true)))
        val result = change(listOf(top, shoes).map(LookRules::owned), command.id, false).apply(before, wardrobe.id)
        assertTrue(result.capsuleItems.containsAll(before.capsuleItems))
        assertEquals(CapsuleItem(capsule.id, shoes.id), result.capsuleItems.single { it.garmentId == shoes.id })
    }
    @Test fun duplicateCombinationCannotSilentlyOverwriteAnotherLook() {
        val first = change().apply(snapshot, wardrobe.id)
        failure(FamilyProblem.CONFLICT) { change().apply(first, wardrobe.id) }
    }
    @Test fun deletedOutfitCannotBeResurrectedDuringRebase() {
        failure(FamilyProblem.MISSING) { change(creating = false).apply(snapshot, wardrobe.id) }
    }
    @Test fun foreignAndRemovedItemsAreRejected() {
        val foreign = top.copy(id = newId(), wardrobeId = newId())
        for (item in listOf(foreign, top.copy(id = newId()))) {
            failure(FamilyProblem.MISSING) { change(listOf(LookRules.owned(item))).apply(snapshot, wardrobe.id) }
        }
    }
    @Test fun archivedAndLaundryItemsAreRejectedFromLatestSnapshot() {
        for (unavailable in listOf(top.copy(archived = true), top.copy(availability = "laundry"))) {
            assertTrue(runCatching { change().apply(snapshot.copy(garments = listOf(unavailable, bottom, shoes)), wardrobe.id) }.isFailure)
        }
    }
    @Test fun rebaseUsesLatestGarmentFieldsRatherThanStaleClientMetadata() {
        val stale = LookRules.owned(top).copy(category = "shoes", photoId = newId(), name = "Old")
        val result = change(listOf(stale, LookRules.owned(shoes))).apply(snapshot, wardrobe.id)
        assertEquals(setOf(top.id, shoes.id), result.parts.map { it.garmentId }.toSet())
        assertEquals(snapshot.photoIds(), result.photoIds())
    }
    @Test fun mixedLookUsesOnlySharedWishAndCurrentPhoto() {
        val wish = Wish(wardrobeId = wardrobe.id, name = "Shoes to buy", category = "shoes", photoId = newId())
        val before = snapshot.copy(wishes = listOf(wish))
        val result = change(listOf(LookRules.owned(top), LookRules.wanted(wish).copy(photoId = newId())))
            .copy(source = "mixed").apply(before, wardrobe.id)
        assertEquals(wish.photoId, result.ideas.single().photoId)
        assertEquals(wish.id, result.ideas.single().wishId)
        assertTrue(runCatching { change(listOf(LookRules.wanted(wish))).copy(source = "mixed")
            .apply(before.copy(wishes = listOf(wish.copy(ownedId = top.id))), wardrobe.id) }.isFailure)
    }
    @Test fun inspirationNeverImportsAnArbitraryPhotoOrPrivateReference() {
        val idea = LookPiece("idea:top", "top", "Example", photoId = newId())
        val result = change(listOf(idea)).copy(source = "inspiration").apply(snapshot, wardrobe.id)
        assertEquals("", result.ideas.single().photoId); assertNull(result.ideas.single().wishId)
        assertEquals(snapshot.photoIds(), result.photoIds())
        assertTrue(runCatching { change(listOf(idea.copy(key = "idea:invented"))).copy(source = "inspiration").apply(snapshot, wardrobe.id) }.isFailure)
    }
    @Test fun canvasIsReconciledAndPreservedWithRealJson() {
        val layout = OutfitCanvasState.encode(listOf(CanvasPlacement("owned:${top.id}", .3f, .4f, 1.2f, 1),
            CanvasPlacement("owned:${shoes.id}", .5f, .7f, 1f, 0)))
        val result = change().copy(layout = layout, background = "sage").apply(snapshot, wardrobe.id)
        assertEquals(listOf(CanvasPlacement("owned:${top.id}", .3f, .4f, 1.2f, 1)), OutfitCanvasState.read(result.outfits.single().layout))
        assertEquals("sage", result.outfits.single().background)
    }
    @Test fun emptyInvisibleOverlongAndInvalidContextAreRejected() {
        for (name in listOf("", " ", "\u200b", "x".repeat(81))) {
            assertTrue(runCatching { change().copy(name = name).apply(snapshot, wardrobe.id) }.isFailure)
        }
        assertTrue(runCatching { change(emptyList()).apply(snapshot, wardrobe.id) }.isFailure)
        assertTrue(runCatching { change().copy(weather = "invalid").apply(snapshot, wardrobe.id) }.isFailure)
        assertTrue(runCatching { change().copy(background = "invalid").apply(snapshot, wardrobe.id) }.isFailure)
    }
}
