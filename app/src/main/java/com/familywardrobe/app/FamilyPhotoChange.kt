package com.familywardrobe.app

import java.io.File

/**
 * FAM-03 photo replacement: give one existing shared item a new final photo.
 * Only that item's photoId/photoFingerprint change; every other record, ID and photo of the
 * latest shared snapshot is kept as is. The old photo leaves the archive only if nothing else
 * references it (Snapshot.photoIds decides what FamilySnapshotZip writes).
 */
internal object FamilyPhotoChange {
    fun apply(latest: Snapshot, garmentId: String, photoId: String, fingerprint: String): Snapshot {
        require(PhotoStore.ID.matches(photoId) && photoId !in latest.photoIds())
        require(fingerprint.isEmpty() || Regex("[0-9a-f]{64}").matches(fingerprint))
        if (latest.garments.none { it.id == garmentId }) throw FamilyTransferException(FamilyProblem.MISSING)
        val changed = latest.copy(garments = latest.garments.map {
            if (it.id == garmentId) it.copy(photoId = photoId, photoFingerprint = fingerprint) else it
        })
        BackupStateRules.validate(changed)
        return changed
    }
}

/** Builds the immutable publication ZIP for a photo replacement in an isolated draft directory. */
internal class FamilyPhotoArchive(private val context: AccountContext, private val repo: Repository,
    private val store: FamilyAddStore, private val guard: () -> Unit) {
    private val copier = FamilyAddArchive(context, repo, store, guard)

    /**
     * [obtain] puts the new photo into the draft's own PhotoStore and returns its ID: an import from
     * the gallery/camera, a copy of a private item's final photo, or the photo kept by a previous draft.
     * With [change] (the server's delta model) only the new photo and a change JSON are kept; the server
     * applies it to its newest head. [latest] still validates the item and the photo ID locally.
     */
    suspend fun stage(remote: FamilyRemote, latest: Snapshot, latestPhotos: PhotoStore, garmentId: String,
        change: Boolean = false, obtain: suspend (PhotoStore) -> String): FamilyAddDraft {
        val id = newId(); val directory = store.directory(id); check(directory.mkdirs())
        val photos = store.photos(id)
        try {
            val photoId = obtain(photos); guard()
            val fingerprint = photos.fingerprint(photoId)
            val changed = FamilyPhotoChange.apply(latest, garmentId, photoId, fingerprint)
            val target = File(directory, "publish.zip")
            val text = if (change) {
                FamilySnapshotZip.writeChange(target, photos, photoId, guard)
                FamilyChange.photo(garmentId, fingerprint, photoId, photos.currentFile(photoId).extension)
            } else {
                for (photo in changed.photoIds() - photoId) copier.copyPhoto(latestPhotos, photo, photos, photo)
                val manifest = FamilyEnvelope(Backup(context, repo, photos)).encode(changed, remote.identity, remote.revision).toByteArray(Charsets.UTF_8)
                FamilySnapshotZip.write(target, manifest, changed, photos, guard); null
            }
            val item = changed.garments.single { it.id == garmentId }
            val draft = FamilyAddDraft(FamilyPublication(remote.identity, id, remote.revision, FamilyArchive.digest(target, guard), target.length()),
                garmentId, photoId, TextLimits.takeTrimmed(item.name, 120).ifBlank { "?" }, change = text)
            if (text != null) FamilyChange.check(draft, "photo")
            // Keep only the new photo for the preview; the shared photos stay inside the immutable ZIP.
            photos.directory.listFiles().orEmpty().filter { it.nameWithoutExtension != photoId }.forEach { check(it.delete()) }
            guard(); return draft
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
        finally { photos.clearMemoryCache() }
    }

    /**
     * Re-applies the same new photo to a fresh shared snapshot after a confirmed conflict or expiry. Only a
     * full-archive draft needs this; with the server's delta support it becomes a change draft.
     */
    suspend fun rebase(draft: FamilyAddDraft, remote: FamilyRemote, latest: Snapshot, latestPhotos: PhotoStore): FamilyAddDraft {
        require(draft.publication.identity == remote.identity && draft.change == null)
        val kept = store.photos(draft.publication.archiveId)
        return stage(remote, latest, latestPhotos, draft.garmentId, FamilyChange.supported(remote)) { photos ->
            copier.copyPhoto(kept, draft.photoId, photos, draft.photoId); draft.photoId
        }
    }
}
