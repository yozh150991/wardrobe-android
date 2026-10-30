package com.familywardrobe.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** PERS-01: reasons are stored with their context, survive a restart, undo exactly what they added, stay format 10. */
@RunWith(AndroidJUnit4::class)
class PreferencePersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun open(name: String) = Room.databaseBuilder(context, WardrobeDatabase::class.java, name)
        .addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()

    @Test fun reasonsPersistUndoAndKeepTheirScope() = runBlocking {
        val name = "preferences-${newId()}.db"
        val home = Repository.DEFAULT_WARDROBE
        val items = listOf("top", "bottom", "shoes").map { Garment(wardrobeId = home, name = it, category = it, color = "navy", photoId = newId()) }
        val ids = items.map { it.id }; val shoes = items.last().id
        var db = open(name)
        try {
            var repo = Repository(db); repo.initialize("Home"); items.forEach { repo.save(it) }
            val combination = repo.recordReason(home, ids, emptyList(), "everyday", "any_weather", FeedbackReason.COMBINATION)
            assertEquals(1, combination.size)
            // The same reason twice adds nothing, so its undo can never remove an older row.
            assertEquals(emptyList<String>(), repo.recordReason(home, ids, emptyList(), "everyday", "any_weather", FeedbackReason.COMBINATION))
            assertEquals(1, repo.recordReason(home, ids, listOf(shoes), "everyday", "any_weather", FeedbackReason.UNCOMFORTABLE).size)
            // An item is uncomfortable once, whatever the context.
            assertEquals(emptyList<String>(), repo.recordReason(home, ids, listOf(shoes), "work", "cold", FeedbackReason.UNCOMFORTABLE))
            val formal = repo.recordReason(home, ids, emptyList(), "work", "cold", FeedbackReason.FORMAL)
            val warm = repo.recordReason(home, ids, emptyList(), "everyday", "cold", FeedbackReason.TOO_WARM)
            assertEquals(1, formal.size); assertEquals(1, warm.size)
            try { repo.recordReason(home, ids, listOf("foreign"), "everyday", "warm", FeedbackReason.UNCOMFORTABLE); fail("Not in the outfit") }
            catch (_: IllegalArgumentException) { }

            // Restart: the rows and their effect are still there.
            db.close(); db = open(name); repo = Repository(db)
            val stored = repo.snapshot()
            assertEquals(4, stored.feedback.size)
            BackupStateRules.validate(stored)
            val signature = DailyRules.signature(ids)
            assertEquals(setOf(signature), DailyOutfits.rejected(stored, home, "active_day", "warm"))
            assertEquals(mapOf(shoes to PreferenceRules.UNCOMFORTABLE), DailyOutfits.penalties(stored, home, "active_day", "warm"))
            assertEquals(PreferenceRules.MAX, DailyOutfits.penalties(stored, home, "work", "cold")[shoes])
            assertEquals(PreferenceRules.CONTEXT, DailyOutfits.penalties(stored, home, "work", "warm")[ids.first()])

            // Undo removes exactly the rows of that reason.
            repo.forgetFeedback(home, formal)
            assertEquals(3, repo.snapshot().feedback.size)
            assertNull(DailyOutfits.penalties(repo.snapshot(), home, "work", "warm")[ids.first()])
            repo.forgetFeedback(home, formal) // already gone: no error
            repo.forgetFeedback(home, combination + warm)
            assertEquals(listOf(shoes), repo.snapshot().feedback.map { it.signature })
            // The item row is valid for a shared wardrobe too: one wardrobe's feedback goes into its family snapshot.
            assertEquals(1, WardrobeSnapshots.select(repo.snapshot(), home).feedback.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    /** PERS-02: choices belong to one account and one wardrobe, survive a new store and reset cleanly. */
    @Test fun personalChoicesAreAccountAndWardrobeScoped() {
        val first = AccountContext(context, AccountLocation(AuthRules.accountKey("tests", "first-${newId()}"), false))
        val second = AccountContext(context, AccountLocation(AuthRules.accountKey("tests", "second-${newId()}"), false))
        try {
            val store = PersonalChoiceStore(first)
            assertEquals(PersonalChoices(), store.read("home"))
            store.setStyle("home", "work", "casual"); store.setWarmth("home", "lighter")
            val before = store.revision.value
            store.setStyle("home", "everyday", "formal")
            assertTrue(store.revision.value > before)
            // A new store (as after a restart) reads the same choices.
            assertEquals(PersonalChoices(mapOf("work" to "casual", "everyday" to "formal"), "lighter"), PersonalChoiceStore(first).read("home"))
            assertEquals(PersonalChoices(), PersonalChoiceStore(first).read("child"))
            assertEquals(PersonalChoices(), PersonalChoiceStore(second).read("home"))
            store.setStyle("home", "work", PreferenceProfile.AUTO)
            assertEquals(PreferenceProfile.AUTO, store.read("home").style("work"))
            try { store.setStyle("home", "work", "pyjamas"); fail("Unknown style") } catch (_: IllegalArgumentException) { }
            store.clear("home")
            assertEquals(PersonalChoices(), PersonalChoiceStore(first).read("home"))
        } finally {
            listOf(first, second).forEach { context.deleteSharedPreferences("account-${it.location.key}-preferences") }
        }
    }
}
