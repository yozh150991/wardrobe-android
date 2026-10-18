package com.familywardrobe.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

internal data class PhotoSessionPlan(val sourceId: String, val actions: List<PhotoSessionRules.Action>, val segments: Int)
internal data class PhotoSessionImage(val frame: PhotoSessionRules.Frame, val bitmap: Bitmap, val owner: PhotoStore, val sourceId: String)

/** One history for every tool; a source reset is a reversible command in that history. */
internal fun photoSessionPlan(history: PhotoEditHistory, initialId: String): PhotoSessionPlan {
    var sourceId = initialId
    var segments = 0
    val actions = mutableListOf<PhotoSessionRules.Action>()
    history.appliedSteps.forEach { text ->
        val command = JSONObject(text)
        when {
            command.has("source") -> { sourceId = command.getString("source"); require(PhotoStore.ID.matches(sourceId)); actions.clear() }
            command.has("s") -> {
                val strokes = maskStrokes(command.getJSONArray("s").toString())
                segments += strokes.size
                actions += PhotoSessionRules.Action.stroke(strokes)
            }
            command.has("crop") -> command.getJSONArray("crop").let {
                require(it.length() == 4)
                actions += PhotoSessionRules.Action.crop(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat(), it.getDouble(3).toFloat())
            }
            command.has("rotate") -> actions += PhotoSessionRules.Action.rotate(command.getInt("rotate"))
            command.has("region") -> command.getJSONArray("region").let {
                require(it.length() == 3)
                actions += PhotoSessionRules.Action.region(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat())
            }
            command.has("edges") -> command.getJSONArray("edges").let {
                require(it.length() == 2)
                actions += PhotoSessionRules.Action.edges(it.getInt(0), it.getInt(1))
            }
            command.has("polygon") -> command.getJSONArray("polygon").let {
                require(it.length() in 6..PhotoPrecisionRules.MAX_POINTS * 2)
                actions += PhotoSessionRules.Action.polygon(FloatArray(it.length()) { index -> it.getDouble(index).toFloat() }, command.getString("mode"))
            }
            command.optBoolean("clearProtection", false) -> actions += PhotoSessionRules.Action.clearProtection()
            else -> error("Invalid photo edit")
        }
    }
    require(PhotoStore.ID.matches(sourceId) && segments <= 2048)
    return PhotoSessionPlan(sourceId, actions, segments)
}

/** The preview and saved PNG use this exact same replay, including restoration pixels. */
internal suspend fun renderPhotoSession(photos: PhotoStore, initialId: String, history: PhotoEditHistory,
    previousHistory: PhotoEditHistory? = null, previousImage: PhotoSessionImage? = null): PhotoSessionImage {
    val plan = photoSessionPlan(history, initialId)
    val applied = history.appliedSteps
    val previous = previousHistory?.appliedSteps
    if (previousImage != null && previousImage.owner === photos && previousImage.sourceId == plan.sourceId &&
        previous != null && applied.size == previous.size + 1 &&
        previous.indices.all { previous[it] == applied[it] } && !JSONObject(applied.last()).has("source")) {
        return withContext(Dispatchers.Default) {
            val work = coroutineContext
            val frame = PhotoSessionRules.apply(previousImage.frame, plan.actions.last()) { work.ensureActive() }
            work.ensureActive()
            PhotoSessionImage(frame, Bitmap.createBitmap(frame.pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888), photos, plan.sourceId)
        }
    }
    val initial = withContext(Dispatchers.IO) {
        val file = photos.currentFile(plan.sourceId)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path))
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            Triple(pixels, bitmap.width, bitmap.height)
        } finally { bitmap.recycle() }
    }
    return withContext(Dispatchers.Default) {
        val work = coroutineContext
        val frame = PhotoSessionRules.replay(initial.first, initial.second, initial.third, plan.actions) { work.ensureActive() }
        work.ensureActive()
        PhotoSessionImage(frame, Bitmap.createBitmap(frame.pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888), photos, plan.sourceId)
    }
}
