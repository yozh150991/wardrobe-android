package com.familywardrobe.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WEAR-01 on a real database: «Вдягаю сьогодні» and wear on an earlier day add plain item rows (no outfit, no plan,
 * backup format 10), only where the day has no wear yet; the undo removes exactly those rows; a future day, another
 * wardrobe's item and a repeated item are refused.
 */
@RunWith(AndroidJUnit4::class)
class WearLogPersistenceTest {
    @Test fun wearingAVariantAddsItemRowsAndUndoesExactly() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val account = AuthIdentity("wear-tests", newId(), "owner@example.test")
        val context = AccountContext(base, AccountLocation(account.key, false))
        val name = "wear-${newId()}.db"
        val db = Room.databaseBuilder(context, WardrobeDatabase::class.java, name).addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
        val photos = PhotoStore(context)
        try {
            val repo = Repository(db); repo.initialize("Home")
            val home = repo.dao.allWardrobes().single().id
            repo.addWardrobe("Second")
            val other = repo.dao.allWardrobes().single { it.id != home }.id
            fun item(category: String, wardrobe: String = home) = Garment(wardrobeId = wardrobe, name = category, category = category, color = "navy", photoId = newId())
            val top = item("top"); val bottom = item("bottom"); val shoes = item("shoes"); val coat = item("outerwear"); val foreign = item("top", other)
            listOf(top, bottom, shoes, coat, foreign).forEach { repo.save(it) }
            val today = "2026-11-11"; val earlier = "2026-11-09"
            val before = repo.snapshot()

            // The shoes were already worn today from a saved outfit: «Вдягаю сьогодні» leaves that row alone.
            val outfit = repo.saveOutfit(home, listOf(top.id, bottom.id, shoes.id), "Saved")
            repo.toggleItemWear(shoes.id, today); repo.toggleItemWear(shoes.id, today)   // on and off again: nothing left
            repo.dao.insertWears(listOf(Wear(shoes.id, today, "outfit:$outfit")))
            val added = repo.wearItems(home, listOf(top.id, bottom.id, shoes.id), today, today)
            assertEquals(listOf(top.id, bottom.id), added)
            var wears = repo.dao.allWears().filter { it.date == today }
            assertEquals(setOf(Wear(top.id, today, "item:${top.id}"), Wear(bottom.id, today, "item:${bottom.id}"), Wear(shoes.id, today, "outfit:$outfit")), wears.toSet())
            // No outfit and no plan was created for it, and the snapshot is valid for a format 10 backup.
            assertEquals(1, repo.dao.allOutfits().size); assertTrue(repo.dao.allPlans().isEmpty())
            BackupStateRules.validate(repo.snapshot())
            val backup = Backup(context, repo, photos)
            val json = backup.encode(repo.snapshot()); assertEquals(10, json.getInt("version"))
            assertEquals(repo.snapshot().wears.toSet(), backup.decode(json).wears.toSet())

            // A second tap adds nothing; the undo of the first removes exactly its rows.
            assertTrue(repo.wearItems(home, listOf(top.id, bottom.id, shoes.id), today, today).isEmpty())
            repo.forgetWears(home, added, today)
            wears = repo.dao.allWears().filter { it.date == today }
            assertEquals(listOf(Wear(shoes.id, today, "outfit:$outfit")), wears)
            // An undo never touches another wardrobe's rows or another day.
            repo.wearItems(other, listOf(foreign.id), today, today); repo.wearItems(home, listOf(top.id), earlier, today)
            repo.forgetWears(home, listOf(foreign.id, top.id), today)
            assertEquals(setOf(foreign.id to today, top.id to earlier, shoes.id to today), repo.dao.allWears().map { it.garmentId to it.date }.toSet())

            // An earlier day counts as a wear day; removing that day (the item's own page) clears it.
            assertEquals(listOf(earlier), repo.dao.allWears().filter { it.garmentId == top.id }.map { it.date })
            repo.toggleItemWear(top.id, earlier)
            assertTrue(repo.dao.allWears().none { it.garmentId == top.id })

            // Refused: a future day, an invalid day, another wardrobe's item, the same item twice, nothing at all.
            val count = repo.dao.allWears().size
            for (attempt in listOf<suspend () -> Any>(
                { repo.wearItems(home, listOf(top.id), "2026-11-12", today) }, { repo.wearItems(home, listOf(top.id), "2026-13-01", today) },
                { repo.wearItems(home, listOf(foreign.id), today, today) }, { repo.wearItems(home, listOf(top.id, top.id), today, today) },
                { repo.wearItems(home, emptyList(), today, today) })) {
                try { attempt(); fail("Must be refused") } catch (_: IllegalArgumentException) { }
            }
            assertEquals(count, repo.dao.allWears().size)
            assertEquals(before.garments.map { it.id }.toSet(), repo.snapshot().garments.map { it.id }.toSet())
        } finally { db.close(); context.deleteDatabase(name); photos.clearMemoryCache(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively() }
    }
}
