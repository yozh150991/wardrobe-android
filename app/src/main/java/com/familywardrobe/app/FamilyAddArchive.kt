package com.familywardrobe.app

import java.io.File
import java.io.FileOutputStream

internal class FamilyAddArchive(private val context: AccountContext, private val repo: Repository,
    private val store: FamilyAddStore, private val guard: () -> Unit) {
    fun copyPhoto(source: PhotoStore, sourceId: String, target: PhotoStore, targetId: String) {
        guard(); val input = source.currentFile(sourceId)
        require(input.isFile && input.length() in 1..12L*1024*1024)
        FileOutputStream(target.file(targetId, input.extension)).use { out ->
            input.inputStream().use { PhotoStore.copyLimited(it, out, 12L*1024*1024, guard) }; out.fd.sync()
        }
    }
    fun stage(remote: FamilyRemote, latest: Snapshot, selected: Snapshot, latestPhotos: PhotoStore,
        selectedPhotos: PhotoStore, selectedPhotoId: String): FamilyAddDraft {
        val id = newId(); val directory = store.directory(id); check(directory.mkdirs())
        val photos = store.photos(id)
        try {
            val merged = FamilyAddition.merge(latest, selected)
            val item = selected.garments.single()
            for (photo in latest.photoIds()) copyPhoto(latestPhotos, photo, photos, photo)
            copyPhoto(selectedPhotos, selectedPhotoId, photos, item.photoId)
            val manifest = FamilyEnvelope(Backup(context, repo, photos)).encode(merged, remote.identity, remote.revision).toByteArray(Charsets.UTF_8)
            val target = File(directory, "publish.zip")
            FamilySnapshotZip.write(target, manifest, merged, photos, guard)
            val draft = FamilyAddDraft(FamilyPublication(remote.identity, id, remote.revision, FamilyArchive.digest(target, guard), target.length()), item.id, item.photoId, item.name)
            // Retain just the selected final photo for preview. Other photos remain only in the immutable ZIP.
            photos.directory.listFiles().orEmpty().filter { it.nameWithoutExtension != item.photoId }.forEach { check(it.delete()) }
            guard(); return draft
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
        finally { photos.clearMemoryCache() }
    }
    fun selection(draft: FamilyAddDraft): Snapshot {
        val text = java.util.zip.ZipFile(store.checked(draft, guard)).use { zip ->
            val entry = requireNotNull(zip.getEntry("manifest.json"))
            zip.getInputStream(entry).use { input -> java.io.ByteArrayOutputStream().also {
                PhotoStore.copyLimited(input, it, FamilyEnvelope.MAX_MANIFEST.toLong(), guard)
            }.toByteArray().toString(Charsets.UTF_8) }
        }
        val snapshot = FamilyEnvelope(Backup(context, repo, store.photos(draft.publication.archiveId)))
            .decode(text, draft.publication.identity, draft.publication.baseRevision)
        return FamilyAddition.recover(snapshot, draft.garmentId).also {
            require(it.garments.single().photoId == draft.photoId && it.garments.single().name == draft.name)
        }
    }
    fun rebase(draft: FamilyAddDraft, remote: FamilyRemote, latest: Snapshot, latestPhotos: PhotoStore): FamilyAddDraft {
        require(draft.publication.identity == remote.identity)
        val temporaryId = newId(); val temporary = store.directory(temporaryId)
        var photos: PhotoStore? = null
        try {
            val extracted = FamilyCacheZip.extract(store.checked(draft, guard), draft.publication.size, draft.publication.hash, temporary, guard)
            val isolated = store.photos(temporaryId); photos = isolated
            val old = FamilyEnvelope(Backup(context, repo, isolated)).decode(extracted.manifest, draft.publication.identity, draft.publication.baseRevision)
            val selected = FamilyAddition.recover(old, draft.garmentId)
            require(selected.garments.single().photoId == draft.photoId)
            return stage(remote, latest, selected, latestPhotos, isolated, draft.photoId)
        } finally { photos?.clearMemoryCache(); temporary.deleteRecursively() }
    }
}
