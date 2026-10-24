package com.familywardrobe.app

import android.content.ContextWrapper
import android.util.AtomicFile
import java.io.File

internal data class FamilyAddDraft(val publication: FamilyPublication, val garmentId: String, val photoId: String,
    val name: String, val submitted: Boolean = false) {
    init { require(FamilyRules.validId(garmentId) && FamilyRules.validId(photoId) && name.isNotBlank() && name.length <= 120) }
    val remoteId get() = publication.identity.remoteId
}

/**
 * Ordered durable immutable publication drafts per wardrobe/account; never part of portable backup.
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
    fun list(remote: String): List<FamilyAddDraft> = synchronized(lock) { read(remote) }
    fun get(remote: String, garment: String? = null): FamilyAddDraft? = synchronized(lock) {
        read(remote).find { garment == null || it.garmentId == garment }
    }
    private fun read(remote: String): List<FamilyAddDraft> {
        val file = journal(remote)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val bytes = file.openRead().use { input -> java.io.ByteArrayOutputStream().also { PhotoStore.copyLimited(input, it, 64L * 1024) }.toByteArray() }
        return FamilyMediaQueue.decode(bytes.toString(Charsets.UTF_8), account.key, remote)
    }
    fun put(draft: FamilyAddDraft, expected: FamilyAddDraft?) = synchronized(lock) {
        val rows = FamilyMediaQueue.put(read(draft.remoteId), draft, expected)
        write(draft.remoteId, rows)
    }
    private fun write(remote: String, rows: List<FamilyAddDraft>) {
        val bytes = FamilyMediaQueue.encode(rows, account.key, remote).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 64 * 1024); check(root.mkdirs() || root.isDirectory)
        val file = journal(remote)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists() &&
            root.listFiles().orEmpty().count { it.extension == "json" } >= 100) throw FamilyTransferException(FamilyProblem.LIMIT)
        val out = file.startWrite()
        try { out.write(bytes); out.fd.sync(); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun remove(expected: FamilyAddDraft) = synchronized(lock) {
        val rows = FamilyMediaQueue.remove(read(expected.remoteId), expected)
        if (rows.isEmpty()) journal(expected.remoteId).delete() else write(expected.remoteId, rows)
        require(get(expected.remoteId, expected.garmentId) == null)
        directory(expected.publication.archiveId).deleteRecursively(); Unit
    }
    fun checked(draft: FamilyAddDraft, guard: () -> Unit): File = archive(draft).also {
        require(it.isFile && it.length() == draft.publication.size && FamilyArchive.digest(it, guard) == draft.publication.hash)
    }
}
