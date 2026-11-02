package com.familywardrobe.app

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** WID-01: «Одягнув сьогодні» from the widget and the widget's views on a real Android. */
@RunWith(AndroidJUnit4::class)
class TodayWidgetTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun wearingFromTheWidgetIsIdempotentAndScoped() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WardrobeDatabase::class.java).build()
        try {
            val repo = Repository(db); repo.initialize("Home")
            val home = Repository.DEFAULT_WARDROBE
            val items = listOf("top", "bottom", "shoes").map { Garment(wardrobeId = home, name = it, category = it, color = "navy", photoId = newId()) }
            items.forEach { repo.save(it) }
            val look = repo.saveLook(home, items.map(LookRules::owned), "Look", "everyday", "any_weather", "owned")
            val today = LocalDate.now().toString()
            val plan = repo.scheduleOutfit(look, today)
            assertTrue(repo.markPlanWorn(plan, home, today))
            // A second tap or a repeated broadcast changes nothing: no double wear, never an undo.
            assertFalse(repo.markPlanWorn(plan, home, today))
            assertTrue(repo.dao.plan(plan)!!.worn)
            assertEquals(3, repo.dao.allWears().size)
            assertTrue(repo.dao.allWears().all { it.source == "plan:$plan" && it.date == today })
            // The tap carries the wardrobe and day the widget showed; anything else is refused.
            val other = Wardrobe(name = "Other"); repo.dao.insertWardrobe(other)
            val tomorrow = repo.scheduleOutfit(look, LocalDate.now().plusDays(1).toString())
            try { repo.markPlanWorn(tomorrow, home, today); fail("Another day") } catch (_: IllegalArgumentException) { }
            try { repo.markPlanWorn(plan, other.id, today); fail("Another wardrobe") } catch (_: IllegalArgumentException) { }
            assertFalse(repo.markPlanWorn("missing", home, today))
            assertEquals(3, repo.dao.allWears().size)
            // Same conditions as the calendar: an item in the laundry blocks the mark.
            repo.togglePlanWorn(plan); repo.changeAvailability(items.first().id, "laundry")
            try { repo.markPlanWorn(plan, home, today); fail("Laundry") } catch (_: IllegalArgumentException) { }
            assertTrue(repo.dao.allWears().isEmpty())
            BackupStateRules.validate(repo.snapshot())
        } finally { db.close() }
    }

    private fun inflate(state: TodayWidgetState, picture: Bitmap? = null): View {
        var view: View? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view = TodayWidgets.views(context, state, "account", picture).apply(context, FrameLayout(context))
        }
        return view!!
    }
    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()
    private fun View.shown(id: Int) = findViewById<View>(id).visibility == View.VISIBLE

    @Test fun everyStateInflatesWithoutLeakingPrivateData() {
        val signedOut = inflate(TodayWidgetState.SignedOut)
        assertEquals(context.getString(R.string.widget_signed_out), signedOut.text(R.id.widget_name))
        assertEquals("", signedOut.text(R.id.widget_subtitle))
        assertFalse(signedOut.shown(R.id.widget_picture)); assertFalse(signedOut.shown(R.id.widget_wear))
        assertEquals(context.getString(R.string.widget_open_app), signedOut.text(R.id.widget_open))

        val empty = inflate(TodayWidgetState.NoPlan("2026-11-02", ""))
        assertEquals(context.getString(R.string.widget_no_plan), empty.text(R.id.widget_name))
        assertEquals(context.getString(R.string.widget_pick), empty.text(R.id.widget_open))
        assertTrue(empty.text(R.id.widget_subtitle).startsWith(context.getString(R.string.my_wardrobe)))
        assertFalse(empty.shown(R.id.widget_wear))

        val ready = TodayWidgetState.Plan("2026-11-02", "Дім", "home", "plan", "look", "Офіс", listOf("a"), 3, false, emptyList(), false, 0)
        val picture = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val plan = inflate(ready, picture)
        assertEquals("Офіс", plan.text(R.id.widget_name))
        assertTrue(plan.text(R.id.widget_subtitle).startsWith("Дім"))
        assertTrue(plan.shown(R.id.widget_picture)); assertTrue(plan.shown(R.id.widget_wear)); assertFalse(plan.shown(R.id.widget_status))
        assertEquals(context.getString(R.string.widget_picture, "Офіс"), plan.findViewById<View>(R.id.widget_picture).contentDescription)

        val worn = inflate(ready.copy(worn = true, more = 2))
        assertFalse(worn.shown(R.id.widget_wear)); assertFalse(worn.shown(R.id.widget_picture))
        val status = worn.text(R.id.widget_status)
        assertTrue(status.contains(context.getString(R.string.widget_worn)))
        assertTrue(status.contains(context.getString(R.string.widget_more, 2)))

        val blocked = inflate(ready.copy(unavailable = listOf("Светр")), picture)
        assertFalse(blocked.shown(R.id.widget_wear))
        assertTrue(blocked.text(R.id.widget_status).contains("Светр"))
    }

    @Test fun thePictureIsSmallAndSkipsMissingPhotos() {
        val photos = PhotoStore(context)
        val ids = List(3) { newId() }
        try {
            ids.take(2).forEach { id ->
                val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                photos.file(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                bitmap.recycle()
            }
            val picture = TodayWidgets.picture(photos, ids)!!
            assertEquals(TodayWidgets.PICTURE, picture.width); assertEquals(TodayWidgets.PICTURE, picture.height)
            assertTrue(picture.byteCount <= 400 * 1024)
            assertNull(TodayWidgets.picture(photos, listOf(ids.last())))
        } finally { ids.forEach { photos.file(it).delete() } }
    }
}
