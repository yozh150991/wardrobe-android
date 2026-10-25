package com.familywardrobe.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import org.json.JSONObject

data class PhotoWork(val owner: String = "", val source: String = "", val running: Boolean = false, val original: String = "")

class WardrobeViewModel internal constructor(application: Application, private val session: AccountSession) : AndroidViewModel(application) {
    internal val sessionToken = session.id
    private val accountContext = session.context
    val repo = session.repository
    val photos = session.photos
    val network = (application as WardrobeApplication).network
    internal val cloud = CloudSyncController(application as WardrobeApplication, session, viewModelScope)
    override fun onCleared() {
        familyReader.invalidateSession()
        familyAccess.invalidateSession()
        familyEdit.invalidateSession()
        familyAdd.invalidateSession()
        familyPhoto.invalidateSession()
        familyOutfit.invalidateSession()
        super.onCleared()
    }
    internal val familyReader = FamilyReaderController(application as WardrobeApplication, session, viewModelScope)
    internal val familySync = FamilySyncController(application as WardrobeApplication, session, familyReader, viewModelScope)
    internal val familyEdit = FamilyEditController(application as WardrobeApplication, session, familyReader, viewModelScope)
    internal val familyAdd = FamilyAddController(application as WardrobeApplication, session, familyReader, viewModelScope)
    internal val familyPhoto = FamilyPhotoController(application as WardrobeApplication, session, familyReader, viewModelScope)
    internal val familyOutfit = FamilyOutfitController(application as WardrobeApplication, session, familyReader, viewModelScope)
    internal val familyAccess = FamilyAccessController(application as WardrobeApplication, session, familyReader, viewModelScope)
    fun readFamily(remoteId: String, refresh: Boolean = false) = perform(OperationKind.CLOUD, cancellable = true) { familyReader.open(remoteId, refresh) }
    internal val family = FamilyTransferController(application as WardrobeApplication, session, viewModelScope)
    fun transferFamily(id: String, restart: Boolean = false) = perform(OperationKind.CLOUD, cancellable = true) { family.transfer(id, restart) }
    fun sendFamilyVerification() = perform(OperationKind.CLOUD) { family.sendVerification() }
    fun enableCloud(on: Boolean) = perform(OperationKind.CLOUD) { cloud.enable(on) }
    fun resolveCloud(choice: String, local: Long, remote: Long) = perform(OperationKind.CLOUD) {
        cloud.resolve(choice, local, remote); cloud.retry(); notice.value = R.string.cloud_resolution_done
    }
    private val prefs = accountContext.getSharedPreferences("preferences", 0)
    val active = MutableStateFlow(prefs.getString("wardrobe", Repository.DEFAULT_WARDROBE)!!)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow(false)
    val failure = MutableStateFlow<OperationFailure?>(null)
    val operation = MutableStateFlow<OperationState?>(null)
    val recommendationRefresh = MutableStateFlow(0)
    private var operationJob: Job? = null
    private var failedRetry: (() -> Unit)? = null
    val notice = MutableStateFlow<Int?>(null)
    val initialized = MutableStateFlow(false)
    private val shareInbox = (application as WardrobeApplication).sharedImports
    internal val pendingShares = shareInbox.entries.map { rows -> rows.filter { it.account.isBlank() || it.account == session.identity.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savedItem = MutableStateFlow<SavedItemNotice?>(null)
    val nextReviewId = MutableStateFlow<String?>(null)
    val savedWish = MutableStateFlow<Wish?>(null)
    // Account-private storage: reopening another UID cannot expose this draft.
    private val draftStore = EditorDraftStore(accountContext)
    private var editorDraft: JSONObject? = draftStore.read()
    internal val photoEditRevision = MutableStateFlow(0)
    fun draft(): JSONObject? = editorDraft
    fun draftFor(kind: String, wardrobe: String, original: String): JSONObject? = editorDraft?.takeIf {
        it.optString("kind") == kind && it.optString("wardrobe") == wardrobe && it.optString("original") == original
    }?.optJSONObject("fields")
    fun keepDraft(kind: String, wardrobe: String, original: String, fields: JSONObject) {
        val next = PhotoSessionDraft.merge(editorDraft, JSONObject().put("kind", kind).put("wardrobe", wardrobe)
            .put("original", original).put("fields", JSONObject(fields.toString())))
        writeEditorDraft(next)
    }
    private fun writeEditorDraft(next: JSONObject): Boolean {
        if (next.toString() == editorDraft?.toString()) return true
        // Small synchronous commit prevents an immediate process death from losing the last typed value.
        if (draftStore.write(next)) {
            draftPhotos.addAll(protectedDraftPhotos() + PhotoSessionDraft.referencedPhotos(next.optJSONObject("fields")))
            editorDraft = next; photoEditRevision.value++; return true
        }
        else notice.value = R.string.draft_save_failed
        return false
    }
    internal fun photoSessionValue(owner: String, photo: String): JSONObject? = editorDraft?.takeIf {
        PhotoSessionDraft.owns(it, owner, photo)
    }?.optJSONObject("fields")?.optJSONObject(PhotoSessionDraft.FIELD)?.let { JSONObject(it.toString()) }
    internal fun keepPhotoSession(owner: String, session: PhotoSessionDraft): Boolean {
        if (photoOwner != owner || !PhotoSessionDraft.owns(editorDraft, owner, session.photoId)) return false
        val next = JSONObject(requireNotNull(editorDraft).toString())
        val fields = next.getJSONObject("fields")
        if (session.history.steps.isEmpty()) fields.remove(PhotoSessionDraft.FIELD)
        else fields.put(PhotoSessionDraft.FIELD, session.json())
        return writeEditorDraft(next)
    }
    internal fun discardPhotoSession(owner: String, photo: String): Boolean {
        if (photoOwner != owner || !PhotoSessionDraft.owns(editorDraft, owner, photo)) return false
        val next = JSONObject(requireNotNull(editorDraft).toString())
        next.getJSONObject("fields").remove(PhotoSessionDraft.FIELD)
        return writeEditorDraft(next)
    }
    fun clearDraft() { cancelPhotoWork(); if (draftStore.clear()) { editorDraft = null; photoEditRevision.value++ } else notice.value = R.string.draft_save_failed }
    internal fun beginShopImport(sharedId: String? = null): Boolean {
        if (busy.value) return false
        val existing = editorDraft
        if (existing != null) {
            if (existing.optString("kind") != "shop") { notice.value = R.string.shop_finish_editor; return false }
            return true
        }
        val shared = if (sharedId != null) shareInbox.claim(sharedId, session.identity.key) ?: return false else null
        val text = shared?.text.orEmpty()
        val id = newId()
        keepDraft("shop", active.value, "", JSONObject().put("id", id).put("input", ShopImportRules.urls(text).singleOrNull().orEmpty())
            .put("sharedText", text).put("shareId", shared?.id.orEmpty()))
        if (editorDraft?.optJSONObject("fields")?.optString("id") != id) return false
        shared?.let { shareInbox.complete(it.id, session.identity.key) }
        return true
    }
    internal fun loadShopMetadata(url: String, done: (List<ShopMetadata.Product>) -> Unit) = perform(OperationKind.SHOP, cancellable = true) {
        done(ShopImportClient.metadata(url))
    }
    internal fun loadShopPhoto(url: String, done: (String) -> Unit) = perform(OperationKind.SHOP, cancellable = true) {
        val file = ShopImportClient.image(url, accountContext.cacheDir)
        try { val id = photos.import(Uri.fromFile(file)); trackDraft(id); updateDraftPhoto(id); done(id) }
        finally { file.delete() }
    }
    internal fun prepareShopEditor(wardrobe: String, target: String, needId: String, name: String, link: String,
        photo: String, price: String, currency: String): ShopEditorTarget? {
        if (busy.value || editorDraft?.optString("kind") != "shop") return null
        val snapshot = state.value
        if (snapshot.wardrobes.none { it.id == wardrobe } || target !in setOf("wish", "garment")) return null
        val need = if (target == "wish" && needId.isNotBlank()) snapshot.wishes.find {
            it.id == needId && it.wardrobeId == wardrobe && it.kind == "need" && it.ownedId.isBlank()
        } ?: return null else null
        val id = need?.id ?: editorDraft?.optJSONObject("fields")?.optString("id")?.takeIf(PhotoStore.ID::matches) ?: newId()
        val finalPrice = if (currency in wishCurrencies) price else ""
        val fields = JSONObject().put("id", id).put("name", name.ifBlank { need?.name.orEmpty() }).put("category", need?.category.orEmpty())
            .put("photo", photo.ifBlank { need?.photoId.orEmpty() }).put("price", finalPrice).put("currency", currency.takeIf { it in wishCurrencies } ?: "PLN")
            .put("kind", "product").put("link", link.ifBlank { need?.link.orEmpty() }).put("advanced", true)
            .put("converting", need != null).put("requirements", need?.requirements.orEmpty())
            .put("budget", moneyInput(need?.budgetMinor)).put("budgetCurrency", need?.budgetCurrency ?: "PLN")
            .put("inspiration", need?.inspiration.orEmpty()).put("shopImported", target == "garment").put("shopLink", link)
        keepDraft(target, wardrobe, need?.id.orEmpty(), fields)
        if (editorDraft?.optString("kind") != target || editorDraft?.optJSONObject("fields")?.optString("id") != id) return null
        return ShopEditorTarget(wardrobe, target, need?.id.orEmpty())
    }
    private fun updateDraftPhoto(id: String) {
        val draft = editorDraft ?: return
        val fields = draft.optJSONObject("fields") ?: return
        val fresh = JSONObject(fields.toString()).put("photo", id)
        keepDraft(draft.getString("kind"), draft.getString("wardrobe"), draft.getString("original"), fresh)
    }
    private fun protectedDraftPhotos(): Set<String> = PhotoSessionDraft.referencedPhotos(editorDraft?.optJSONObject("fields"))
    val availabilityUndo = MutableStateFlow<AvailabilityChange?>(null)
    val purchaseUndo = MutableStateFlow<PurchaseUndo?>(null)
    val bulkUndo = MutableStateFlow<BulkChange?>(null)
    val photoWork = MutableStateFlow(PhotoWork())
    private val photoGate = PhotoWorkGate()
    private val photoQueue = Mutex()
    private var photoOwner = ""
    fun activatePhotoEditor(key: String) { photoOwner = key; photoGate.activate(key) }
    fun releasePhotoEditor(key: String) { if (photoOwner == key) { cancelPhotoWork(); photoOwner = "" } }
    fun cancelPhotoWork() { photoGate.cancel(); photoWork.value = photoWork.value.copy(running = false) }
    fun latestEditorPhoto(key: String): String? = editorPhotos.value[key]
    val photoAnalysis = MutableStateFlow<PhotoAnalysis?>(null)
    private val editorPhotos = MutableStateFlow<Map<String, String>>(emptyMap())
    fun editorPhoto(key: String) = editorPhotos.map { it[key] }
    fun rememberEditorPhoto(key: String, id: String, manual: Boolean = true) { if (manual) cancelPhotoWork(); editorPhotos.value = editorPhotos.value + (key to id); updateDraftPhoto(id) }
    fun forgetEditorPhotos() { cancelPhotoWork(); photoOwner = ""; photoWork.value = PhotoWork(); editorPhotos.value = emptyMap() }
    private val draftPhotos = prefs.getStringSet("draftPhotos", emptySet()).orEmpty().toMutableSet()
    private val analyzer = session.analyzer
    val imports = repo.dao.imports().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val batchImporter = BatchImporter(accountContext, repo, photos, analyzer, photoQueue, viewModelScope) { notice.value = it }
    internal val photoReprocessor = PhotoReprocessor(PhotoReprocessStore(accountContext), repo, photos, analyzer, photoQueue, viewModelScope) { notice.value = it }
    init { repo.editorDraftPhotos = ::protectedDraftPhotos }
    internal fun reprocessPhotos(wardrobe: String, ids: List<String>, done: () -> Unit) = perform(OperationKind.PHOTO) {
        photoReprocessor.begin(wardrobe, ids); done()
    }
    internal fun acceptReprocessedPhoto(owner: String, entry: PhotoReprocessEntry) = perform(OperationKind.PHOTO) {
        trackDraft(entry.sourceId); photoReprocessor.accept(owner, entry)
    }
    internal fun skipReprocessedPhoto(owner: String, entry: PhotoReprocessEntry) = perform(OperationKind.PHOTO) {
        trackDraft(entry.sourceId); photoReprocessor.skip(owner, entry)
    }
    internal fun cancelReprocessing(owner: String) = perform(OperationKind.PHOTO) {
        trackDrafts(photoReprocessor.state.value.entries.map { it.sourceId })
        photoReprocessor.cancel(owner)
    }
    private val backup = Backup(accountContext, repo, photos)
    private val backupHealthStore = BackupHealthStore(accountContext)
    val backupHealth = backupHealthStore.changes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), backupHealthStore.read())
    val scheduledBackup = BackupSchedule.states(accountContext, session.identity.key).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduledBackupState.IDLE)
    val restorePreview = MutableStateFlow<RestorePreview?>(null)
    val backupVerification = MutableStateFlow<BackupVerification?>(null)
    fun chooseBackupFolder(uri: Uri) = perform(OperationKind.FOLDER) {
        backupHealthStore.chooseFolder(uri); BackupSchedule.update(accountContext, session.identity.key, false)
    }
    fun enableScheduledBackup(enabled: Boolean) = perform(OperationKind.FOLDER) {
        backupHealthStore.setEnabled(enabled); BackupSchedule.update(accountContext, session.identity.key, enabled)
    }
    fun verifyBackup(uri: Uri) = perform(OperationKind.VERIFY, cancellable = true) { backupVerification.value = null; backupVerification.value = backup.verify(uri) }
    fun previewRestore(uri: Uri) = perform(OperationKind.PREVIEW_RESTORE, cancellable = true) {
        restorePreview.value = null
        restorePreview.value = RestorePreview(uri.toString(), backup.verify(uri))
    }
    val weather = WeatherController(accountContext, viewModelScope)
    val state = combine(repo.dao.wardrobes(), repo.dao.garments(), repo.dao.outfits(), repo.dao.parts(), repo.dao.wears()) {
        w, g, o, p, wear -> Snapshot(w, g, o, p, wear)
    }.combine(repo.dao.wishes()) { s, wishes -> s.copy(wishes = wishes) }
        .combine(repo.dao.ideas()) { s, ideas -> s.copy(ideas = ideas) }
        .combine(repo.dao.plans()) { s, plans -> s.copy(plans = plans) }
        .combine(repo.dao.feedback()) { s, feedback -> s.copy(feedback = feedback) }
        .combine(repo.dao.capsules()) { s, capsules -> s.copy(capsules = capsules) }
        .combine(repo.dao.capsuleItems()) { s, items -> s.copy(capsuleItems = items) }
        .combine(repo.dao.capsuleOutfits()) { s, outfits -> s.copy(capsuleOutfits = outfits) }
        .combine(repo.dao.purchaseUndos()) { s, rows -> s.copy(purchaseUndos = rows) }
        .combine(repo.dao.wishEvents()) { s, rows -> s.copy(wishEvents = rows) }
        .combine(repo.dao.customCategories()) { s, rows -> s.copy(customCategories = rows) }
        .combine(repo.dao.customColors()) { s, rows -> s.copy(customColors = rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Snapshot())

    init {
        session.privateJob = viewModelScope.coroutineContext[kotlinx.coroutines.Job]
        perform(OperationKind.INITIALIZE) {
        repo.initialize(application.getString(R.string.my_wardrobe))
        editorDraft?.optString("wardrobe")?.takeIf { id -> repo.dao.allWardrobes().any { it.id == id } }?.let { active.value = it }
        val legacyClean = photos.removeLegacyCopies()
        val retried = photos.retryRemovals(repo.referencedPhotos() + draftPhotos + protectedDraftPhotos())
        if (!legacyClean || !retried) notice.value = R.string.photo_cleanup_failed
        photoReprocessor.recover()
        initialized.value = true
        batchImporter.recover()
        }
        viewModelScope.launch {
            repo.dao.wardrobes().collect { rows ->
                if (initialized.value && rows.isNotEmpty() && rows.none { it.id == active.value }) select(rows.first().id)
            }
        }
    }
    fun undoStoredPurchase(wishId: String) = perform {
        val row = repo.dao.purchaseUndoRow(wishId)
        if (row == null || !repo.undoPurchase(PurchaseUndo(wishId, GarmentReceipt.read(row.garmentJson)), photos)) notice.value = R.string.purchase_undo_unavailable
        else notice.value = R.string.purchase_undo_done
    }
    fun returnToNeed(wishId: String) = perform {
        notice.value = if (repo.returnToNeed(wishId)) R.string.candidate_undo_done else R.string.purchase_undo_unavailable
    }
    fun select(id: String) { availabilityUndo.value = null; bulkUndo.value = null; savedItem.value = null; active.value = id; prefs.edit().putString("wardrobe", id).apply() }
    fun dismissFailure() { error.value = false; failure.value = null; failedRetry = null }
    fun retryFailure() { val retry = failedRetry; dismissFailure(); retry?.invoke() }
    fun reportFailure(kind: OperationKind, cause: Exception, retry: (() -> Unit)? = null) {
        val problem = operationFailure(kind, cause)
        failedRetry = retry
        failure.value = if (problem.action == FailureAction.RETRY && retry == null) problem.copy(action = FailureAction.CLOSE) else problem
        error.value = true
    }
    fun refreshRecommendations() { recommendationRefresh.value++; dismissFailure() }
    fun cancelOperation() {
        val work = operation.value ?: return
        if (work.cancellable) { operation.value = work.copy(cancelling = true); operationJob?.cancel() }
    }
    fun perform(kind: OperationKind = OperationKind.SAVE, cancellable: Boolean = false, block: suspend () -> Unit) {
        // Say why a tap did nothing instead of silently dropping it.
        if (busy.value) { if (notice.value == null) notice.value = R.string.operation_wait; return }
        busy.value = true; dismissFailure(); operation.value = OperationState(kind, cancellable)
        operationJob = viewModelScope.launch {
            try { block() } catch (e: CancellationException) {
                if (operation.value?.cancelling == true && kind != OperationKind.EXPORT) notice.value = if (kind == OperationKind.IMPORT) R.string.operation_import_cancelled else R.string.operation_cancelled
                throw e
            }
            catch (e: Exception) {
                reportFailure(kind, e) { perform(kind, cancellable, block) }
            }
            finally { operation.value = null; busy.value = false; operationJob = null }
        }
    }
    private fun trackDraft(id: String) = trackDrafts(listOf(id))
    private fun trackDrafts(ids: Collection<String>) {
        draftPhotos.addAll(ids)
        prefs.edit().putStringSet("draftPhotos", draftPhotos.toSet()).commit()
    }
    fun importPhoto(uri: Uri, done: (String) -> Unit) = perform(OperationKind.PHOTO, cancellable = true) {
        cancelPhotoWork()
        val id = photos.import(uri)
        trackDraft(id)
        photoAnalysis.value = null
        done(id)
        startPhotoAnalysis(id)
    }
    fun importReferencePhoto(uri: Uri, done: (String) -> Unit) = perform(OperationKind.PHOTO, cancellable = true) {
        val id = photos.import(uri); trackDraft(id); updateDraftPhoto(id); done(id)
    }
    private fun startPhotoAnalysis(id: String, prepareModel: Boolean = false, stronger: Boolean = false) {
        val owner = photoOwner
        if (photoSessionValue(owner, id) != null) { notice.value = R.string.photo_session_finish; return }
        if (owner.isBlank() || busy.value && editorPhotos.value[owner] != id) return
        // Retry from the temporary pre-cutout photo while it is still available, never compound erosion.
        val sourceId = photoWork.value.takeIf { it.owner == owner && it.source == id }
            ?.original?.takeIf { it.isNotBlank() } ?: id
        val original = sourceId.takeIf { it != id }.orEmpty()
        val generation = photoGate.begin(owner, id)
        photoAnalysis.value = null
        photoWork.value = PhotoWork(owner, id, running = true, original = original)
        viewModelScope.launch {
            photoQueue.withLock {
                if (!photoGate.accepts(generation, owner, id)) return@withLock
                var result: PhotoAnalysis? = null
                var fresh: String? = null
                try {
                    if (prepareModel) analyzer.requestModel()
                    val analysis = analyzer.analyze(sourceId, stronger)
                    result = analysis
                    if (!photoGate.accepts(generation, owner, id)) return@withLock
                    val candidate = analysis.cutoutPath?.let(::File)
                    if (candidate != null && analysis.cutoutStatus == CutoutStatus.READY) {
                        val created = photos.copyVariant(sourceId, candidate)
                        fresh = created
                        if (!photoGate.accepts(generation, owner, id)) return@withLock
                        trackDraft(created)
                        rememberEditorPhoto(owner, created, manual = false)
                        photoAnalysis.value = analysis.copy(sourceId = created, cutoutPath = null)
                        photoWork.value = PhotoWork(owner, created, original = sourceId)
                    } else {
                        photoAnalysis.value = analysis.copy(sourceId = id)
                        photoWork.value = PhotoWork(owner, id, original = original)
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    if (photoGate.accepts(generation, owner, id)) photoAnalysis.value = photoFailure(id, e)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (photoGate.accepts(generation, owner, id)) photoAnalysis.value = photoFailure(id, e)
                } finally {
                    result?.cutoutPath?.let { File(it).delete() }
                    fresh?.takeIf { editorPhotos.value[owner] != it }?.let { orphan ->
                        withContext(NonCancellable) { repo.discardPhotos(setOf(orphan), photos) }
                    }
                    if (photoGate.accepts(generation, owner, id)) photoWork.value = photoWork.value.copy(running = false)
                }
            }
        }
    }
    fun analyzePhoto(id: String) = startPhotoAnalysis(id)
    fun cleanPhotoStronger(id: String) = startPhotoAnalysis(id, stronger = true)
    fun preparePhotoModel(id: String) = startPhotoAnalysis(id, prepareModel = true)
    fun keepPhotoBackground() {
        val work = photoWork.value
        if (photoSessionValue(photoOwner, work.source) != null) { notice.value = R.string.photo_session_finish; return }
        if (work.owner == photoOwner && work.original.isNotBlank()) {
            rememberEditorPhoto(work.owner, work.original)
            photoAnalysis.value = null
            photoWork.value = PhotoWork(work.owner, work.original)
        } else cancelPhotoWork()
    }
    fun applyCutout(result: PhotoAnalysis, done: (String) -> Unit) = perform(OperationKind.PHOTO) {
        val candidate = File(requireNotNull(result.cutoutPath))
        require(candidate.canonicalFile.parentFile == File(accountContext.cacheDir, "photo-analysis").canonicalFile)
        val fresh = photos.copyVariant(result.sourceId, candidate)
        trackDraft(fresh); candidate.delete(); done(fresh)
    }
    fun editPhoto(id: String, turns: Int, left: Float, top: Float, right: Float, bottom: Float,
        done: (String) -> Unit) = perform(OperationKind.PHOTO, cancellable = true) {
        cancelPhotoWork()
        val fresh = photos.edit(id, turns, left, top, right, bottom)
        trackDraft(fresh)
        photoAnalysis.value = null
        done(fresh)
    }
    fun discardDraftPhotos() {
        if (draftPhotos.isEmpty() || busy.value) return
        perform(OperationKind.CLEANUP) {
            val candidates = draftPhotos.toSet() - protectedDraftPhotos()
            if (!repo.discardPhotos(candidates, photos)) notice.value = R.string.photo_cleanup_failed
            // Failed deletions are persisted in PhotoStore's retry queue, not retried in a UI loop.
            draftPhotos.removeAll(candidates)
            prefs.edit().putStringSet("draftPhotos", draftPhotos.toSet()).apply()
            photoAnalysis.value?.cutoutPath?.let { File(it).delete() }
            photoAnalysis.value = null
        }
    }
    fun save(item: Garment, done: () -> Unit) = perform {
        cancelPhotoWork()
        if (!repo.save(item.copy(photoFingerprint = if (repo.dao.garment(item.id)?.photoId == item.photoId) item.photoFingerprint else photos.fingerprint(photoWork.value.original.ifBlank { item.photoId })), photos)) notice.value = R.string.photo_cleanup_failed
        clearDraft()
        // Release obsolete originals and variants after commit, including consecutive saves.
        val candidates = draftPhotos.toSet()
        if (!repo.discardPhotos(candidates, photos)) notice.value = R.string.photo_cleanup_failed
        draftPhotos.removeAll(candidates)
        prefs.edit().putStringSet("draftPhotos", draftPhotos.toSet()).apply()
        photoAnalysis.value?.cutoutPath?.let { File(it).delete() }
        photoAnalysis.value = null
        forgetEditorPhotos()
        savedItem.value = SavedItemNotice(item.id, item.wardrobeId, item.name, item.category)
        done()
    }
    fun enqueueImports(uris: List<Uri>) = perform(OperationKind.IMPORT, cancellable = true) { batchImporter.enqueue(active.value, uris) }
    fun captureImport(uri: Uri, done: () -> Unit) = perform(OperationKind.IMPORT) { batchImporter.enqueueCapture(active.value, uri); done() }
    fun applyImportTemplate(ids: Set<String>, category: String, season: String, style: String) = perform {
        repo.applyImportTemplate(active.value, ids, category, season, style)
    }
    fun maskPhoto(id: String, strokes: List<PhotoMaskRules.Stroke>, done: (String) -> Unit) = perform(OperationKind.MASK, cancellable = true) {
        cancelPhotoWork()
        val fresh = photos.mask(id, strokes); trackDraft(fresh); photoAnalysis.value = null; done(fresh)
    }
    fun editPhotoSession(id: String, history: PhotoEditHistory, owner: String, done: (String) -> Unit) = perform(OperationKind.PHOTO, cancellable = true) {
        check(photoOwner == owner && PhotoSessionDraft.owns(editorDraft, owner, id))
        cancelPhotoWork()
        // Persist allocation before PNG creation, so interruption cannot leave an untracked variant.
        val fresh = newId()
        draftPhotos.add(fresh)
        check(prefs.edit().putStringSet("draftPhotos", draftPhotos.toSet()).commit())
        var accepted = false
        try {
            photos.editSession(id, history, fresh)
            check(photoOwner == owner && PhotoSessionDraft.owns(editorDraft, owner, id))
            val next = JSONObject(requireNotNull(editorDraft).toString())
            next.getJSONObject("fields").put("photo", fresh).remove(PhotoSessionDraft.FIELD)
            check(writeEditorDraft(next))
            accepted = true
            editorPhotos.value = editorPhotos.value + (owner to fresh)
            photoAnalysis.value = null; done(fresh)
        } finally {
            if (!accepted) withContext(NonCancellable) { repo.discardPhotos(setOf(fresh), photos) }
        }
    }
    fun retryImport(id: String) = perform { batchImporter.retry(id) }
    fun skipImport(id: String) = perform { repo.skipImport(id, photos); batchImporter.diagnostics.remove(id) }
    fun saveImported(id: String, item: Garment, nextIds: List<String> = emptyList(), done: () -> Unit) = perform {
        cancelPhotoWork()
        try {
            withContext(Dispatchers.IO) {
                val file = photos.currentFile(item.photoId)
                if (!file.isFile) throw java.io.FileNotFoundException()
                photos.validateImage(file)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            photoAnalysis.value = photoFailure(item.photoId, e)
            reportFailure(OperationKind.PHOTO, e)
            return@perform
        }
        if (!repo.saveImported(id, item, photos)) notice.value = R.string.photo_cleanup_failed
        batchImporter.diagnostics.remove(id)
        clearDraft()
        val candidates = draftPhotos.toSet()
        if (!repo.discardPhotos(candidates, photos)) notice.value = R.string.photo_cleanup_failed
        draftPhotos.removeAll(candidates); prefs.edit().putStringSet("draftPhotos", draftPhotos.toSet()).apply()
        forgetEditorPhotos()
        savedItem.value = SavedItemNotice(item.id, item.wardrobeId, item.name, item.category)
        val ready = repo.dao.allImports().filter { it.wardrobeId == item.wardrobeId && it.status == "review" }.map { it.id }.toSet()
        nextReviewId.value = ImportReviewRules.next(nextIds, ready, emptySet(), id)
        done()
    }
    fun bulk(ids: Set<String>, action: String, season: String = "all") = perform {
        bulkUndo.value = repo.bulkChange(active.value, ids, action, season)
    }
    fun undoBulk(change: BulkChange) = perform {
        if (!repo.undoBulk(change)) notice.value = R.string.undo_availability_conflict
        if (bulkUndo.value == change) bulkUndo.value = null
    }
    fun update(item: Garment, change: (Garment) -> Garment) = perform { repo.update(item.id, change) }
    fun changeAvailability(item: Garment, availability: String? = null, archived: Boolean? = null) = perform {
        repo.changeAvailability(item.id, availability, archived)?.let { availabilityUndo.value = it }
    }
    fun undoAvailability(change: AvailabilityChange) = perform {
        if (!repo.undoAvailability(change)) notice.value = R.string.undo_availability_conflict
        if (availabilityUndo.value == change) availabilityUndo.value = null
    }
    fun wear(item: Garment) = perform { repo.toggleItemWear(item.id) }
    fun wearOutfit(id: String) = perform { repo.toggleOutfitWear(id) }
    fun schedule(id: String, date: String, done: () -> Unit) = perform { repo.scheduleOutfit(id, date); done() }
    fun movePlan(id: String, date: String, done: () -> Unit) = perform { repo.movePlan(id, date); done() }
    fun removePlan(id: String) = perform { repo.removePlan(id) }
    fun wearPlan(id: String) = perform { repo.togglePlanWorn(id) }
    fun feedback(wardrobe: String, ids: List<String>, occasion: String, weather: String, reason: String) = perform {
        repo.recordFeedback(wardrobe, ids, occasion, weather, reason)
    }
    fun setNoRepeatDays(wardrobe: String, days: Int) = perform {
        require(days in 0..30)
        repo.writes.withLock { require(repo.dao.setNoRepeatDays(wardrobe, days) == 1) }
    }
    fun excludeFromRecommendations(id: String, excluded: Boolean) = perform { repo.update(id) { it.copy(suggestionExcluded = excluded) } }
    fun resetFeedback(wardrobe: String) = perform { repo.writes.withLock { repo.dao.clearFeedback(wardrobe) } }
    fun saveCanvasLook(wardrobe: String, pieces: List<LookPiece>, name: String, occasion: String, weather: String,
        source: String, outfitId: String, layout: String, background: String, done: (String) -> Unit) = perform {
        val id = repo.saveLook(wardrobe, pieces, name, occasion, weather, source, outfitId, photos, layout, background)
        clearDraft(); done(id)
    }
    fun saveDaily(wardrobe: String, items: List<Garment>, name: String, occasion: String, weather: String, done: (String) -> Unit, capsuleId: String = "", packedOnly: Boolean = false) = perform {
        require(items.isNotEmpty() && items.all { it.wardrobeId == wardrobe })
        done(if (capsuleId.isBlank()) repo.saveLook(wardrobe, items.map(LookRules::owned), name, occasion, weather, "owned", "", photos)
            else repo.saveCapsuleDaily(capsuleId, items.map { it.id }, name, occasion, weather, packedOnly))
    }
    fun saveCapsule(value: Capsule, done: (String) -> Unit) = perform(OperationKind.CAPSULE) { done(repo.saveCapsule(value)) }
    fun removeCapsule(id: String, done: () -> Unit) = perform { repo.removeCapsule(id); done() }
    fun linkCapsule(capsule: String, outfit: String, selected: Boolean) = perform(OperationKind.CAPSULE) { repo.linkCapsuleOutfit(capsule, outfit, selected) }
    fun manualCapsule(capsule: String, item: String, selected: Boolean) = perform(OperationKind.CAPSULE) { repo.manualCapsuleItem(capsule, item, selected) }
    fun pack(capsule: String, item: String, packed: Boolean) = perform(OperationKind.CAPSULE) { repo.packCapsuleItem(capsule, item, packed) }
    fun resetPacking(capsule: String) = perform { repo.resetPacking(capsule) }
    fun addCustomColor(wardrobe: String, name: String, base: String, done: (CustomColor) -> Unit) = perform {
        val created = repo.addCustomColor(wardrobe, name, base)
        done(created)
    }
    fun addCustomCategory(wardrobe: String, name: String, base: String, done: (CustomCategory) -> Unit) = perform {
        val created = repo.addCustomCategory(wardrobe, name, base)
        done(created)
    }
    fun addWardrobe(name: String) = perform { repo.addWardrobe(name) }
    fun renameWardrobe(id: String, name: String) = perform { repo.renameWardrobe(id, name) }
    fun deleteWardrobe(id: String) = perform {
        val result = repo.deleteWardrobe(id, photos)
        if (active.value == id) select(result.nextWardrobeId)
        notice.value = if (result.photosRemoved) R.string.wardrobe_deleted else R.string.wardrobe_deleted_cleanup
    }
    fun saveOutfit(wardrobe: String, ids: List<String>, name: String, done: (String) -> Unit) = perform {
        done(repo.saveOutfit(wardrobe, ids, name))
    }
    fun saveLook(wardrobe: String, pieces: List<LookPiece>, name: String, occasion: String, weather: String, source: String, outfitId: String, done: (String) -> Unit) = perform {
        val id = repo.saveLook(wardrobe, pieces, name, occasion, weather, source, outfitId, photos)
        clearDraft(); done(id)
    }
    fun saveWish(wish: Wish, converting: Boolean, done: () -> Unit) = perform {
        if (!repo.saveWish(wish, converting, photos)) notice.value = R.string.photo_cleanup_failed
        clearDraft()
        val candidates = draftPhotos.toSet()
        if (!repo.discardPhotos(candidates, photos)) notice.value = R.string.photo_cleanup_failed
        draftPhotos.removeAll(candidates); prefs.edit().putStringSet("draftPhotos", draftPhotos.toSet()).apply()
        forgetEditorPhotos()
        savedWish.value = requireNotNull(repo.dao.wish(wish.id)); done()
    }
    fun purchase(wishId: String, reviewed: Garment, done: (String) -> Unit) = perform {
        cancelPhotoWork()
        val receipt = repo.purchaseWish(wishId, reviewed)
        clearDraft()
        val candidates = draftPhotos.toSet()
        if (!repo.discardPhotos(candidates, photos)) notice.value = R.string.photo_cleanup_failed
        draftPhotos.removeAll(candidates); prefs.edit().putStringSet("draftPhotos", draftPhotos.toSet()).apply()
        forgetEditorPhotos()
        purchaseUndo.value = receipt.undo
        savedItem.value = SavedItemNotice(receipt.garment.id, reviewed.wardrobeId, receipt.garment.name, receipt.garment.category)
        done(receipt.garment.id)
    }
    internal fun saveShopOwned(reviewed: Garment, source: String, done: (String) -> Unit) = perform {
        require(WishRules.validLink(source))
        val item = reviewed.copy(availability = "available", archived = false, photoFingerprint = photos.fingerprint(reviewed.photoId))
        val product = Wish(id = item.id, wardrobeId = item.wardrobeId, name = item.name, category = item.category, kind = "product",
            priceMinor = item.priceMinor, currency = item.currency, link = source.trim(), photoId = item.photoId, color = item.color,
            inspiration = item.description, createdAt = item.createdAt)
        // Source URL, price and image join the existing backup/sync/history atomically with ownership.
        val receipt = repo.purchaseWish(product.id, item, product)
        clearDraft()
        val candidates = draftPhotos.toSet()
        if (!repo.discardPhotos(candidates, photos)) notice.value = R.string.photo_cleanup_failed
        draftPhotos.removeAll(candidates); prefs.edit().putStringSet("draftPhotos", draftPhotos.toSet()).apply()
        forgetEditorPhotos()
        purchaseUndo.value = receipt.undo
        savedItem.value = SavedItemNotice(receipt.garment.id, item.wardrobeId, receipt.garment.name, receipt.garment.category)
        done(receipt.garment.id)
    }
    fun undoPurchase(change: PurchaseUndo) = perform {
        if (!repo.undoPurchase(change, photos)) notice.value = R.string.purchase_undo_conflict
        else savedItem.value = null
        if (purchaseUndo.value == change) purchaseUndo.value = null
    }
    fun deleteWish(id: String, done: () -> Unit) = perform { repo.removeWish(id, photos); if (savedWish.value?.id == id) savedWish.value = null; done() }
    private data class ExportRequest(val id: String, val wardrobeId: String?)
    private var lastExportRequest: ExportRequest? = null
    val exportRequestToken: String get() = lastExportRequest?.id.orEmpty()
    fun retryExportAt(uri: Uri, requestToken: String) {
        val request = lastExportRequest?.takeIf { it.id == requestToken && requestToken.isNotBlank() } ?: return
        if (busy.value) return
        dismissFailure()
        export(uri, request.wardrobeId)
    }
    fun export(uri: Uri, wardrobeId: String? = null) {
        if (busy.value) return
        lastExportRequest = ExportRequest(newId(), wardrobeId)
        perform(OperationKind.EXPORT, cancellable = true) {
            if (wardrobeId == null) backupHealthStore.attempt()
            try {
                backup.export(uri, wardrobeId = wardrobeId)
                if (wardrobeId == null) backupHealthStore.success()
                notice.value = if (wardrobeId == null) R.string.backup_done else R.string.backup_wardrobe_done
            }
            catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val removed = runCatching { androidx.documentfile.provider.DocumentFile.fromSingleUri(accountContext, uri)?.delete() == true }.getOrDefault(false)
                    notice.value = if (removed) R.string.operation_cancelled else R.string.operation_cancelled_file
                }
                throw e
            }
            catch (e: Exception) { if (wardrobeId == null) backupHealthStore.failure(); throw e }
        }
    }
    fun restore(uri: Uri, restoredLabel: String, defaultName: String, expectedFingerprint: String? = null) = perform(OperationKind.RESTORE) {
        backup.restore(uri, restoredLabel, defaultName, expectedFingerprint)?.let { select(it) }
        notice.value = R.string.restore_done
    }
}
