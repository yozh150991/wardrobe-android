package com.familywardrobe.app

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

/** Apply a verified cloud snapshot with stable IDs. Portable backup restore stays additive. */
internal class CloudArchive(private val context: Context,private val repo: Repository,private val photos: PhotoStore) {
    suspend fun apply(file: File,remote: CloudHead,expectedLocal: Long,guard: ()->Unit) = repo.writes.withLock {
        guard()
        if(EditorDraftStore(context).read()!=null || repo.dao.allImports().isNotEmpty())throw CloudDraftException()
        val photoQueue = PhotoReprocessStore(context).state.value
        if(photoQueue.entries.isNotEmpty() || photoQueue.unreadable)throw CloudDraftException()
        if((repo.dao.cloudMeta()?.localRevision ?: 0L)!=expectedLocal)throw CloudChangedException()
        val oldPhotos=repo.referencedPhotos()
        val staging=File(context.cacheDir,"cloud-apply-${newId()}").apply {check(mkdirs())}
        val copied=mutableListOf<File>()
        try {
            var manifest: JSONObject?=null
            val names=mutableSetOf<String>();var total=0L
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                while(true) {
                    coroutineContext.ensureActive();guard();val entry=zip.nextEntry ?: break
                    require(!entry.isDirectory && names.add(entry.name) && names.size<=10_001)
                    if(entry.name=="manifest.json") {
                        val out=java.io.ByteArrayOutputStream();total+=PhotoStore.copyLimited(zip,out,8L*1024*1024)
                        manifest=JSONObject(out.toString("UTF-8"))
                    } else {
                        require(Regex("photos/[a-f0-9-]{36}\\.(jpg|png)").matches(entry.name))
                        total+=File(staging,entry.name.substringAfter('/')).outputStream().use {PhotoStore.copyLimited(zip,it,PhotoStore.MAX_PHOTO)}
                    }
                    require(total<=CloudBackend.MAX_ARCHIVE);zip.closeEntry()
                }
            }
            val json=requireNotNull(manifest);require(json.getInt("version") in 8..10)
            val state=Backup(context,repo,photos).decode(json)
            val expected=state.photoIds().map { "photos/$it.${json.getJSONObject("photoFiles").getString(it)}" }.toSet()+"manifest.json"
            require(names==expected && state.wardrobes.size==remote.wardrobes && state.garments.size==remote.garments && state.outfits.size==remote.outfits)
            state.photoIds().forEach { id ->
                val ext=json.getJSONObject("photoFiles").getString(id);val source=File(staging,"$id.$ext");val target=photos.file(id,ext)
                photos.validateImage(source)
                if(photos.currentFile(id).exists())require(photos.currentFile(id).extension==ext) {"Photo format collision"}
                if(target.exists())require(hash(source)==hash(target)) {"Photo ID collision"}
                else {copied.add(target);check(source.copyTo(target).isFile)}
            }
            repo.db.withTransaction {
                coroutineContext.ensureActive();guard();val before=requireNotNull(repo.dao.cloudMeta())
                if(before.localRevision!=expectedLocal)throw CloudChangedException()
                repo.dao.saveCloudMeta(before.copy(applying=true))
                // References are removed first; this transaction either replaces all records or none.
                val db=repo.db.openHelper.writableDatabase
                listOf("capsule_items","capsule_outfits","day_plans","outfit_feedback","capsules","outfit_parts","outfit_ideas","outfits","wears","purchase_undo","wish_events","wishes","garments","custom_colors","custom_categories","wardrobes").forEach {db.execSQL("DELETE FROM $it")}
                state.wardrobes.forEach {repo.dao.insertWardrobe(it)}
                state.customCategories.forEach {repo.dao.insertCustomCategory(it)}
                state.customColors.forEach {repo.dao.insertCustomColor(it)};state.garments.forEach {repo.dao.saveGarment(it)}
                state.wishes.forEach {repo.dao.insertWish(it)};state.wishEvents.forEach {repo.dao.saveWishEvent(it)};state.outfits.forEach {repo.dao.insertOutfit(it)}
                repo.dao.insertParts(state.parts);repo.dao.insertIdeas(state.ideas);repo.dao.insertWears(state.wears)
                state.plans.forEach {repo.dao.savePlan(it)};state.feedback.forEach {repo.dao.saveFeedback(it)}
                state.capsules.forEach {repo.dao.saveCapsule(it)};state.capsuleItems.forEach {repo.dao.saveCapsuleItem(it)}
                state.capsuleOutfits.forEach {repo.dao.saveCapsuleOutfit(it)};state.purchaseUndos.forEach {repo.dao.savePurchaseUndo(it)}
                repo.dao.saveCloudMeta(before.copy(applying=false,dirty=false,baseRevision=remote.revision,conflictRevision=0,conflictPath="",conflictHash="",error="",lastSuccess=System.currentTimeMillis(),uploadPath="",uploadHash=""))
            }
            copied.clear();photos.clearMemoryCache()
            photos.remove(oldPhotos-repo.referencedPhotos())
        } finally {copied.forEach {it.delete()};staging.deleteRecursively()}
    }
    private fun hash(file: File): String {
        val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use {stream ->val buffer=ByteArray(65536);while(true){val n=stream.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
}
