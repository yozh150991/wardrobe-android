package com.familywardrobe.app

import android.content.Context
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.FileNotFoundException
import java.io.IOException
import org.json.JSONObject

enum class PhotoFailureKind { NONE, ACCESS, MISSING, UNREADABLE, TOO_LARGE, SPACE, IO, UNKNOWN }
class UnreadablePhotoException : IllegalArgumentException("Photo cannot be decoded")
class PhotoTooLargeException : IllegalArgumentException("File exceeds limit")

/** Report only what the failing operation establishes; never infer network or disk capacity. */
fun photoFailure(photoId: String, error: Exception): PhotoAnalysis {
    val noSpace = generateSequence<Throwable>(error) { it.cause }.take(12).any {
        it is android.system.ErrnoException && it.errno == android.system.OsConstants.ENOSPC
    }
    return PhotoAnalysis(photoId,
    cutoutStatus = if (error is TimeoutCancellationException) CutoutStatus.TIMED_OUT else CutoutStatus.FAILED,
    failure = if (noSpace) PhotoFailureKind.SPACE else when (error) {
        is TimeoutCancellationException -> PhotoFailureKind.NONE
        is SecurityException -> PhotoFailureKind.ACCESS
        is FileNotFoundException -> PhotoFailureKind.MISSING
        is UnreadablePhotoException -> PhotoFailureKind.UNREADABLE
        is PhotoTooLargeException -> PhotoFailureKind.TOO_LARGE
        is IOException -> PhotoFailureKind.IO
        else -> PhotoFailureKind.UNKNOWN
    }, labelFailed = true)
}

fun photoFailureMessage(kind: PhotoFailureKind): Int = when (kind) {
    PhotoFailureKind.ACCESS -> R.string.photo_error_access
    PhotoFailureKind.MISSING -> R.string.photo_error_missing
    PhotoFailureKind.UNREADABLE -> R.string.photo_error_unreadable
    PhotoFailureKind.TOO_LARGE -> R.string.photo_error_large
    PhotoFailureKind.SPACE -> R.string.failure_space
    PhotoFailureKind.IO -> R.string.photo_error_io
    else -> R.string.photo_error_unknown
}

/** Small account-private diagnostics for unfinished imports, never source URLs or exception text.
 * Queue and photo ownership remain in Room. Old queues simply have no recorded report.
 */
class PhotoDiagnosticStore(context: Context) {
    private val prefs = context.getSharedPreferences("import-photo-diagnostics", Context.MODE_PRIVATE)
    val reports = MutableStateFlow(read())

    private fun read(): Map<String, PhotoAnalysis> = prefs.all.mapNotNull { (id, raw) ->
        if (!PhotoStore.ID.matches(id)) return@mapNotNull null
        runCatching {
            val value = JSONObject(raw as String)
            val photo = value.getString("photo")
            require(photo.isEmpty() || PhotoStore.ID.matches(photo))
            id to PhotoAnalysis(photo,
                category = value.optString("category").takeIf { it in Matching.categories },
                color = value.optString("color").takeIf { it in Matching.colors },
                cutoutStatus = CutoutStatus.valueOf(value.getString("cutout")),
                labelFailed = value.optBoolean("labels"),
                failure = PhotoFailureKind.valueOf(value.getString("failure")))
        }.getOrNull()
    }.toMap()

    fun record(id: String, result: PhotoAnalysis) {
        require(PhotoStore.ID.matches(id) && (result.sourceId.isEmpty() || PhotoStore.ID.matches(result.sourceId)))
        val safe = result.copy(cutoutPath = null)
        val value = JSONObject().put("photo", safe.sourceId).put("category", safe.category.orEmpty())
            .put("color", safe.color.orEmpty()).put("cutout", safe.cutoutStatus.name)
            .put("labels", safe.labelFailed).put("failure", safe.failure.name)
        prefs.edit().putString(id, value.toString()).commit()
        reports.value = reports.value + (id to safe)
    }

    fun remove(id: String) { prefs.edit().remove(id).commit(); reports.value = reports.value - id }
    fun retain(ids: Set<String>) {
        val removed = prefs.all.keys - ids
        if (removed.isNotEmpty()) {
            prefs.edit().also { edit -> removed.forEach { edit.remove(it) } }.commit()
            reports.value = reports.value.filterKeys { it in ids }
        }
    }
}
