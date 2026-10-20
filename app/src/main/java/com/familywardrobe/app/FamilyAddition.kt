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
