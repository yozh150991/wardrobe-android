package com.familywardrobe.app

/** Validate every reference before copying photos or mutating the database. Draft queues and city settings are excluded. */
object BackupStateRules {
    fun validate(s: Snapshot) {
        fun unique(ids: List<String>) { require(ids.size == ids.distinct().size && ids.all { PhotoStore.ID.matches(it) }) }
        unique(s.wardrobes.map { it.id }); unique(s.garments.map { it.id }); unique(s.outfits.map { it.id }); unique(s.wishes.map { it.id })
        unique(s.plans.map { it.id }); unique(s.feedback.map { it.id }); unique(s.capsules.map { it.id })
        unique(s.wishEvents.map { it.id }); unique(s.customCategories.map { it.id }); unique(s.customColors.map { it.id })
        fun validColor(g: Garment): Boolean = g.customColorId == null || s.customColor(g) != null
        fun validCategory(g: Garment): Boolean = g.customCategoryId == null || s.customCategory(g) != null
        require(s.wishEvents.all { e -> s.wishes.any { it.id == e.wishId } && e.kind in listOf("candidate", "candidate_cancelled", "purchased", "purchase_cancelled") &&
            e.name.trim().length in 1..120 && e.category in Matching.categories && WishRules.validMinor(e.priceMinor) && e.currency in wishCurrencies &&
            WishRules.validLink(e.link) && e.inspiration.length <= 2000 && (e.photoId.isEmpty() || PhotoStore.ID.matches(e.photoId)) && e.createdAt >= 0 })
        require(s.purchaseUndos.map { it.wishId }.distinct().size == s.purchaseUndos.size)
        s.purchaseUndos.forEach { u ->
            val g = GarmentReceipt.read(u.garmentJson)
            require(u.createdAt >= 0 && s.wishes.any { it.id == u.wishId && it.ownedId == g.id && it.wardrobeId == g.wardrobeId })
            require(s.garments.any { it.id == g.id && it.wardrobeId == g.wardrobeId } && validCategory(g) && validColor(g))
        }
        val wardrobes = s.wardrobes.map { it.id }.toSet()
        val garments = s.garments.associateBy { it.id }; val outfits = s.outfits.associateBy { it.id }; val plans = s.plans.associateBy { it.id }
        val wishes = s.wishes.associateBy { it.id }; val capsules = s.capsules.associateBy { it.id }
        require(s.customCategories.all { it.wardrobeId in wardrobes && it.baseCategory in Matching.categories &&
            CategoryRules.validName(it.name) && CategoryRules.clean(it.name) == it.name && CategoryRules.key(it.name) == it.nameKey })
        require(s.customCategories.map { it.wardrobeId to it.nameKey }.distinct().size == s.customCategories.size)
        require(s.customColors.all { it.wardrobeId in wardrobes && it.baseColor in Matching.colors &&
            CategoryRules.validName(it.name) && CategoryRules.clean(it.name) == it.name && CategoryRules.key(it.name) == it.nameKey })
        require(s.customColors.map { it.wardrobeId to it.nameKey }.distinct().size == s.customColors.size)
        require(s.wardrobes.all { it.name.trim().length in 1..80 && it.noRepeatDays in 0..30 })
        require(s.garments.all { validCategory(it) && validColor(it) && it.wardrobeId in wardrobes && WishRules.validMinor(it.priceMinor) && it.currency in wishCurrencies && it.name.trim().length in 1..120 && it.category in Matching.categories && it.color in Matching.colors && it.season in Matching.seasons && it.style in Matching.styles && it.availability in Matching.availability && PhotoStore.ID.matches(it.photoId) && it.subcategory.length <= 60 && (it.photoFingerprint.isBlank() || Regex("[a-f0-9]{64}").matches(it.photoFingerprint)) })
        require(s.outfits.all { it.wardrobeId in wardrobes })
        require(s.wishes.all { it.wardrobeId in wardrobes && (it.ownedId.isBlank() || garments[it.ownedId]?.wardrobeId == it.wardrobeId) })
        require(s.parts.size == s.parts.distinct().size && s.parts.all { garments[it.garmentId]?.wardrobeId == outfits[it.outfitId]?.wardrobeId && it.outfitId in outfits })
        require(s.ideas.map { it.outfitId to it.partId }.distinct().size == s.ideas.size && s.ideas.all {
            it.outfitId in outfits && (it.wishId == null || wishes[it.wishId]?.let { w -> w.wardrobeId == outfits[it.outfitId]?.wardrobeId && w.ownedId.isBlank() } == true)
        })
        s.outfits.forEach { outfit ->
            val parts = s.parts.filter { it.outfitId == outfit.id }; val ideas = s.ideas.filter { it.outfitId == outfit.id }
            require(outfit.name.trim().length in 1..120 && parts.size + ideas.size in 1..WardrobeRules.MAX_PARTS)
            require((parts.map { it.garmentId } + ideas.map { it.partId }).sorted().joinToString("|") == outfit.signature)
            require(outfit.occasion in LookRules.OCCASIONS && outfit.weather in LookRules.WEATHERS && outfit.source in LookRules.SOURCES)
            val keys = parts.map { "owned:${it.garmentId}" } + ideas.map { it.partId }
            require(OutfitCanvasState.read(outfit.layout).all { it.key in keys } && outfit.background in OutfitCanvasState.backgrounds)
        }
        require(s.wears.size == s.wears.distinct().size)
        s.wears.forEach { wear ->
            val garment = requireNotNull(garments[wear.garmentId]); require(RoadmapRules.validDate(wear.date))
            val id = wear.source.substringAfter(':'); require(PhotoStore.ID.matches(id))
            require(when (wear.source.substringBefore(':')) {
                "item" -> id == garment.id
                "outfit" -> outfits[id]?.wardrobeId == garment.wardrobeId
                "plan" -> plans[id]?.let { it.date == wear.date && it.wardrobeId == garment.wardrobeId } == true
                else -> false
            })
        }
        require(s.plans.map { Triple(it.wardrobeId, it.date, it.outfitId) }.distinct().size == s.plans.size)
        val wearBySource = s.wears.groupBy { it.source to it.date }
        s.plans.forEach { plan ->
            require(plan.wardrobeId in wardrobes && outfits[plan.outfitId]?.wardrobeId == plan.wardrobeId && RoadmapRules.validDate(plan.date) && plan.notes.length <= 1000)
            val ids = if (plan.wornItemIds.isBlank()) emptyList() else plan.wornItemIds.split('|')
            val actual = wearBySource["plan:${plan.id}" to plan.date].orEmpty().map { it.garmentId }
            require(ids.size <= WardrobeRules.MAX_PARTS && ids.size == ids.distinct().size && ids.all { garments[it]?.wardrobeId == plan.wardrobeId })
            require(plan.worn == ids.isNotEmpty() && DailyRules.signature(ids) == plan.wornItemIds && ids.toSet() == actual.toSet())
        }
        require(s.feedback.map { listOf(it.wardrobeId, it.signature, it.weather, it.occasion, it.reason) }.distinct().size == s.feedback.size)
        s.feedback.forEach { feedback ->
            val ids = feedback.signature.split('|')
            require(feedback.wardrobeId in wardrobes && feedback.weather in LookRules.WEATHERS && feedback.occasion in LookRules.OCCASIONS && feedback.reason in setOf("dislike", "hot", "occasion"))
            require(ids.size in 1..WardrobeRules.MAX_PARTS && ids.distinct().size == ids.size && ids.all { garments[it]?.wardrobeId == feedback.wardrobeId } && DailyRules.signature(ids) == feedback.signature)
        }
        require(s.capsules.all { it.wardrobeId in wardrobes && it.name.trim().length in 1..80 && it.kind in setOf("capsule", "trip") && RoadmapRules.period(it.startDate, it.endDate) })
        require(s.capsuleOutfits.distinct().size == s.capsuleOutfits.size && s.capsuleOutfits.all {
            it.capsuleId in capsules && outfits[it.outfitId]?.wardrobeId == capsules[it.capsuleId]?.wardrobeId
        })
        require(s.capsuleItems.map { it.capsuleId to it.garmentId }.distinct().size == s.capsuleItems.size && s.capsuleItems.all {
            it.capsuleId in capsules && garments[it.garmentId]?.wardrobeId == capsules[it.capsuleId]?.wardrobeId
        })
        val linksByCapsule = s.capsuleOutfits.groupBy { it.capsuleId }
        val itemsByCapsule = s.capsuleItems.groupBy { it.capsuleId }
        val partsByOutfit = s.parts.groupBy { it.outfitId }
        s.capsules.forEach { capsule ->
            val links = linksByCapsule[capsule.id].orEmpty().map { it.outfitId }
            val rows = itemsByCapsule[capsule.id].orEmpty()
            val expected = links.flatMap { partsByOutfit[it].orEmpty() }.map { it.garmentId }.toSet() + rows.filter { it.manual }.map { it.garmentId }
            require(rows.map { it.garmentId }.toSet() == expected)
        }
    }
}
