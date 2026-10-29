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
    /**
     * Delta model: only the selected photo and the change JSON. The same merge the server repeats on its newest
     * head is checked here against [latest] first, so a label clash is shown before anything is sent.
     */
    fun stageChange(remote: FamilyRemote, latest: Snapshot, selected: Snapshot, selectedPhotos: PhotoStore,
        selectedPhotoId: String): FamilyAddDraft {
        val id = newId(); val directory = store.directory(id); check(directory.mkdirs())
        val photos = store.photos(id)
        try {
            FamilyAddition.merge(latest, selected)
            val item = selected.garments.single()
            copyPhoto(selectedPhotos, selectedPhotoId, photos, item.photoId)
            val text = FamilyChange.addition(Backup(context, repo, photos).encode(selected))
            val target = File(directory, "publish.zip")
            FamilySnapshotZip.writeChange(target, photos, item.photoId, guard)
            val draft = FamilyAddDraft(FamilyPublication(remote.identity, id, remote.revision, FamilyArchive.digest(target, guard), target.length()),
                item.id, item.photoId, item.name, change = text)
            FamilyChange.check(draft, "add")
            guard(); return draft
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
        finally { photos.clearMemoryCache() }
    }
    fun selection(draft: FamilyAddDraft): Snapshot {
        draft.change?.let { change ->
            store.checked(draft, guard); FamilyChange.check(draft, "add")
            val snapshot = Backup(context, repo, store.photos(draft.publication.archiveId))
                .decode(FamilyChange.preview(change, draft.publication.identity.wardrobeId))
            return FamilyAddition.recover(snapshot, draft.garmentId).also {
                require(it.garments.single().photoId == draft.photoId && it.garments.single().name == draft.name)
            }
        }
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
    /** A full-archive draft only; with the server's delta support it becomes a change draft that never goes stale. */
    fun rebase(draft: FamilyAddDraft, remote: FamilyRemote, latest: Snapshot, latestPhotos: PhotoStore): FamilyAddDraft {
        // Decoded with its own identity (the envelope it was built with), re-staged with the current one.
        require(draft.publication.identity.sameWardrobe(remote.identity) && draft.change == null)
        val temporaryId = newId(); val temporary = store.directory(temporaryId)
        var photos: PhotoStore? = null
        try {
            val extracted = FamilyCacheZip.extract(store.checked(draft, guard), draft.publication.size, draft.publication.hash, temporary, guard)
            val isolated = store.photos(temporaryId); photos = isolated
            val old = FamilyEnvelope(Backup(context, repo, isolated)).decode(extracted.manifest, draft.publication.identity, draft.publication.baseRevision)
            val selected = FamilyAddition.recover(old, draft.garmentId)
            require(selected.garments.single().photoId == draft.photoId)
            return if (FamilyChange.supported(remote)) stageChange(remote, latest, selected, isolated, draft.photoId)
                else stage(remote, latest, selected, latestPhotos, isolated, draft.photoId)
        } finally { photos?.clearMemoryCache(); temporary.deleteRecursively() }
    }
}
