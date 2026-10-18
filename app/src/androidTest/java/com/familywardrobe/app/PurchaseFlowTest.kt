package com.familywardrobe.app

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.File
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PurchaseFlowTest {
    private fun scenario(block: suspend (Repository, PhotoStore, Wish, List<Garment>) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WardrobeDatabase::class.java).build()
        try {
            val repo = Repository(db)
            repo.initialize("Home")
            val wardrobe = Repository.DEFAULT_WARDROBE
            val clothes = listOf("top", "bottom").map { Garment(wardrobeId = wardrobe, name = it, category = it, color = "navy", photoId = newId()) }
            clothes.forEach { repo.save(it) }
            val wish = Wish(wardrobeId = wardrobe, name = "Trainers", category = "shoes", kind = "product", photoId = newId(),
                priceMinor = 8900, currency = "EUR", link = "https://example.org/trainers", color = "white",
                needName = "Walking shoes", needCategory = "shoes", requirements = "Comfortable", budgetMinor = 35000, budgetCurrency = "PLN")
            val photos = PhotoStore(context)
            repo.saveWish(wish, false, photos)
            block(repo, photos, wish, clothes)
        } finally { db.close() }
    }
    private fun owned(wish: Wish) = Garment(id = wish.id, wardrobeId = wish.wardrobeId, name = wish.name,
        category = wish.category, color = wish.color, photoId = wish.photoId)

    @Test fun importedOwnedProductKeepsSourceAndRepeatedSaveIsIdempotent() = scenario { repo, photos, wish, _ ->
        val product = wish.copy(id = newId(), needName = "", needCategory = "")
        val reviewed = owned(product).copy(priceMinor = product.priceMinor, currency = product.currency)
        val receipt = repo.purchaseWish(product.id, reviewed, product)
        val repeated = repo.purchaseWish(product.id, reviewed, product)
        assertNull(repeated.undo)
        assertEquals(1, repo.dao.allGarments().count { it.id == product.id })
        assertEquals(product.copy(ownedId = product.id), repo.dao.wish(product.id))
        assertEquals(1, repo.dao.allWishEvents().count { it.wishId == product.id && it.kind == "purchased" })
        assertTrue(repo.undoPurchase(receipt.undo!!, photos))
        assertNull(repo.dao.garment(product.id))
        assertEquals(product, repo.dao.wish(product.id))
    }
    @Test fun invalidImportedOwnedProductRollsBackSourceAndGarmentTogether() = scenario { repo, _, wish, _ ->
        val product = wish.copy(id = newId(), needName = "", needCategory = "")
        val before = repo.snapshot()
        try { repo.purchaseWish(product.id, owned(product).copy(category = "invalid"), product); fail("Expected validation error") }
        catch (_: IllegalArgumentException) { }
        assertNull(repo.dao.wish(product.id))
        assertNull(repo.dao.garment(product.id))
        assertEquals(before, repo.snapshot())
    }
    @Test fun importedSourceCannotOverwriteAnExistingWantedProduct() = scenario { repo, _, wish, _ ->
        val imported = wish.copy(name = "Unexpected overwrite", needName = "", needCategory = "")
        try { repo.purchaseWish(wish.id, owned(imported), imported); fail("Expected identity conflict") }
        catch (_: IllegalArgumentException) { }
        assertEquals(wish, repo.dao.wish(wish.id))
        assertNull(repo.dao.garment(wish.id))
    }

    @Test fun purchaseUpdatesOutfitAndRepeatedConfirmationDoesNotDuplicate() = scenario { repo, photos, wish, clothes ->
        val planned = repo.saveLook(wish.wardrobeId, clothes.map(LookRules::owned) + LookRules.wanted(wish), "Walk",
            "everyday", "any_weather", "mixed")
        val receipt = repo.purchaseWish(wish.id, owned(wish))
        val repeated = repo.purchaseWish(wish.id, receipt.garment)
        assertNull(repeated.undo)
        assertEquals(3, repo.snapshot().garments.size)
        assertEquals(receipt.garment.id, repo.dao.wish(wish.id)!!.ownedId)
        assertEquals(wish.copy(ownedId = receipt.garment.id), repo.dao.wish(wish.id))
        assertTrue(repo.dao.allIdeas().none { it.outfitId == planned })
        assertEquals(setOf(receipt.garment.id) + clothes.map { it.id }, repo.dao.outfitIds(planned).toSet())
        assertEquals(R.string.look_ready, LookRules.status(LookRules.saved(repo.snapshot(), repo.dao.outfit(planned)!!)))
        assertTrue(repo.undoPurchase(receipt.undo!!, photos))
        assertEquals(wish, repo.dao.wish(wish.id))
        assertEquals(2, repo.snapshot().garments.size)
        assertEquals(wish.id, repo.dao.allIdeas().single().wishId)
        assertEquals(planned, repo.dao.allOutfits().single().id)
    }
    @Test fun editedItemCannotBeRemovedByStaleUndo() = scenario { repo, photos, wish, _ ->
        val receipt = repo.purchaseWish(wish.id, owned(wish))
        repo.update(receipt.garment.id) { it.copy(name = "Edited name") }
        assertFalse(repo.undoPurchase(receipt.undo!!, photos))
        assertEquals("Edited name", repo.dao.garment(receipt.garment.id)!!.name)
        assertEquals(receipt.garment.id, repo.dao.wish(wish.id)!!.ownedId)
    }
    @Test fun recordedWearCannotBeLostByUndo() = scenario { repo, photos, wish, _ ->
        val receipt = repo.purchaseWish(wish.id, owned(wish))
        repo.toggleItemWear(receipt.garment.id, "2026-10-02")
        assertFalse(repo.undoPurchase(receipt.undo!!, photos))
        assertEquals(1, repo.dao.allWears().size)
    }
    @Test fun conflictingCategoryRollsBackTheWholePurchase() = scenario { repo, _, wish, clothes ->
        repo.saveLook(wish.wardrobeId, clothes.map(LookRules::owned) + LookRules.wanted(wish), "Walk", "everyday", "any_weather", "mixed")
        try { repo.purchaseWish(wish.id, owned(wish).copy(category = "top")); fail("Expected category conflict") }
        catch (_: IllegalArgumentException) { }
        assertEquals(2, repo.dao.allGarments().size)
        assertEquals("", repo.dao.wish(wish.id)!!.ownedId)
        assertEquals(wish.id, repo.dao.allIdeas().single().wishId)
    }
    @Test fun backupRestoresPurchaseHistoryAndOwnershipReferences() = scenario { repo, photos, wish, _ ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repo.purchaseWish(wish.id, owned(wish))
        val archive = File(context.cacheDir, "purchase-backup-${newId()}.zip")
        try {
            for (id in repo.snapshot().photoIds()) {
                val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.BLUE)
                photos.file(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bitmap.recycle()
            }
            val backup = Backup(context, repo, photos)
            backup.export(Uri.fromFile(archive))
            val restoredWardrobe = backup.restore(Uri.fromFile(archive))!!
            val history = repo.dao.allWishes().single { it.wardrobeId == restoredWardrobe }
            val restored = repo.dao.garment(history.ownedId)!!
            assertEquals(restoredWardrobe, restored.wardrobeId)
            assertNotEquals(wish.id, history.id)
            assertNotEquals(wish.id, restored.id)
            assertEquals(wish.priceMinor, history.priceMinor)
            assertEquals(wish.budgetMinor, history.budgetMinor)
            assertEquals(wish.currency, history.currency)
            assertEquals(wish.budgetCurrency, history.budgetCurrency)
            assertEquals(wish.link, history.link)
            assertEquals(wish.requirements, history.requirements)
            assertTrue(repo.dao.allWishes().filter { it.wardrobeId == restoredWardrobe && it.ownedId.isBlank() }.isEmpty())
        } finally {
            archive.delete()
            photos.remove(repo.snapshot().photoIds())
        }
    }
    @Test fun undoKeepsAnOutfitCreatedAfterPurchase() = scenario { repo, photos, wish, clothes ->
        val receipt = repo.purchaseWish(wish.id, owned(wish))
        val outfit = repo.saveLook(wish.wardrobeId, (clothes + receipt.garment).map(LookRules::owned), "New outfit", "everyday", "any_weather", "owned")
        assertTrue(repo.undoPurchase(receipt.undo!!, photos))
        assertEquals(outfit, repo.dao.allOutfits().single().id)
        assertEquals("mixed", repo.dao.outfit(outfit)!!.source)
        assertEquals(R.string.look_planned, LookRules.status(LookRules.saved(repo.snapshot(), repo.dao.outfit(outfit)!!)))
    }
}
