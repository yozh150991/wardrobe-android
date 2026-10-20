package com.familywardrobe.app

/** Copy one item only: never copy private outfits, wear/purchase history or wishes. */
internal object FamilyAddition {
    fun select(source: Snapshot, id: String, target: Wardrobe): Snapshot {
        val item = source.garments.single { it.id == id }
        val category = item.customCategoryId?.let { key -> source.customCategories.single { it.id == key }.copy(id = newId(), wardrobeId = target.id) }
        val color = item.customColorId?.let { key -> source.customColors.single { it.id == key }.copy(id = newId(), wardrobeId = target.id) }
        return Snapshot(wardrobes = listOf(target), garments = listOf(item.copy(id = newId(), wardrobeId = target.id,
            photoId = newId(), customCategoryId = category?.id, customColorId = color?.id)),
            customCategories = listOfNotNull(category), customColors = listOfNotNull(color))
    }
    fun recover(source: Snapshot, id: String): Snapshot {
        val item = source.garments.single { it.id == id }
        return Snapshot(wardrobes = source.wardrobes, garments = listOf(item),
            customCategories = source.customCategories.filter { it.id == item.customCategoryId },
            customColors = source.customColors.filter { it.id == item.customColorId })
    }
    fun merge(latest: Snapshot, selected: Snapshot): Snapshot {
        val item = selected.garments.single(); val wardrobe = latest.wardrobes.single()
        require(item.wardrobeId == wardrobe.id && latest.garments.none { it.id == item.id } && item.photoId !in latest.photoIds())
        val category = selected.customCategories.singleOrNull()
        val color = selected.customColors.singleOrNull()
        val existingCategory = category?.let { c -> latest.customCategories.find { it.nameKey == c.nameKey } }
        val existingColor = color?.let { c -> latest.customColors.find { it.nameKey == c.nameKey } }
        require(existingCategory == null || existingCategory.baseCategory == item.category)
        require(existingColor == null || existingColor.baseColor == item.color)
        val merged = latest.copy(garments = latest.garments + item.copy(customCategoryId = existingCategory?.id ?: category?.id,
            customColorId = existingColor?.id ?: color?.id),
            customCategories = latest.customCategories + listOfNotNull(category?.takeIf { existingCategory == null }),
            customColors = latest.customColors + listOfNotNull(color?.takeIf { existingColor == null }))
        BackupStateRules.validate(merged)
        return merged
    }
}

/**
 * FAM-03: fields of a brand-new item created directly in a shared wardrobe (photo from the
 * gallery/camera), without first saving a private item. A custom category/colour, when chosen,
 * must already exist in that shared wardrobe and match the base category/colour.
 */
internal data class FamilyNewItem(val name: String, val category: String, val color: String, val size: String = "",
    val location: String = "", val description: String = "", val season: String = "all", val style: String = "casual",
    val customCategoryId: String? = null, val customColorId: String? = null) {
    val valid get() = name.trim().length in 1..120 && size.length <= 40 && location.length <= 200 && description.length <= 1000 &&
        category in Matching.categories && color in Matching.colors && season in Matching.seasons && style in Matching.styles
    /** Shared custom labels that may be used for this base category/colour. */
    fun categories(latest: Snapshot, target: Wardrobe) = latest.customCategories.filter { it.wardrobeId == target.id && it.baseCategory == category }
    fun colors(latest: Snapshot, target: Wardrobe) = latest.customColors.filter { it.wardrobeId == target.id && it.baseColor == color }
    /**
     * The new shared item with fresh IDs; it is merged by [FamilyAddition.merge] like any other
     * addition, which reuses the shared label with the same nameKey instead of duplicating it.
     */
    fun selection(target: Wardrobe, fingerprint: String, latest: Snapshot = Snapshot(wardrobes = listOf(target))): Snapshot {
        require(valid && (fingerprint.isEmpty() || Regex("[0-9a-f]{64}").matches(fingerprint)))
        val customCategory = customCategoryId?.let { id -> requireNotNull(categories(latest, target).find { it.id == id }) }
        val customColor = customColorId?.let { id -> requireNotNull(colors(latest, target).find { it.id == id }) }
        val item = Garment(wardrobeId = target.id, name = name.trim(), category = category, color = color, size = size.trim(),
            location = location.trim(), description = description.trim(), photoId = newId(), season = season, style = style,
            photoFingerprint = fingerprint, customCategoryId = customCategory?.id, customColorId = customColor?.id)
        return Snapshot(wardrobes = listOf(target), garments = listOf(item),
            customCategories = listOfNotNull(customCategory), customColors = listOfNotNull(customColor))
    }
}
