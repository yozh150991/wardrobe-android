package com.familywardrobe.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** ROT-02: postponements and the card's frequency survive a restart and belong to one account and one wardrobe. */
@RunWith(AndroidJUnit4::class)
class RotationStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = "2026-11-03"

    @Test fun settingsSurviveARestartAndStayScoped() {
        val first = AccountContext(context, AccountLocation(AuthRules.accountKey("tests", "rotation-${newId()}"), false))
        val second = AccountContext(context, AccountLocation(AuthRules.accountKey("tests", "rotation-${newId()}"), false))
        val store = RotationStore(first)
        try {
            assertEquals(RotationState(), store.read("home", today))
            val before = store.revision.value
            assertEquals("2027-02-01", store.snooze("home", "coat", RotationRules.SNOOZE_SEASON, RotationRules.WAITING, today))
            store.snooze("home", "scarf", RotationRules.SNOOZE_WEEK, RotationRules.LATER, today)
            store.setFrequency("home", RotationRules.WEEKLY)
            store.hideToday("home", today)
            assertTrue(store.revision.value > before)
            // Recording what the card showed is quiet: it never makes the card recompute itself.
            val quiet = store.revision.value
            store.markShown("home", "coat", today)
            assertEquals(quiet, store.revision.value)

            // A new store (as after a restart) reads the same settings.
            val again = RotationStore(first).read("home", today)
            assertEquals(RotationRules.WEEKLY, again.frequency); assertEquals(today, again.hiddenOn); assertEquals(today, again.lastShown)
            assertEquals(RotationSnooze("coat", "2027-02-01", RotationRules.WAITING), again.snoozes["coat"])
            assertEquals(setOf("coat", "scarf"), again.snoozes.keys); assertEquals(today, again.seen["coat"])
            // On its day a postponement is over and no longer read.
            assertEquals(setOf("coat"), RotationStore(first).read("home", "2026-11-10").snoozes.keys)
            // Another wardrobe and another account start from the defaults.
            assertEquals(RotationState(), RotationStore(first).read("child", today))
            assertEquals(RotationState(), RotationStore(second).read("home", today))
            // History older than a week is dropped when the card shows something new.
            store.markShown("home", "scarf", "2026-11-20")
            assertEquals(setOf("scarf"), RotationStore(first).read("home", "2026-11-20").seen.keys)

            store.restore("home", "coat"); store.unhide("home")
            assertNull(store.read("home", today).snoozes["coat"]); assertEquals("", store.read("home", today).hiddenOn)
            try { store.setFrequency("home", "hourly"); fail("Unknown frequency") } catch (_: IllegalArgumentException) { }
            try { store.snooze("home", "coat", 5, RotationRules.LATER, today); fail("Unknown duration") } catch (_: IllegalArgumentException) { }
            store.clear("home")
            assertEquals(RotationState(), store.read("home", today))
        } finally {
            store.clear("home"); store.clear("child"); RotationStore(second).clear("home")
        }
    }
}
