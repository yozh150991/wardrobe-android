package com.familywardrobe.app

import android.content.ContextWrapper
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

internal data class FamilyOutfitForm(val remote: String, val outfit: String, val base: Long, val payload: String = "")
internal data class FamilyOutfitDraft(val publication: FamilyPublication, val change: FamilyOutfitChange, val submitted: Boolean = false)

/** Account-private, crash-safe editor state and one immutable publication per shared wardrobe. */
internal class FamilyOutfitStore(private val context: AccountContext, private val account: AuthIdentity) {
    init { require(context.location.key == account.key) }
    private val root = File(context.noBackupFilesDir, "family-outfits/${account.key}")
    private val lock = locks.computeIfAbsent(root.canonicalPath) { Any() }
    companion object { private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>() }
    private fun journal(remote: String, suffix: String): AtomicFile {
        require(FamilyRules.validId(remote)); return AtomicFile(File(root, "$remote-$suffix.json"))
    }
    private fun read(remote: String, suffix: String): JSONObject? {
        val f = journal(remote, suffix)
        if (!f.baseFile.exists() && !File(f.baseFile.path + ".bak").exists()) return null
        return f.openRead().use { input ->
            val bytes = java.io.ByteArrayOutputStream().also { PhotoStore.copyLimited(input, it, 128L * 1024) }.toByteArray()
            JSONObject(bytes.toString(Charsets.UTF_8)).also {
                require(it.getInt("version") == 1 && it.getString("account") == account.key && it.getString("remote") == remote)
            }
        }
    }
    private fun write(remote: String, suffix: String, value: JSONObject) {
        val bytes = value.put("version", 1).put("account", account.key).put("remote", remote).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 128 * 1024); check(root.mkdirs() || root.isDirectory)
        val file = journal(remote, suffix); val out = file.startWrite()
        try { out.write(bytes); out.fd.sync(); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun form(remote: String): FamilyOutfitForm? = synchronized(lock) {
        read(remote, "form")?.let { j -> FamilyOutfitForm(remote, j.getString("outfit"), j.getLong("base"), j.getString("payload")).also {
            require((it.outfit.isEmpty() || FamilyRules.validId(it.outfit)) && it.base > 0)
            if (it.payload.isNotEmpty()) JSONObject(it.payload)
        } }
    }
    fun putForm(form: FamilyOutfitForm) = synchronized(lock) {
        require(form.base > 0 && (form.outfit.isEmpty() || FamilyRules.validId(form.outfit)))
        require(form.payload.length <= 40_000)
        write(form.remote, "form", JSONObject().put("outfit", form.outfit).put("base", form.base).put("payload", form.payload))
    }
    fun clearForm(remote: String) = synchronized(lock) { journal(remote, "form").delete() }
    fun draft(remote: String): FamilyOutfitDraft? = synchronized(lock) {
        read(remote, "draft")?.let { j ->
            val p = FamilyPublication(FamilyArchiveIdentity(remote, j.getString("wardrobe"), j.getString("owner")),
                j.getString("archive"), j.getLong("base"), j.getString("hash"), j.getLong("size"))
            val c = j.getJSONObject("change"); val pieces = c.getJSONArray("pieces")
            FamilyOutfitDraft(p, FamilyOutfitChange(c.getString("id"), c.getBoolean("creating"), c.getString("name"),
                (0 until pieces.length()).map { pieces.getJSONObject(it).let { piece ->
                    LookPiece(piece.getString("key"), piece.getString("category"), piece.getString("name"))
                } }, c.getString("occasion"), c.getString("weather"), c.getString("source"), c.getString("layout"),
                c.getString("background"), c.getLong("createdAt")), j.getBoolean("submitted"))
        }
    }
    // Publication identity and submitted state form the journal CAS; display metadata is re-resolved.
    fun put(draft: FamilyOutfitDraft, expected: FamilyOutfitDraft?) = synchronized(lock) {
        val current = this.draft(draft.publication.identity.remoteId)
        require(current?.publication == expected?.publication && current?.submitted == expected?.submitted)
        val p = draft.publication; val c = draft.change
        val change = JSONObject().put("id", c.id).put("creating", c.creating).put("name", c.name)
            .put("occasion", c.occasion).put("weather", c.weather).put("source", c.source).put("layout", c.layout)
            .put("background", c.background).put("createdAt", c.createdAt).put("pieces", JSONArray().apply {
                c.pieces.forEach { put(JSONObject().put("key", it.key).put("category", it.category).put("name", it.name)) }
            })
        write(p.identity.remoteId, "draft", JSONObject().put("wardrobe", p.identity.wardrobeId).put("owner", p.identity.ownerUid)
            .put("archive", p.archiveId).put("base", p.baseRevision).put("hash", p.hash).put("size", p.size)
            .put("submitted", draft.submitted).put("change", change))
    }
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
        require(this.draft(remote)?.publication == draft.publication)
        journal(remote, "draft").delete(); require(this.draft(remote) == null)
        directory(draft.publication.archiveId).deleteRecursively(); Unit
    }
    fun stage(view: FamilyReadView, change: FamilyOutfitChange, repo: Repository, guard: () -> Unit): FamilyOutfitDraft {
        val changed = change.apply(view.candidate.snapshot, view.remote.identity.wardrobeId)
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
