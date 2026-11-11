package com.familywardrobe.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PERS-02: the person's own choices for one wardrobe (PERS-03 adds their colours). Missing = [PreferenceProfile.AUTO] (derived from their reasons).
 * They live in this account's private preferences: never in a shared wardrobe, a family copy or another account.
 */
data class PersonalChoices(val styles: Map<String, String> = emptyMap(), val warmth: String = PreferenceProfile.AUTO,
    /** PERS-03: base colours the person likes wearing, and ones they would rather wear less; never both. */
    val liked: Set<String> = emptySet(), val avoided: Set<String> = emptySet()) {
    fun style(occasion: String): String = styles[occasion] ?: PreferenceProfile.AUTO
    fun palette(colour: String): String = when (colour) { in avoided -> AVOIDED; in liked -> LIKED; else -> NONE }
    companion object {
        const val LIKED = "liked"; const val AVOIDED = "avoided"; const val NONE = "none"
        /** Every base colour except «Інший / різнокольоровий», in the order of the colour lists. */
        val PALETTE: List<String> = Matching.colors.filter { it != "other" }
    }
}

internal class PersonalChoiceStore(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(0)
    /** Bumps on every change, so screens re-read the choices. */
    val revision = mutable.asStateFlow()
    private fun styleKey(wardrobe: String, occasion: String) = "pref.style.$wardrobe.$occasion"
    private fun warmthKey(wardrobe: String) = "pref.warmth.$wardrobe"
    private fun paletteKey(wardrobe: String, kind: String) = "pref.palette.$wardrobe.$kind"
    private fun colours(wardrobe: String, kind: String): Set<String> =
        prefs.getString(paletteKey(wardrobe, kind), null).orEmpty().split(',').filter { it in PersonalChoices.PALETTE }.toSet()

    fun read(wardrobe: String): PersonalChoices = PersonalChoices(
        LookRules.OCCASIONS.mapNotNull { occasion ->
            prefs.getString(styleKey(wardrobe, occasion), null)?.takeIf { it in PreferenceProfile.STYLES }?.let { occasion to it }
        }.toMap(),
        prefs.getString(warmthKey(wardrobe), null)?.takeIf { it in PreferenceProfile.WARMTH } ?: PreferenceProfile.AUTO,
        colours(wardrobe, PersonalChoices.LIKED) - colours(wardrobe, PersonalChoices.AVOIDED), colours(wardrobe, PersonalChoices.AVOIDED))

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
    /** PERS-03: [colour] becomes liked, worn less or neither; it leaves the other list. */
    fun setPalette(wardrobe: String, colour: String, value: String) {
        require(colour in PersonalChoices.PALETTE && value in listOf(PersonalChoices.LIKED, PersonalChoices.AVOIDED, PersonalChoices.NONE))
        val current = read(wardrobe)
        val liked = if (value == PersonalChoices.LIKED) current.liked + colour else current.liked - colour
        val avoided = if (value == PersonalChoices.AVOIDED) current.avoided + colour else current.avoided - colour
        prefs.edit().apply {
            listOf(PersonalChoices.LIKED to liked, PersonalChoices.AVOIDED to avoided).forEach { (kind, set) ->
                val key = paletteKey(wardrobe, kind)
                if (set.isEmpty()) remove(key) else putString(key, PersonalChoices.PALETTE.filter { it in set }.joinToString(","))
            }
        }.commit()
        mutable.value++
    }
    fun clear(wardrobe: String) {
        prefs.edit().apply {
            LookRules.OCCASIONS.forEach { remove(styleKey(wardrobe, it)) }
            remove(warmthKey(wardrobe))
            remove(paletteKey(wardrobe, PersonalChoices.LIKED)); remove(paletteKey(wardrobe, PersonalChoices.AVOIDED))
        }.commit()
        mutable.value++
    }
}
