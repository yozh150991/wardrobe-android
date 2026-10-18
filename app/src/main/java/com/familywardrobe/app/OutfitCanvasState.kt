package com.familywardrobe.app

import org.json.JSONArray
import org.json.JSONObject

data class CanvasPlacement(val key: String, val x: Float, val y: Float, val scale: Float, val z: Int)

object OutfitCanvasState {
    val backgrounds = listOf("paper", "sand", "sage", "rose", "slate")
    fun read(text: String): List<CanvasPlacement> {
        if (text.isBlank()) return emptyList()
        require(text.length <= 12_000)
        val array = JSONArray(text); require(array.length() <= WardrobeRules.MAX_PARTS)
        val result = (0 until array.length()).map { index -> array.getJSONObject(index).let { p ->
            CanvasPlacement(p.getString("key"), p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), p.getDouble("scale").toFloat(), p.getInt("z"))
        } }
        require(result.map { it.key }.distinct().size == result.size)
        result.forEach { p ->
            require(p.key.length <= 60 && p.key.matches(Regex("(owned|wish):[a-f0-9-]{36}|idea:(${Matching.categories.joinToString("|")})")))
            require(p.x.isFinite() && p.y.isFinite() && p.scale.isFinite() && p.x in 0f..1f && p.y in 0f..1f && p.scale in .6f..1.8f && p.z in 0 until WardrobeRules.MAX_PARTS)
        }
        return result
    }
    fun encode(parts: List<CanvasPlacement>): String = JSONArray().apply { parts.forEach { p ->
        put(JSONObject().put("key", p.key).put("x", p.x).put("y", p.y).put("scale", p.scale).put("z", p.z))
    } }.toString().also { read(it) }
    fun reconcile(text: String, keys: Collection<String>): String = encode(read(text).filter { it.key in keys })
    fun remap(text: String, mapping: (String) -> String): String = encode(read(text).map { it.copy(key = mapping(it.key)) })
    fun placements(pieces: List<LookPiece>, text: String): List<CanvasPlacement> {
        val saved = runCatching { read(text) }.getOrDefault(emptyList()).associateBy { it.key }
        return pieces.mapIndexed { index, p ->
            val old = saved[p.key]
            val point = if (old == null) CanvasRules.initial(p.category, index, pieces.size)
                else CanvasRules.constrain(p.category, old.x, old.y, old.scale)
            CanvasPlacement(p.key, point[0], point[1], point[2], old?.z ?: index)
        }
    }
    fun change(pieces: List<LookPiece>, text: String, key: String, dx: Float = 0f, dy: Float = 0f, zoom: Float = 1f, front: Boolean = false): String {
        val parts = placements(pieces, text); val p = pieces.find { it.key == key } ?: return text
        val old = parts.single { it.key == key }; val point = CanvasRules.constrain(p.category, old.x + dx, old.y + dy, old.scale * zoom)
        val changed = parts.map { if (it.key == key) it.copy(x = point[0], y = point[1], scale = point[2]) else it }
        return encode(if (!front) changed else (changed.filter { it.key != key }.sortedWith(compareBy<CanvasPlacement> { it.z }.thenBy { it.key }) + changed.single { it.key == key }).mapIndexed { index, placement -> placement.copy(z = index) })
    }
}
