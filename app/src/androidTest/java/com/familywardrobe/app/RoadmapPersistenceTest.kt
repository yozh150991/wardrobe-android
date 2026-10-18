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
