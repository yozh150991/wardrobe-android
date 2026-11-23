package com.familywardrobe.app

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familywardrobe.app.auth.AccountLocation
import com.familywardrobe.app.auth.AccountSession
import com.familywardrobe.app.auth.AuthIdentity
import com.familywardrobe.app.data.wearItems
import com.familywardrobe.app.ui.AppearanceSettings
import com.familywardrobe.app.ui.AppearanceViewModel
import com.familywardrobe.app.ui.WardrobeApp
import com.familywardrobe.app.ui.WardrobeTheme
import com.familywardrobe.app.ui.WardrobeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Locale

/**
 * DEV-04: a screenshot tour for visual review. The real WardrobeApp runs on a seeded account (eight items with drawn
 * photos, an outfit, two wishes, a few wear days) on a phone-sized screen (1080×2340, 420 dpi), and the main screens are
 * captured in Ukrainian, Ukrainian with a large font (1.3), English and Polish.
 *
 * Every picture is halved, saved as JPEG and written to logcat under the tag [TAG] as `WSHOT|name|part|parts|base64`
 * lines; CI collects them (tools/ci_screenshots.py), prints them into the job log and uploads them as an artifact.
 * The tour fails only when a screen cannot be reached; whatever was captured before that is already in the log.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTourTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun ukrainian() = tour("uk", "uk", 1.0f)
    @Test fun ukrainianLargeFont() = tour("uk-large", "uk", 1.3f)
    @Test fun english() = tour("en", "en", 1.0f)
    @Test fun polish() = tour("pl", "pl", 1.0f)

    private fun tour(config: String, language: String, fontScale: Float) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WardrobeApplication
        val identity = AuthIdentity("screens", newId(), "screens@example.test")
        val session = AccountSession(identity, app, AccountLocation(identity.key, false))
        try {
            runBlocking { seed(session, localized(app.resources, language).getString(R.string.my_wardrobe)) }
            val vm = ViewModelProvider(session, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = WardrobeViewModel(app, session) as T
            })[WardrobeViewModel::class.java]
            val appearance = AppearanceViewModel(app)
            compose.setContent {
                Localized(language, fontScale) {
                    WardrobeTheme(AppearanceSettings()) {
                        Box(Modifier.fillMaxSize().testTag(ROOT)) { WardrobeApp(vm, appearance, identity.email, {}, {}) }
                    }
                }
            }
            shot(config, "1-today")
            tap("nav-catalog"); shot(config, "2-wardrobe")
            tap("nav-shopping"); shot(config, "3-want-to-buy")
            tap("nav-outfits"); shot(config, "4-outfits")
            tap("nav-settings"); shot(config, "5-profile")
            tap("nav-catalog")
            // The photo has its own zoom; the item opens from the text under it.
            compose.onAllNodesWithTag("garment-card")[0].performTouchInput { click(Offset(centerX, height - 24f)) }
            shot(config, "6-item")
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            tap("catalog-add"); tap("add-method-manual"); shot(config, "7-new-item")
        } finally {
            session.close(signedOut = false)
        }
    }

    private fun tap(tag: String) {
        compose.waitForIdle()
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun shot(config: String, screen: String) {
        // Generation, photos and the weather run off the main thread; Compose idling does not see them.
        compose.waitForIdle(); SystemClock.sleep(SETTLE_MS); compose.waitForIdle()
        val full = compose.onNodeWithTag(ROOT).captureToImage().asAndroidBitmap()
        val half = Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true)
        val bytes = ByteArrayOutputStream().also { half.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
        val parts = Base64.encodeToString(bytes, Base64.NO_WRAP).chunked(CHUNK)
        val name = "$config-$screen"
        parts.forEachIndexed { index, part -> Log.i(TAG, "WSHOT|$name|${index + 1}|${parts.size}|$part") }
        Log.i(TAG, "WSHOT-DONE|$name|${full.width}x${full.height}|${bytes.size}")
    }

    private suspend fun seed(session: AccountSession, wardrobeName: String) {
        val repo = session.repository
        repo.initialize(wardrobeName)
        val home = repo.dao.allWardrobes().single().id
        fun item(name: String, category: String, color: String, style: String = "casual") =
            Garment(wardrobeId = home, name = name, category = category, color = color, style = style, photoId = newId())
        val shirt = item("Біла сорочка", "top", "white", "business")
        val sweater = item("Светр з високим горлом, довгий рукав", "top", "burgundy")
        val tee = item("Футболка", "top", "navy")
        val jeans = item("Джинси", "bottom", "light_blue")
        val trousers = item("Брюки", "bottom", "beige", "business")
        val sneakers = item("Кросівки", "shoes", "white")
        val boots = item("Черевики", "shoes", "brown")
        val coat = item("Пальто", "outerwear", "camel")
        val items = listOf(shirt, sweater, tee, jeans, trousers, sneakers, boots, coat)
        for (garment in items) {
            val file = session.photos.file(garment.photoId, "png")
            file.parentFile?.mkdirs()
            file.outputStream().use { picture(garment.category, COLOURS.getValue(garment.color)).compress(Bitmap.CompressFormat.PNG, 100, it) }
            repo.save(garment)
        }
        repo.saveOutfit(home, listOf(shirt.id, trousers.id, boots.id), "Офіс")
        repo.saveWish(Wish(wardrobeId = home, name = "Зимові черевики", category = "shoes", kind = "need", budgetMinor = 40000), false, session.photos)
        repo.saveWish(Wish(wardrobeId = home, name = "Светр оверсайз", category = "top", kind = "product", priceMinor = 25900, color = "olive"), false, session.photos)
        val today = LocalDate.now()
        repo.wearItems(home, listOf(jeans.id, tee.id, sneakers.id), today.minusDays(3).toString(), today.toString())
        repo.wearItems(home, listOf(trousers.id, shirt.id, boots.id), today.minusDays(1).toString(), today.toString())
    }

    /** A flat garment silhouette on a transparent background, like a photo after background removal. */
    private fun picture(category: String, colour: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = Path()
        when (category) {
            "top" -> {
                path.moveTo(180f, 80f); path.lineTo(240f, 60f); path.quadTo(300f, 120f, 360f, 60f); path.lineTo(420f, 80f)
                path.lineTo(560f, 200f); path.lineTo(490f, 280f); path.lineTo(440f, 240f); path.lineTo(440f, 660f)
                path.lineTo(160f, 660f); path.lineTo(160f, 240f); path.lineTo(110f, 280f); path.lineTo(40f, 200f); path.close()
            }
            "bottom" -> {
                path.moveTo(170f, 40f); path.lineTo(430f, 40f); path.lineTo(470f, 690f); path.lineTo(330f, 690f)
                path.lineTo(300f, 260f); path.lineTo(270f, 690f); path.lineTo(130f, 690f); path.close()
            }
            "shoes" -> {
                path.addRoundRect(RectF(60f, 380f, 540f, 560f), 90f, 90f, Path.Direction.CW)
                path.addRoundRect(RectF(60f, 300f, 260f, 480f), 60f, 60f, Path.Direction.CW)
            }
            else -> {
                path.moveTo(200f, 40f); path.lineTo(400f, 40f); path.lineTo(540f, 160f); path.lineTo(560f, 700f)
                path.lineTo(40f, 700f); path.lineTo(60f, 160f); path.close()
            }
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour })
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000; style = Paint.Style.STROKE; strokeWidth = 6f })
        return bitmap
    }

    companion object {
        const val TAG = "WardrobeShots"
        private const val ROOT = "screenshot-root"
        private const val CHUNK = 3000            // a logcat line holds about 4 000 bytes
        private const val SETTLE_MS = 1500L
        private val COLOURS = mapOf(
            "white" to 0xFFF4F2EC.toInt(), "burgundy" to 0xFF6D1F2B.toInt(), "navy" to 0xFF1F2A44.toInt(),
            "light_blue" to 0xFF8FB3D9.toInt(), "beige" to 0xFFD9C7A7.toInt(), "brown" to 0xFF6B4A2F.toInt(),
            "camel" to 0xFFC19A6B.toInt())

        /** A phone-sized screen for every test of this class (the emulator's default is smaller); restored afterwards. */
        @BeforeClass @JvmStatic fun phoneScreen() { shell("wm size 1080x2340"); shell("wm density 420") }
        @AfterClass @JvmStatic fun restoreScreen() { shell("wm size reset"); shell("wm density reset") }

        private fun shell(command: String): String {
            val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
                .also { Log.i(TAG, "shell $command → ${it.trim()}") }
        }

        private fun localized(base: Resources, language: String): Resources {
            val configuration = Configuration(base.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            @Suppress("DEPRECATION")
            return Resources(base.assets, base.displayMetrics, configuration)
        }
    }
}

/** The app as a phone in [language] with the system font scale [fontScale] would show it. */
@Composable
private fun Localized(language: String, fontScale: Float, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val configuration = remember(language, fontScale) {
        Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)); this.fontScale = fontScale }
    }
    val context = remember(configuration) {
        val resources = base.createConfigurationContext(configuration).resources
        object : ContextWrapper(base) { override fun getResources(): Resources = resources }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration,
        LocalDensity provides Density(density.density, fontScale)) { content() }
}
