@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.familywardrobe.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun label(code: String): Int = when (code) {
    "top" -> R.string.top; "bottom" -> R.string.bottom; "shoes" -> R.string.shoes
    "dress" -> R.string.dress; "jumpsuit" -> R.string.jumpsuit; "layer" -> R.string.layer
    "bag" -> R.string.bag; "tights" -> R.string.tights; "jewelry" -> R.string.jewelry; "hat" -> R.string.hat; "accessory" -> R.string.accessory
    "outerwear" -> R.string.outerwear; "other_item" -> R.string.other_item
    "white" -> R.string.white; "black" -> R.string.black; "grey" -> R.string.grey
    "beige" -> R.string.beige; "navy" -> R.string.navy; "brown" -> R.string.brown
    "olive" -> R.string.olive; "blue" -> R.string.blue; "red" -> R.string.red; "other" -> R.string.other
    "all" -> R.string.all_seasons; "summer" -> R.string.summer; "winter" -> R.string.winter; "midseason" -> R.string.midseason
    "casual" -> R.string.casual; "sport" -> R.string.sport; "formal" -> R.string.formal
    "available" -> R.string.available; "laundry" -> R.string.laundry; "repair" -> R.string.repair; "lent" -> R.string.lent
    "newest" -> R.string.newest; "least_worn" -> R.string.least_worn; "most_worn" -> R.string.most_worn
    "everyday" -> R.string.occasion_everyday; "work" -> R.string.occasion_work; "occasion" -> R.string.occasion_event; "active_day" -> R.string.occasion_active
    "any_weather" -> R.string.weather_any; "warm" -> R.string.weather_warm; "cool" -> R.string.weather_cool; "cold" -> R.string.weather_cold; "rain" -> R.string.weather_rain
    "owned" -> R.string.source_owned; "mixed" -> R.string.source_mixed; "inspiration" -> R.string.source_inspiration
    else -> R.string.other
}

