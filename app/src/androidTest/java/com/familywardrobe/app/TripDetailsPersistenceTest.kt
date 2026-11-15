package com.familywardrobe.app

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familywardrobe.app.auth.AccountContext
import com.familywardrobe.app.auth.AccountLocation
import com.familywardrobe.app.auth.AuthIdentity
import com.familywardrobe.app.backup.Backup
import com.familywardrobe.app.backup.BackupStateRules
import com.familywardrobe.app.data.Capsule
import com.familywardrobe.app.data.saveCapsule
import com.familywardrobe.app.family.FamilyArchiveIdentity
import com.familywardrobe.app.family.FamilyEnvelope
import com.familywardrobe.app.outfits.WeatherCity
import com.familywardrobe.app.photo.PhotoStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TRIP-01 (0.14.44) on a real database: a trip's city, coordinates and occasion are saved with the capsule; a backup
 * is format 11 only when some capsule has them (otherwise 10, readable by older apps); the verified archive reports
 * its format for the cloud head; a shared wardrobe's copy stays format 10 without the details; invalid details are
 * refused when saved and when restored.
 */
@RunWith(AndroidJUnit4::class)
class TripDetailsPersistenceTest {
    @Test fun tripDetailsRoundTripAndStayPrivate() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val account = AuthIdentity("trip-tests", newId(), "owner@example.test")
        val context = AccountContext(base, AccountLocation(account.key, false))
        val name = "trip-${newId()}.db"
        val db = Room.databaseBuilder(context, WardrobeDatabase::class.java, name).addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
        val photos = PhotoStore(context)
        try {
            val repo = Repository(db); repo.initialize("Home")
            val home = repo.dao.allWardrobes().single().id
            val backup = Backup(context, repo, photos)

            // Without details: format 10, exactly as before.
            val plain = repo.saveCapsule(Capsule(wardrobeId = home, name = "Lviv", kind = "trip", startDate = "2026-12-01", endDate = "2026-12-03"))
            assertFalse(repo.snapshot().capsules.single().hasTripDetails)
            assertEquals(10, backup.encode(repo.snapshot()).getInt("version"))

            // With a city and an occasion: format 11, and the details come back.
            val trip = repo.snapshot().capsules.single { it.id == plain }.copy(city = "Lviv, Lviv Oblast, Ukraine", latitude = 49.84, longitude = 24.03, occasion = "work")
            repo.saveCapsule(trip)
            val stored = repo.snapshot()
            assertEquals(trip, stored.capsules.single())
            assertEquals(WeatherCity("Lviv, Lviv Oblast, Ukraine", 49.84, 24.03), stored.capsules.single().tripCity)
            BackupStateRules.validate(stored)
            val json = backup.encode(stored)
            assertEquals(11, json.getInt("version"))
            assertEquals(trip, backup.decode(json).capsules.single())
            // An older reader's cap (10) leaves the details out instead of writing a format it cannot read.
            val capped = backup.encode(stored, 10)
            assertEquals(10, capped.getInt("version"))
            assertFalse(capped.getJSONArray("capsules").getJSONObject(0).has("city"))
            assertFalse(backup.decode(capped).capsules.single().hasTripDetails)

            // The verified archive reports format 11, which the cloud head records.
            val file = File(context.cacheDir, "trip-${newId()}.zip")
            backup.export(Uri.fromFile(file))
            assertEquals(11, backup.verify(Uri.fromFile(file)).format)
            file.delete()

            // A shared wardrobe's copy is the family format 10 and never carries the details.
            val identity = FamilyArchiveIdentity(newId(), home, account.uid)
            val envelope = FamilyEnvelope(backup)
            val wire = envelope.encode(stored, identity, 2)
            assertEquals(10, JSONObject(wire).getJSONObject("snapshot").getInt("version"))
            assertFalse(wire.contains("Lviv Oblast"))
            val shared = envelope.decode(wire, identity, 2).capsules.single()
            assertEquals(trip.copy(city = "", latitude = null, longitude = null, occasion = ""), shared)

            // Refused when saved: a city without coordinates, one coordinate, out of range, an unknown occasion.
            for (bad in listOf(trip.copy(latitude = null, longitude = null), trip.copy(longitude = null), trip.copy(city = ""),
                trip.copy(latitude = 91.0), trip.copy(longitude = -181.0), trip.copy(occasion = "party"), trip.copy(city = "x".repeat(Capsule.MAX_CITY + 1)))) {
                try { repo.saveCapsule(bad); fail("Must be refused: $bad") } catch (_: IllegalArgumentException) { }
            }
            assertEquals(trip, repo.snapshot().capsules.single())
            // Refused when restored: the same checks guard a format 11 archive.
            for (change in listOf<(JSONObject) -> Unit>({ it.put("latitude", 120.0) }, { it.put("longitude", JSONObject.NULL) },
                { it.put("occasion", "party") }, { it.remove("city") })) {
                val broken = backup.encode(stored); change(broken.getJSONArray("capsules").getJSONObject(0))
                val rejected = runCatching { backup.decode(broken).also(BackupStateRules::validate) }.isFailure
                assertTrue("A broken trip must be rejected", rejected)
            }
            // Removing the details brings the backup back to format 10.
            repo.saveCapsule(trip.copy(city = "", latitude = null, longitude = null, occasion = ""))
            assertEquals(10, backup.encode(repo.snapshot()).getInt("version"))
        } finally { db.close(); context.deleteDatabase(name); photos.clearMemoryCache(); context.filesDir.deleteRecursively(); context.cacheDir.deleteRecursively() }
    }
}
