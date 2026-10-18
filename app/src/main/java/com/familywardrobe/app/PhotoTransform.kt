package com.familywardrobe.app

import android.graphics.Bitmap
import android.graphics.Matrix

object PhotoTransform {
    fun rotate(source: Bitmap, quarterTurns: Int): Bitmap {
        val turns = PhotoGeometry.turns(quarterTurns)
        if (turns == 0) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height,
            Matrix().apply { setRotate(turns * 90f) }, false)
    }
}
