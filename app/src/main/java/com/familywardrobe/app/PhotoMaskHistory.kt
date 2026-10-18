package com.familywardrobe.app

import org.json.JSONArray
import org.json.JSONObject

internal data class MaskHistoryState(val sourceId: String, val strokes: String, val totalSegments: Int)

/** Each command stores one stroke, not a complete copy of every earlier stroke. */
internal fun maskHistoryState(history: PhotoEditHistory, initialId: String, pending: String = ""): MaskHistoryState {
    var source = initialId
    var strokes = JSONArray()
    var total = 0
    val commands = history.appliedSteps.toList() + listOfNotNull(pending.takeIf { it.isNotEmpty() })
    commands.forEach { text ->
        val command = JSONObject(text)
        if (command.has("source")) {
            source = command.getString("source")
            require(PhotoStore.ID.matches(source))
            strokes = JSONArray()
        } else {
            val next = command.getJSONArray("s")
            for (i in 0 until next.length()) { strokes.put(next.getJSONArray(i)); total++ }
        }
    }
    require(total <= 2048)
    return MaskHistoryState(source, strokes.toString(), total)
}

internal fun maskStrokes(text: String): List<PhotoMaskRules.Stroke> {
    val array = JSONArray(text)
    return (0 until array.length()).map { i -> array.getJSONArray(i).let {
        PhotoMaskRules.Stroke(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat(),
            it.getDouble(3).toFloat(), it.getDouble(4).toFloat(), it.getInt(5) != 0,
            it.optDouble(6, 1.0).toFloat(), it.optDouble(7, 1.0).toFloat())
    } }
}
