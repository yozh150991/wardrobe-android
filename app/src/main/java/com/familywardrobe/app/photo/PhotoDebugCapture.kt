package com.familywardrobe.app.photo

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.nio.FloatBuffer

/** PHOTO-19: debug builds keep what the cutout rules were given, so the rules can be tuned on real masks.
 *
 * For each analysed photo three PNG files go to the app's external files folder `photo-debug/`
 * (`adb pull /sdcard/Android/data/<package>/files/photo-debug`):
 * `<n>-source.png` (the pixels the rules saw), `<n>-mask.png` (the model's confidence, saturated to 0..1 and
 * written as 16 bits: red = high byte, green = low byte) and `<n>-cutout.png` (the result, when there is one).
 * Only the newest [KEEP] captures stay. A release build never writes anything: [enabled] is false there.
 */
internal object PhotoDebugCapture {
    private const val TAG = "WardrobePhoto"
    const val FOLDER = "photo-debug"
    const val KEEP = 8

    fun enabled(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Never throws: a failed capture must not change the result of the analysis. */
    fun save(context: Context, source: IntArray, width: Int, height: Int, mask: FloatBuffer, cutout: IntArray?, stronger: Boolean) {
        if (!enabled(context)) return
        try {
            val folder = File(context.applicationContext.getExternalFilesDir(null) ?: return, FOLDER).apply { mkdirs() }
            val name = System.currentTimeMillis().toString() + if (stronger) "-stronger" else ""
            write(File(folder, "$name-source.png"), source, width, height)
            write(File(folder, "$name-mask.png"), encode(mask, width * height), width, height)
            if (cutout != null) write(File(folder, "$name-cutout.png"), cutout, width, height)
            folder.listFiles()?.groupBy { it.name.substringBefore('-') }?.toSortedMap()?.values?.toList()?.dropLast(KEEP)
                ?.forEach { files -> files.forEach(File::delete) }
            Log.i(TAG, "Debug capture $name (${width}x$height)")
        } catch (e: Exception) { Log.w(TAG, "Debug capture failed", e) }
    }

    /** Reads with absolute indexes, so the SDK buffer position is left as it was. */
    internal fun encode(mask: FloatBuffer, size: Int): IntArray {
        val start = mask.position()
        return IntArray(size) { i ->
            val value = mask.get(start + i)
            val level = if (value.isFinite()) (value.coerceIn(0f, 1f) * 65535f + .5f).toInt() else 0
            (0xFF shl 24) or ((level ushr 8) shl 16) or ((level and 0xFF) shl 8)
        }
    }

    private fun write(file: File, pixels: IntArray, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
    }
}
