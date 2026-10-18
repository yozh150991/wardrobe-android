package com.familywardrobe.app

/** A callback bound to one UI snapshot, safe for deferred drawing/semantics reads. */
internal fun familyUploadProgress(uploading: Boolean, bytes: Long, total: Long): (() -> Float)? {
    if (!uploading || total <= 0L) return null
    // Clamp integers before conversion. Unknown totals use indeterminate progress;
    // known totals cannot divide by zero or produce NaN, even at Long extremes.
    val fraction = (bytes.coerceIn(0L, total).toDouble() / total.toDouble()).toFloat()
    return { fraction }
}
