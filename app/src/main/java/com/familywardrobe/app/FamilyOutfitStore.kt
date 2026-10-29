package com.familywardrobe.app

import android.content.ContextWrapper
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * [origin] — the edited outfit's rows when the editor opened ([FamilyChange.outfitRows]); empty for a new outfit or a
 * form saved before 0.14.22. With the server's outfit changes only a change to these rows makes the form stale.
 */
internal data class FamilyOutfitForm(val remote: String, val outfit: String, val base: Long, val payload: String = "",
    val origin: String = "")
/**
 * [delta] — an outfit change the server applies to its newest head ([FamilyChange.outfit]); then the publication
 * describes those UTF-8 bytes and no archive file exists. Without it the publication is a full snapshot archive.
 */
internal data class FamilyOutfitDraft(val publication: FamilyPublication, val change: FamilyOutfitChange, val submitted: Boolean = false,
    val delta: String? = null) {
    init { require(delta == null || delta.length in 1..FamilyChange.MAX_TEXT) }
}

/** Account-private, crash-safe editor state and a bounded publication queue per shared wardrobe. */
internal class FamilyOutfitStore(private val context: AccountContext, private val account: AuthIdentity) {
    init { require(context.location.key == account.key) }
    private val root = File(context.noBackupFilesDir, "family-outfits/${account.key}")
    private val lock = locks.computeIfAbsent(root.canonicalPath) { Any() }
    companion object { private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>() }
    private fun journal(remote: String, suffix: String): AtomicFile {
        require(FamilyRules.validId(remote)); return AtomicFile(File(root, "$remote-$suffix.json"))
    }
    private fun readJson(remote: String, suffix: String, limit: Long): JSONObject? {
        val f = journal(remote, suffix)
        if (!f.baseFile.exists() && !File(f.baseFile.path + ".bak").exists()) return null
        return f.openRead().use { input ->
            val bytes = java.io.ByteArrayOutputStream().also { PhotoStore.copyLimited(input, it, limit) }.toByteArray()
            JSONObject(bytes.toString(Charsets.UTF_8))
        }
    }
    private fun state(remote: String): FamilyOutfitJournal {
        readJson(remote, "state", FamilyOutfitQueue.MAX_JOURNAL.toLong())?.let {
            return FamilyOutfitQueue.decode(it, account.key, remote)
        }
        // Legacy publication took precedence over the form; it was already durably prepared.
        val draft = readJson(remote, "draft", 128L * 1024)?.let {
            FamilyOutfitQueue.checkEnvelope(it, 1, account.key, remote)
            FamilyOutfitQueue.readDraft(it, remote)
        }
        val form = if (draft != null) null else readJson(remote, "form", 128L * 1024)?.let {
            FamilyOutfitQueue.checkEnvelope(it, 1, account.key, remote)
            FamilyOutfitQueue.readForm(it, remote)
        }
        return FamilyOutfitQueue.validate(FamilyOutfitJournal(form, listOfNotNull(draft)), remote)
    }
    private fun write(remote: String, state: FamilyOutfitJournal) {
        val bytes = FamilyOutfitQueue.encode(state, account.key, remote).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= FamilyOutfitQueue.MAX_JOURNAL); check(root.mkdirs() || root.isDirectory)
        val file = journal(remote, "state"); val out = file.startWrite()
        try { out.write(bytes); out.fd.sync(); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
        // v2 is authoritative even when cleanup is interrupted; never fall back from corrupt v2.
        journal(remote, "form").delete(); journal(remote, "draft").delete()
    }
    fun form(remote: String): FamilyOutfitForm? = synchronized(lock) { state(remote).form }
    fun putForm(form: FamilyOutfitForm) = synchronized(lock) {
        write(form.remote, state(form.remote).copy(form = form))
    }
    fun clearForm(remote: String, expected: FamilyOutfitForm? = null) = synchronized(lock) {
        val state = state(remote)
        require(expected == null || state.form == expected) { "Form changed" }
        write(remote, state.copy(form = null))
    }
    fun list(remote: String): List<FamilyOutfitDraft> = synchronized(lock) { state(remote).drafts }
    fun draft(remote: String, outfit: String? = null): FamilyOutfitDraft? = synchronized(lock) {
        list(remote).let { rows -> if (outfit == null) rows.firstOrNull() else rows.find { it.change.id == outfit } }
    }
    fun put(draft: FamilyOutfitDraft, expected: FamilyOutfitDraft?, preparedForm: FamilyOutfitForm? = null) = synchronized(lock) {
        val remote = draft.publication.identity.remoteId
        write(remote, FamilyOutfitQueue.put(state(remote), draft, expected, preparedForm))
    }.also { FamilySyncLocks.touched(account.key) }
    fun directory(id: String): File { require(FamilyRules.validId(id)); return File(root, id) }
    private fun photos(id: String) = PhotoStore(object : ContextWrapper(context) {
        override fun getFilesDir() = directory(id)
        override fun getCacheDir() = File(directory(id), "temporary").apply { check(mkdirs() || isDirectory) }
    })
    fun checked(draft: FamilyOutfitDraft, guard: () -> Unit) = File(directory(draft.publication.archiveId), "publish.zip").also {
        require(it.isFile && it.length() == draft.publication.size && FamilyArchive.digest(it, guard) == draft.publication.hash)
    }
    fun remove(draft: FamilyOutfitDraft) = synchronized(lock) {
        val remote = draft.publication.identity.remoteId
        write(remote, FamilyOutfitQueue.remove(state(remote), draft))
        directory(draft.publication.archiveId).deleteRecursively(); Unit
    }.also { FamilySyncLocks.touched(account.key) }
    /** Rows of outfit [id] in [state] encoded like the manifest; the shared photos only provide file extensions. */
    fun rows(view: FamilyReadView, state: Snapshot, id: String, repo: Repository): org.json.JSONObject? {
        val outfit = state.outfits.find { it.id == id } ?: return null
        val single = Snapshot(wardrobes = state.wardrobes, outfits = listOf(outfit), parts = state.parts.filter { it.outfitId == id },
            ideas = state.ideas.filter { it.outfitId == id })
        return FamilyChange.outfitRows(Backup(context, repo, view.candidate.photos).encode(single), id)
    }
    /**
     * [delta]: the server applies the outfit's rows to its newest head, so nothing is uploaded; an edit carries the rows
     * it started from (the shared outfit as of [view]). Otherwise a full snapshot archive is built as before.
     */
    fun stage(view: FamilyReadView, change: FamilyOutfitChange, repo: Repository, guard: () -> Unit, delta: Boolean = false): FamilyOutfitDraft {
        val changed = change.apply(view.candidate.snapshot, view.remote.identity.wardrobeId)
        if (delta) {
            val updated = requireNotNull(rows(view, changed, change.id, repo))
            val previous = if (change.creating) null else requireNotNull(rows(view, view.candidate.snapshot, change.id, repo))
            val text = FamilyChange.outfit(updated, previous); val bytes = text.toByteArray(Charsets.UTF_8)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            guard()
            return FamilyOutfitDraft(FamilyPublication(view.remote.identity, newId(), view.remote.revision, hash, bytes.size.toLong()),
                change, delta = text).also { FamilyChange.checkOutfit(text, change.id, change.creating) }
        }
        val id = newId(); val directory = directory(id); check(directory.mkdirs())
        val photos = photos(id)
        try {
            for (photo in changed.photoIds()) {
                guard(); val file = view.candidate.photos.currentFile(photo)
                require(file.isFile && file.length() in 1..12L * 1024 * 1024)
                FileOutputStream(photos.file(photo, file.extension)).use { out ->
                    file.inputStream().use { PhotoStore.copyLimited(it, out, 12L * 1024 * 1024, guard) }; out.fd.sync()
                }
            }
            val manifest = FamilyEnvelope(Backup(context, repo, photos)).encode(changed, view.remote.identity, view.remote.revision)
            val zip = File(directory, "publish.zip")
            FamilySnapshotZip.write(zip, manifest.toByteArray(Charsets.UTF_8), changed, photos, guard)
            val publication = FamilyPublication(view.remote.identity, id, view.remote.revision, FamilyArchive.digest(zip, guard), zip.length())
            check(photos.directory.deleteRecursively()); guard()
            return FamilyOutfitDraft(publication, change)
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
        finally { photos.clearMemoryCache() }
    }
}
