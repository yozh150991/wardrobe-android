package com.familywardrobe.app.family

import com.familywardrobe.app.Snapshot
import com.familywardrobe.app.outfits.Matching
import com.familywardrobe.app.rules.ColorRules

/**
 * COL-02: colours added in 0.14.32 reach a shared wardrobe only when its server knows them ("colors2" feature, the
 * family functions deployed with this release). For an older server every colour is replaced by its nearest older
 * base ([ColorRules.legacy]) before anything is sent, so the server and older apps can still read the wardrobe.
 * The private wardrobe keeps the exact colour.
 */
internal object FamilyColors {
    const val FEATURE = "colors2"
    fun supported(features: Set<String>) = FEATURE in features
    fun supported(remote: FamilyRemote) = supported(remote.features)

    /** The colours [supported] servers accept; the shared «new item» form offers only these. */
    fun choices(supported: Boolean): List<String> = if (supported) Matching.colors else ColorRules.LEGACY

    /** Garments and their custom shades change together, so a shade's base always equals its garments' colour. */
    fun forServer(s: Snapshot, supported: Boolean): Snapshot = if (supported) s else s.copy(
        garments = s.garments.map { it.copy(color = ColorRules.legacy(it.color)) },
        customColors = s.customColors.map { it.copy(baseColor = ColorRules.legacy(it.baseColor)) },
        wishes = s.wishes.map { if (it.color.isEmpty()) it else it.copy(color = ColorRules.legacy(it.color)) },
        ideas = s.ideas.map { if (it.color.isEmpty()) it else it.copy(color = ColorRules.legacy(it.color)) })
}