@Composable
fun WardrobeApp(vm: WardrobeViewModel, appearance: AppearanceViewModel, accountEmail: String, onSignOut: () -> Unit, retryConnection: () -> Unit) {
    val snapshot by vm.state.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val initialized by vm.initialized.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val initialDraft = remember { vm.draft() }
    var route by rememberSaveable { mutableStateOf(when (initialDraft?.optString("kind")) { "garment", "purchase" -> "edit"; "import" -> "importedit"; "shop" -> "shop"; "wish" -> "wishedit"; "look" -> "look"; else -> "catalog" }) }
    val online by vm.network.online.collectAsStateWithLifecycle()
    var reviewIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var deferredIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var reviewSaved by rememberSaveable { mutableIntStateOf(0) }
    var importId by rememberSaveable { mutableStateOf(if (initialDraft?.optString("kind") == "import") initialDraft.optString("original") else "") }
    var buyingWishId by rememberSaveable { mutableStateOf(if (initialDraft?.optString("kind") == "purchase") initialDraft.optString("original") else "") }
    var wishId by rememberSaveable { mutableStateOf(if (initialDraft?.optString("kind") == "wish") initialDraft.optString("original") else "") }
    var wishKind by rememberSaveable { mutableStateOf(initialDraft?.optJSONObject("fields")?.optString("kind", "product") ?: "product") }
    var convertingWish by rememberSaveable { mutableStateOf(initialDraft?.optJSONObject("fields")?.optBoolean("converting", false) ?: false) }
    var detailReturn by rememberSaveable { mutableStateOf("catalog") }
    var wishReturn by rememberSaveable { mutableStateOf("shopping") }
    var lookAnchor by rememberSaveable { mutableStateOf("") }
    var lookId by rememberSaveable { mutableStateOf(if (initialDraft?.optString("kind") == "look") initialDraft.optString("original") else "") }
    var lookSetup by remember { mutableStateOf(true) }
    val screenState = rememberSaveableStateHolder()
    var entryKey by rememberSaveable { mutableStateOf(route) }
    var returnStack by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val operation by vm.operation.collectAsStateWithLifecycle()
    val failure by vm.failure.collectAsStateWithLifecycle()
    val canNavigate = !busy || operation?.kind?.canBrowse == true
    LaunchedEffect(route, initialized, busy) {
        if (initialized && route !in setOf("edit", "importedit", "wishedit", "shop") && !busy) { vm.forgetEditorPhotos(); vm.discardDraftPhotos() }
    }
    var selected by rememberSaveable { mutableStateOf("") }
    var editId by rememberSaveable { mutableStateOf(if (initialDraft?.optString("kind") == "garment") initialDraft.optString("original") else "") }
    var outfitId by rememberSaveable { mutableStateOf("") }
    var calendarDate by rememberSaveable { mutableStateOf("") }
    var schedulingId by rememberSaveable { mutableStateOf("") }
    var capsuleId by rememberSaveable { mutableStateOf("") }
    var capsulePickItems by rememberSaveable { mutableStateOf(false) }
    var requestedCategory by rememberSaveable { mutableStateOf("") }
    var editorCategory by rememberSaveable { mutableStateOf("") }
    var editorLaunch by rememberSaveable { mutableStateOf("") }
    var addMethods by rememberSaveable { mutableStateOf(false) }
    val incomingShares by vm.pendingShares.collectAsStateWithLifecycle()
    var editorSequence by rememberSaveable { mutableIntStateOf(0) }
    var editorDirty by remember { mutableStateOf(false) }
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    var pendingWardrobe by remember { mutableStateOf<String?>(null) }
    val undo by vm.availabilityUndo.collectAsStateWithLifecycle()
    val importRows by vm.imports.collectAsStateWithLifecycle()
    val bulkUndo by vm.bulkUndo.collectAsStateWithLifecycle()
    val purchaseUndo by vm.purchaseUndo.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val purchasedLabel = stringResource(R.string.purchase_saved)
    var pendingPurchaseUndo by remember { mutableStateOf<PurchaseUndo?>(null) }
    var pendingBulkUndo by remember { mutableStateOf<BulkChange?>(null) }
    var pendingUndo by remember { mutableStateOf<AvailabilityChange?>(null) }
    val undoLabel = stringResource(R.string.undo_action)
    val changedLabel = stringResource(R.string.availability_changed)
    LaunchedEffect(undo) {
        snackbar.currentSnackbarData?.dismiss()
        undo?.let { change ->
            val result = snackbar.showSnackbar(changedLabel, undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) pendingUndo = change
            else if (vm.availabilityUndo.value == change) vm.availabilityUndo.value = null
        }
    }
    LaunchedEffect(bulkUndo) {
        bulkUndo?.let { change ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(changedLabel, undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) pendingBulkUndo = change
            else if (vm.bulkUndo.value == change) vm.bulkUndo.value = null
        }
    }
    LaunchedEffect(pendingBulkUndo, busy) { if (!busy) pendingBulkUndo?.let { vm.undoBulk(it); pendingBulkUndo = null } }
    LaunchedEffect(purchaseUndo) {
        purchaseUndo?.let { change ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(purchasedLabel, undoLabel, withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) pendingPurchaseUndo = change
            else if (vm.purchaseUndo.value == change) vm.purchaseUndo.value = null
        }
    }
    LaunchedEffect(pendingPurchaseUndo, busy) { if (!busy) pendingPurchaseUndo?.let { vm.undoPurchase(it); pendingPurchaseUndo = null } }
    LaunchedEffect(pendingUndo, busy) {
        if (!busy) pendingUndo?.let { vm.undoAvailability(it); pendingUndo = null }
    }
    var newWardrobe by rememberSaveable { mutableStateOf(false) }
    var wardrobeMenu by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now().toString(); delay(30_000) } }
    val current = snapshot.wardrobes.find { it.id == active } ?: snapshot.wardrobes.firstOrNull()
    val garment = snapshot.garments.find { it.id == selected }
    val nested = route in setOf("detail", "edit", "look", "saved", "manage", "appearance", "wishdetail", "wishedit", "batch", "importedit", "daily", "calendar", "statistics", "capsules", "capsule", "capsuledaily", "compatibility", "shop", "reprocess")
    val roots = setOf("catalog", "shopping", "outfits", "settings")
    fun frame(): String = JSONObject().put("route", route).put("key", entryKey)
        .put("selected", selected).put("editId", editId).put("outfitId", outfitId)
        .put("wishId", wishId).put("capsuleId", capsuleId).put("importId", importId)
        .put("lookId", lookId).put("lookAnchor", lookAnchor).put("buyingWishId", buyingWishId)
        .put("calendarDate", calendarDate).put("capsulePickItems", capsulePickItems).toString()
    fun restoreFrame(value: String) {
        val f = JSONObject(value)
        selected = f.optString("selected"); editId = f.optString("editId"); outfitId = f.optString("outfitId")
        wishId = f.optString("wishId"); capsuleId = f.optString("capsuleId"); importId = f.optString("importId")
        lookId = f.optString("lookId"); lookAnchor = f.optString("lookAnchor"); buyingWishId = f.optString("buyingWishId")
        calendarDate = f.optString("calendarDate"); capsulePickItems = f.optBoolean("capsulePickItems"); route = f.getString("route"); entryKey = f.getString("key")
    }
    fun clearReturnStates(wardrobeId: String) {
        if (route !in roots) screenState.removeState("$wardrobeId:$entryKey")
        returnStack.forEach { value -> val frame = JSONObject(value); if (frame.optString("route") !in roots) screenState.removeState("$wardrobeId:${frame.optString("key")}") }
    }
    fun go(destination: String, replace: Boolean = false) {
        if (destination == "__back") {
            if (route !in roots) screenState.removeState("${current?.id}:$entryKey")
            if (returnStack.isNotEmpty()) {
                val previous = returnStack.last(); returnStack = ArrayList(returnStack.dropLast(1)); restoreFrame(previous)
            } else { route = if (route == "importedit") "batch" else "catalog"; entryKey = route }
            return
        }
        if (destination in roots) { clearReturnStates(current?.id.orEmpty()); returnStack = arrayListOf() }
        else if (!replace) {
            if (returnStack.size >= 32) {
                val dropped = JSONObject(returnStack.first()); if (dropped.optString("route") !in roots) screenState.removeState("${current?.id}:${dropped.optString("key")}")
                returnStack = ArrayList(returnStack.drop(1))
            }
            returnStack = ArrayList(returnStack + frame())
        }
        else if (route !in roots) screenState.removeState("${current?.id}:$entryKey")
        route = destination; entryKey = if (destination in roots) destination else newId()
    }
    fun navigate(destination: String) {
        if (!canNavigate) return
        if (route in setOf("edit", "importedit", "wishedit", "look", "shop") && editorDirty) pendingRoute = destination
        else { if (route in setOf("edit", "importedit", "wishedit", "look", "shop")) vm.clearDraft(); go(destination) }
    }
    fun back() { navigate("__back") }
    BackHandler(nested) { back() }
    fun openItem(g: Garment) { detailReturn = route; selected = g.id; go("detail") }
    fun addItem(category: String) { editorLaunch = ""; vm.clearDraft(); vm.forgetEditorPhotos(); buyingWishId = ""; editId = ""; editorCategory = category; editorSequence++; editorDirty = false; go("edit", replace = route == "edit") }
    fun openShop(sharedId: String? = null) {
        if (vm.beginShopImport(sharedId)) { editorDirty = false; go("shop") }
    }
    fun firstOutfit() { vm.clearDraft(); lookAnchor = ""; lookId = ""; editorDirty = false; go("look") }
    fun buildOutfit() { garment?.let { vm.clearDraft(); lookAnchor = it.id; lookId = ""; editorDirty = false; go("look") } }
    fun addWish(kind: String) { vm.clearDraft(); wishId = ""; wishKind = kind; convertingWish = false; editorSequence++; editorDirty = false; go("wishedit") }
    fun editWish(wish: Wish, converting: Boolean) { vm.clearDraft(); wishId = wish.id; wishKind = if (converting) "product" else wish.kind; convertingWish = converting; editorSequence++; editorDirty = false; go("wishedit") }
    var navigationWardrobe by rememberSaveable { mutableStateOf(active) }
    LaunchedEffect(current?.id) {
        current?.id?.let { id -> if (navigationWardrobe != id) { clearReturnStates(navigationWardrobe); navigationWardrobe = id; editorDirty = false; returnStack = arrayListOf(); route = "catalog"; entryKey = "catalog"; requestedCategory = "" } }
    }
    val title = when (route) {
        "reprocess" -> R.string.photo_reprocess_title
        "shop" -> R.string.shop_title
        "capsuledaily" -> R.string.capsule_generate; "daily" -> R.string.daily_title; "calendar" -> R.string.calendar_title; "statistics" -> R.string.statistics_title
        "capsules", "capsule" -> R.string.capsules_title; "compatibility" -> R.string.compatibility_title
        "batch" -> R.string.batch_title; "importedit" -> R.string.batch_review; "detail" -> R.string.item_details; "edit" -> if (buyingWishId.isNotBlank()) R.string.purchase_review else if (editId.isEmpty()) R.string.add_item else R.string.edit_item
        "look" -> if (lookSetup) R.string.look_setup else R.string.outfit_builder;
        "wishdetail" -> R.string.wish_details; "wishedit" -> if (convertingWish) R.string.found_candidate else R.string.add_wish; "saved", "outfits" -> R.string.outfits
        "shopping" -> R.string.shopping; "settings" -> R.string.settings; "manage" -> R.string.manage_wardrobes
        "appearance" -> R.string.appearance; else -> R.string.my_wardrobe
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                WardrobeTopBar(title, current, snapshot.wardrobes, nested, canNavigate,
                    back = ::back, select = { vm.select(it); go("catalog"); requestedCategory = "" },
                    create = { newWardrobe = true }, manage = { go("manage") },
                    statistics = if (route == "catalog") ({ go("statistics") }) else null,
                    imports = if (route == "catalog") ({ go("batch") }) else null)
                if (incomingShares.isNotEmpty() && initialized) {
                    TextButton(onClick = { openShop(incomingShares.first().id) },
                        enabled = !busy && route !in setOf("edit", "importedit", "wishedit", "look", "shop"),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(if (route in setOf("edit", "importedit", "wishedit", "look", "shop")) R.string.shop_waiting else R.string.shop_incoming, incomingShares.size))
                    }
                }
                if (!online && initialized) OfflineBanner { vm.network.refresh(); retryConnection() }
                operation?.let { work ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(if (work.cancelling) R.string.operation_cancelling else work.kind.title),
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            if (work.cancellable) TextButton(onClick = vm::cancelOperation, enabled = !work.cancelling) { Text(stringResource(R.string.cancel)) }
                        }
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }, bottomBar = {
            val navigation = listOf("catalog" to Icons.Outlined.Checkroom, "shopping" to Icons.Outlined.BookmarkBorder,
                "outfits" to Icons.Outlined.Layers, "settings" to Icons.Outlined.Settings)
            val navColumns = if (LocalConfiguration.current.fontScale >= 1.4f) 2 else 4
            if (!nested) Surface(color = MaterialTheme.colorScheme.surface) {
              Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                navigation.chunked(navColumns).forEach { row ->
                  NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0)) {
                  row.forEach { (screen, icon) ->
                    val name = when (screen) { "outfits" -> R.string.outfits; "shopping" -> R.string.shopping; "settings" -> R.string.settings; else -> R.string.catalog }
                    NavigationBarItem(selected = route == screen || (nested && screen == when (route) { "look", "saved", "daily", "capsuledaily", "calendar", "capsules", "capsule" -> "outfits"; "wishdetail", "wishedit", "compatibility" -> "shopping"; "detail" -> if (detailReturn == "saved") "outfits" else "catalog"; "manage", "appearance" -> "settings"; else -> "catalog" }),
                        onClick = { navigate(screen) }, enabled = canNavigate, icon = { Icon(icon, null) },
                        label = { Text(stringResource(name), maxLines = 2) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
                  }
                  }
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
              }
            } else Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!initialized || current == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!error) CircularProgressIndicator()
            } else screenState.SaveableStateProvider("${current.id}:$entryKey") { when (route) {
                "catalog" -> {
                    Catalog(snapshot, current.id, vm, today, requestedCategory, ::openItem, ::addItem, ::firstOutfit,
                        onCategoryConsumed = { requestedCategory = "" }, onBatch = { go("batch") },
                        onDaily = { go("daily") }, onStatistics = { go("statistics") }, onAddMethods = { addMethods = true },
                        onReprocess = { go("reprocess") })
                }
                "reprocess" -> PhotoReprocessScreen(snapshot, vm)
                "batch" -> BatchImportScreen(importRows.filter { it.wardrobeId == current.id }, vm) { row ->
                    vm.clearDraft(); vm.forgetEditorPhotos(); importId = row.id; editorSequence++; editorDirty = false
                    reviewIds = ArrayList(importRows.filter { it.wardrobeId == current.id }.sortedWith(compareBy<ImportDraft> { it.createdAt }.thenBy { it.position }.thenBy { it.id }).map { it.id })
                    deferredIds = arrayListOf(); reviewSaved = 0; go("importedit")
                }
                "importedit" -> key(importId, editorSequence) {
                    val row = importRows.find { it.id == importId && it.wardrobeId == current.id }
                    if (row == null) Text(stringResource(R.string.batch_missing), Modifier.padding(16.dp))
                    else {
                        LaunchedEffect(row.id, reviewIds.isEmpty()) { if (reviewIds.isEmpty()) reviewIds = ArrayList(importRows.filter { it.wardrobeId == current.id }.map { it.id }) }
                        val ready = importRows.filter { it.wardrobeId == current.id && it.status == "review" }.map { it.id }.toSet()
                        val next = ImportReviewRules.next(reviewIds, ready, deferredIds.toSet(), importId)
                        fun continueReview(nextId: String?) {
                            vm.clearDraft(); vm.forgetEditorPhotos(); editorDirty = false
                            if (nextId == null) { go("__back"); vm.notice.value = R.string.review_ready_finished }
                            else { importId = nextId; editorSequence++ }
                        }
                        EditItem(null, current.id, vm, row.category, onDirtyChange = { editorDirty = it }, imported = row,
                            reviewNextIds = reviewIds.filter { it !in deferredIds }, reviewPosition = reviewIds.indexOf(row.id).coerceAtLeast(0) + 1,
                            reviewTotal = reviewIds.size, reviewSaved = reviewSaved, reviewDeferred = deferredIds.size,
                            reviewWaiting = importRows.count { it.wardrobeId == current.id && it.status in setOf("queued", "working") },
                            nextReviewPhoto = importRows.find { it.id == next }?.photoId.orEmpty(),
                            deferReview = { deferredIds = ArrayList(deferredIds + row.id); continueReview(next) },
                            reviewQueue = { navigate("__back") }) { _, addNext ->
                            editorDirty = false; reviewSaved++
                            if (addNext) continueReview(vm.nextReviewId.value) else go("__back")
                        }
                    }
                }
                "detail" -> if (garment != null) Detail(garment, snapshot, vm, today,
                    onEdit = { buyingWishId = ""; editId = garment.id; editorCategory = ""; editorSequence++; editorDirty = false; go("edit") }, onMatch = ::buildOutfit)
                    else MissingSelection { go("__back") }
                "edit" -> key(editId, current.id, buyingWishId, editorSequence) {
                    if (buyingWishId.isNotBlank() && snapshot.wishes.none { it.id == buyingWishId && it.ownedId.isBlank() }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.purchase_source_missing))
                            TextButton(onClick = { vm.clearDraft(); buyingWishId = ""; go("shopping") }) { Text(stringResource(R.string.back)) }
                        }
                    } else EditItem(snapshot.garments.find { it.id == editId }, current.id, vm, editorCategory,
                        onDirtyChange = { editorDirty = it }, initialPhotoAction = editorLaunch, purchase = snapshot.wishes.find { it.id == buyingWishId && it.ownedId.isBlank() }) { id, addNext ->
                        selected = id; buyingWishId = ""; editorDirty = false
                        if (addNext) addItem(vm.savedItem.value?.category.orEmpty()) else if (editId.isNotBlank()) go("__back") else go("catalog")
                    }
                }
                "look" -> key(current.id, lookAnchor, lookId) {
                    LookEditor(snapshot, current.id, lookAnchor, lookId, vm, onDirty = { editorDirty = it }, onStage = { lookSetup = it }) { id ->
                        outfitId = id; editorDirty = false
                        val previous = returnStack.lastOrNull()?.let(::JSONObject)
                        if (previous?.optString("route") == "saved" && previous.optString("outfitId") == id) go("__back") else go("saved", replace = true)
                    }
                }
                "daily" -> DailyScreen(snapshot, current.id, vm, today, ::openItem,
                    saved = { outfitId = it; go("saved") }, schedule = { schedulingId = it }, addItems = { go("catalog") })
                "capsuledaily" -> snapshot.capsules.find { it.id == capsuleId && it.wardrobeId == current.id }?.let { capsule ->
                    DailyScreen(snapshot, current.id, vm, today, ::openItem,
                        saved = { outfitId = it; go("saved") }, schedule = { schedulingId = it }, capsule = capsule, addItems = { go("catalog") }, editCapsule = { capsulePickItems = true; go("capsule") })
                } ?: MissingSelection { go("__back") }

                "calendar" -> CalendarScreen(snapshot, current.id, vm, today, calendarDate,
                    open = { outfitId = it; go("saved") }, daily = { go("daily") })
                "statistics" -> StatisticsScreen(snapshot, current.id, vm, today, ::openItem) { g ->
                    vm.clearDraft(); lookAnchor = g.id; lookId = ""; editorDirty = false; go("look")
                }
                "capsules" -> CapsuleListScreen(snapshot, current.id, vm) { capsuleId = it; capsulePickItems = false; go("capsule") }
                "capsule" -> snapshot.capsules.find { it.id == capsuleId && it.wardrobeId == current.id }?.let { capsule ->
                    CapsuleDetailScreen(capsule, snapshot, vm, ::openItem, openOutfit = { outfitId = it; go("saved") }, removed = { go("__back") }, generate = { go("capsuledaily") }, initialPickItems = capsulePickItems)
                } ?: MissingSelection { go("__back") }

                "compatibility" -> snapshot.wishes.find { it.id == wishId && it.wardrobeId == current.id && it.ownedId.isBlank() }?.let { w ->
                    CompatibilityScreen(w, snapshot, vm, ::openItem, saved = { outfitId = it; go("saved") }, bought = { hints ->
                        vm.clearDraft(); vm.forgetEditorPhotos()
                        val fields = JSONObject().put("id", w.id)
                        if (hints.season.isNotBlank()) fields.put("season", hints.season)
                        if (hints.style.isNotBlank()) fields.put("style", hints.style)
                        if (hints.subcategory.isNotBlank()) fields.put("subcategory", hints.subcategory)
                        vm.keepDraft("purchase", current.id, w.id, fields)
                        buyingWishId = w.id; editId = ""; editorCategory = w.category; editorSequence++; editorDirty = false; go("edit")
                    }, defer = { vm.notice.value = R.string.advice_deferred; go("shopping") })
                } ?: MissingSelection { go("__back") }

                "outfits" -> OutfitList(snapshot, current.id, vm, ::firstOutfit,
                    daily = { go("daily") }, calendar = { calendarDate = today; go("calendar") }, capsules = { go("capsules") }) { outfitId = it; go("saved") }
                "saved" -> snapshot.outfits.find { it.id == outfitId }?.let { SavedOutfit(it, snapshot, vm, today, ::openItem, openWish = { id -> wishReturn = "saved"; wishId = id; go("wishdetail") }, schedule = { schedulingId = it.id }) { vm.clearDraft(); lookId = it.id; lookAnchor = ""; editorDirty = false; go("look") } } ?: MissingSelection { go("__back") }

                "shopping" -> Wishlist(snapshot, current.id, vm, ::addWish, foundProduct = { editWish(it, true) }, importProduct = { openShop() }) { wishReturn = "shopping"; wishId = it; go("wishdetail") }
                "wishdetail" -> snapshot.wishes.find { it.id == wishId }?.let { w ->
                    WishDetail(w, vm, edit = { editWish(w, false) }, found = { editWish(w, true) },
                        check = { requestedCategory = w.category; go("catalog") }, compatibility = { go("compatibility") }, removed = { go("shopping") }, bought = {
                            vm.clearDraft(); vm.forgetEditorPhotos(); buyingWishId = w.id; editId = ""; editorCategory = w.category; editorSequence++; editorDirty = false; go("edit") })
                } ?: MissingSelection { go("__back") }

                "wishedit" -> key(current.id, wishId, editorSequence) {
                    WishEditor(snapshot.wishes.find { it.id == wishId }, current.id, wishKind, convertingWish, vm,
                        onDirty = { editorDirty = it }) { id -> val wasNew = wishId.isBlank(); wishId = id; editorDirty = false; if (wasNew) go("shopping") else go("__back") }
                }
                "shop" -> ShopImportScreen(snapshot, current.id, vm, onDirty = { editorDirty = it }, openEditor = { destination ->
                    vm.forgetEditorPhotos(); navigationWardrobe = destination.wardrobe; vm.select(destination.wardrobe)
                    editorLaunch = ""; editorSequence++; editorDirty = false; editId = ""; buyingWishId = ""
                    wishId = destination.original; wishKind = "product"; convertingWish = destination.original.isNotBlank()
                    go(if (destination.kind == "wish") "wishedit" else "edit", replace = true)
                }, openExisting = { wish ->
                    pendingWardrobe = wish.wardrobeId.takeIf { it != current.id }
                    editorDirty = true
                    if (wish.ownedId.isBlank()) { wishId = wish.id; navigate("wishdetail") }
                    else { selected = wish.ownedId; navigate("detail") }
                })
                "settings" -> Settings(vm, appearance, accountEmail, onSignOut, onNewWardrobe = { newWardrobe = true }, onManageWardrobes = { go("manage") }, onAppearance = { go("appearance") })
                "appearance" -> AppearanceScreen(appearance, onBrowse = { go("catalog") })
                "manage" -> ManageWardrobes(snapshot, current.id, vm) { id ->
                    vm.select(id); go("catalog"); requestedCategory = ""
                }
            } }
        }
    }
    if (addMethods) AddItemMethods(dismiss = { addMethods = false }) { method ->
        addMethods = false
        when (method) {
            "batch" -> go("batch")
            "shop" -> openShop()
            else -> { addItem(""); editorLaunch = if (method == "manual") "" else method }
        }
    }
    snapshot.outfits.find { it.id == schedulingId && it.wardrobeId == current?.id }?.let { outfit ->
        ScheduleDialog(outfit, vm, today, { schedulingId = "" }) { day -> schedulingId = ""; calendarDate = day; go("calendar") }
    }
    if (pendingRoute != null) AlertDialog(onDismissRequest = { pendingRoute = null; pendingWardrobe = null },
        title = { Text(stringResource(R.string.discard_item_title)) }, text = { Text(stringResource(R.string.discard_item_hint)) },
        confirmButton = { TextButton(onClick = {
            vm.clearDraft(); vm.forgetEditorPhotos(); editorDirty = false
            pendingWardrobe?.let { navigationWardrobe = it; vm.select(it); returnStack = arrayListOf() }
            go(pendingRoute ?: "catalog"); pendingRoute = null; pendingWardrobe = null
        }) {
            Text(stringResource(R.string.discard_changes))
        } }, dismissButton = { TextButton(onClick = { pendingRoute = null; pendingWardrobe = null }) { Text(stringResource(R.string.keep_editing)) } })
    if (newWardrobe) NameDialog(R.string.new_wardrobe, R.string.wardrobe_name, onDismiss = { newWardrobe = false }) {
        vm.addWardrobe(it); newWardrobe = false
    }
    var copyRequestToken by rememberSaveable { mutableStateOf("") }
    val createErrorCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.retryExportAt(uri, copyRequestToken)
    }
    val restorePreview by vm.restorePreview.collectAsStateWithLifecycle()
    val restoredLabel = stringResource(R.string.restored_label)
    val defaultWardrobeName = stringResource(R.string.my_wardrobe)
    restorePreview?.let { preview -> AlertDialog(onDismissRequest = { vm.restorePreview.value = null }, title = { Text(stringResource(R.string.restore_confirm)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.backup_verified_counts, preview.verification.wardrobes, preview.verification.garments, preview.verification.outfits))
            Text(stringResource(R.string.restore_adds_separately))
        } }, confirmButton = { TextButton(onClick = {
            vm.restore(Uri.parse(preview.uri), restoredLabel, defaultWardrobeName, preview.verification.fingerprint)
            vm.restorePreview.value = null
        }, enabled = !busy) { Text(stringResource(R.string.import_backup)) } },
        dismissButton = { TextButton(onClick = { vm.restorePreview.value = null }) { Text(stringResource(R.string.cancel)) } }) }
    val chooseErrorFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { vm.dismissFailure(); vm.chooseBackupFolder(uri) }
    }
    val chooseErrorFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val kind = vm.failure.value?.kind; vm.dismissFailure()
            if (kind == OperationKind.VERIFY) vm.verifyBackup(uri) else vm.previewRestore(uri)
        }
    }
    failure?.let { problem -> AlertDialog(onDismissRequest = vm::dismissFailure,
        title = { Text(stringResource(R.string.error_title)) }, text = { Text(stringResource(problem.message)) },
        confirmButton = { TextButton(onClick = {
            when (problem.action) {
                FailureAction.RETRY -> vm.retryFailure()
                FailureAction.FOLDER -> chooseErrorFolder.launch(null)
                FailureAction.EXPORT_LOCATION -> { copyRequestToken = vm.exportRequestToken; createErrorCopy.launch("wardrobe-${LocalDate.now()}.zip") }
                FailureAction.FILE -> chooseErrorFile.launch(arrayOf("application/zip", "application/octet-stream"))
                FailureAction.REFRESH -> vm.refreshRecommendations()
                FailureAction.CLOSE -> vm.dismissFailure()
            }
        }) { Text(stringResource(when (problem.action) { FailureAction.RETRY -> R.string.retry; FailureAction.EXPORT_LOCATION -> R.string.choose_copy_location; FailureAction.FOLDER -> R.string.backup_choose_folder; FailureAction.FILE -> R.string.choose_another_file; FailureAction.REFRESH -> R.string.daily_refresh; FailureAction.CLOSE -> R.string.close })) } },
        dismissButton = { if (problem.action != FailureAction.CLOSE) TextButton(onClick = vm::dismissFailure) { Text(stringResource(R.string.close)) } }) }
    notice?.let { id -> AlertDialog(onDismissRequest = { vm.notice.value = null }, text = { Text(stringResource(id)) },
        confirmButton = { TextButton(onClick = { vm.notice.value = null }) { Text(stringResource(R.string.close)) } }) }
}

