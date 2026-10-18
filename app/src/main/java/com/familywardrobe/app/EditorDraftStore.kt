package com.familywardrobe.app
import android.content.Context
import org.json.JSONObject

/** Receives AccountContext; never touches authentication credentials or another account's preferences. */
class EditorDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", 0)
    fun read(): JSONObject? = runCatching {
        prefs.getString("editorDraft", null)?.let(::JSONObject)?.takeIf {
            it.optString("kind") in setOf("garment", "wish", "look", "purchase", "import", "shop") && PhotoStore.ID.matches(it.optString("wardrobe")) &&
                it.optJSONObject("fields") != null
        }
    }.getOrNull()
    fun write(value: JSONObject): Boolean {
        // The source cleanup ledger and command draft are committed together.
        val photos = prefs.getStringSet("draftPhotos", emptySet()).orEmpty() +
            PhotoSessionDraft.referencedPhotos(read()?.optJSONObject("fields")) +
            PhotoSessionDraft.referencedPhotos(value.optJSONObject("fields"))
        return prefs.edit().putString("editorDraft", value.toString()).putStringSet("draftPhotos", photos).commit()
    }
    fun clear(): Boolean = prefs.edit().remove("editorDraft").commit()
}
