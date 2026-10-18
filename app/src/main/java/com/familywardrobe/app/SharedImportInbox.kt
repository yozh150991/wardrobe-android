package com.familywardrobe.app

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal data class SharedLink(val id: String, val text: String, val account: String, val received: Long)

/** App-private intake only. A link is bound to its account before becoming an editor draft. */
internal class SharedImportInbox(context: Context) {
    private val prefs = context.getSharedPreferences("shop-share-inbox", Context.MODE_PRIVATE)
    val entries = MutableStateFlow(read())
    private fun read(): List<SharedLink> = runCatching {
        val a = JSONArray(prefs.getString("entries", "[]"))
        (0 until minOf(a.length(), 10)).map { a.getJSONObject(it) }.mapNotNull {
            val id = it.optString("id"); val text = it.optString("text"); val account = it.optString("account")
            if (!PhotoStore.ID.matches(id) || text.isBlank() || text.length > ShopImportRules.MAX_TEXT ||
                (account.isNotBlank() && !AuthRules.validAccountKey(account))) null
            else SharedLink(id, text, account, it.optLong("received"))
        }
    }.getOrDefault(emptyList())
    private fun write(rows: List<SharedLink>, completed: SharedLink? = null): Boolean {
        val array = JSONArray()
        rows.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text).put("account", it.account).put("received", it.received)) }
        val edit = prefs.edit().putString("entries", array.toString())
        completed?.let { edit.putString("last", digest(it.account + "\n" + it.text)).putLong("lastAt", System.currentTimeMillis()) }
        if (!edit.commit()) return false
        entries.value = rows; return true
    }
    @Synchronized fun receive(intent: Intent, account: String): Boolean {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return false
        if (account.isNotBlank() && !AuthRules.validAccountKey(account)) return false
        val text = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim() }.getOrNull().orEmpty()
        if (text.isBlank() || text.length > ShopImportRules.MAX_TEXT) return false
        val rows = entries.value
        if (rows.any { it.text == text && it.account == account }) return true
        // Process recreation can redeliver the original Activity intent after draft handoff.
        val key = digest(account + "\n" + text)
        if (prefs.getString("last", "") == key && System.currentTimeMillis() - prefs.getLong("lastAt", 0) in 0..120_000) return true
        if (rows.size >= 10) return false
        return write(rows + SharedLink(newId(), text, account, System.currentTimeMillis()))
    }
    @Synchronized fun claim(id: String, account: String): SharedLink? {
        require(AuthRules.validAccountKey(account))
        val row = entries.value.find { it.id == id && (it.account.isBlank() || it.account == account) } ?: return null
        val bound = row.copy(account = account)
        return if (write(entries.value.map { if (it.id == id) bound else it })) bound else null
    }
    @Synchronized fun complete(id: String, account: String): Boolean {
        val row = entries.value.find { it.id == id && it.account == account } ?: return true
        return write(entries.value.filterNot { it.id == id }, row)
    }
    private fun digest(text: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
}