@Composable
fun Detail(g: Garment, s: Snapshot, vm: WardrobeViewModel, today: String, onEdit: () -> Unit, onMatch: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }
    val dates = s.wears.filter { it.garmentId == g.id }.map { it.date }.distinct().sortedDescending()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ZoomableItemPhoto(g.photoId, vm, Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp)), title = g.name, garmentColour = g.color)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconToggleButton(g.favorite, { vm.update(g) { it.copy(favorite = !it.favorite) } }, enabled = !busy) {
                Icon(if (g.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, stringResource(R.string.favorite))
            }
            IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = {}, label = { Text(s.customCategory(g)?.name ?: stringResource(label(g.category))) })
            AssistChip(onClick = {}, label = { Text(stringResource(if (g.archived) R.string.archived else label(g.availability))) })
            FilterChip(g.rarelyWorn, { vm.update(g) { it.copy(rarelyWorn = !it.rarelyWorn) } }, enabled = !busy, label = { Text(stringResource(R.string.rare)) })
        }
        s.wishes.find { it.ownedId == g.id }?.let { PurchaseHistory(it, vm) }
        Info(R.string.size, g.size.ifBlank { "—" }); Info(R.string.color, s.customColor(g)?.name ?: stringResource(label(g.color)))
        Info(R.string.location, g.location.ifBlank { "—" }); Info(R.string.season, stringResource(label(g.season)))
        Info(R.string.style, stringResource(label(g.style)))
        if (g.description.isNotBlank()) Info(R.string.description, g.description)
        Info(R.string.last_worn, dates.firstOrNull()?.let { dateLabel(it) }
            ?: stringResource(R.string.never_worn))
        Text(stringResource(R.string.wear_days, dates.size), style = MaterialTheme.typography.bodySmall)
        if (!g.archived && g.availability in setOf("available", "laundry")) OutlinedButton(
            onClick = { vm.changeAvailability(g, availability = if (g.availability == "laundry") "available" else "laundry") },
            enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (g.availability == "laundry") R.string.return_after_wash else R.string.mark_laundry))
        }
        if (g.subcategory.isNotBlank()) Text(g.subcategory)
        OutlinedButton(onClick = { vm.excludeFromRecommendations(g.id, !g.suggestionExcluded) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (g.suggestionExcluded) R.string.rules_include else R.string.rules_exclude))
        }
        if (g.suggestionExcluded) Text(stringResource(R.string.rules_manual_allowed), style = MaterialTheme.typography.bodySmall)
        Button(onClick = onMatch, enabled = !busy && !g.archived && g.availability == "available", modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.match))
        }
        OutlinedButton(onClick = { vm.wear(g) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (today in dates) R.string.undo_wear else R.string.wear))
        }
        TextButton(onClick = { if (g.archived) vm.changeAvailability(g, archived = false) else confirmArchive = true }, enabled = !busy) {
            Text(stringResource(if (g.archived) R.string.restore_item else R.string.archive))
        }
    }
    if (confirmArchive) AlertDialog(onDismissRequest = { confirmArchive = false }, title = { Text(stringResource(R.string.archive_title)) },
        text = { Text(stringResource(R.string.archive_hint)) }, confirmButton = { TextButton(onClick = {
            vm.changeAvailability(g, archived = true); confirmArchive = false
        }) { Text(stringResource(R.string.archive)) } }, dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun EditItem(original: Garment?, wardrobe: String, vm: WardrobeViewModel, initialCategory: String,
    onDirtyChange: (Boolean) -> Unit, purchase: Wish? = null, imported: ImportDraft? = null, initialPhotoAction: String = "",
    reviewNextIds: List<String> = emptyList(), reviewPosition: Int = 1, reviewTotal: Int = 1, reviewSaved: Int = 0,
    reviewDeferred: Int = 0, reviewWaiting: Int = 0, nextReviewPhoto: String = "", deferReview: () -> Unit = {}, reviewQueue: () -> Unit = {},
    done: (String, Boolean) -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val draftKind = if (imported != null) "import" else if (purchase == null) "garment" else "purchase"
    val originalId = imported?.id ?: purchase?.id ?: original?.id.orEmpty()
    val seed = original ?: imported?.let { Garment(id = it.id, wardrobeId = wardrobe, name = it.name, category = it.category, color = it.color, photoId = it.photoId, season = it.season, style = it.style, photoFingerprint = it.photoFingerprint) } ?: purchase?.let { Garment(id = it.id, wardrobeId = wardrobe, name = it.name,
        category = it.category, color = it.color.ifBlank { "other" }, photoId = it.photoId,
        description = it.inspiration, priceMinor = it.priceMinor, currency = it.currency) }
    val restored = remember { vm.draftFor(draftKind, wardrobe, originalId) }
    fun initial(key: String, fallback: String) = restored?.optString(key, fallback) ?: fallback
    val id = rememberSaveable { initial("id", seed?.id ?: newId()) }
    var name by rememberSaveable { mutableStateOf(initial("name", seed?.name.orEmpty())) }
    var category by rememberSaveable { mutableStateOf(initial("category", seed?.category ?: initialCategory.takeIf { it in Matching.categories }.orEmpty())) }
    var color by rememberSaveable { mutableStateOf(initial("color", seed?.color ?: "other")) }
    var size by rememberSaveable { mutableStateOf(initial("size", seed?.size.orEmpty())) }
    var location by rememberSaveable { mutableStateOf(initial("location", seed?.location.orEmpty())) }
    var description by rememberSaveable { mutableStateOf(initial("description", seed?.description.orEmpty())) }
    var season by rememberSaveable { mutableStateOf(initial("season", seed?.season ?: "all")) }
    var style by rememberSaveable { mutableStateOf(initial("style", seed?.style ?: "casual")) }
    var availability by rememberSaveable { mutableStateOf(initial("availability", seed?.availability ?: "available")) }
    var price by rememberSaveable { mutableStateOf(initial("price", moneyInput(seed?.priceMinor))) }
    var currency by rememberSaveable { mutableStateOf(initial("currency", seed?.currency ?: "PLN")) }
    val shopImported = remember { restored?.optBoolean("shopImported", false) == true && original == null && purchase == null && imported == null }
    var shopPhotoAnalyzed by rememberSaveable { mutableStateOf(restored?.optBoolean("shopPhotoAnalyzed", false) ?: false) }
    var shopLink by rememberSaveable { mutableStateOf(initial("shopLink", "")) }
    var subcategory by rememberSaveable { mutableStateOf(initial("subcategory", seed?.subcategory.orEmpty())) }
    var customCategoryId by rememberSaveable { mutableStateOf(initial("customCategoryId", seed?.customCategoryId.orEmpty())) }
    var customColorId by rememberSaveable { mutableStateOf(initial("customColorId", seed?.customColorId.orEmpty())) }
    val catalog by vm.state.collectAsStateWithLifecycle()
    var duplicateConfirmation by remember { mutableStateOf<Boolean?>(null) }
    val moneyValid = remember(price) { runCatching { WishRules.money(price) }.isSuccess }
    // The durable item draft may be newer than the activity saved-state bundle after an interrupted Apply.
    var photo by remember(vm, wardrobe, id) { mutableStateOf(initial("photo", seed?.photoId.orEmpty())) }
    val photoWork by vm.photoWork.collectAsStateWithLifecycle()
    val workingSource = photoWork.takeIf { it.owner == "$wardrobe:$id" }?.let { it.original.ifBlank { it.source } }.orEmpty()
    val fingerprintSource = workingSource.ifBlank { imported?.originalPhotoId?.takeIf { it.isNotBlank() } ?: photo }
    val fingerprint by produceState("", fingerprintSource) {
        value = if (fingerprintSource.isBlank()) "" else runCatching { vm.photos.fingerprint(fingerprintSource) }.getOrDefault("")
    }
    val duplicates = catalog.garments.filter { it.wardrobeId == wardrobe && it.id != id && fingerprint.isNotBlank() && it.photoFingerprint == fingerprint }
    val previousItem = catalog.garments.filter { it.wardrobeId == wardrobe && it.id != id }.maxByOrNull { it.createdAt }
    var copyingPrevious by rememberSaveable { mutableStateOf(false) }
    var copyFields by rememberSaveable { mutableStateOf(arrayListOf("category", "season", "style")) }
    var confirmDefer by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(restored?.optBoolean("advanced", false) ?: false) }
    val editorPhotoKey = "$wardrobe:$id"
    val photoEditRevision by vm.photoEditRevision.collectAsStateWithLifecycle()
    val hasPhotoSession = remember(photoEditRevision, editorPhotoKey, photo) { vm.photoSessionValue(editorPhotoKey, photo) != null }
    DisposableEffect(editorPhotoKey) { vm.activatePhotoEditor(editorPhotoKey); onDispose { vm.releasePhotoEditor(editorPhotoKey) } }
    val pendingPhoto by remember(vm, editorPhotoKey) { vm.editorPhoto(editorPhotoKey) }.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(pendingPhoto) { pendingPhoto?.let { photo = it } }
    fun receivePhoto(next: String) { vm.rememberEditorPhoto(editorPhotoKey, next); photo = next }
    var editingPhoto by rememberSaveable { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf("") }
    var validation by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val priceFocus = remember { FocusRequester() }
    val fieldsView = remember { BringIntoViewRequester() }
    val categoryView = remember { BringIntoViewRequester() }
    val priceView = remember { BringIntoViewRequester() }
    val photoView = remember { BringIntoViewRequester() }
    val linkView = remember { BringIntoViewRequester() }
    val editorScope = rememberCoroutineScope()
    val importReports by vm.batchImporter.diagnostics.reports.collectAsStateWithLifecycle()
    val savedPhotoReport = imported?.let { importReports[it.id] }?.takeIf { it.sourceId == photo }
    fun showRequiredFields() { editorScope.launch {
        if (!TextLimits.hasVisibleText(name)) { fieldsView.bringIntoView(); nameFocus.requestFocus() }
        else { categoryView.bringIntoView(); categoryFocus.requestFocus() }
    } }
    LaunchedEffect(validation, advanced, moneyValid) {
        if (validation && advanced && !moneyValid) { priceView.bringIntoView(); priceFocus.requestFocus() }
    }
    val saveBlocker = when {
        hasPhotoSession -> R.string.photo_session_finish
        photo.isBlank() -> R.string.photo_save_missing
        !TextLimits.hasVisibleText(name) -> R.string.wish_name_required
        category !in Matching.categories -> R.string.photo_save_category
        !moneyValid -> R.string.wish_amount_error
        shopImported && !WishRules.validLink(shopLink) -> R.string.wish_link_error
        else -> null
    }
    val dirty = name != seed?.name.orEmpty() || photo != seed?.photoId.orEmpty() || category != (seed?.category ?: initialCategory) ||
        color != (seed?.color ?: "other") || size != seed?.size.orEmpty() || location != seed?.location.orEmpty() ||
        description != seed?.description.orEmpty() || season != (seed?.season ?: "all") || style != (seed?.style ?: "casual") ||
        availability != (seed?.availability ?: "available") || price != moneyInput(seed?.priceMinor) || currency != (seed?.currency ?: "PLN") || subcategory != seed?.subcategory.orEmpty() || customCategoryId != seed?.customCategoryId.orEmpty() || customColorId != seed?.customColorId.orEmpty() || purchase != null || shopImported || hasPhotoSession
    val payload = JSONObject().put("id", id).put("name", name).put("category", category).put("color", color).put("size", size)
        .put("location", location).put("description", description).put("season", season).put("style", style).put("availability", availability)
        .put("photo", photo).put("advanced", advanced).put("price", price).put("currency", currency).put("subcategory", subcategory).put("customCategoryId", customCategoryId).put("customColorId", customColorId)
        .put("shopImported", shopImported).put("shopLink", shopLink).put("shopPhotoAnalyzed", shopPhotoAnalyzed).toString()
    LaunchedEffect(payload, hasPhotoSession) { onDirtyChange(dirty); vm.keepDraft(draftKind, wardrobe, originalId, JSONObject(payload)) }
    LaunchedEffect(shopImported, photo, busy) {
        if (shopImported && !shopPhotoAnalyzed && photo.isNotBlank() && !busy) {
            shopPhotoAnalyzed = true
            vm.analyzePhoto(photo)
        }
    }
    fun saveItem(addNext: Boolean, confirmedDuplicate: Boolean = false) {
        if (vm.photoSessionValue(editorPhotoKey, photo) != null) { editingPhoto = true; return }
        val finalPhoto = vm.latestEditorPhoto(editorPhotoKey) ?: photo
        validation = !TextLimits.hasVisibleText(name) || finalPhoto.isBlank() || category !in Matching.categories || !moneyValid || (shopImported && !WishRules.validLink(shopLink))
        when {
            finalPhoto.isBlank() -> editorScope.launch { photoView.bringIntoView() }
            !TextLimits.hasVisibleText(name) || category !in Matching.categories -> showRequiredFields()
            !moneyValid -> {
                if (advanced) editorScope.launch { priceView.bringIntoView(); priceFocus.requestFocus() }
                else advanced = true
            }
            shopImported && !WishRules.validLink(shopLink) -> editorScope.launch { linkView.bringIntoView() }
            !validation -> {
                if (duplicates.isNotEmpty() && !confirmedDuplicate) { duplicateConfirmation = addNext; return }
                val next = (seed ?: Garment(id = id, wardrobeId = wardrobe, name = name.trim(), category = category, color = color, photoId = finalPhoto))
                    .copy(name = name.trim(), category = category, color = color, size = size.trim(), location = location.trim(),
                        description = description.trim(), photoId = finalPhoto, season = season, style = style,
                        availability = if (purchase == null) availability else "available", archived = if (purchase == null) seed?.archived ?: false else false,
                        priceMinor = WishRules.money(price), currency = currency, subcategory = CategoryRules.clean(subcategory),
                        // An unchanged photo keeps the original-import fingerprint used for duplicate detection.
                        photoFingerprint = if (original != null && finalPhoto == original.photoId) original.photoFingerprint else fingerprint, customCategoryId = customCategoryId.takeIf { it.isNotBlank() },
                        customColorId = customColorId.takeIf { it.isNotBlank() })
                if (imported != null) vm.saveImported(imported.id, next, if (addNext) reviewNextIds else emptyList()) { onDirtyChange(false); done(next.id, addNext) }
                else if (shopImported) vm.saveShopOwned(next, shopLink) { saved -> onDirtyChange(false); done(saved, addNext) }
                else if (purchase == null) vm.save(next) { onDirtyChange(false); done(next.id, addNext) }
                else vm.purchase(purchase.id, next) { saved -> onDirtyChange(false); done(saved, false) }
            }
        }
    }
    if (editingPhoto && photo.isNotEmpty()) PhotoEditor(photo, vm, onDismiss = { editingPhoto = false }, onPhoto = ::receivePhoto, beforePhotoId = fingerprintSource, owner = editorPhotoKey)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.importPhoto(uri, ::receivePhoto) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingCapture.isNotEmpty()) vm.importPhoto(Uri.parse(pendingCapture)) { receivePhoto(it); pendingCapture = "" }
        else pendingCapture = ""
    }
    var actionStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialPhotoAction, busy) {
        if (!actionStarted && !busy && original == null && purchase == null && imported == null && initialPhotoAction.isNotBlank()) {
            actionStarted = true
            if (initialPhotoAction == "gallery") gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            else if (initialPhotoAction == "camera") runCatching { vm.photos.newCapture() }.onSuccess {
                pendingCapture = it.toString(); camera.launch(it)
            }.onFailure { vm.reportFailure(OperationKind.PHOTO, it as? Exception ?: IllegalStateException()) }
        }
    }

    Column(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (restored != null) Text(stringResource(R.string.draft_restored), color = MaterialTheme.colorScheme.primary)
            if (purchase != null) Text(stringResource(R.string.purchase_review_hint), style = MaterialTheme.typography.bodyMedium)
            if (imported != null) {
                Text(stringResource(R.string.review_position, reviewPosition, reviewTotal), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.review_counts, reviewSaved, reviewDeferred, reviewWaiting), style = MaterialTheme.typography.bodySmall)
                if (nextReviewPhoto.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemPhoto(nextReviewPhoto, vm, Modifier.size(48.dp)); Text(stringResource(R.string.review_next_preview), Modifier.padding(start = 8.dp))
                }
            }
            ItemPhoto(photo, vm, Modifier.bringIntoViewRequester(photoView).fillMaxWidth().height(190.dp).clip(MaterialTheme.shapes.large), fit = true, garmentColour = color)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy && !hasPhotoSession, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.gallery)) }
                OutlinedButton(onClick = { runCatching { val uri = vm.photos.newCapture(); pendingCapture = uri.toString(); camera.launch(uri) }.onFailure { vm.reportFailure(OperationKind.PHOTO, it as? Exception ?: IllegalStateException()) } },
                    enabled = !busy && !hasPhotoSession, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.camera)) }
            }
            if (photo.isNotBlank()) TextButton(onClick = { vm.cancelPhotoWork(); editingPhoto = true }, enabled = !busy) {
                Text(stringResource(if (hasPhotoSession) R.string.photo_session_resume else R.string.photo_edit))
            }
            if (imported != null && imported.originalPhotoId.isNotBlank() && imported.originalPhotoId != photo)
                TextButton(onClick = { receivePhoto(imported.originalPhotoId) }, enabled = !busy && !hasPhotoSession) { Text(stringResource(R.string.batch_original)) }
            if (photo.isBlank()) savedPhotoReport?.let { PhotoDiagnosticSummary(it) }
            PhotoProcessingPanel(photo, vm, onPhoto = ::receivePhoto, onSuggestions = { nextCategory, nextColor ->
                if (nextCategory != null && customCategoryId.isBlank()) category = nextCategory; if (nextColor != null && customColorId.isBlank()) color = nextColor },
                previous = savedPhotoReport, missingReport = imported != null && savedPhotoReport == null,
                onManual = ::showRequiredFields)
            if (duplicates.isNotEmpty()) Text(stringResource(R.string.import_duplicate_hint, duplicates.first().name), color = MaterialTheme.colorScheme.primary)
            if (original == null && purchase == null && previousItem != null) TextButton(onClick = { copyingPrevious = true },
                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.import_copy_previous)) }
            OutlinedTextField(name, { name = TextLimits.input(it, 120) }, label = { Text(stringResource(R.string.name_required)) }, singleLine = true,
                enabled = !busy, isError = validation && !TextLimits.hasVisibleText(name), supportingText = { if (validation && !TextLimits.hasVisibleText(name)) Text(stringResource(R.string.wish_name_required)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(fieldsView).focusRequester(nameFocus))
            WardrobeCategoryChoice(category, customCategoryId, catalog, wardrobe, vm, { base, custom ->
                if (base != category || custom != customCategoryId) subcategory = ""
                category = base; customCategoryId = custom
            }, title = R.string.category_required, enabled = !busy,
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(categoryView).focusRequester(categoryFocus))
            Field(subcategory, { subcategory = TextLimits.input(it, 60) }, R.string.item_subcategory, enabled = !busy)
            val knownSubcategories = subcategoryOptions(catalog.garments, wardrobe, customCategoryId.ifBlank { category })
            if (knownSubcategories.isNotEmpty()) SubcategoryChoice(subcategory, knownSubcategories, { subcategory = it },
                enabled = !busy, allowAll = false, title = R.string.subcategory_choose)
            if (validation && category !in Matching.categories) Text(stringResource(R.string.wish_category_required), color = MaterialTheme.colorScheme.error)
            WardrobeColorChoice(color, customColorId, catalog, wardrobe, vm, { base, custom ->
                color = base; customColorId = custom
            }, enabled = !busy)
            TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.additional_details)); Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
            if (shopImported) OutlinedTextField(shopLink, { shopLink = TextLimits.input(it, 2000) }, label = { Text(stringResource(R.string.wish_link_optional)) },
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(linkView), enabled = !busy, isError = validation && !WishRules.validLink(shopLink),
                supportingText = { if (validation && !WishRules.validLink(shopLink)) Text(stringResource(R.string.wish_link_error)) })
            if (advanced) {
                Box(Modifier.fillMaxWidth().bringIntoViewRequester(priceView)) {
                    MoneyField(price, { price = it }, currency, { currency = it }, R.string.item_price_optional, busy,
                        validation && !moneyValid, priceFocus)
                }
                Field(size, { size = TextLimits.input(it, 40) }, R.string.size, enabled = !busy)
                Field(location, { location = TextLimits.input(it, 200) }, R.string.location, enabled = !busy, placeholder = stringResource(R.string.location_hint))
                Choice(season, Matching.seasons, { season = it }, R.string.season, enabled = !busy)
                Choice(style, Matching.styles, { style = it }, R.string.style, enabled = !busy)
                if (purchase == null && !shopImported) Choice(availability, Matching.availability, { availability = it }, R.string.availability, enabled = !busy)
                OutlinedTextField(description, { description = TextLimits.input(it, 1000) }, label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !busy)
            }
            if (validation && photo.isBlank()) Text(stringResource(R.string.item_required_fields), color = MaterialTheme.colorScheme.error)
            if (imported != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { saveItem(false) }, enabled = !busy) { Text(stringResource(R.string.review_save_queue)) }
                TextButton(onClick = { if (dirty) confirmDefer = true else deferReview() }, enabled = !busy) { Text(stringResource(R.string.review_defer)) }
                TextButton(onClick = reviewQueue, enabled = !busy) { Text(stringResource(R.string.review_queue)) }
            }
            if (original == null && purchase == null && imported == null) TextButton(onClick = { saveItem(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.save_add_next)) }
        }
        if (saveBlocker != null) Text(stringResource(saveBlocker), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = { saveItem(imported != null) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(if (imported != null) R.string.review_save_next else if (purchase == null) R.string.save else R.string.purchase_confirm)) }
    }
    if (confirmDefer) AlertDialog(onDismissRequest = { confirmDefer = false }, text = { Text(stringResource(R.string.review_defer_changes)) },
        confirmButton = { TextButton(onClick = { confirmDefer = false; deferReview() }) { Text(stringResource(R.string.review_defer)) } },
        dismissButton = { TextButton(onClick = { confirmDefer = false }) { Text(stringResource(R.string.keep_editing)) } })
    if (copyingPrevious && previousItem != null) AlertDialog(onDismissRequest = { copyingPrevious = false }, title = { Text(stringResource(R.string.import_copy_previous)) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.copy_fields_hint, previousItem.name))
            listOf("category" to (stringResource(R.string.category) + ": " + (catalog.customCategory(previousItem)?.name ?: stringResource(label(previousItem.category)))),
                "color" to (stringResource(R.string.color) + ": " + (catalog.customColor(previousItem)?.name ?: stringResource(label(previousItem.color)))),
                "season" to (stringResource(R.string.season) + ": " + stringResource(label(previousItem.season))),
                "style" to (stringResource(R.string.style) + ": " + stringResource(label(previousItem.style))),
                "subcategory" to (stringResource(R.string.item_subcategory) + ": " + previousItem.subcategory),
                "size" to (stringResource(R.string.size) + ": " + previousItem.size),
                "location" to (stringResource(R.string.location) + ": " + previousItem.location)).forEach { (key, text) ->
                SelectionRow(text, key in copyFields, !busy) { checked -> copyFields = ArrayList(if (checked) copyFields + key else copyFields - key) }
            }
        }
    }, confirmButton = { TextButton(onClick = {
        if ("category" in copyFields) { category = previousItem.category; customCategoryId = previousItem.customCategoryId.orEmpty(); subcategory = "" }
        if ("color" in copyFields) { color = previousItem.color; customColorId = previousItem.customColorId.orEmpty() }
        if ("season" in copyFields) season = previousItem.season
        if ("style" in copyFields) style = previousItem.style
        if ("subcategory" in copyFields) subcategory = previousItem.subcategory
        if ("size" in copyFields) size = previousItem.size
        if ("location" in copyFields) location = previousItem.location
        copyingPrevious = false
    }, enabled = !busy && copyFields.isNotEmpty()) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = { copyingPrevious = false }) { Text(stringResource(R.string.cancel)) } })
    duplicateConfirmation?.let { addNext -> AlertDialog(onDismissRequest = { duplicateConfirmation = null },
        title = { Text(stringResource(R.string.import_duplicate_title)) }, text = { Text(stringResource(R.string.import_duplicate_hint, duplicates.firstOrNull()?.name.orEmpty())) },
        confirmButton = { TextButton(onClick = { duplicateConfirmation = null; saveItem(addNext, true) }, enabled = !busy) { Text(stringResource(R.string.import_duplicate_keep)) } },
        dismissButton = { TextButton(onClick = { duplicateConfirmation = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
fun OutfitList(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, add: () -> Unit, daily: () -> Unit, calendar: () -> Unit, capsules: () -> Unit, open: (String) -> Unit) {
    val found = s.outfits.filter { it.wardrobeId == wardrobe }
    val busy by vm.busy.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = daily, enabled = !busy) { Text(stringResource(R.string.daily_title)) }
            OutlinedButton(onClick = calendar, enabled = !busy) { Text(stringResource(R.string.calendar_title)) }
            OutlinedButton(onClick = capsules, enabled = !busy) { Text(stringResource(R.string.capsules_title)) }
        } }
        item { Button(onClick = add, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_outfit)) } }
        if (found.isEmpty()) item { Text(stringResource(R.string.empty_outfits_new), Modifier.padding(vertical = 24.dp)) }
        items(found, key = { it.id }) { outfit ->
            val pieces = LookRules.saved(s, outfit)
            Card(onClick = { open(outfit.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { pieces.take(4).forEach { p -> ItemPhoto(p.photoId, vm, Modifier.weight(1f).height(110.dp), description = p.name, fit = true) } }
                Column(Modifier.padding(14.dp)) { Text(outfit.name, style = MaterialTheme.typography.titleMedium); Text(stringResource(LookRules.status(pieces)), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun SavedOutfit(outfit: Outfit, s: Snapshot, vm: WardrobeViewModel, today: String, open: (Garment) -> Unit, openWish: (String) -> Unit, schedule: () -> Unit, edit: () -> Unit) {
    var ideaDetails by remember { mutableStateOf<LookPiece?>(null) }
    val pieces = LookRules.saved(s, outfit)
    val owned = pieces.mapNotNull { it.ownedId }
    val marked = owned.isNotEmpty() && owned.all { id -> s.wears.any { it.garmentId == id && it.date == today && it.source == "outfit:${outfit.id}" } }
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(outfit.name, style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(LookRules.status(pieces)), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(label(outfit.occasion)) + " · " + stringResource(label(outfit.weather)))
        pieces.filter { !it.ready }.forEach { p ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(unavailableReason(p)), color = MaterialTheme.colorScheme.error)
                    s.garments.find { it.id == p.ownedId }?.let { garment ->
                        TextButton(onClick = { vm.changeAvailability(garment, availability = "available", archived = false) }, enabled = !busy) { Text(stringResource(R.string.restore_available)) }
                    }
                    TextButton(onClick = edit, enabled = !busy) { Text(stringResource(R.string.replace_or_restore)) }
                }
            }
        }
        OutfitComposition(pieces, vm, layout = outfit.layout, background = outfit.background) { p ->
            when { p.ownedId != null -> s.garments.find { it.id == p.ownedId }?.let(open)
                p.wishId != null -> openWish(p.wishId); else -> ideaDetails = p }
        }
        Button(onClick = { vm.wearOutfit(outfit.id) }, enabled = !busy && (marked || (pieces.isNotEmpty() && pieces.all { it.ownedId != null && it.ready })), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (marked) R.string.undo_outfit else R.string.wear_outfit))
        }
        OutlinedButton(onClick = edit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit)) }
        OutlinedButton(onClick = schedule, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calendar_plan)) }
        if (pieces.any { it.ownedId == null }) Text(stringResource(R.string.planned_wear_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.outfit_hint), style = MaterialTheme.typography.bodySmall)
    }
    ideaDetails?.let { p -> AlertDialog(onDismissRequest = { ideaDetails = null }, title = { Text(p.name) }, text = {
        Column { Text(stringResource(ownershipLabel(p))); Text(stringResource(label(p.category))); ItemPhoto(p.photoId, vm, Modifier.fillMaxWidth().height(180.dp), description = p.name, fit = true) }
    }, confirmButton = { TextButton(onClick = { ideaDetails = null }) { Text(stringResource(R.string.close)) } }) }

}

