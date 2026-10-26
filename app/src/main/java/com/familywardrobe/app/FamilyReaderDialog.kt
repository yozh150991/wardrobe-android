package com.familywardrobe.app

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun FamilyReaderDialog(vm: WardrobeViewModel, remoteId: String, onDismiss: () -> Unit) {
    val state by vm.familyReader.state.collectAsStateWithLifecycle()
    val access by vm.familyAccess.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var syncing by rememberSaveable(remoteId) { mutableStateOf(false) }
    var outfit by rememberSaveable(remoteId) { mutableStateOf(false) }
    var adding by rememberSaveable(remoteId) { mutableStateOf(false) }
    var editing by rememberSaveable(remoteId) { mutableStateOf(false) }
    var photo by rememberSaveable(remoteId) { mutableStateOf(false) }
    var query by rememberSaveable(remoteId) { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            now = SystemClock.elapsedRealtime()
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) {
        while (foreground) { now = SystemClock.elapsedRealtime(); delay(500) }
    }
    val view = state.view?.takeIf { foreground && state.remoteId == remoteId && it.readable(now) && vm.familyReader.readable(it) }
    Dialog(onDismissRequest = { if (state.running) vm.cancelOperation(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.family_read_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.family_read_hint), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.readFamily(remoteId, refresh = true) }, enabled = !busy && !state.running,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.family_read_refresh)) }
                    TextButton(onClick = { if (state.running) vm.cancelOperation(); onDismiss() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.close)) }
                }
                OutlinedButton(onClick = { syncing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_sync_title)) }
                if (state.remoteId == remoteId && state.running) {
                    Text(stringResource(R.string.family_read_loading), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    val progress = familyUploadProgress(true, state.bytes, state.total)
                    if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                }
                if (view == null && !state.running) Text(stringResource(when (state.problem) {
                    FamilyProblem.ACCESS -> R.string.family_access
                    FamilyProblem.EMAIL -> R.string.family_email
                    FamilyProblem.CONFIG -> R.string.family_config
                    FamilyProblem.ARCHIVE -> R.string.family_archive
                    FamilyProblem.LIMIT -> R.string.family_limit
                    FamilyProblem.CONFLICT -> R.string.family_read_changed
                    FamilyProblem.MISSING -> R.string.family_read_empty_head
                    FamilyProblem.RETRY -> R.string.family_retry_error
                    else -> R.string.family_read_expired
                }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (view != null) key(view.candidate) {
                    val snapshot = view.candidate.snapshot
                    // A default-named wardrobe of another owner must not read as the member's own "My wardrobe".
                    val shared = access.wardrobes.find { it.remoteId == remoteId }?.name.orEmpty()
                    val wardrobe = snapshot.wardrobes.single()
                    Text(shared.ifBlank { if (view.remote.role == "owner") wardrobe.displayName()
                        else if (wardrobe.usesDefaultName) stringResource(R.string.family_unnamed) else wardrobe.name },
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.family_read_counts, view.remote.revision, snapshot.garments.size, snapshot.outfits.size, snapshot.wishes.size))
                    Text(stringResource(when (view.remote.role) {
                        "owner" -> R.string.family_read_owner
                        "editor" -> R.string.family_read_editor
                        else -> R.string.family_read_viewer
                    }), style = MaterialTheme.typography.bodySmall)
                    val matches = snapshot.garments.filter { item ->
                        query.isBlank() || listOf(item.name, item.location, item.description).any { it.contains(query.trim(), ignoreCase = true) }
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (view.remote.role in setOf("owner", "editor")) {
                                    OutlinedButton(onClick = { vm.familyEdit.open(remoteId); editing = true }, enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_edit_resume)) }
                                    OutlinedButton(onClick = { vm.familyAdd.open(remoteId, vm.active.value); adding = true }, enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_add_open)) }
                                    OutlinedButton(onClick = { vm.familyOutfit.open(remoteId); outfit = true }, enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_look_open)) }
                                }
                                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                                    label = { Text(stringResource(R.string.family_read_search)) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        items(snapshot.outfits.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }, key = { "outfit:${it.id}" }) { look ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(look.name, style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(label(look.source)), style = MaterialTheme.typography.bodySmall)
                                    CompositionLocalProvider(LocalLookPhotos provides view.candidate.photos) {
                                        OutfitComposition(LookRules.saved(snapshot, look), vm, enabled = false,
                                            layout = look.layout, background = look.background) {}
                                    }
                                    if (view.remote.role in setOf("owner", "editor")) OutlinedButton(onClick = {
                                        vm.familyOutfit.open(remoteId, look.id); outfit = true
                                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_look_edit)) }
                                }
                            }
                        }
                        if (matches.isEmpty()) item { Text(stringResource(R.string.family_read_no_items)) }
                        items(matches, key = { it.id }) { garment ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ZoomableItemPhoto(garment.photoId, view.candidate.photos, Modifier.fillMaxWidth().height(200.dp),
                                        garment.name, garmentColour = garment.color)
                                    if (view.remote.role in setOf("owner", "editor")) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { vm.familyEdit.open(remoteId, garment.id); editing = true }, enabled = !busy,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.family_edit_open)) }
                                        OutlinedButton(onClick = { vm.familyPhoto.open(remoteId, garment.id, garment.name, vm.active.value); photo = true }, enabled = !busy,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.family_photo_open)) }
                                    }
                                    Text(garment.name, style = MaterialTheme.typography.titleMedium)
                                    Text(snapshot.customCategories.find { it.id == garment.customCategoryId }?.name
                                        ?: stringResource(label(garment.category)))
                                    if (garment.subcategory.isNotBlank()) Text(garment.subcategory)
                                    if (garment.size.isNotBlank()) Text(stringResource(R.string.family_read_size, garment.size))
                                    if (garment.location.isNotBlank()) Text(garment.location)
                                    if (garment.description.isNotBlank()) Text(garment.description)
                                    Text(stringResource(if (garment.archived) R.string.archived else label(garment.availability)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (syncing) FamilySyncDialog(vm, remoteId, view != null && view.remote.role in setOf("owner", "editor")) { syncing = false }
    if (outfit) FamilyOutfitDialog(vm, remoteId) { outfit = false }
    if (adding) FamilyAddDialog(vm, remoteId) { adding = false }
    if (editing) FamilyEditDialog(vm, remoteId) { editing = false }
    if (photo) FamilyPhotoDialog(vm, remoteId) { photo = false }
}
