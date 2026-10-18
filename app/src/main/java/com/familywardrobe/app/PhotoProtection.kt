package com.familywardrobe.app

import android.graphics.Bitmap
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

internal fun photoAlphaBitmap(alpha: ByteArray, width: Int, height: Int, checkpoint: () -> Unit): Bitmap {
    require(alpha.size.toLong() == width.toLong() * height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
    try {
        val padded = ByteArray(bitmap.rowBytes * height)
        for (y in 0 until height) { checkpoint(); alpha.copyInto(padded, y * bitmap.rowBytes, y * width, (y + 1) * width) }
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(padded)); checkpoint()
        return bitmap
    } catch (error: Throwable) { bitmap.recycle(); throw error }
}

@Composable
internal fun rememberPhotoProtection(frame: PhotoSessionRules.Frame?): Bitmap? {
    val mask by produceState<Pair<ByteArray, Bitmap>?>(null, frame?.protection) {
        value = if (frame == null) null else withContext(Dispatchers.Default) {
            val work = coroutineContext
            if (frame.protection.none { it.toInt() != 0 }) null
            else frame.protection to photoAlphaBitmap(frame.protection, frame.width, frame.height) { work.ensureActive() }
        }
    }
    return mask?.takeIf { it.first === frame?.protection }?.second
}