@Composable
fun Settings(vm: WardrobeViewModel, appearanceVm: AppearanceViewModel, accountEmail: String, onSignOut: () -> Unit, onNewWardrobe: () -> Unit, onManageWardrobes: () -> Unit, onAppearance: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val appearance by appearanceVm.appearance.collectAsStateWithLifecycle()
    var signOutDialog by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) vm.export(uri) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.previewRestore(uri) }
    val selectedLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val languageNames = mapOf("" to stringResource(R.string.system_language), "uk" to "Українська", "en" to "English", "pl" to "Polski")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.auth_account), style = MaterialTheme.typography.titleMedium)
        Text(accountEmail)
        CloudSyncPanel(vm)
        OutlinedButton(onClick = { signOutDialog = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_sign_out))
        }
        HorizontalDivider()
        Card(onClick = onAppearance, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
                    Text("${stringResource((appearance ?: AppearanceSettings()).palette.title)} · ${stringResource((appearance ?: AppearanceSettings()).mode.title)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, null)
            }
        }
        Box {
            OutlinedButton(onClick = { languageMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("${stringResource(R.string.language)}: ${languageNames[selectedLanguage] ?: selectedLanguage}")
            }
            DropdownMenu(languageMenu, onDismissRequest = { languageMenu = false }) {
                languageNames.forEach { (code, name) -> DropdownMenuItem(text = { Text(name) }, onClick = {
                    languageMenu = false; AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                }) }
            }
        }
        OutlinedButton(onClick = onNewWardrobe, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.new_wardrobe)) }
        OutlinedButton(onClick = onManageWardrobes, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_wardrobes)) }
        HorizontalDivider()
        Text(stringResource(R.string.backup), style = MaterialTheme.typography.titleLarge)
        BackupHealthPanel(vm, createNow = { export.launch("wardrobe-${LocalDate.now()}.zip") })
        SingleWardrobeBackupControl(vm)
        HorizontalDivider()
        FamilyTransferControl(vm)
        FamilyAccessControl(vm)
        HorizontalDivider()
        OutlinedButton(onClick = { restore.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_backup))
        }
        HorizontalDivider()
        Text(stringResource(R.string.local_version), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.local_notice), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (signOutDialog) AlertDialog(onDismissRequest = { signOutDialog = false },
        title = { Text(stringResource(R.string.auth_logout_title)) },
        text = { Text(stringResource(R.string.auth_logout_hint)) },
        confirmButton = { TextButton(onClick = { signOutDialog = false; onSignOut() }, enabled = !busy) { Text(stringResource(R.string.auth_sign_out)) } },
        dismissButton = { TextButton(onClick = { signOutDialog = false }) { Text(stringResource(R.string.cancel)) } })

}

