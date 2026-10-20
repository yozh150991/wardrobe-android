package com.familywardrobe.app

import android.content.ContextWrapper
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

internal data class FamilyAddDraft(val publication: FamilyPublication, val garmentId: String, val photoId: String,
    val name: String, val submitted: Boolean = false) {
    init { require(FamilyRules.validId(garmentId) && FamilyRules.validId(photoId) && name.isNotBlank() && name.length <= 120) }
    val remoteId get() = publication.identity.remoteId
}

/**
 * One durable immutable publication draft per wardrobe/account; never part of portable backup.
 * Additions use "family-additions"; photo replacements reuse the same journal format in their
 * own folder, so the two kinds of drafts never overwrite each other.
 */
internal class FamilyAddStore(private val context: AccountContext, private val account: AuthIdentity,
    folder: String = "family-additions") {
    init { require(folder in setOf("family-additions", "family-photo-replacements")) }
    private val root = File(context.noBackupFilesDir, "$folder/${account.key}")
    private val lock = locks.computeIfAbsent(root.canonicalPath) { Any() }
    companion object { private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>() }
    init { require(context.location.key == account.key) }
    private fun journal(remote: String): AtomicFile {
        require(FamilyRules.validId(remote)); return AtomicFile(File(root, "$remote.json"))
    }
    fun directory(archiveId: String): File {
        require(FamilyRules.validId(archiveId)); return File(root, archiveId)
    }
    fun archive(draft: FamilyAddDraft) = File(directory(draft.publication.archiveId), "publish.zip")
    fun photos(archiveId: String): PhotoStore = PhotoStore(object : ContextWrapper(context) {
        override fun getFilesDir() = directory(archiveId)
        override fun getCacheDir() = File(directory(archiveId), "temporary").apply { check(mkdirs() || isDirectory) }
    })
    fun get(remote: String): FamilyAddDraft? = synchronized(lock) {
        val file = journal(remote)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized null
        val bytes = file.openRead().use { input -> java.io.ByteArrayOutputStream().also { PhotoStore.copyLimited(input, it, 8192) }.toByteArray() }
        val j = JSONObject(bytes.toString(Charsets.UTF_8)); require(j.getInt("version") == 1 && j.getString("account") == account.key && j.getString("remoteId") == remote)
        FamilyAddDraft(FamilyPublication(FamilyArchiveIdentity(remote, j.getString("wardrobeId"), j.getString("ownerUid")),
            j.getString("archiveId"), j.getLong("baseRevision"), j.getString("sha256"), j.getLong("size")),
            j.getString("garmentId"), j.getString("photoId"), j.getString("name"), j.getBoolean("submitted"))
    }
    fun put(draft: FamilyAddDraft, expected: FamilyAddDraft?) = synchronized(lock) {
        require(get(draft.remoteId) == expected || get(draft.remoteId) == draft) { "Draft changed" }
        val p = draft.publication; val j = JSONObject().put("version", 1).put("account", account.key)
            .put("remoteId", draft.remoteId).put("wardrobeId", p.identity.wardrobeId).put("ownerUid", p.identity.ownerUid)
            .put("archiveId", p.archiveId).put("baseRevision", p.baseRevision).put("sha256", p.hash).put("size", p.size)
            .put("garmentId", draft.garmentId).put("photoId", draft.photoId).put("name", draft.name).put("submitted", draft.submitted)
        check(root.mkdirs() || root.isDirectory)
        require(expected != null || root.listFiles().orEmpty().count { it.extension == "json" } < 100)
        val file = journal(draft.remoteId); val out = file.startWrite()
        try { out.write(j.toString().toByteArray(Charsets.UTF_8)); out.fd.sync(); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun remove(expected: FamilyAddDraft) = synchronized(lock) {
        require(get(expected.remoteId) == expected); journal(expected.remoteId).delete()
        require(get(expected.remoteId) == null)
        directory(expected.publication.archiveId).deleteRecursively(); Unit
    }
    fun checked(draft: FamilyAddDraft, guard: () -> Unit): File = archive(draft).also {
        require(it.isFile && it.length() == draft.publication.size && FamilyArchive.digest(it, guard) == draft.publication.hash)
    }
}
