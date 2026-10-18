package com.familywardrobe.app

/** One wardrobe, stable entity IDs, and no unrelated rows or photo references. */
internal object WardrobeSnapshots {
    private fun key(kind: String, id: String) = "$kind:$id"
    private data class Group<T>(val values: List<Pair<WardrobeScope.Row, T>>) {
        fun selected(keys: Set<String>): List<T> = values.filter { it.first.key in keys }.map { it.second }
    }
    fun select(source: Snapshot, wardrobeId: String): Snapshot {
        BackupStateRules.validate(source)
        val graph = mutableListOf<WardrobeScope.Row>()
        fun <T> group(kind: String, values: List<T>, id: (T) -> String, parent: (T) -> String?,
            references: (T) -> List<String> = { emptyList() }, photos: (T) -> List<String> = { emptyList() }): Group<T> =
            Group(values.map { value ->
                val row = WardrobeScope.Row(key(kind, id(value)), parent(value), references(value), photos(value).filter { it.isNotBlank() })
                graph.add(row); row to value
            })
        val wardrobes = group("wardrobe", source.wardrobes, { it.id }, { null })
        val garments = group("garment", source.garments, { it.id }, { key("wardrobe", it.wardrobeId) },
            { listOfNotNull(it.customCategoryId?.let { id -> key("category", id) }, it.customColorId?.let { id -> key("color", id) }) }, { listOf(it.photoId) })
        val outfits = group("outfit", source.outfits, { it.id }, { key("wardrobe", it.wardrobeId) })
        val parts = group("part", source.parts, { "${it.outfitId}/${it.garmentId}" }, { key("outfit", it.outfitId) }, { listOf(key("garment", it.garmentId)) })
        val wears = group("wear", source.wears, { "${it.garmentId}/${it.date}/${it.source}" }, { key("garment", it.garmentId) }, {
            val kind = when (it.source.substringBefore(':')) { "item" -> "garment"; "outfit" -> "outfit"; "plan" -> "plan"; else -> error("Invalid wear source") }
            listOf(key(kind, it.source.substringAfter(':')))
        })
        val wishes = group("wish", source.wishes, { it.id }, { key("wardrobe", it.wardrobeId) },
            { if (it.ownedId.isBlank()) emptyList() else listOf(key("garment", it.ownedId)) }, { listOf(it.photoId, it.needPhotoId) })
        val ideas = group("idea", source.ideas, { "${it.outfitId}/${it.partId}" }, { key("outfit", it.outfitId) },
            { listOfNotNull(it.wishId?.let { id -> key("wish", id) }) }, { listOf(it.photoId) })
        val plans = group("plan", source.plans, { it.id }, { key("wardrobe", it.wardrobeId) },
            { listOf(key("outfit", it.outfitId)) + it.wornItemIds.split('|').filter(String::isNotBlank).map { id -> key("garment", id) } })
        val feedback = group("feedback", source.feedback, { it.id }, { key("wardrobe", it.wardrobeId) },
            { it.signature.split('|').map { id -> key("garment", id) } })
        val capsules = group("capsule", source.capsules, { it.id }, { key("wardrobe", it.wardrobeId) })
        val capsuleItems = group("capsuleItem", source.capsuleItems, { "${it.capsuleId}/${it.garmentId}" }, { key("capsule", it.capsuleId) }, { listOf(key("garment", it.garmentId)) })
        val capsuleOutfits = group("capsuleOutfit", source.capsuleOutfits, { "${it.capsuleId}/${it.outfitId}" }, { key("capsule", it.capsuleId) }, { listOf(key("outfit", it.outfitId)) })
        val purchaseUndos = group("receipt", source.purchaseUndos, { it.wishId }, { key("wish", it.wishId) }, {
            val g = GarmentReceipt.read(it.garmentJson)
            listOf(key("garment", g.id)) + listOfNotNull(g.customCategoryId?.let { id -> key("category", id) }, g.customColorId?.let { id -> key("color", id) })
        }, { listOf(GarmentReceipt.read(it.garmentJson).photoId) })
        val events = group("event", source.wishEvents, { it.id }, { key("wish", it.wishId) }, photos = { listOf(it.photoId) })
        val categories = group("category", source.customCategories, { it.id }, { key("wardrobe", it.wardrobeId) })
        val colors = group("color", source.customColors, { it.id }, { key("wardrobe", it.wardrobeId) })
        val scope = WardrobeScope.select(wardrobeId, graph)
        val keys = scope.keys
        return Snapshot(wardrobes.selected(keys), garments.selected(keys), outfits.selected(keys), parts.selected(keys),
            wears.selected(keys), wishes.selected(keys), ideas.selected(keys), plans.selected(keys), feedback.selected(keys),
            capsules.selected(keys), capsuleItems.selected(keys), capsuleOutfits.selected(keys), purchaseUndos.selected(keys),
            events.selected(keys), categories.selected(keys), colors.selected(keys)).also {
            BackupStateRules.validate(it)
            require(it.wardrobes.single().id == wardrobeId && it.photoIds() == scope.photos)
        }
    }
}
