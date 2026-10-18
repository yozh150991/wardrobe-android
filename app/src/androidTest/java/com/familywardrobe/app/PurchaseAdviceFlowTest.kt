package com.familywardrobe.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PurchaseAdviceFlowTest {
    @Test fun savePlannedAndOwnedAlternativesThenPurchaseAndUndoRetainHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WardrobeDatabase::class.java).build()
        try {
            val repo = Repository(db); repo.initialize("Home"); val photos = PhotoStore(context)
            val wardrobe = Repository.DEFAULT_WARDROBE
            listOf("top", "bottom", "shoes").forEach { category ->
                repo.save(Garment(wardrobeId = wardrobe, name = "Owned $category", category = category, color = "navy", photoId = newId()))
            }
            val wish = Wish(wardrobeId = wardrobe, name = "Candidate", category = "shoes", kind = "product", color = "navy", photoId = newId(),
                link = "https://shop.example/candidate", priceMinor = 9000, currency = "EUR", budgetMinor = 12000, budgetCurrency = "EUR")
            repo.saveWish(wish, false, photos)
            val preview = PurchaseCompatibility.analyze(repo.snapshot(), wish, "everyday", "any_weather", "2026-10-08").previews.single()
            val planned = repo.saveLook(wardrobe, preview.pieces, "Plan", "everyday", "any_weather", "mixed")
            val ownPieces = preview.pieces.map { if (it.wishId == wish.id) LookRules.owned(preview.replacements.single()) else it }
            val owned = repo.saveLook(wardrobe, ownPieces, "Without purchase", "everyday", "any_weather", "owned")
            assertEquals(owned, repo.saveLook(wardrobe, ownPieces, "Repeat", "everyday", "any_weather", "owned"))
            assertEquals(2, repo.dao.allOutfits().size)
            assertEquals(wish, repo.dao.wish(wish.id))
            assertEquals(3, repo.dao.allGarments().size)
            assertEquals(wish.id, repo.dao.allIdeas().single().wishId)
            val receipt = repo.purchaseWish(wish.id, Garment(id = wish.id, wardrobeId = wardrobe, name = wish.name,
                category = wish.category, color = wish.color, photoId = wish.photoId, season = "summer", style = "sport", subcategory = "Running shoes",
                priceMinor = wish.priceMinor, currency = wish.currency))
            assertTrue(repo.dao.outfitIds(planned).contains(wish.id))
            assertEquals(wish.link, repo.dao.wish(wish.id)!!.link)
            assertEquals(wish.budgetMinor, repo.dao.wish(wish.id)!!.budgetMinor)
            assertTrue(repo.undoPurchase(receipt.undo!!, photos))
            assertEquals(wish, repo.dao.wish(wish.id))
            assertEquals(2, repo.dao.allOutfits().size)
            assertEquals(3, repo.dao.outfitIds(owned).size)
            assertEquals(planned, repo.dao.allIdeas().single().outfitId)
        } finally { db.close() }
    }
}
