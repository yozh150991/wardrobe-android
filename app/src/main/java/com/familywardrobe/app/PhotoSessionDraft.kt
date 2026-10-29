package com.familywardrobe.app

import org.json.JSONArray
import org.json.JSONObject

/** Commands only, embedded in the existing account-private item draft. No bitmap copies. */
internal data class PhotoSessionDraft(val photoId: String, val beforeId: String, val history: PhotoEditHistory, val tool: String) {
    fun json(): JSONObject = JSONObject().put("version", 1).put("photo", photoId).put("before", beforeId)
        .put("tool", tool).put("history", JSONObject().put("steps", JSONArray(history.steps.toList())).put("cursor", history.cursor))

    companion object {
        const val FIELD = "photoEdit"
        private val tools = setOf("crop", "rotate", "background", "precision")
        fun decode(value: JSONObject, photo: String): PhotoSessionDraft {
            require(value.toString().length <= PhotoEditHistory.MAX_CHARACTERS * 3 + 8192)
            require(value.getInt("version") == 1 && value.getString("photo") == photo && PhotoStore.ID.matches(photo))
            val before = value.getString("before"); require(PhotoStore.ID.matches(before))
            val tool = value.getString("tool"); require(tool in tools)
            val saved = value.getJSONObject("history"); val steps = saved.getJSONArray("steps")
            require(steps.length() <= PhotoEditHistory.MAX_STEPS)
            val history = PhotoEditHistory(Array(steps.length()) { steps.getString(it) }, saved.getInt("cursor"))
            // Validate the redo branch as well as the visible prefix before enabling the editor.
            photoSessionPlan(PhotoEditHistory(history.steps, history.steps.size), photo)
            return PhotoSessionDraft(photo, before, history, tool)
        }
        fun owns(draft: JSONObject?, owner: String, photo: String): Boolean {
            val fields = draft?.optJSONObject("fields") ?: return false
            return owner == draft.optString("wardrobe") + ":" + fields.optString("id") && fields.optString("photo") == photo
        }
        /**
         * Form autosave must retain commands, but never carry them into another item/photo.
         * The command object is shared, not copied: published drafts are treated as immutable, and
         * copying up to hundreds of KB of history on every typed character froze the UI (audit A-01).
         */
        fun merge(previous: JSONObject?, next: JSONObject): JSONObject {
            val fields = next.getJSONObject("fields")
            fields.remove(FIELD)
            val oldFields = previous?.optJSONObject("fields") ?: return next
            if (listOf("kind", "wardrobe", "original").all { previous.optString(it) == next.optString(it) } &&
                oldFields.optString("id") == fields.optString("id") && oldFields.optString("photo") == fields.optString("photo")) {
                oldFields.optJSONObject(FIELD)?.let { fields.put(FIELD, it) }
            }
            return next
        }
        /** New draft object with the command session replaced or removed; other values are shared, not copied. */
        fun withSession(draft: JSONObject, session: JSONObject?): JSONObject {
            val next = JSONObject(draft, draft.keys().asSequence().filter { it != "fields" }.toList().toTypedArray())
            val old = draft.getJSONObject("fields")
            val fields = JSONObject(old, old.keys().asSequence().filter { it != FIELD }.toList().toTypedArray())
            if (session != null) fields.put(FIELD, session)
            return next.put("fields", fields)
        }
        /** Cheap identity of everything except the command session (compared on every autosave). */
        fun formKey(draft: JSONObject?): String? {
            val fields = draft?.optJSONObject("fields") ?: return null
            val plain = JSONObject(fields, fields.keys().asSequence().filter { it != FIELD }.toList().toTypedArray())
            return listOf(draft.optString("kind"), draft.optString("wardrobe"), draft.optString("original"), plain.toString()).joinToString("\u0000")
        }
        private val cacheLock = Any()
        private var cachedSession: JSONObject? = null
        private var cachedSteps = -1
        private var cachedSources: Set<String> = emptySet()
        /** Retain all reset sources, including steps currently in the redo branch. */
        fun referencedPhotos(fields: JSONObject?): Set<String> = buildSet {
            fun addId(id: String?) { if (id != null && PhotoStore.ID.matches(id)) add(id) }
            addId(fields?.optString("photo"))
            val value = fields?.optJSONObject(FIELD) ?: return@buildSet
            addId(value.optString("photo")); addId(value.optString("before"))
            val steps = value.optJSONObject("history")?.optJSONArray("steps") ?: return@buildSet
            addAll(sources(value, steps))
        }
        /** Parsing every history step on each autosave is the expensive part; reuse it for the same session object. */
        private fun sources(value: JSONObject, steps: org.json.JSONArray): Set<String> = synchronized(cacheLock) {
            if (cachedSession === value && cachedSteps == steps.length()) return cachedSources
            val result = buildSet {
                for (i in 0 until minOf(steps.length(), PhotoEditHistory.MAX_STEPS)) {
                    runCatching { JSONObject(steps.getString(i)).optString("source") }.getOrNull()
                        ?.takeIf(PhotoStore.ID::matches)?.let(::add)
                }
            }
            cachedSession = value; cachedSteps = steps.length(); cachedSources = result
            result
        }
    }
}
