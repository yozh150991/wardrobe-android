package com.familywardrobe.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PERS-02: the person's own choices for one wardrobe. Missing = [PreferenceProfile.AUTO] (derived from their reasons).
 * They live in this account's private preferences: never in a shared wardrobe, a family copy or another account.
 */
data class PersonalChoices(val styles: Map<String, String> = emptyMap(), val warmth: String = PreferenceProfile.AUTO) {
    fun style(occasion: String): String = styles[occasion] ?: PreferenceProfile.AUTO
}

internal class PersonalChoiceStore(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(0)
    /** Bumps on every change, so screens re-read the choices. */
    val revision = mutable.asStateFlow()
    private fun styleKey(wardrobe: String, occasion: String) = "pref.style.$wardrobe.$occasion"
    private fun warmthKey(wardrobe: String) = "pref.warmth.$wardrobe"

    fun read(wardrobe: String): PersonalChoices = PersonalChoices(
        LookRules.OCCASIONS.mapNotNull { occasion ->
            prefs.getString(styleKey(wardrobe, occasion), null)?.takeIf { it in PreferenceProfile.STYLES }?.let { occasion to it }
        }.toMap(),
        prefs.getString(warmthKey(wardrobe), null)?.takeIf { it in PreferenceProfile.WARMTH } ?: PreferenceProfile.AUTO)

    fun setStyle(wardrobe: String, occasion: String, value: String) {
        require(occasion in LookRules.OCCASIONS && (value == PreferenceProfile.AUTO || value in PreferenceProfile.STYLES))
        prefs.edit().apply { if (value == PreferenceProfile.AUTO) remove(styleKey(wardrobe, occasion)) else putString(styleKey(wardrobe, occasion), value) }.commit()
        mutable.value++
    }
    fun setWarmth(wardrobe: String, value: String) {
        require(value == PreferenceProfile.AUTO || value in PreferenceProfile.WARMTH)
        prefs.edit().apply { if (value == PreferenceProfile.AUTO) remove(warmthKey(wardrobe)) else putString(warmthKey(wardrobe), value) }.commit()
        mutable.value++
    }
    fun clear(wardrobe: String) {
        prefs.edit().apply {
            LookRules.OCCASIONS.forEach { remove(styleKey(wardrobe, it)) }
            remove(warmthKey(wardrobe))
        }.commit()
        mutable.value++
    }
}
