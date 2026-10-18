package com.familywardrobe.app

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/** Bounded sample cached for this immutable bitmap; transparent margins never count as fabric. */
@Composable
fun rememberPhotoBackdrop(bitmap: Bitmap?, garmentColour: String? = null): Color = remember(bitmap, garmentColour) {
    if (bitmap == null) Color(PhotoBackdropRules.background(intArrayOf(), garmentColour))
    else {
        val samples = IntArray(64 * 64)
        for (y in 0 until 64) for (x in 0 until 64) {
            samples[y * 64 + x] = bitmap.getPixel(
                ((x + .5f) * bitmap.width / 64).toInt().coerceAtMost(bitmap.width - 1),
                ((y + .5f) * bitmap.height / 64).toInt().coerceAtMost(bitmap.height - 1))
        }
        Color(PhotoBackdropRules.background(samples, garmentColour))
    }
}
