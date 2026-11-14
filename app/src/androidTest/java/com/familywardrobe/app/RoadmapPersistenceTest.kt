package com.familywardrobe.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class RoadmapPersistenceTest {
    private fun scenario(test: suspend (Repository, PhotoStore, List<Garment>, String) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WardrobeDatabase::class.java).build()
        val photos = PhotoStore(context)
        val items = listOf("top", "bottom", "shoes").map { category ->
            Garment(wardrobeId = Repository.DEFAULT_WARDROBE, name = category, category = category, color = "navy", photoId = newId(), priceMinor = 9000, currency = "EUR")
        }
        try {
            val repo = Repository(db); repo.initialize("Home"); items.forEach { repo.save(it) }
            val look = repo.saveLook(Repository.DEFAULT_WARDROBE, items.map(LookRules::owned), "Look", "everyday", "any_weather", "owned")
            test(repo, photos, items, look)
        } finally { items.forEach { photos.file(it.photoId).delete() }; db.close() }
    }
    /** UX-07 (0.14.47): removing a saved outfit removes its plans and capsule links; what was worn stays worn. */
    @Test fun removingAnOutfitKeepsTheWearHistory() = scenario { repo, photos, items, look ->
        val today = LocalDate.now().toString(); val earlier = LocalDate.now().minusDays(3).toString()
        val plan = repo.scheduleOutfit(look, earlier); repo.togglePlanWorn(plan)
        repo.scheduleOutfit(look, LocalDate.now().plusDays(2).toString())
        // The first item is worn twice today: by itself and with the outfit.
        repo.toggleItemWear(items.first().id, today)
        repo.toggleOutfitWear(look, today)
        val extra = items.first().copy(id = newId(), name = "Extra", category = "other_item"); repo.save(extra)
        val trip = repo.saveCapsule(Capsule(wardrobeId = extra.wardrobeId, name = "Trip", kind = "trip"))
        repo.linkCapsuleOutfit(trip, look, true); repo.manualCapsuleItem(trip, extra.id, true)
        val other = repo.saveLook(Repository.DEFAULT_WARDROBE, (items.drop(1) + extra).map(LookRules::owned), "Other", "everyday", "any_weather", "owned")
        val daysBefore = repo.dao.allWears().map { it.garmentId to it.date }.toSet()
        assertEquals(7, repo.dao.allWears().size)

        repo.removeOutfit(look, photos)

        val s = repo.snapshot()
        assertEquals(listOf(other), s.outfits.map { it.id })
        assertTrue(s.plans.isEmpty() && s.capsuleOutfits.isEmpty())
        assertTrue(s.parts.all { it.outfitId == other })
        // Only the item added by hand stays in the trip; the removed outfit no longer brings its items.
        assertEquals(listOf(extra.id), s.capsuleItems.map { it.garmentId })
        // The same items on the same days, now as the items' own wear (one row where two sources met).
        assertEquals(daysBefore, s.wears.map { it.garmentId to it.date }.toSet())
        assertEquals(6, s.wears.size)
        assertTrue(s.wears.all { it.source == "item:${it.garmentId}" })
        BackupStateRules.validate(s)
        try { repo.removeOutfit(look, photos); fail("A removed outfit cannot be removed again") } catch (_: IllegalArgumentException) { }
        repo.removeOutfit(other, photos)
        assertTrue(repo.snapshot().outfits.isEmpty()); BackupStateRules.validate(repo.snapshot())
    }
    /** UX-15 (0.14.49): only an item that was never worn and is in no outfit can be deleted; the rest is archived. */
    @Test fun onlyAnUnusedItemCanBeDeleted() = scenario { repo, photos, items, look ->
        val wardrobe = Repository.DEFAULT_WARDROBE
        val extra = items.first().copy(id = newId(), name = "Extra", category = "other_item", photoId = newId()); repo.save(extra)
        val trip = repo.saveCapsule(Capsule(wardrobeId = wardrobe, name = "Trip", kind = "trip"))
        repo.manualCapsuleItem(trip, extra.id, true); repo.manualCapsuleItem(trip, items.first().id, true)
        // Reactions that name the item go with it; the one about other items stays.
        repo.dao.saveFeedback(OutfitFeedback(wardrobeId = wardrobe, signature = DailyRules.signature(listOf(extra.id, items.first().id)), weather = "any_weather", occasion = "everyday", reason = "dislike"))
        repo.dao.saveFeedback(OutfitFeedback(wardrobeId = wardrobe, signature = DailyRules.signature(listOf(items[0].id, items[1].id)), weather = "any_weather", occasion = "everyday", reason = "dislike"))
        photos.file(extra.photoId).writeBytes(byteArrayOf(1, 2, 3))

        // In a saved outfit: refused.
        try { repo.removeGarment(items.first().id, photos); fail("An item of a saved outfit is archived, not deleted") } catch (_: IllegalArgumentException) { }
        // Worn once: refused, also after the outfit is gone.
        repo.toggleItemWear(extra.id)
        try { repo.removeGarment(extra.id, photos); fail("A worn item is archived, not deleted") } catch (_: IllegalArgumentException) { }
        repo.toggleItemWear(extra.id)

        repo.removeGarment(extra.id, photos)

        val s = repo.snapshot()
        assertEquals(items.map { it.id }.toSet(), s.garments.map { it.id }.toSet())
        assertEquals(listOf(items.first().id), s.capsuleItems.map { it.garmentId })
        assertEquals(1, s.feedback.size); assertFalse(s.feedback.single().signature.contains(extra.id))
        assertEquals(listOf(look), s.outfits.map { it.id }); assertEquals(3, s.parts.size)
        assertFalse(photos.file(extra.photoId).exists())
        BackupStateRules.validate(s)
        try { repo.removeGarment(extra.id, photos); fail("A deleted item cannot be deleted again") } catch (_: IllegalArgumentException) { }
        // Once its outfit is removed and it was never worn, an item can go too.
        repo.removeOutfit(look, photos); repo.removeGarment(items.last().id, photos)
        assertEquals(2, repo.snapshot().garments.size); BackupStateRules.validate(repo.snapshot())
    }
    @Test fun planningAndMovingNeverCreateWear() = scenario { repo, _, _, look ->
        val day = LocalDate.now().plusDays(1).toString(); val plan = repo.scheduleOutfit(look, day)
        assertEquals(plan, repo.scheduleOutfit(look, day)); assertTrue(repo.dao.allWears().isEmpty())
        val next = LocalDate.now().plusDays(2).toString(); repo.movePlan(plan, next)
        assertEquals(next, repo.dao.plan(plan)!!.date); assertTrue(repo.dao.allWears().isEmpty())
        try { repo.togglePlanWorn(plan); fail("Future plan cannot be worn") } catch (_: IllegalArgumentException) { }
        repo.removePlan(plan); assertTrue(repo.dao.allPlans().isEmpty())
    }
    @Test fun calendarWearUndoKeepsIndependentItemWearAndCountsUniqueDays() = scenario { repo, _, items, look ->
        val date = LocalDate.now().toString(); repo.toggleItemWear(items.first().id, date)
        val plan = repo.scheduleOutfit(look, date); repo.togglePlanWorn(plan)
        assertTrue(repo.dao.plan(plan)!!.worn); assertEquals(4, repo.dao.allWears().size)
        assertEquals(1, WardrobeStats.usage(repo.snapshot(), items.first().wardrobeId, date, false).first { it.garment.id == items.first().id }.days)
        try { repo.movePlan(plan, LocalDate.now().plusDays(1).toString()); fail("Worn entry cannot be moved") } catch (_: IllegalArgumentException) { }
        repo.togglePlanWorn(plan)
        assertEquals("item:${items.first().id}", repo.dao.allWears().single().source)
        assertFalse(repo.dao.plan(plan)!!.worn)
    }
    @Test fun laundryBlocksCalendarConfirmationAndItemDayUndoReconcilesLedger() = scenario { repo, _, items, look ->
        val date = LocalDate.now().toString(); val plan = repo.scheduleOutfit(look, date)
        repo.changeAvailability(items.first().id, "laundry")
        try { repo.togglePlanWorn(plan); fail("Laundry must block wear") } catch (_: IllegalArgumentException) { }
        assertTrue(repo.dao.allWears().isEmpty())
        repo.changeAvailability(items.first().id, "available"); repo.togglePlanWorn(plan)
        repo.toggleItemWear(items.first().id, date)
        assertEquals(2, repo.dao.plan(plan)!!.wornItemIds.split('|').size)
        BackupStateRules.validate(repo.snapshot())
        repo.togglePlanWorn(plan); assertTrue(repo.dao.allWears().isEmpty())
    }
    @Test fun packingUnionManualItemsAndOutfitEditsPreserveOtherChecks() = scenario { repo, _, items, look ->
        val extra = items.first().copy(id = newId(), name = "Extra", category = "other_item"); repo.save(extra)
        val trip = repo.saveCapsule(Capsule(wardrobeId = extra.wardrobeId, name = "Trip", kind = "trip"))
        repo.linkCapsuleOutfit(trip, look, true); repo.manualCapsuleItem(trip, items.first().id, true); repo.manualCapsuleItem(trip, extra.id, true)
        assertEquals(4, repo.dao.allCapsuleItems().size)
        repo.packCapsuleItem(trip, items.last().id, true)
        repo.saveLook(extra.wardrobeId, items.drop(1).map(LookRules::owned), "Changed", "everyday", "any_weather", "owned", look)
        assertTrue(repo.dao.allCapsuleItems().single { it.garmentId == items.last().id }.packed)
        repo.linkCapsuleOutfit(trip, look, false)
        assertEquals(setOf(items.first().id, extra.id), repo.dao.allCapsuleItems().map { it.garmentId }.toSet())
        BackupStateRules.validate(repo.snapshot())
        repo.removeCapsule(trip); assertTrue(repo.dao.allCapsuleItems().isEmpty()); assertNotNull(repo.dao.garment(extra.id))
    }
    @Test fun capsuleRejectsCrossWardrobeItemsAndLaundryPacking() = scenario { repo, _, items, look ->
        val other = Wardrobe(name = "Other"); repo.dao.insertWardrobe(other)
        val capsule = repo.saveCapsule(Capsule(wardrobeId = other.id, name = "Other trip"))
        try { repo.linkCapsuleOutfit(capsule, look, true); fail("Cross-wardrobe look") } catch (_: IllegalArgumentException) { }
        try { repo.manualCapsuleItem(capsule, items.first().id, true); fail("Cross-wardrobe item") } catch (_: IllegalArgumentException) { }
        val own = repo.saveCapsule(Capsule(wardrobeId = items.first().wardrobeId, name = "Own")); repo.linkCapsuleOutfit(own, look, true)
        repo.changeAvailability(items.first().id, "laundry")
        try { repo.packCapsuleItem(own, items.first().id, true); fail("Laundry cannot be packed") } catch (_: IllegalArgumentException) { }
        assertFalse(repo.dao.allCapsuleItems().single { it.garmentId == items.first().id }.packed)
    }
    @Test fun currentBackupRemapsPlansFeedbackCapsulesAndPrices() = scenario { repo, photos, items, look ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        items.forEach { g -> photos.file(g.photoId).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }; bitmap.recycle()
        val plan = repo.scheduleOutfit(look, LocalDate.now().toString()); repo.togglePlanWorn(plan)
        repo.recordFeedback(items.first().wardrobeId, items.map { it.id }, "everyday", "any_weather", "dislike")
        val trip = repo.saveCapsule(Capsule(wardrobeId = items.first().wardrobeId, name = "Trip", kind = "trip"))
        repo.linkCapsuleOutfit(trip, look, true); repo.packCapsuleItem(trip, items.first().id, true)
        val file = File(context.cacheDir, "roadmap-backup-${newId()}.zip")
        var restoredPhotos = emptySet<String>()
        try {
            val backup = Backup(context, repo, photos); backup.export(Uri.fromFile(file)); val target = backup.restore(Uri.fromFile(file))!!
            val restored = repo.snapshot(); val garments = restored.garments.filter { it.wardrobeId == target }; restoredPhotos = garments.map { it.photoId }.toSet()
            assertTrue(garments.all { it.priceMinor == 9000L && it.currency == "EUR" }); assertTrue(garments.none { it.id in items.map { g -> g.id } })
            val calendar = restored.plans.single { it.wardrobeId == target }; assertTrue(calendar.worn); assertNotEquals(plan, calendar.id)
            assertEquals(garments.map { it.id }.toSet(), restored.wears.filter { it.source == "plan:${calendar.id}" }.map { it.garmentId }.toSet())
            val capsule = restored.capsules.single { it.wardrobeId == target }; assertEquals(1, restored.capsuleItems.count { it.capsuleId == capsule.id && it.packed })
            BackupStateRules.validate(restored)
        } finally { file.delete(); photos.remove(restoredPhotos) }
    }
}
