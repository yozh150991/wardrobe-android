package com.familywardrobe.app

import androidx.room.withTransaction
import kotlinx.coroutines.sync.withLock

suspend fun Repository.enqueueImports(wardrobe: String, uris: List<String>): List<ImportDraft> = writes.withLock {
    require(uris.isNotEmpty() && uris.size <= 20 && uris.all { it.length in 1..4000 && it.startsWith("content://") })
    db.withTransaction {
        require(dao.allWardrobes().any { it.id == wardrobe })
        require(dao.allImports().count { it.wardrobeId == wardrobe } + uris.size <= 200)
        val batch = newId()
        uris.mapIndexed { position, uri -> ImportDraft(wardrobeId = wardrobe, batchId = batch,
            position = position, inputUri = uri) }.also { rows -> rows.forEach { dao.saveImport(it) } }
    }
}

suspend fun Repository.saveImported(id: String, garment: Garment, photos: PhotoStore) = writes.withLock {
    val candidates = db.withTransaction {
        val draft = requireNotNull(dao.importDraft(id))
        require(draft.status in setOf("review", "failed") && draft.wardrobeId == garment.wardrobeId && draft.id == garment.id)
        require(dao.garment(id) == null && garment.name.trim().length in 1..120)
        require(garment.category in Matching.categories && garment.color in Matching.colors && PhotoStore.ID.matches(garment.photoId))
        require(garment.season in Matching.seasons && garment.style in Matching.styles && garment.availability in Matching.availability)
        require(WishRules.validMinor(garment.priceMinor) && garment.currency in WishRules.CURRENCIES)
        if (draft.status == "failed") kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            photos.validateImage(photos.currentFile(garment.photoId))
        }
        dao.saveGarment(garment.copy(photoFingerprint = garment.photoFingerprint.ifBlank { draft.photoFingerprint }))
        dao.deleteImport(id)
        setOf(draft.photoId, draft.originalPhotoId).filter { it.isNotBlank() }.toSet()
    }
    photos.remove(candidates - referencedPhotos())
}

suspend fun Repository.skipImport(id: String, photos: PhotoStore) = writes.withLock {
    val candidates = db.withTransaction {
        val draft = dao.importDraft(id) ?: return@withTransaction emptySet<String>()
        require(draft.status != "working")
        dao.deleteImport(id)
        setOf(draft.photoId, draft.originalPhotoId).filter { it.isNotBlank() }.toSet()
    }
    photos.remove(candidates - referencedPhotos())
}

suspend fun Repository.bulkChange(wardrobe: String, ids: Set<String>, action: String, season: String = "all"): BulkChange = writes.withLock {
    db.withTransaction {
        require(ids.isNotEmpty() && ids.size <= 10_000)
        val before = ids.map { requireNotNull(dao.garment(it)) }
        require(before.all { it.wardrobeId == wardrobe })
        require(action in setOf("laundry", "available", "archive", "restore", "season") && season in Matching.seasons)
        val after = before.map { g -> when (action) {
            "laundry" -> g.copy(availability = "laundry")
            "available" -> g.copy(availability = "available")
            "archive" -> g.copy(archived = true)
            "restore" -> g.copy(archived = false)
            else -> g.copy(season = season)
        } }
        after.forEach { dao.saveGarment(it) }
        BulkChange(before, after)
    }
}

suspend fun Repository.undoBulk(change: BulkChange): Boolean = writes.withLock {
    db.withTransaction {
        val current = change.after.map { dao.garment(it.id) ?: return@withTransaction false }
        if (current.zip(change.after).any { (g, expected) ->
            g.wardrobeId != expected.wardrobeId || g.availability != expected.availability ||
                g.archived != expected.archived || g.season != expected.season }) return@withTransaction false
        current.zip(change.before).forEach { (g, old) ->
            dao.saveGarment(g.copy(availability = old.availability, archived = old.archived, season = old.season))
        }
        true
    }
}

/** Camera bytes are copied before enqueue, so account sign-out cannot destroy an unprocessed capture. */
suspend fun Repository.enqueueCapturedImport(wardrobe: String, photo: String, fingerprint: String, name: String) = writes.withLock {
    db.withTransaction {
        require(PhotoStore.ID.matches(photo) && Regex("[a-f0-9]{64}").matches(fingerprint))
        require(dao.allWardrobes().any { it.id == wardrobe } && dao.allImports().count { it.wardrobeId == wardrobe } < 200)
        dao.saveImport(ImportDraft(wardrobeId = wardrobe, batchId = newId(), position = 0,
            photoId = photo, originalPhotoId = photo, name = name, photoFingerprint = fingerprint))
    }
}

suspend fun Repository.applyImportTemplate(wardrobe: String, ids: Set<String>, category: String, season: String, style: String) = writes.withLock {
    db.withTransaction {
        require(ids.isNotEmpty() && ids.size <= 200)
        require(category.isBlank() || category in Matching.categories)
        require(season.isBlank() || season in Matching.seasons)
        require(style.isBlank() || style in Matching.styles)
        ids.forEach { id ->
            val row = requireNotNull(dao.importDraft(id))
            require(row.wardrobeId == wardrobe && row.status in setOf("queued", "working", "review", "failed"))
            dao.saveImport(row.copy(category = category.ifBlank { row.category }, season = season.ifBlank { row.season }, style = style.ifBlank { row.style }))
        }
    }
}
