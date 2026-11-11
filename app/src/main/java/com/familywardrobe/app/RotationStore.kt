package com.familywardrobe.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ROT-02: one postponed item: hidden from «Давно не вдягали» until [until] (exclusive), and why. */
data class RotationSnooze(val garmentId: String, val until: String, val reason: String)

/**
 * ROT-02: the person's own settings for the «Давно не вдягали» card in one wardrobe. Defaults: daily, nothing postponed.
 * [seen]: the last day the card showed each item (a week of history), so the same item does not lead every day.
 */
data class RotationState(val frequency: String = RotationRules.DAILY, val lastShown: String = "", val hiddenOn: String = "",
    val snoozes: Map<String, RotationSnooze> = emptyMap(), val seen: Map<String, String> = emptyMap())

/**
 * Kept in this account's private preferences (like PERS-02 choices): survives a restart, never goes into a shared
 * wardrobe, a family copy, the backup or another account, and is erased with the account.
 */
internal class RotationStore(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(0)
    /** Bumps when what the card shows may change; not when the card merely records that it was shown. */
    val revision = mutable.asStateFlow()
    private fun frequencyKey(wardrobe: String) = "rot.frequency.$wardrobe"
    private fun shownKey(wardrobe: String) = "rot.shown.$wardrobe"
    private fun hiddenKey(wardrobe: String) = "rot.hidden.$wardrobe"
    private fun snoozePrefix(wardrobe: String) = "rot.snooze.$wardrobe."
    private fun seenPrefix(wardrobe: String) = "rot.seen.$wardrobe."

    /** Only postponements still in force and the last week of [seen]. */
    fun read(wardrobe: String, today: String): RotationState {
        val all = prefs.all
        val snoozes = all.filterKeys { it.startsWith(snoozePrefix(wardrobe)) }.mapNotNull { (key, value) ->
            val parts = (value as? String)?.split('|') ?: return@mapNotNull null
            val until = parts.getOrNull(0).orEmpty(); val reason = parts.getOrNull(1)?.takeIf { it in RotationRules.REASONS } ?: RotationRules.LATER
            val id = key.removePrefix(snoozePrefix(wardrobe))
            if (RotationRules.snoozed(until, today)) id to RotationSnooze(id, until, reason) else null
        }.toMap()
        val seen = all.filterKeys { it.startsWith(seenPrefix(wardrobe)) }.mapNotNull { (key, value) ->
            (value as? String)?.let { key.removePrefix(seenPrefix(wardrobe)) to it }
        }.toMap()
        return RotationState(prefs.getString(frequencyKey(wardrobe), null)?.takeIf { it in RotationRules.FREQUENCIES } ?: RotationRules.DAILY,
            prefs.getString(shownKey(wardrobe), null).orEmpty(), prefs.getString(hiddenKey(wardrobe), null).orEmpty(), snoozes, seen)
    }

    fun setFrequency(wardrobe: String, value: String) {
        require(value in RotationRules.FREQUENCIES)
        prefs.edit().apply { if (value == RotationRules.DAILY) remove(frequencyKey(wardrobe)) else putString(frequencyKey(wardrobe), value) }.commit()
        mutable.value++
    }

    /**
     * The card showed [garment] today. Recorded quietly (no [revision] bump): it only orders the following days, and
     * old history is dropped so the preferences stay small.
     */
    fun markShown(wardrobe: String, garment: String, today: String) {
        if (prefs.getString(shownKey(wardrobe), null) == today && prefs.getString(seenPrefix(wardrobe) + garment, null) == today) return
        val stale = prefs.all.filter { (key, value) ->
            key.startsWith(seenPrefix(wardrobe)) && (value as? String)?.let { it != today && !RotationRules.recentlySeen(it, today) } != false
        }.keys
        prefs.edit().apply {
            stale.forEach { remove(it) }
            putString(shownKey(wardrobe), today); putString(seenPrefix(wardrobe) + garment, today)
        }.apply()
    }

    fun hideToday(wardrobe: String, today: String) { prefs.edit().putString(hiddenKey(wardrobe), today).commit(); mutable.value++ }
    fun unhide(wardrobe: String) { prefs.edit().remove(hiddenKey(wardrobe)).commit(); mutable.value++ }

    /** Returns the first day the item may appear again. */
    fun snooze(wardrobe: String, garment: String, days: Int, reason: String, today: String): String {
        require(reason in RotationRules.REASONS)
        val until = RotationRules.until(today, days)
        prefs.edit().putString(snoozePrefix(wardrobe) + garment, "$until|$reason").commit()
        mutable.value++
        return until
    }
    fun restore(wardrobe: String, garment: String) { prefs.edit().remove(snoozePrefix(wardrobe) + garment).commit(); mutable.value++ }

    /** «Скинути всі вподобання»: the card's frequency, postponements and history of this wardrobe. */
    fun clear(wardrobe: String) {
        val keys = prefs.all.keys.filter { it.startsWith(snoozePrefix(wardrobe)) || it.startsWith(seenPrefix(wardrobe)) } +
            listOf(frequencyKey(wardrobe), shownKey(wardrobe), hiddenKey(wardrobe))
        prefs.edit().apply { keys.forEach { remove(it) } }.commit()
        mutable.value++
    }
}
