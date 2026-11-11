package com.familywardrobe.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * COL-02 on a real database: new base colours and shades are stored, survive a backup round trip with format 10, and a
 * family envelope carries them only for a server with "colors2" (an older one receives their older bases).
 */
@RunWith(AndroidJUnit4::class)
class ColorsPersistenceTest {
    @Test fun newColoursRoundTripAndFallBackForOlderServers() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val account = AuthIdentity("colour-tests", newId(), "owner@example.test")
        val context = AccountContext(base, AccountLocation(account.key, false))
        val name = "colours-${newId()}.db"
        val db = Room.databaseBuilder(context, WardrobeDatabase::class.java, name).addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
        val photos = PhotoStore(context)
        try {
            val repo = Repository(db); repo.initialize("Home")
            val home = repo.dao.allWardrobes().single().id
            val forest = repo.addCustomColor(home, "Forest", "green").id
            val coat = Garment(wardrobeId = home, name = "Coat", category = "outerwear", color = "green", photoId = newId(), customColorId = forest)
            val shirt = Garment(wardrobeId = home, name = "Shirt", category = "top", color = "cream", photoId = newId())
            val jeans = Garment(wardrobeId = home, name = "Jeans", category = "bottom", color = "light_blue", photoId = newId())
            listOf(coat, shirt, jeans).forEach { repo.save(it) }
            val stored = repo.snapshot()
            assertEquals(setOf("green", "cream", "light_blue"), stored.garments.map { it.color }.toSet())
            assertEquals("green", stored.customColors.single { it.id == forest }.baseColor)
            BackupStateRules.validate(stored)

            // The private backup keeps every colour and stays format 10.
            val backup = Backup(context, repo, photos)
            val json = backup.encode(stored)
            assertEquals(10, json.getInt("version"))
            val restored = backup.decode(json)
            assertEquals(stored.garments.map { it.id to it.color }.toSet(), restored.garments.map { it.id to it.color }.toSet())

            // A shared copy: exact colours for a "colors2" server, older bases for an older one.
            val identity = FamilyArchiveIdentity(newId(), home, account.uid)
            val envelope = FamilyEnvelope(backup)
            val current = envelope.decode(envelope.encode(stored, identity, 3, colors2 = true), identity, 3)
            assertEquals("green", current.garments.single { it.id == coat.id }.color)
            val older = envelope.decode(envelope.encode(stored, identity, 3), identity, 3)
            assertEquals(mapOf(coat.id to "olive", shirt.id to "white", jeans.id to "blue"), older.garments.associate { it.id to it.color })
            assertEquals("olive", older.customColors.single { it.id == forest }.baseColor)
            BackupStateRules.validate(older)
            assertTrue(older.garments.all { it.color in ColorRules.LEGACY })
        } finally { db.close(); context.deleteDatabase(name); photos.clearMemoryCache(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively() }
    }
}
