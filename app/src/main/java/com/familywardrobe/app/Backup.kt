package com.familywardrobe.app

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable, user-selected ZIP. Import adds new wardrobes and never replaces existing ones. */
class Backup(private val context: Context, private val repo: Repository, private val photos: PhotoStore) {
    /** Returns the cloud localRevision of the exported snapshot (-1 without sync metadata).
     * Only the snapshot and the photo pins take the account write lock; ZIP writing and
     * verification run without it, so the UI is not blocked for the whole archive. */
    suspend fun export(uri: Uri, guard: () -> Unit = {}, wardrobeId: String? = null): Long = withContext(Dispatchers.IO) {
        val checkpoint = { coroutineContext.ensureActive(); guard() }
        val staging = File(context.filesDir, "backup-staging").apply { mkdirs() }
        val archive = File(staging, "${newId()}.zip")
        var pinned = emptySet<String>()
        try {
            val (state, json, revision) = repo.writes.withLock {
                staging.listFiles()?.filter { file ->
                    Regex("[a-f0-9-]{36}\\.zip").matches(file.name) &&
                        System.currentTimeMillis() - file.lastModified() > 86_400_000L
                }?.forEach { it.delete() }
                checkpoint()
                val complete = repo.snapshot()
                val revision = repo.dao.cloudMeta()?.localRevision ?: -1L
                val state = if (wardrobeId == null) complete else WardrobeSnapshots.select(complete, wardrobeId)
                BackupStateRules.validate(state)
                val json = encode(state).toString().toByteArray(Charsets.UTF_8)
                require(json.size <= 8 * 1024 * 1024 && state.photoIds().size <= 10_000 && state.wears.size <= 100_000)
                val photoBytes = state.photoIds().sumOf { photos.currentFile(it).length() }
                require(photoBytes + json.size <= 20L * 1024 * 1024 * 1024)
                // Deletions of these final photos are deferred until the archive is written.
                pinned = state.photoIds(); photos.pin(pinned)
                Triple(state, json, revision)
            }
            write(archive, uri, state, json, checkpoint) { photos.unpin(pinned); pinned = emptySet() }
            revision
        } finally { photos.unpin(pinned); archive.delete() }
    }
    /**
     * FAM-04: a portable copy of an isolated one-wardrobe snapshot that is not in Room — the owner's copy of a shared
     * wardrobe as just read from the server ([photos] is that read view's store). Same format, limits and write
     * verification as [export]. Restoring it adds an independent local wardrobe; no membership is carried.
     */
    suspend fun exportSnapshot(uri: Uri, source: Snapshot, guard: () -> Unit = {}) = withContext(Dispatchers.IO) {
        val checkpoint = { coroutineContext.ensureActive(); guard() }
        val state = WardrobeSnapshots.select(source, source.wardrobes.single().id)
        BackupStateRules.validate(state)
        val json = encode(state).toString().toByteArray(Charsets.UTF_8)
        require(json.size <= 8 * 1024 * 1024 && state.photoIds().size <= 10_000 && state.wears.size <= 100_000)
        val staging = File(context.filesDir, "backup-staging").apply { mkdirs() }
        val archive = File(staging, "${newId()}.zip")
        try { write(archive, uri, state, json, checkpoint) } finally { archive.delete() }
    }
    /** ZIP of [json] + photos into [archive], then a copy to [uri] verified byte for byte. [written] runs after the ZIP. */
    private fun write(archive: File, uri: Uri, state: Snapshot, json: ByteArray, checkpoint: () -> Unit, written: () -> Unit = {}) {
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(json); zip.closeEntry()
            for (id in state.photoIds()) {
                checkpoint()
                val file = photos.currentFile(id)
                require(file.isFile && file.length() in 1..PhotoStore.MAX_PHOTO) { "Missing photo" }
                zip.putNextEntry(ZipEntry("photos/${file.name}"))
                file.inputStream().use { transfer(it, zip, checkpoint) }; zip.closeEntry()
            }
        }
        written()
        val expected = archive.inputStream().use { digest(it, checkpoint) }
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
            archive.inputStream().use { transfer(it, output, checkpoint) }
        }
        val actual = requireNotNull(context.contentResolver.openInputStream(uri)).use { digest(it, checkpoint) }
        require(MessageDigest.isEqual(expected, actual)) { "Backup verification failed" }
        checkpoint()
    }
    private fun transfer(input: InputStream, output: OutputStream, checkpoint: () -> Unit) {
        val buffer = ByteArray(64 * 1024)
        while (true) { checkpoint(); val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
    }
    private fun digest(input: InputStream, checkpoint: () -> Unit): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(64 * 1024); var total = 0L
        while (true) {
            checkpoint(); val count = input.read(buffer); if (count < 0) break
            total += count; require(total <= 20L * 1024 * 1024 * 1024 + 16L * 1024 * 1024)
            hash.update(buffer, 0, count)
        }
        return hash.digest()
    }
    /** Read every entry (including CRC) without importing or changing any existing wardrobe. */
    suspend fun verify(uri: Uri): BackupVerification = withContext(Dispatchers.IO) {
        var manifest: JSONObject? = null; val names = mutableSetOf<String>(); var total = 0L; var fingerprint = ""; val workContext = coroutineContext
        requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
            val checked = VerifiedArchiveInput(input, 20L*1024*1024*1024 + 16L*1024*1024) { workContext.ensureActive() }
            ZipInputStream(checked.buffered()).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && names.add(entry.name) && names.size <= 30_001)
                    if (entry.name == "manifest.json") {
                        val bytes = ByteArrayOutputStream()
                        total += PhotoStore.copyLimited(zip, bytes, 8L * 1024 * 1024) { workContext.ensureActive() }
                        manifest = JSONObject(bytes.toString("UTF-8"))
                    } else {
                        require(Regex("photos/[a-f0-9-]{36}\\.(jpg|original|png)").matches(entry.name))
                        total += PhotoStore.copyLimited(zip, object : OutputStream() {
                            override fun write(b: Int) {}
                            override fun write(b: ByteArray, off: Int, len: Int) {}
                        }, PhotoStore.MAX_PHOTO) { workContext.ensureActive() }
                    }
                    require(total <= 20L * 1024 * 1024 * 1024); zip.closeEntry()
                }
                fingerprint = checked.finish()
            }
        }
        val json = requireNotNull(manifest); val state = decode(json)
        val expected = mutableSetOf("manifest.json")
        require(state.photoIds().size <= 10_000)
        if (json.getInt("version") >= 3) require(json.getJSONObject("photoFiles").length() == state.photoIds().size)
        val cutouts = json.optJSONArray("cutouts")
        state.photoIds().forEach { id ->
            if (json.getInt("version") < 3) {
                expected.add("photos/$id.original"); expected.add("photos/$id.jpg")
                if (cutouts != null && (0 until cutouts.length()).any { cutouts.getString(it) == id }) expected.add("photos/$id.png")
            } else {
                val extension = json.getJSONObject("photoFiles").getString(id)
                require(extension in setOf("jpg", "png")); expected.add("photos/$id.$extension")
            }
        }
        require(names == expected)
        BackupVerification(state.wardrobes.size, state.garments.size, state.outfits.size, fingerprint)
    }
    suspend fun restore(uri: Uri, restoredLabel: String = context.getString(R.string.restored_label),
        defaultName: String = context.getString(R.string.my_wardrobe), expectedFingerprint: String? = null,
        expectedLocalRevision: Long? = null, cloudBaseRevision: Long? = null, cloudGuard: () -> Unit = {}): String? = withContext(Dispatchers.IO) {
        repo.writes.withLock {
            cloudGuard()
            if (expectedLocalRevision != null && repo.dao.cloudMeta()?.localRevision != expectedLocalRevision) throw CloudChangedException()
            val staging = File(context.cacheDir, "restore-${newId()}").apply { mkdirs() }
            val copied = mutableListOf<File>()
            try {
                val workContext = coroutineContext
                var fingerprint = ""
                var manifest: JSONObject? = null
                val names = mutableSetOf<String>(); var total = 0L
                requireNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
                    val checked = VerifiedArchiveInput(stream, 20L*1024*1024*1024 + 16L*1024*1024) { workContext.ensureActive() }
                    ZipInputStream(checked.buffered()).use { zip ->
                        while (true) {
                            workContext.ensureActive()
                            val entry = zip.nextEntry ?: break
                            require(!entry.isDirectory && names.add(entry.name) && names.size <= 30_001)
                            if (entry.name == "manifest.json") {
                                val out = ByteArrayOutputStream()
                                total += PhotoStore.copyLimited(zip, out, 8L * 1024 * 1024) { workContext.ensureActive() }
                                manifest = JSONObject(out.toString("UTF-8"))
                            } else {
                                require(Regex("photos/[a-f0-9-]{36}\\.(jpg|original|png)").matches(entry.name))
                                val target = File(staging, entry.name.substringAfter('/'))
                                total += target.outputStream().use { PhotoStore.copyLimited(zip, it, PhotoStore.MAX_PHOTO) { workContext.ensureActive() } }
                            }
                            require(total <= 20L * 1024 * 1024 * 1024)
                            zip.closeEntry()
                        }
                        fingerprint = checked.finish()
                    }
                }
                if (expectedFingerprint != null && fingerprint != expectedFingerprint) throw BackupChangedException()
                val json = requireNotNull(manifest)
                val state = decode(json)
                require(state.wishes.filter { it.ownedId.isNotBlank() }.all { wish ->
                    state.garments.any { it.id == wish.ownedId && it.wardrobeId == wish.wardrobeId } })
                require(state.ideas.all { idea -> idea.wishId == null || state.wishes.single { it.id == idea.wishId }.ownedId.isBlank() })
                val cutouts = if (json.getInt("version") == 2) {
                    val entries = json.getJSONArray("cutouts")
                    require(entries.length() <= 10_000)
                    (0 until entries.length()).map { entries.getString(it).also { id -> require(PhotoStore.ID.matches(id)) } }
                        .also { require(it.size == it.toSet().size) }.toSet()
                } else emptySet()
                val wardMap = state.wardrobes.associate { it.id to newId() }
                val categoryMap = state.customCategories.associate { it.id to newId() }
                val colorMap = state.customColors.associate { it.id to newId() }
                val itemMap = state.garments.associate { it.id to newId() }
                val outfitMap = state.outfits.associate { it.id to newId() }
                val wishMap = state.wishes.associate { it.id to newId() }
                val planMap = state.plans.associate { it.id to newId() }
                val capsuleMap = state.capsules.associate { it.id to newId() }
                val photoMap = state.photoIds().associateWith { newId() }
                require(photoMap.size <= 10_000 && photoMap.keys.containsAll(cutouts))
                val currentFormats = if (json.getInt("version") >= 3) {
                    val formats = json.getJSONObject("photoFiles")
                    require(formats.length() == photoMap.size)
                    photoMap.keys.associateWith { id -> formats.getString(id).also { require(it == "jpg" || it == "png") } }
                } else photoMap.keys.associateWith { if (it in cutouts) "png" else "jpg" }
                val expected = mutableSetOf("manifest.json")
                photoMap.keys.forEach { id ->
                    if (json.getInt("version") < 3) {
                        expected.add("photos/$id.original"); expected.add("photos/$id.jpg")
                        if (id in cutouts) expected.add("photos/$id.png")
                    } else expected.add("photos/$id.${currentFormats.getValue(id)}")
                }
                require(names == expected) { "Backup photos do not match manifest" }
                require(wardMap.size == state.wardrobes.size && itemMap.size == state.garments.size && outfitMap.size == state.outfits.size && wishMap.size == state.wishes.size)
                for ((old, fresh) in photoMap) {
                    val ext = currentFormats.getValue(old)
                    val source = File(staging, "$old.$ext")
                    photos.validateImage(source)
                    if (old in cutouts) photos.validateCutout(source, File(staging, "$old.jpg"))
                    val target = photos.file(fresh, ext)
                    copied.add(target); source.copyTo(target)
                }
                repo.db.withTransaction {
                    coroutineContext.ensureActive(); cloudGuard()
                    val dao = repo.dao
                    val usedNames = dao.allWardrobes().map { it.name }.toMutableSet()
                    val restoredAt = System.currentTimeMillis()
                    state.wardrobes.forEachIndexed { index, w ->
                        val base = if (w.usesDefaultName) defaultName else w.name
                        var number = 1
                        var restoredName: String
                        do {
                            val suffix = " — $restoredLabel ${number++}"
                            restoredName = TextLimits.takeTrimmed(base, (80 - suffix.length).coerceAtLeast(0)) + suffix
                        } while (!usedNames.add(restoredName))
                        dao.insertWardrobe(w.copy(id = wardMap.getValue(w.id), name = restoredName,
                            createdAt = restoredAt + index, usesDefaultName = false))
                    }
                    state.customCategories.forEach { c -> dao.insertCustomCategory(c.copy(id = categoryMap.getValue(c.id),
                        wardrobeId = wardMap.getValue(c.wardrobeId))) }
                    state.customColors.forEach { c -> dao.insertCustomColor(c.copy(id = colorMap.getValue(c.id),
                        wardrobeId = wardMap.getValue(c.wardrobeId))) }
                    state.garments.forEach { g -> dao.saveGarment(g.copy(id = itemMap.getValue(g.id),
                        wardrobeId = wardMap.getValue(g.wardrobeId), photoId = photoMap.getValue(g.photoId),
                        customCategoryId = g.customCategoryId?.let { categoryMap.getValue(it) },
                                customColorId = g.customColorId?.let { colorMap.getValue(it) })) }
                    state.wishes.forEach { w -> dao.insertWish(w.copy(id = wishMap.getValue(w.id), wardrobeId = wardMap.getValue(w.wardrobeId),
                        photoId = if (w.photoId.isBlank()) "" else photoMap.getValue(w.photoId),
                        needPhotoId = if (w.needPhotoId.isBlank()) "" else photoMap.getValue(w.needPhotoId),
                        ownedId = if (w.ownedId.isBlank()) "" else itemMap.getValue(w.ownedId))) }
                    state.wishEvents.forEach { e -> dao.saveWishEvent(e.copy(id = newId(), wishId = wishMap.getValue(e.wishId),
                        photoId = if (e.photoId.isBlank()) "" else photoMap.getValue(e.photoId))) }
                    state.purchaseUndos.forEach { u ->
                        val g = GarmentReceipt.read(u.garmentJson)
                        dao.savePurchaseUndo(u.copy(wishId = wishMap.getValue(u.wishId), garmentJson = GarmentReceipt.write(
                            g.copy(id = itemMap.getValue(g.id), wardrobeId = wardMap.getValue(g.wardrobeId), photoId = photoMap[g.photoId] ?: g.photoId,
                                customCategoryId = g.customCategoryId?.let { categoryMap.getValue(it) },
                                customColorId = g.customColorId?.let { colorMap.getValue(it) }))))
                    }
                    state.outfits.forEach { outfit ->
                        val oldParts = state.parts.filter { it.outfitId == outfit.id }
                        val ids = oldParts.map { itemMap.getValue(it.garmentId) }
                        val oldIdeas = state.ideas.filter { it.outfitId == outfit.id }
                        val mappedIdeas = oldIdeas.map { idea ->
                            val freshWish = idea.wishId?.let(wishMap::getValue)
                            val partId = if (idea.partId.startsWith("wish:")) "wish:${freshWish ?: newId()}" else idea.partId
                            idea.copy(outfitId = outfitMap.getValue(outfit.id), partId = partId, wishId = freshWish,
                                photoId = if (idea.photoId.isBlank()) "" else photoMap.getValue(idea.photoId))
                        }
                        val categories = oldParts.map { part -> state.garments.single { it.id == part.garmentId }.also {
                            require(it.wardrobeId == outfit.wardrobeId)
                        }.category } + oldIdeas.map { it.category }
                        require(categories.isNotEmpty() && categories.size <= WardrobeRules.MAX_PARTS && categories.all { it in Matching.categories } && ids.size == ids.distinct().size)
                        require(json.getInt("version") >= 7 || categories.distinct().size == categories.size)
                        require(json.getInt("version") >= 4 || categories.containsAll(WardrobeRules.CORE))
                        require(oldIdeas.all { it.wishId == null || state.wishes.single { w -> w.id == it.wishId }.wardrobeId == outfit.wardrobeId })
                        val signature = (ids + mappedIdeas.map { it.partId }).sorted().joinToString("|")
                        val ideaKeys = oldIdeas.zip(mappedIdeas).associate { (old, fresh) -> old.partId to fresh.partId }
                        val layout = OutfitCanvasState.remap(outfit.layout) { key ->
                            if (key.startsWith("owned:")) "owned:${itemMap.getValue(key.removePrefix("owned:"))}" else ideaKeys[key] ?: key
                        }
                        dao.insertOutfit(outfit.copy(id = outfitMap.getValue(outfit.id), wardrobeId = wardMap.getValue(outfit.wardrobeId), signature = signature, layout = layout))
                        dao.insertParts(ids.map { OutfitPart(outfitMap.getValue(outfit.id), it) })
                        dao.insertIdeas(mappedIdeas)
                    }
                    state.plans.forEach { p -> dao.savePlan(p.copy(id = planMap.getValue(p.id), wardrobeId = wardMap.getValue(p.wardrobeId),
                        outfitId = outfitMap.getValue(p.outfitId), wornItemIds = if (p.wornItemIds.isBlank()) "" else
                            DailyRules.signature(p.wornItemIds.split('|').map(itemMap::getValue)))) }
                    state.feedback.forEach { f -> dao.saveFeedback(f.copy(id = newId(), wardrobeId = wardMap.getValue(f.wardrobeId),
                        signature = DailyRules.signature(f.signature.split('|').map(itemMap::getValue)))) }
                    state.capsules.forEach { c -> dao.saveCapsule(c.copy(id = capsuleMap.getValue(c.id), wardrobeId = wardMap.getValue(c.wardrobeId))) }
                    state.capsuleOutfits.forEach { c -> dao.saveCapsuleOutfit(c.copy(capsuleId = capsuleMap.getValue(c.capsuleId), outfitId = outfitMap.getValue(c.outfitId))) }
                    state.capsuleItems.forEach { c -> dao.saveCapsuleItem(c.copy(capsuleId = capsuleMap.getValue(c.capsuleId), garmentId = itemMap.getValue(c.garmentId))) }
                    dao.insertWears(state.wears.map { wear ->
                        LocalDate.parse(wear.date)
                        val oldSource = wear.source.substringAfter(':')
                        val source = when {
                            wear.source.startsWith("item:") -> "item:${itemMap.getValue(oldSource)}"
                            wear.source.startsWith("outfit:") -> "outfit:${outfitMap.getValue(oldSource)}"
                            wear.source.startsWith("plan:") -> "plan:${planMap.getValue(oldSource)}"
                            else -> error("Unknown wear source")
                        }
                        Wear(itemMap.getValue(wear.garmentId), wear.date, source)
                    })
                    if (cloudBaseRevision != null) {
                        val meta = requireNotNull(dao.cloudMeta())
                        dao.saveCloudMeta(meta.copy(baseRevision = cloudBaseRevision, dirty = true,
                            conflictRevision = 0, conflictPath = "", conflictHash = "", error = "", uploadPath = "", uploadHash = ""))
                    }
                }
                copied.clear() // Files now belong to the committed database.
                wardMap.values.firstOrNull()
            } finally { copied.forEach { it.delete() }; staging.deleteRecursively() }
        }
    }
    private fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply { fields.forEach { put(it.first, it.second ?: JSONObject.NULL) } }
    private fun <T> arr(values: List<T>, convert: (T) -> JSONObject) = JSONArray().apply { values.forEach { put(convert(it)) } }
    internal fun encode(s: Snapshot) = obj("format" to "family-wardrobe", "version" to 10,
        "photoFiles" to JSONObject().apply {
            s.photoIds().forEach { put(it, photos.currentFile(it).extension) }
        },
        "wardrobes" to arr(s.wardrobes) { obj("id" to it.id, "name" to it.name, "createdAt" to it.createdAt,
            "usesDefaultName" to it.usesDefaultName, "noRepeatDays" to it.noRepeatDays) },
        "customCategories" to arr(s.customCategories) { obj("id" to it.id, "wardrobeId" to it.wardrobeId,
            "name" to it.name, "nameKey" to it.nameKey, "baseCategory" to it.baseCategory) },
        "customColors" to arr(s.customColors) { obj("id" to it.id, "wardrobeId" to it.wardrobeId,
            "name" to it.name, "nameKey" to it.nameKey, "baseColor" to it.baseColor) },
        "garments" to arr(s.garments) { g -> obj("id" to g.id, "wardrobeId" to g.wardrobeId, "name" to g.name,
            "category" to g.category, "color" to g.color, "size" to g.size, "location" to g.location,
            "description" to g.description, "photoId" to g.photoId, "season" to g.season, "style" to g.style,
            "availability" to g.availability, "favorite" to g.favorite, "rarelyWorn" to g.rarelyWorn,
            "archived" to g.archived, "createdAt" to g.createdAt, "priceMinor" to g.priceMinor, "currency" to g.currency, "subcategory" to g.subcategory, "suggestionExcluded" to g.suggestionExcluded, "photoFingerprint" to g.photoFingerprint, "customCategoryId" to g.customCategoryId, "customColorId" to g.customColorId) },
        "outfits" to arr(s.outfits) { obj("id" to it.id, "wardrobeId" to it.wardrobeId, "name" to it.name, "signature" to it.signature,
            "createdAt" to it.createdAt, "occasion" to it.occasion, "weather" to it.weather, "source" to it.source, "layout" to it.layout, "background" to it.background) },
        "ideas" to arr(s.ideas) { obj("outfitId" to it.outfitId, "partId" to it.partId, "wishId" to it.wishId,
            "category" to it.category, "name" to it.name, "color" to it.color, "photoId" to it.photoId) },
        "parts" to arr(s.parts) { obj("outfitId" to it.outfitId, "garmentId" to it.garmentId) },
        "wears" to arr(s.wears) { obj("garmentId" to it.garmentId, "date" to it.date, "source" to it.source) },
        "wishes" to arr(s.wishes) { w -> obj("id" to w.id, "wardrobeId" to w.wardrobeId, "name" to w.name, "category" to w.category,
            "kind" to w.kind, "priceMinor" to w.priceMinor, "currency" to w.currency, "link" to w.link, "color" to w.color,
            "inspiration" to w.inspiration, "photoId" to w.photoId, "needName" to w.needName, "needCategory" to w.needCategory,
            "requirements" to w.requirements, "budgetMinor" to w.budgetMinor, "budgetCurrency" to w.budgetCurrency, "createdAt" to w.createdAt, "ownedId" to w.ownedId,
            "needPhotoId" to w.needPhotoId, "needLink" to w.needLink, "needInspiration" to w.needInspiration) },
        "plans" to arr(s.plans) { obj("id" to it.id, "wardrobeId" to it.wardrobeId, "outfitId" to it.outfitId, "date" to it.date,
            "notes" to it.notes, "worn" to it.worn, "wornItemIds" to it.wornItemIds) },
        "feedback" to arr(s.feedback) { obj("id" to it.id, "wardrobeId" to it.wardrobeId, "signature" to it.signature,
            "weather" to it.weather, "occasion" to it.occasion, "reason" to it.reason) },
        "capsules" to arr(s.capsules) { obj("id" to it.id, "wardrobeId" to it.wardrobeId, "name" to it.name, "kind" to it.kind,
            "startDate" to it.startDate, "endDate" to it.endDate) },
        "capsuleItems" to arr(s.capsuleItems) { obj("capsuleId" to it.capsuleId, "garmentId" to it.garmentId, "manual" to it.manual, "packed" to it.packed) },
        "capsuleOutfits" to arr(s.capsuleOutfits) { obj("capsuleId" to it.capsuleId, "outfitId" to it.outfitId) },
        "purchaseUndos" to arr(s.purchaseUndos) { obj("wishId" to it.wishId, "garmentJson" to it.garmentJson, "createdAt" to it.createdAt) },
        "wishEvents" to arr(s.wishEvents) { e -> obj("id" to e.id, "wishId" to e.wishId, "kind" to e.kind, "name" to e.name, "category" to e.category,
            "priceMinor" to e.priceMinor, "currency" to e.currency, "link" to e.link, "inspiration" to e.inspiration, "photoId" to e.photoId, "createdAt" to e.createdAt) })
    private fun <T> read(json: JSONObject, key: String, convert: (JSONObject) -> T): List<T> {
        val a = json.getJSONArray(key); require(a.length() <= 100_000)
        return (0 until a.length()).map { convert(a.getJSONObject(it)) }
    }
    private fun JSONObject.text(key: String, maximum: Int = 1000): String = getString(key).also { require(it.length <= maximum) }
    private fun JSONObject.id(key: String): String = text(key).also { require(PhotoStore.ID.matches(it)) }
    private fun JSONObject.optionalMoney(key: String): Long? {
        if (isNull(key)) return null
        val raw = get(key)
        require(raw is Long || raw is Int)
        return getLong(key).also { require(WishRules.validMinor(it)) }
    }
    private fun JSONObject.optionalPhoto(): String = text("photoId").also { require(it.isEmpty() || PhotoStore.ID.matches(it)) }
    internal fun decode(j: JSONObject): Snapshot {
        require(j.getString("format") == "family-wardrobe" && j.getInt("version") in 1..10)
        val state = Snapshot(
            read(j, "wardrobes") { Wardrobe(it.id("id"), it.text("name", 80), it.getLong("createdAt"),
                it.optBoolean("usesDefaultName", it.getString("id") == Repository.DEFAULT_WARDROBE),
                if (j.getInt("version") >= 7) it.getInt("noRepeatDays").also { days -> require(days in 0..30) } else 0) },
            read(j, "garments") { g -> Garment(id = g.id("id"), wardrobeId = g.id("wardrobeId"), name = g.text("name", 120),
                category = g.text("category").also { require(it in Matching.categories) },
                color = g.text("color").also { require(it in Matching.colors) }, size = g.text("size", 40), location = g.text("location", 200),
                description = g.text("description"), photoId = g.id("photoId"),
                season = g.text("season").also { require(it in Matching.seasons) },
                style = g.text("style").also { require(it in Matching.styles) },
                availability = g.text("availability").also { require(it in Matching.availability) },
                favorite = g.getBoolean("favorite"), rarelyWorn = g.getBoolean("rarelyWorn"), archived = g.getBoolean("archived"), createdAt = g.getLong("createdAt"),
                priceMinor = if (j.getInt("version") >= 6) g.optionalMoney("priceMinor") else null,
                currency = if (j.getInt("version") >= 6) g.text("currency").also { require(it in wishCurrencies) } else "PLN",
                subcategory = if (j.getInt("version") >= 7) g.text("subcategory", 60) else "",
                suggestionExcluded = j.getInt("version") >= 7 && g.getBoolean("suggestionExcluded"),
                photoFingerprint = if (j.getInt("version") >= 7) g.text("photoFingerprint", 64).also { require(it.isEmpty() || Regex("[a-f0-9]{64}").matches(it)) } else "",
                customCategoryId = if (j.getInt("version") >= 9 && !g.isNull("customCategoryId")) g.id("customCategoryId") else null,
                customColorId = if (j.getInt("version") >= 10 && !g.isNull("customColorId")) g.id("customColorId") else null) },
            read(j, "outfits") { Outfit(it.id("id"), it.id("wardrobeId"), it.text("name", 120), it.text("signature"), it.getLong("createdAt"),
                it.optString("occasion", "everyday").also { require(it in LookRules.OCCASIONS) },
                it.optString("weather", "any_weather").also { require(it in LookRules.WEATHERS) },
                it.optString("source", "owned").also { require(it in LookRules.SOURCES) },
                if (j.getInt("version") >= 7) it.text("layout", 12_000).also { value -> OutfitCanvasState.read(value) } else "",
                if (j.getInt("version") >= 7) it.text("background").also { require(it in OutfitCanvasState.backgrounds) } else "paper") },
            read(j, "parts") { OutfitPart(it.id("outfitId"), it.id("garmentId")) },
            read(j, "wears") { Wear(it.id("garmentId"), it.text("date", 10), it.text("source", 50)) },
            read(j, "wishes") { w ->
                val basic = Wish(w.id("id"), w.id("wardrobeId"), w.text("name", 120), w.text("category"))
                (if (j.getInt("version") < 4) basic.copy(needName = basic.name, needCategory = basic.category, createdAt = 0)
                else basic.copy(kind = w.text("kind"), priceMinor = w.optionalMoney("priceMinor"), currency = w.text("currency"),
                    link = w.text("link", 2000), color = w.text("color"), inspiration = w.text("inspiration", 2000), photoId = w.optionalPhoto(),
                    needName = w.text("needName", 120), needCategory = w.text("needCategory"), requirements = w.text("requirements", 2000),
                    budgetMinor = w.optionalMoney("budgetMinor"), budgetCurrency = w.text("budgetCurrency"), createdAt = w.getLong("createdAt"),
                    ownedId = if (j.getInt("version") >= 5) w.text("ownedId", 36) else "",
                    needPhotoId = if (j.getInt("version") >= 8) w.text("needPhotoId", 36) else "",
                    needLink = if (j.getInt("version") >= 8) w.text("needLink", 2000) else "",
                    needInspiration = if (j.getInt("version") >= 8) w.text("needInspiration", 2000) else "")).also(::validateWish)
            },
            if (j.getInt("version") < 4) emptyList() else read(j, "ideas") { idea ->
                OutfitIdea(idea.id("outfitId"), idea.text("partId", 60).also { require((Regex("wish:[a-f0-9-]{36}").matches(it) || InspirationCatalog.validKey(it, idea.getString("category")))) },
                    if (idea.isNull("wishId")) null else idea.id("wishId"), idea.text("category").also { require(it in Matching.categories) },
                    idea.text("name", 120), idea.text("color").also { require(it.isEmpty() || it in Matching.colors) }, idea.optionalPhoto())
            },
            if (j.getInt("version") < 6) emptyList() else read(j, "plans") { p -> DayPlan(p.id("id"), p.id("wardrobeId"), p.id("outfitId"),
                p.text("date", 10), p.text("notes"), p.getBoolean("worn"), p.text("wornItemIds", 443)) },
            if (j.getInt("version") < 6) emptyList() else read(j, "feedback") { f -> OutfitFeedback(f.id("id"), f.id("wardrobeId"),
                f.text("signature", 443), f.text("weather"), f.text("occasion"), f.text("reason")) },
            if (j.getInt("version") < 6) emptyList() else read(j, "capsules") { c -> Capsule(c.id("id"), c.id("wardrobeId"), c.text("name", 80),
                c.text("kind"), c.text("startDate", 10), c.text("endDate", 10)) },
            if (j.getInt("version") < 6) emptyList() else read(j, "capsuleItems") { c -> CapsuleItem(c.id("capsuleId"), c.id("garmentId"), c.getBoolean("manual"), c.getBoolean("packed")) },
            if (j.getInt("version") < 6) emptyList() else read(j, "capsuleOutfits") { c -> CapsuleOutfit(c.id("capsuleId"), c.id("outfitId")) },
            if (j.getInt("version") < 8) emptyList() else read(j, "purchaseUndos") { c ->
                PurchaseUndoRow(c.id("wishId"), c.text("garmentJson", 20_000), c.getLong("createdAt"))
            },
            if (j.getInt("version") < 8) emptyList() else read(j, "wishEvents") { e ->
                WishEvent(e.id("id"), e.id("wishId"), e.text("kind", 24), e.text("name", 120), e.text("category"),
                    e.optionalMoney("priceMinor"), e.text("currency"), e.text("link", 2000), e.text("inspiration", 2000), e.optionalPhoto(), e.getLong("createdAt"))
            },
            if (j.getInt("version") < 9) emptyList() else read(j, "customCategories") { c ->
                CustomCategory(c.id("id"), c.id("wardrobeId"), c.text("name", 60), c.text("nameKey", 180), c.text("baseCategory", 30))
            },
            if (j.getInt("version") < 10) emptyList() else read(j, "customColors") { c ->
                CustomColor(c.id("id"), c.id("wardrobeId"), c.text("name", 60), c.text("nameKey", 180), c.text("baseColor", 30))
            }
        )
        val restored = if (j.getInt("version") >= 6) state else state.copy(garments = state.garments.map { garment ->
            val purchase = state.wishes.filter { it.ownedId == garment.id }.maxWithOrNull(compareBy<Wish> { it.createdAt }.thenBy { it.id })
            if (purchase == null) garment else garment.copy(priceMinor = purchase.priceMinor, currency = purchase.currency)
        })
        BackupStateRules.validate(restored)
        return restored
    }
}
