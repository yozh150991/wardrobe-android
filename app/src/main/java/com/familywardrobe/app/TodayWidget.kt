package com.familywardrobe.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.widget.RemoteViews
import androidx.room.Room
import com.familywardrobe.app.auth.AccountContext
import com.familywardrobe.app.auth.AccountErasure
import com.familywardrobe.app.auth.AuthIdentity
import com.familywardrobe.app.data.markPlanWorn
import com.familywardrobe.app.outfits.TodayWidgetModel
import com.familywardrobe.app.outfits.TodayWidgetState
import com.familywardrobe.app.photo.PhotoStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** WID-01: a widget tap that the open app turns into navigation, only for the account that is signed in. */
data class WidgetRequest(val kind: String, val outfitId: String = "") {
    companion object { const val OUTFIT = "outfit"; const val DAILY = "daily" }
}

/**
 * WID-01 «Образ на сьогодні». The provider only receives the system's broadcasts (it must be exported); the widget's own
 * taps and the midnight refresh go to [TodayWidgetActions], which no other app can call.
 */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = TodayWidgets.refreshAsync(this, context)
    override fun onEnabled(context: Context) = TodayWidgets.refreshAsync(this, context)
    override fun onDisabled(context: Context) = TodayWidgets.cancelMidnight(context)
    override fun onReceive(context: Context, intent: Intent) {
        // A new day can also come from a time zone or clock change.
        if (intent.action in setOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_LOCALE_CHANGED)) {
            TodayWidgets.refreshAsync(this, context); return
        }
        super.onReceive(context, intent)
    }
}

class TodayWidgetActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as WardrobeApplication
        val pending = goAsync()
        app.backgroundScope.launch {
            try {
                if (intent.action == TodayWidgets.ACTION_WEAR) TodayWidgets.wear(app, intent)
                TodayWidgets.refresh(app)
            } finally { pending.finish() }
        }
    }
}

internal object TodayWidgets {
    const val ACTION_WEAR = "com.familywardrobe.app.widget.WEAR"
    const val ACTION_REFRESH = "com.familywardrobe.app.widget.REFRESH"
    const val ACTION_OPEN = "com.familywardrobe.app.widget.OPEN"
    const val ACTION_DAILY = "com.familywardrobe.app.widget.DAILY"
    const val EXTRA_ACCOUNT = "widget_account"
    const val EXTRA_OUTFIT = "widget_outfit"
    private const val EXTRA_PLAN = "widget_plan"
    private const val EXTRA_WARDROBE = "widget_wardrobe"
    private const val EXTRA_DATE = "widget_date"
    internal const val PICTURE = 288 // ≈330 KB ARGB: well inside the widget binder budget
    private val lock = Mutex()

    /** Empty when the phone has no widget host or none of ours is placed: then nothing is read or drawn. */
    private fun ids(context: Context): IntArray = runCatching {
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
    }.getOrNull() ?: IntArray(0)
    private fun today() = LocalDate.now(ZoneId.systemDefault()).toString()

    /** From the app: the widget's data may have changed (plans, wear, items, active wardrobe, sign-in). */
    fun request(context: Context) {
        val app = context.applicationContext as WardrobeApplication
        if (ids(app).isEmpty()) return
        app.backgroundScope.launch { refresh(app) }
    }
    fun refreshAsync(receiver: BroadcastReceiver, context: Context) {
        val app = context.applicationContext as WardrobeApplication
        val pending = receiver.goAsync()
        app.backgroundScope.launch { try { refresh(app) } finally { pending.finish() } }
    }

