package com.familywardrobe.app

/** An explicit change to one shared look; all references are resolved against the shared snapshot. */
internal data class FamilyOutfitChange(val id: String, val creating: Boolean, val name: String,
    val pieces: List<LookPiece>, val occasion: String, val weather: String, val source: String,
    val layout: String, val background: String, val createdAt: Long = System.currentTimeMillis()) {
    fun apply(latest: Snapshot, wardrobe: String): Snapshot {
        require(latest.wardrobes.single().id == wardrobe && FamilyRules.validId(id) && createdAt >= 0)
        val previous = latest.outfits.find { it.id == id }
        if (!creating && previous == null) throw FamilyTransferException(FamilyProblem.MISSING)
        require(if (creating) previous == null else previous?.wardrobeId == wardrobe)
        require(TextLimits.hasVisibleText(name) && name.trim().length in 1..80)
        require(occasion in LookRules.OCCASIONS && weather in LookRules.WEATHERS && source in LookRules.SOURCES)
        require(background in OutfitCanvasState.backgrounds)
        require(pieces.size in 1..WardrobeRules.MAX_PARTS && pieces.map { it.key }.distinct().size == pieces.size)
        val resolved = pieces.map { p -> when {
            p.key.startsWith("owned:") -> {
                val item = latest.garments.find { it.id == p.key.removePrefix("owned:") && it.wardrobeId == wardrobe }
                    ?: throw FamilyTransferException(FamilyProblem.MISSING)
                require(source != "inspiration" && WardrobeRules.ready(item.rulePiece()))
                LookRules.owned(item)
            }
            p.key.startsWith("wish:") -> {
                val wish = latest.wishes.find { it.id == p.key.removePrefix("wish:") && it.wardrobeId == wardrobe }
                    ?: throw FamilyTransferException(FamilyProblem.MISSING)
                require(source == "mixed" && wish.ownedId.isBlank())
                LookRules.wanted(wish)
            }
            else -> {
                require(source == "inspiration" && InspirationCatalog.validKey(p.key, p.category))
                require(TextLimits.hasVisibleText(p.name) && p.name.length <= 120)
                val idea = InspirationCatalog.all().find { it.key == p.key }
                LookPiece(p.key, p.category, p.name, color = idea?.color.orEmpty(), season = idea?.season ?: "all")
            }
        } }
        require(WardrobeRules.validCategories(resolved.map { it.category }))
        val owned = resolved.filter { it.ownedId != null }
        require(owned.all { a -> owned.all { b -> Matching.compatibleSeasons(a.season, b.season) } })
        val signature = resolved.map { it.ownedId ?: it.key }.sorted().joinToString("|")
        if (latest.outfits.any { it.id != id && it.wardrobeId == wardrobe && it.signature == signature })
            throw FamilyTransferException(FamilyProblem.CONFLICT)
        val outfit = (previous ?: Outfit(id, wardrobe, name.trim(), signature, createdAt)).copy(
            name = java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFC), signature = signature,
            occasion = occasion, weather = weather, source = source,
            layout = OutfitCanvasState.reconcile(layout, resolved.map { it.key }), background = background)
        val parts = latest.parts.filterNot { it.outfitId == id } + owned.map { OutfitPart(id, requireNotNull(it.ownedId)) }
        val ideas = latest.ideas.filterNot { it.outfitId == id } + resolved.filter { it.ownedId == null }.map {
            OutfitIdea(id, it.key, it.wishId, it.category, it.name, it.color, it.photoId)
        }
        // Reconcile only linked capsules, preserving manual extras and packed flags on retained items.
        val affected = latest.capsuleOutfits.filter { it.outfitId == id }.map { it.capsuleId }.toSet()
        val packing = latest.capsuleItems.filter { it.capsuleId !in affected } + affected.flatMap { capsule ->
            val old = latest.capsuleItems.filter { it.capsuleId == capsule }
            val links = latest.capsuleOutfits.filter { it.capsuleId == capsule }.map { it.outfitId }.toSet()
            val needed = parts.filter { it.outfitId in links }.map { it.garmentId }.toSet() + old.filter { it.manual }.map { it.garmentId }
            needed.map { garment -> old.find { it.garmentId == garment } ?: CapsuleItem(capsule, garment) }
        }
        // Historical wears and worn plans describe what was actually worn; never rewrite them.
        return latest.copy(outfits = latest.outfits.filterNot { it.id == id } + outfit, parts = parts,
            ideas = ideas, capsuleItems = packing).also(BackupStateRules::validate)
    }
}