@Composable
fun ItemPhoto(id: String, vm: WardrobeViewModel, modifier: Modifier = Modifier, description: String? = null, fit: Boolean = true, backgroundColor: androidx.compose.ui.graphics.Color? = null, garmentColour: String? = null) {
    ItemPhoto(id, vm.photos, modifier, description, fit, backgroundColor, garmentColour)
}

@Composable
internal fun ItemPhoto(id: String, photos: PhotoStore, modifier: Modifier = Modifier, description: String? = null, fit: Boolean = true, backgroundColor: androidx.compose.ui.graphics.Color? = null, garmentColour: String? = null) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, id, photos) {
        value = null
        if (id.isNotEmpty()) value = photos.bitmap(id)
    }
    val backdrop = rememberPhotoBackdrop(bitmap, garmentColour)
    Box(modifier.background(backgroundColor ?: if (bitmap != null) backdrop else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = description, modifier = Modifier.fillMaxSize(), contentScale = if (fit) ContentScale.Fit else ContentScale.Crop) }
            ?: Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Image, null, Modifier.size(36.dp))
                Text(stringResource(if (id.isEmpty()) R.string.photo else R.string.photo_missing), style = MaterialTheme.typography.bodySmall)
            }
    }
}

@Composable
fun Choice(value: String, options: List<String>, change: (String) -> Unit, title: Int,
    modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true, emptyLabel: Int = R.string.all,
    optionLabels: Map<String, Int> = emptyMap()) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(stringResource(title), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(if (value.isEmpty()) emptyLabel else optionLabels[value] ?: label(value)))
            }
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code -> DropdownMenuItem(text = { Text(stringResource(if (code.isEmpty()) emptyLabel else optionLabels[code] ?: label(code))) },
                onClick = { expanded = false; change(code) }) }
        }
    }
}

@Composable
fun Field(value: String, change: (String) -> Unit, title: Int, enabled: Boolean = true, placeholder: String = "") {
    OutlinedTextField(value, change, label = { Text(stringResource(title)) }, placeholder = { Text(placeholder) },
        singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
}

@Composable
fun Info(title: Int, value: String) {
    Column { Text(stringResource(title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value) }
}

@Composable
fun Toggle(title: Int, value: Boolean, change: (Boolean) -> Unit) {
    SelectionRow(stringResource(title), value, change = change)
}

@Composable
fun NameDialog(title: Int, field: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(title)) },
        text = { Field(name, { name = TextLimits.input(it, 80) }, field) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = TextLimits.hasVisibleText(name)) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