    /** The signed-in account that was already opened on this phone, its database and its active wardrobe. */
    private suspend fun <T> account(app: WardrobeApplication, block: suspend (AuthIdentity, Repository, PhotoStore, String) -> T): T? {
        val first = app.authBackend.signedInIdentity() ?: return null
        // A closing session or an account erasure finishes first; signing out always precedes an erasure, so the
        // second look below never opens (or recreates) a database that is being erased.
        app.accountClosures[first.key]?.join()
        val identity = app.authBackend.signedInIdentity()?.takeIf { it.key == first.key } ?: return null
        val location = runCatching { app.accountRegistry.known(identity.key) }.getOrNull() ?: return null
        // Deleted on the server and waiting for its local erasure: shows nothing.
        if (AccountErasure.pending(app)[identity.key] == AccountErasure.Stage.DELETED) return null
        if (!app.getDatabasePath(location.databaseName).exists()) return null
        val context = AccountContext(app, location)
        val stored = context.getSharedPreferences("preferences", Context.MODE_PRIVATE).getString("wardrobe", Repository.DEFAULT_WARDROBE)!!
        val db = Room.databaseBuilder(app, WardrobeDatabase::class.java, location.databaseName)
            .enableMultiInstanceInvalidation().addMigrations(*WardrobeDatabase.ALL_MIGRATIONS).build()
        return try {
            block(identity, Repository(db, AccountWriteLocks.forDatabase(location.databaseName)), PhotoStore(context), stored)
        } finally { db.close() }
    }

    suspend fun refresh(app: WardrobeApplication) = lock.withLock {
        val widgets = ids(app)
        if (widgets.isEmpty()) { cancelMidnight(app); return@withLock }
        val date = today()
        val rendered = runCatching {
            account(app) { identity, repo, photos, stored ->
                val s = repo.snapshot()
                val wardrobe = s.wardrobes.find { it.id == stored }?.id ?: s.wardrobes.firstOrNull()?.id
                val state = if (wardrobe == null) TodayWidgetState.NoPlan(date, "") else TodayWidgetModel.state(s, wardrobe, date)
                views(app, state, identity.key, (state as? TodayWidgetState.Plan)?.let { picture(photos, it.photoIds) })
            }
        }.getOrNull() ?: views(app, TodayWidgetState.SignedOut, "", null)
        AppWidgetManager.getInstance(app).updateAppWidget(widgets, rendered)
        scheduleMidnight(app)
    }

    /**
     * «Одягнув сьогодні»: only for the account, wardrobe and day the widget showed (they travel with the tap, so a
     * wardrobe or account switched meanwhile receives nothing); repeating it changes nothing.
     */
    suspend fun wear(app: WardrobeApplication, intent: Intent) {
        val plan = intent.getStringExtra(EXTRA_PLAN) ?: return
        val wardrobe = intent.getStringExtra(EXTRA_WARDROBE) ?: return
        val date = intent.getStringExtra(EXTRA_DATE) ?: return
        val key = intent.getStringExtra(EXTRA_ACCOUNT) ?: return
        if (date != today()) return // the day changed since the widget was drawn
        runCatching {
            account(app) { identity, repo, _, _ -> if (identity.key == key) repo.markPlanWorn(plan, wardrobe, date) else false }
        }
    }

