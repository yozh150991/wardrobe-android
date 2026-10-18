package com.familywardrobe.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

enum class WardrobePalette(val key: String, val title: Int, val description: Int) {
    OLIVE_TERRA("olive_terra", R.string.palette_olive_terra, R.string.palette_olive_terra_hint),
    PLUM_SAGE("plum_sage", R.string.palette_plum_sage, R.string.palette_plum_sage_hint),
    INDIGO_APRICOT("indigo_apricot", R.string.palette_indigo_apricot, R.string.palette_indigo_apricot_hint),
    TEAL_HONEY("teal_honey", R.string.palette_teal_honey, R.string.palette_teal_honey_hint),
    BURGUNDY_LINEN("burgundy_linen", R.string.palette_burgundy_linen, R.string.palette_burgundy_linen_hint),
    COBALT_CORAL("cobalt_coral", R.string.palette_cobalt_coral, R.string.palette_cobalt_coral_hint),
    BLUE("blue", R.string.palette_blue, R.string.palette_blue_hint),
    SAGE("sage", R.string.palette_sage, R.string.palette_sage_hint),
    TERRACOTTA("terracotta", R.string.palette_terracotta, R.string.palette_terracotta_hint),
    PLUM("plum", R.string.palette_plum, R.string.palette_plum_hint),
    GRAPHITE("graphite", R.string.palette_graphite, R.string.palette_graphite_hint);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: OLIVE_TERRA
    }
}

enum class AppearanceMode(val key: String, val title: Int) {
    SYSTEM("system", R.string.appearance_system),
    LIGHT("light", R.string.appearance_light),
    DARK("dark", R.string.appearance_dark);

    fun usesDark(systemDark: Boolean) = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

data class AppearanceSettings(
    val palette: WardrobePalette = WardrobePalette.OLIVE_TERRA,
    val mode: AppearanceMode = AppearanceMode.SYSTEM
)

// One DataStore per file, shared by every repository instance in this process.
private val Context.wardrobeAppearance by preferencesDataStore(
    name = "wardrobe_appearance_v1",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class AppearanceStore internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.wardrobeAppearance)
    private val paletteKey = stringPreferencesKey("palette")
    private val modeKey = stringPreferencesKey("mode")

    suspend fun load(): AppearanceSettings {
        val values = store.data.first()
        return AppearanceSettings(
            WardrobePalette.fromKey(values[paletteKey]),
            AppearanceMode.fromKey(values[modeKey])
        )
    }

    suspend fun save(settings: AppearanceSettings) {
        store.edit { values ->
            values[paletteKey] = settings.palette.key
            values[modeKey] = settings.mode.key
        }
    }
}
