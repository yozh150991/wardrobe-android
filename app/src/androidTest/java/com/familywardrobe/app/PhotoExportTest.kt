package com.familywardrobe.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.familywardrobe.app.auth.AccountContext
import com.familywardrobe.app.auth.AccountLocation
import com.familywardrobe.app.auth.AuthIdentity
import com.familywardrobe.app.photo.PhotoExportInfo
import com.familywardrobe.app.photo.PhotoStore
import com.familywardrobe.app.rules.PhotoExportRules
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** PHOTO-17 (0.14.46) on a device: PNG keeps transparency, JPEG gets a real background, sizes shrink only, no originals exposed. */
@RunWith(AndroidJUnit4::class)
class PhotoExportTest {
    @Test fun exportsAreNewFilesWithRealBackgrounds() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = AccountContext(base, AccountLocation(AuthIdentity("export-tests", newId(), "owner@example.test").key, false))
        val photos = PhotoStore(context)
        val id = newId()
        // A 1600×1200 cut-out: a red garment in the middle, transparent around it.
        val cutout = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (x in 400 until 1200) for (y in 300 until 900) setPixel(x, y, Color.rgb(200, 20, 30))
        }
        val saved = photos.file(id, "png")
        saved.outputStream().use { cutout.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val before = saved.readBytes()
        try {
            val info = photos.exportInfo(id)!!
            assertEquals(PhotoExportInfo(1600, 1200, true), info)

            val png = photos.export(id, PhotoExportRules.PNG, 0, "Червона сукня")
            val pngFile = exported(context, png.lastPathSegment!!)
            assertEquals("червона-сукня.png", pngFile.name)
            val pngBitmap = BitmapFactory.decodeFile(pngFile.path)!!
            assertEquals(1600, pngBitmap.width); assertEquals(1200, pngBitmap.height)
            assertEquals(0, Color.alpha(pngBitmap.getPixel(10, 10)))
            assertEquals(Color.rgb(200, 20, 30), pngBitmap.getPixel(800, 600))

            val jpeg = photos.export(id, PhotoExportRules.CREAM, 720, "Червона сукня")
            val jpegFile = exported(context, jpeg.lastPathSegment!!)
            assertEquals("червона-сукня.jpg", jpegFile.name)
            val jpegBitmap = BitmapFactory.decodeFile(jpegFile.path)!!
            assertEquals(720, jpegBitmap.width); assertEquals(540, jpegBitmap.height)
            near(PhotoExportRules.CREAM_ARGB, jpegBitmap.getPixel(5, 5))
            near(Color.rgb(200, 20, 30), jpegBitmap.getPixel(360, 270))
            assertEquals(255, Color.alpha(jpegBitmap.getPixel(5, 5)))

            val white = photos.export(id, PhotoExportRules.WHITE, 2048, "")
            val whiteBitmap = BitmapFactory.decodeFile(exported(context, white.lastPathSegment!!).path)!!
            assertEquals("never enlarged", 1600, whiteBitmap.width)
            near(Color.WHITE, whiteBitmap.getPixel(5, 5))

            // The saved photo is untouched and the shared files are under cache/share/ only.
            assertArrayEquals(before, saved.readBytes())
            assertTrue(png.toString().contains("/share/") && png.authority == "${base.packageName}.files")
            assertNotEquals(png, photos.export(id, PhotoExportRules.PNG, 0, "Червона сукня"))
            // The camera's file is reachable through the same provider from an account context (it used to fail).
            val capture = photos.newCapture()
            assertTrue(capture.toString().contains("/camera/") && capture.authority == "${base.packageName}.files")
        } finally { photos.remove(setOf(id)); File(context.applicationContext.cacheDir, "share").deleteRecursively(); context.filesDir.deleteRecursively() }
    }

    private fun exported(context: AccountContext, name: String): File =
        File(context.applicationContext.cacheDir, "share").walkTopDown().filter { it.isFile && it.name == name }.maxBy { it.lastModified() }

    /** JPEG is lossy: each channel within a few steps. */
    private fun near(expected: Int, actual: Int) {
        for (shift in listOf(16, 8, 0)) assertTrue("${Integer.toHexString(expected)} vs ${Integer.toHexString(actual)}",
            kotlin.math.abs((expected shr shift and 255) - (actual shr shift and 255)) <= 6)
    }
}
