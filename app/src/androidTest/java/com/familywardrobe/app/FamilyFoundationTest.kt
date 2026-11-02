package com.familywardrobe.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Native acceptance for actual Kotlin/Room/AtomicFile/ZIP paths. Not executed by host checks. */
class FamilyFoundationTest {
    private fun scenario(block: suspend (AccountContext, AuthIdentity, Repository, PhotoStore) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val account = AuthIdentity("family-tests", newId(), "owner@example.test")
        val context = AccountContext(base, AccountLocation(account.key, false))
        val name = "family-${newId()}.db"
        val db = Room.databaseBuilder(context, WardrobeDatabase::class.java, name).build()
        val photos = PhotoStore(context)
        try { val repo = Repository(db); repo.initialize("Home"); block(context, account, repo, photos) }
        finally { db.close(); context.deleteDatabase(name); photos.clearMemoryCache(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively() }
    }
    private inline fun denied(block: () -> Unit) {
        var rejected = false
        try { block() } catch (_: Exception) { rejected = true }
        assertTrue("Invalid input must fail before accepting or writing it", rejected)
    }
    private fun photo(photos: PhotoStore): String {
        val id = newId()
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff224466.toInt()) }
        try { photos.file(id, "png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        return id
    }
    private suspend fun seed(repo: Repository, photos: PhotoStore): Snapshot {
        val first = repo.dao.allWardrobes().single()
        val second = Wardrobe(name = "Other"); repo.dao.insertWardrobe(second)
        repo.dao.insertWardrobe(Wardrobe(name = "Empty"))
        for (ward in listOf(first, second)) {
            val category = repo.addCustomCategory(ward.id, "Trail", "shoes")
            repo.addCustomCategory(ward.id, "Unused category", "top")
            val color = repo.addCustomColor(ward.id, "Midnight", "navy")
            repo.addCustomColor(ward.id, "Unused color", "red")
            val bought = Wish(wardrobeId = ward.id, name = "Shoes", category = "shoes", kind = "product", photoId = photo(photos))
            repo.dao.insertWish(bought)
            val garment = Garment(id = bought.id, wardrobeId = ward.id, name = "Shoes", category = "shoes", color = "navy",
                photoId = photo(photos), customCategoryId = category.id, customColorId = color.id)
            repo.purchaseWish(bought.id, garment)
            // The receipt and purchase event must keep the previous photo after an item edit.
            repo.save(garment.copy(photoId = photo(photos)), photos)
            val wanted = Wish(wardrobeId = ward.id, name = "Shirt", category = "top", kind = "product", photoId = photo(photos),
                needName = "Light shirt", needCategory = "top", needPhotoId = photo(photos))
            repo.dao.insertWish(wanted)
            repo.dao.saveWishEvent(wanted.event("candidate").copy(photoId = photo(photos)))
            val look = Outfit(wardrobeId = ward.id, name = "Weekend", source = "mixed",
                signature = listOf(garment.id, "wish:${wanted.id}").sorted().joinToString("|"))
            repo.dao.insertOutfit(look); repo.dao.insertParts(listOf(OutfitPart(look.id, garment.id)))
            repo.dao.insertIdeas(listOf(OutfitIdea(look.id, "wish:${wanted.id}", wanted.id, "top", "Shirt", "white", photo(photos))))
            val plan = DayPlan(wardrobeId = ward.id, outfitId = look.id, date = "2026-10-13", worn = true, wornItemIds = garment.id)
            repo.dao.savePlan(plan)
            repo.dao.insertWears(listOf(Wear(garment.id, plan.date, "plan:${plan.id}"), Wear(garment.id, "2026-10-12", "item:${garment.id}"),
                Wear(garment.id, "2026-10-11", "outfit:${look.id}")))
            repo.dao.saveFeedback(OutfitFeedback(wardrobeId = ward.id, signature = garment.id, weather = "any_weather", occasion = "everyday", reason = "dislike"))
            val capsule = Capsule(wardrobeId = ward.id, name = "Weekend")
            repo.dao.saveCapsule(capsule); repo.dao.saveCapsuleOutfit(CapsuleOutfit(capsule.id, look.id))
            repo.dao.saveCapsuleItem(CapsuleItem(capsule.id, garment.id))
        }
        return repo.snapshot().also(BackupStateRules::validate)
    }
    private fun id(state: Snapshot) = state.wardrobes.single { it.name == "Home" }.id

    @Test fun selectionKeepsEveryRelationAndOnlyItsPhotos() = scenario { _, _, repo, photos ->
        val before = seed(repo, photos); val chosen = id(before)
        val selected = WardrobeSnapshots.select(before, chosen)
        val other = WardrobeSnapshots.select(before, before.wardrobes.single { it.name == "Other" }.id)
        assertEquals(listOf(before.wardrobes.single { it.id == chosen }), selected.wardrobes)
        assertEquals(before.garments.filter { it.wardrobeId == chosen }, selected.garments)
        assertEquals(listOf(1, 1, 1, 3, 2, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2), listOf(selected.garments.size, selected.outfits.size,
            selected.parts.size, selected.wears.size, selected.wishes.size, selected.ideas.size, selected.plans.size, selected.feedback.size,
            selected.capsules.size, selected.capsuleItems.size, selected.capsuleOutfits.size, selected.purchaseUndos.size,
            selected.wishEvents.size, selected.customCategories.size, selected.customColors.size))
        assertTrue(selected.photoIds().intersect(other.photoIds()).isEmpty())
        assertEquals(before.photoIds(), selected.photoIds() + other.photoIds())
        assertTrue(GarmentReceipt.read(selected.purchaseUndos.single().garmentJson).photoId in selected.photoIds())
        assertEquals(before, repo.snapshot())
        denied { WardrobeSnapshots.select(before, newId()) }
        val empty = WardrobeSnapshots.select(before, before.wardrobes.single { it.name == "Empty" }.id)
        assertTrue(empty.garments.isEmpty() && empty.photoIds().isEmpty())
    }
    @Test fun singleWardrobeZipRestoresAdditivelyWithRemappedRelations() = scenario { context, account, repo, photos ->
        val before = seed(repo, photos); val selected = WardrobeSnapshots.select(before, id(before))
        FamilyMigrationStore(context, account).prepare(id(before))
        val backup = Backup(context, repo, photos); val file = File(context.cacheDir, "one.zip"); val uri = Uri.fromFile(file)
        backup.export(uri, wardrobeId = id(before))
        assertEquals(1, backup.verify(uri).wardrobes)
        ZipFile(file).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertEquals(setOf("manifest.json") + selected.photoIds().map { "photos/$it.png" }, names)
            val json = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use { it.readText() })
            assertEquals(selected, backup.decode(json))
            assertFalse(json.has("members")); assertFalse(json.has("remoteId"))
        }
        val restoredId = requireNotNull(backup.restore(uri)); val after = repo.snapshot()
        assertEquals(before.wardrobes.size + 1, after.wardrobes.size)
        before.wardrobes.forEach { assertEquals(WardrobeSnapshots.select(before, it.id), WardrobeSnapshots.select(after, it.id)) }
        val restored = WardrobeSnapshots.select(after, restoredId)
        assertEquals(selected.photoIds().size, restored.photoIds().size)
        assertTrue(restored.photoIds().intersect(before.photoIds()).isEmpty())
        val g = restored.garments.single(); val receipt = GarmentReceipt.read(restored.purchaseUndos.single().garmentJson)
        assertNotEquals(selected.garments.single().id, g.id)
        assertEquals(g.id, receipt.id); assertEquals(g.customCategoryId, receipt.customCategoryId); assertEquals(g.customColorId, receipt.customColorId)
        assertTrue(receipt.photoId in restored.photoIds()); assertNotEquals(g.photoId, receipt.photoId)
        assertEquals(g.id, restored.plans.single().wornItemIds)
        assertEquals(g.id, restored.capsuleItems.single().garmentId)
        assertEquals(1, FamilyMigrationStore(context, account).records().size)
        BackupStateRules.validate(after)
    }
    @Test fun invalidScopeOrCrossWardrobeReferenceCannotWriteDestination() = scenario { context, _, repo, photos ->
        val before = seed(repo, photos); val file = File(context.cacheDir, "existing.zip").apply { writeText("keep") }
        denied { Backup(context, repo, photos).export(Uri.fromFile(file), wardrobeId = newId()) }
        assertEquals("keep", file.readText()); assertEquals(before, repo.snapshot())
        val foreign = before.garments.single { it.wardrobeId != id(before) }
        val ownPart = before.parts.single { it.garmentId != foreign.id }
        val bad = before.copy(parts = before.parts.map { if (it == ownPart) it.copy(garmentId = foreign.id) else it })
        denied { WardrobeSnapshots.select(bad, id(before)) }
    }
    @Test fun familyEnvelopePreservesIdsAndRejectsAccountOrForgedIdentity() = scenario { context, account, repo, photos ->
        val before = seed(repo, photos); val backup = Backup(context, repo, photos); val envelope = FamilyEnvelope(backup)
        val identity = FamilyArchiveIdentity(newId(), id(before), account.uid)
        val wire = envelope.encode(before, identity, 7)
        // PERS-02: everything but the publisher's own reasons for turning down outfits.
        val selected = WardrobeSnapshots.select(before, id(before))
        assertEquals(1, selected.feedback.size)
        assertEquals(selected.copy(feedback = emptyList()), envelope.decode(wire, identity, 7))
        denied { backup.decode(JSONObject(wire)) }
        denied { envelope.decode(backup.encode(before).toString(), identity, 7) }
        denied { envelope.decode(wire, identity.copy(ownerUid = "other"), 7) }
        denied { envelope.decode(wire, identity.copy(remoteId = newId()), 7) }
        denied { envelope.decode(wire, identity.copy(wardrobeId = newId()), 7) }
        denied { envelope.decode(wire, identity, 8) }
        denied { envelope.decode(JSONObject(wire).put("role", "owner").toString(), identity, 7) }
        denied { envelope.decode(JSONObject(wire).put("version", 1.5).toString(), identity, 7) }
        denied { envelope.decode(JSONObject(wire).put("baseRevision", "7").toString(), identity, 7) }
        denied { envelope.decode(JSONObject(wire).put("snapshot", backup.encode(before)).toString(), identity, 7) }
        val extraPhoto = JSONObject(wire).apply { getJSONObject("snapshot").getJSONObject("photoFiles").put(newId(), "png") }
        denied { envelope.decode(extraPhoto.toString(), identity, 7) }
    }
    @Test fun migrationReopensAndRepeatsWithoutNewRemoteIdentity() = scenario { context, account, _, _ ->
        val ward = newId(); val store = FamilyMigrationStore(context, account); val prepared = store.prepare(ward)
        val reopened = FamilyMigrationStore(context, account)
        assertEquals(prepared, reopened.prepare(ward))
        val staged = reopened.staged(prepared, 14, "ab".repeat(32))
        assertEquals(staged, store.staged(prepared, 14, staged.fingerprint))
        val uploaded = store.uploaded(staged)
        val verified = reopened.confirmServer(uploaded, uploaded.identity, uploaded.path, uploaded.fingerprint, 1, 14)
        assertEquals(FamilyMigrationPhase.VERIFIED, verified.phase)
        assertEquals(verified, store.confirmServer(uploaded, uploaded.identity, uploaded.path, uploaded.fingerprint, 1, 14))
        assertEquals(verified, store.uploaded(staged))
        assertEquals(verified, FamilyMigrationStore(context, account).prepare(ward))
        assertEquals(1, store.records().size)
        assertEquals(prepared.remoteId, verified.remoteId); assertEquals(prepared.archiveId, verified.archiveId)
    }
    @Test fun migrationRejectsStaleAndUnverifiedCompletionWithoutAdvancing() = scenario { context, account, _, _ ->
        val store = FamilyMigrationStore(context, account); val prepared = store.prepare(newId())
        denied { store.uploaded(prepared) }
        val staged = store.staged(prepared, 5, "ab".repeat(32))
        denied { store.uploaded(prepared) }
        denied { store.confirmServer(staged, staged.identity, staged.path, staged.fingerprint, 1, 5) }
        denied { store.staged(prepared, 6, staged.fingerprint) }
        denied { store.staged(prepared, 5, "cd".repeat(32)) }
        val uploaded = store.uploaded(staged)
        denied { store.confirmServer(uploaded, uploaded.identity, uploaded.path, uploaded.fingerprint, 1, 6) }
        denied { store.confirmServer(uploaded, uploaded.identity.copy(ownerUid = "other"), uploaded.path, uploaded.fingerprint, 1, 5) }
        denied { store.confirmServer(uploaded, uploaded.identity, "users/other/snapshot.zip", uploaded.fingerprint, 1, 5) }
        denied { store.confirmServer(uploaded, uploaded.identity, uploaded.path, "cd".repeat(32), 1, 5) }
        denied { store.confirmServer(uploaded, uploaded.identity, uploaded.path, uploaded.fingerprint, 2, 5) }
        denied { store.uploaded(uploaded.copy(archiveId = newId())) }
        assertEquals(listOf(uploaded), store.records())
    }
    @Test fun migrationJournalCannotBeAdoptedByAnotherAccount() = scenario { context, account, _, _ ->
        val store = FamilyMigrationStore(context, account); store.prepare(newId())
        val another = account.copy(uid = newId())
        denied { FamilyMigrationStore(context, another) }
        scenario { otherContext, otherAccount, _, _ ->
            File(context.filesDir, "family-migration-v1.json").copyTo(File(otherContext.filesDir, "family-migration-v1.json"))
            denied { FamilyMigrationStore(otherContext, otherAccount).prepare(newId()) }
        }
        assertEquals(1, store.records().size)
    }
    @Test fun corruptJournalFailsClosedAndCannotAllocateDuplicateMigration() = scenario { context, account, _, _ ->
        val store = FamilyMigrationStore(context, account); val row = store.prepare(newId())
        val file = File(context.filesDir, "family-migration-v1.json"); val original = file.readBytes()
        file.writeText("{broken")
        denied { store.prepare(row.wardrobeId) }
        assertEquals("{broken", file.readText())
        file.writeBytes(original)
        assertEquals(row, store.prepare(row.wardrobeId))
    }
}