    /** Up to four item photos, in a 2×2 grid; nothing is kept on disk. */
    internal fun picture(photos: PhotoStore, ids: List<String>): Bitmap? {
        val bitmaps = ids.take(TodayWidgetModel.PHOTOS).mapNotNull { id -> runCatching { decode(photos.currentFile(id).path) }.getOrNull() }
        if (bitmaps.isEmpty()) return null
        val output = Bitmap.createBitmap(PICTURE, PICTURE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val columns = if (bitmaps.size == 1) 1 else 2
        val rows = if (bitmaps.size <= 2) 1 else 2
        val cell = Rect(0, 0, PICTURE / columns, PICTURE / rows)
        bitmaps.forEachIndexed { index, bitmap ->
            val left = (index % columns) * cell.width(); val top = (index / columns) * cell.height()
            val scale = minOf(cell.width().toFloat() / bitmap.width, cell.height().toFloat() / bitmap.height)
            val width = (bitmap.width * scale).toInt(); val height = (bitmap.height * scale).toInt()
            val x = left + (cell.width() - width) / 2; val y = top + (cell.height() - height) / 2
            canvas.drawBitmap(bitmap, null, Rect(x, y, x + width, y + height), null)
            bitmap.recycle()
        }
        return output
    }
    private fun decode(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= PICTURE / 2 && bounds.outHeight / (sample * 2) >= PICTURE / 2) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    internal fun views(context: Context, state: TodayWidgetState, account: String, picture: Bitmap?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        fun open(action: String, code: Int, outfit: String = ""): PendingIntent = PendingIntent.getActivity(context, code,
            Intent(context, MainActivity::class.java).setAction(action).putExtra(EXTRA_ACCOUNT, account).putExtra(EXTRA_OUTFIT, outfit)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val launch = PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setViewVisibility(R.id.widget_picture, View.GONE)
        views.setViewVisibility(R.id.widget_status, View.GONE)
        views.setViewVisibility(R.id.widget_wear, View.GONE)
        views.setViewVisibility(R.id.widget_open, View.VISIBLE)
        when (state) {
            // No private name, picture or date leaks once nobody is signed in.
            TodayWidgetState.SignedOut -> {
                views.setTextViewText(R.id.widget_subtitle, "")
                views.setTextViewText(R.id.widget_name, context.getString(R.string.widget_signed_out))
                views.setTextViewText(R.id.widget_open, context.getString(R.string.widget_open_app))
                views.setOnClickPendingIntent(R.id.widget_open, launch)
                views.setOnClickPendingIntent(R.id.widget_root, launch)
            }
            is TodayWidgetState.NoPlan -> {
                views.setTextViewText(R.id.widget_subtitle, subtitle(context, state.wardrobe, state.date))
                views.setTextViewText(R.id.widget_name, context.getString(R.string.widget_no_plan))
                views.setTextViewText(R.id.widget_open, context.getString(R.string.widget_pick))
                val daily = open(ACTION_DAILY, 2)
                views.setOnClickPendingIntent(R.id.widget_open, daily)
                views.setOnClickPendingIntent(R.id.widget_root, daily)
            }
            is TodayWidgetState.Plan -> {
                views.setTextViewText(R.id.widget_subtitle, subtitle(context, state.wardrobe, state.date))
                views.setTextViewText(R.id.widget_name, state.name)
                if (picture != null) {
                    views.setImageViewBitmap(R.id.widget_picture, picture)
                    views.setContentDescription(R.id.widget_picture, context.getString(R.string.widget_picture, state.name))
                    views.setViewVisibility(R.id.widget_picture, View.VISIBLE)
                }
                val status = listOfNotNull(
                    context.getString(R.string.widget_worn).takeIf { state.worn },
                    context.getString(R.string.widget_unavailable, state.unavailable.joinToString(", ")).takeIf { state.unavailable.isNotEmpty() && !state.worn },
                    context.getString(R.string.widget_ideas).takeIf { state.ideas && !state.worn },
                    context.getString(R.string.widget_no_photos).takeIf { picture == null && state.items > 0 },
                    context.getString(R.string.widget_more, state.more).takeIf { state.more > 0 })
                if (status.isNotEmpty()) {
                    views.setTextViewText(R.id.widget_status, status.joinToString(" · "))
                    views.setViewVisibility(R.id.widget_status, View.VISIBLE)
                }
                views.setTextViewText(R.id.widget_open, context.getString(R.string.widget_open))
                val outfit = open(ACTION_OPEN, 1, state.outfitId)
                views.setOnClickPendingIntent(R.id.widget_open, outfit)
                views.setOnClickPendingIntent(R.id.widget_root, outfit)
                if (state.canWear) {
                    views.setViewVisibility(R.id.widget_wear, View.VISIBLE)
                    views.setOnClickPendingIntent(R.id.widget_wear, PendingIntent.getBroadcast(context, 4,
                        Intent(context, TodayWidgetActions::class.java).setAction(ACTION_WEAR).putExtra(EXTRA_PLAN, state.planId)
                            .putExtra(EXTRA_WARDROBE, state.wardrobeId).putExtra(EXTRA_DATE, state.date).putExtra(EXTRA_ACCOUNT, account),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
        }
        return views
    }
    private fun subtitle(context: Context, wardrobe: String, date: String): String {
        val name = wardrobe.ifBlank { context.getString(R.string.my_wardrobe) }
        val day = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(date)
        return "$name · $day"
    }

    private fun midnight(context: Context) = PendingIntent.getBroadcast(context, 5,
        Intent(context, TodayWidgetActions::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    /** An inexact alarm shortly after local midnight: the widget moves to the new day without exact-alarm rights. */
    private fun scheduleMidnight(context: Context) {
        val zone = ZoneId.systemDefault()
        val next = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusMinutes(1).toInstant().toEpochMilli()
        context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, next, midnight(context))
    }
    fun cancelMidnight(context: Context) { context.getSystemService(AlarmManager::class.java)?.cancel(midnight(context)) }
}
