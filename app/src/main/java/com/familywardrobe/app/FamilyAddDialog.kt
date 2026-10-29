package com.familywardrobe.app

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
internal fun FamilyAddDialog(vm: WardrobeViewModel, remote: String, dismiss: () -> Unit) {
    val state by vm.familyAdd.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    // New item directly in the shared wardrobe (FAM-03). Launchers live at the dialog level so a
    // camera result is still delivered when the form item has scrolled out of the list.
    var creating by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf("top") }
    var newColor by rememberSaveable { mutableStateOf("black") }
    var newSize by rememberSaveable { mutableStateOf("") }
    var newLocation by rememberSaveable { mutableStateOf("") }
    var newDescription by rememberSaveable { mutableStateOf("") }
    var newSeason by rememberSaveable { mutableStateOf("all") }
    var newStyle by rememberSaveable { mutableStateOf("casual") }
    var newCustomCategory by rememberSaveable { mutableStateOf("") }
    var newCustomColor by rememberSaveable { mutableStateOf("") }
    // Shared custom labels come from the server copy the reader already holds for this wardrobe.
    val reader by vm.familyReader.state.collectAsStateWithLifecycle()
    val sharedSnapshot = reader.view?.takeIf { reader.remoteId == remote }?.candidate?.snapshot
    val sharedWardrobe = sharedSnapshot?.wardrobes?.singleOrNull()
    var pendingCapture by rememberSaveable { mutableStateOf("") }
    var pendingGeneration by rememberSaveable { mutableLongStateOf(-1L) }
    LaunchedEffect(state.synced) {
        if (state.synced) { creating = false; newName = ""; newSize = ""; newLocation = ""; newDescription = ""; newCustomCategory = ""; newCustomColor = "" }
    }
    val fields = FamilyNewItem(newName, newCategory, newColor, newSize, newLocation, newDescription, newSeason, newStyle,
        newCustomCategory.ifEmpty { null }, newCustomColor.ifEmpty { null })
    val customCategories = if (sharedSnapshot != null && sharedWardrobe != null) fields.categories(sharedSnapshot, sharedWardrobe) else emptyList()
    val customColors = if (sharedSnapshot != null && sharedWardrobe != null) fields.colors(sharedSnapshot, sharedWardrobe) else emptyList()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && fields.valid) vm.familyAdd.prepareNew(fields, uri, pendingGeneration)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingCapture.isNotEmpty() && fields.valid) vm.familyAdd.prepareNew(fields, Uri.parse(pendingCapture), pendingGeneration)
        pendingCapture = ""
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED); tick = SystemClock.elapsedRealtime() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) { while (foreground) { tick = SystemClock.elapsedRealtime(); delay(500) } }
    val readable = tick >= 0 && foreground && vm.familyAdd.readable() && state.remoteId == remote
    val draft = state.draft?.takeIf { readable }
    val canAdd = readable && !state.running && state.queue.size < FamilyMediaQueue.LIMIT
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.family_add_title), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) }
                if (state.running) FamilyBusyRow(state.waiting) { vm.familyAdd.cancel() }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text(stringResource(R.string.family_media_add_hint)) }
                    if (readable && state.queue.isNotEmpty()) item {
                        FamilyMediaQueuePanel(state.queue, !state.running) { pending ->
                            vm.familyAdd.open(remote, state.localWardrobe, pending.garmentId)
                        }
                    }
                    if (readable && (draft != null || state.synced)) item {
                        OutlinedButton(onClick = {
                            creating = false; newName = ""; newSize = ""; newLocation = ""; newDescription = ""
                            newCustomCategory = ""; newCustomColor = ""
                            vm.familyAdd.open(remote, state.localWardrobe, startNew = true)
                        }, enabled = canAdd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_media_add_another)) }
                    }
                    if (state.synced) item { Text(stringResource(R.string.family_add_synced)) }
                    state.problem?.let { problem -> item { Text(stringResource(when (problem) {
                        FamilyProblem.ACCESS -> R.string.family_access
                        FamilyProblem.CONFLICT -> if (draft?.change != null) R.string.family_change_conflict else R.string.family_add_conflict
                        FamilyProblem.EXPIRED -> R.string.family_add_expired
                        FamilyProblem.ARCHIVE -> if (state.unreadablePhoto) R.string.photo_error_unreadable else R.string.family_archive
                        FamilyProblem.CONFIG -> R.string.family_members_config
                        FamilyProblem.LIMIT -> R.string.family_limit
                        FamilyProblem.EMAIL -> R.string.family_email
                        else -> R.string.family_members_retry
                    }), color = MaterialTheme.colorScheme.error) } }
                    if (!readable && !state.running) item {
                        Text(stringResource(R.string.family_read_expired))
                        OutlinedButton(onClick = { vm.familyAdd.open(remote, state.localWardrobe.ifEmpty { vm.active.value }) }) { Text(stringResource(R.string.family_read_refresh)) }
                    }
                    if (draft != null) {
                        item {
                            Text(draft.name, style = MaterialTheme.typography.titleMedium)
                            state.preview?.let { ZoomableItemPhoto(draft.photoId, it, Modifier.fillMaxWidth().height(240.dp), draft.name) }
                            // A change draft is applied by the server to its newest head; only a full archive has a fixed base.
                            Text(if (draft.change != null) stringResource(R.string.family_change_latest)
                                else stringResource(R.string.family_edit_base, draft.publication.baseRevision))
                            Text(stringResource(R.string.family_add_saved))
                        }
                        state.selection?.let { selection ->
                            val detail = selection.garments.single()
                            item {
                                Text(selection.customCategories.singleOrNull()?.name ?: stringResource(label(detail.category)))
                                Text(selection.customColors.singleOrNull()?.name ?: stringResource(label(detail.color)))
                                Text(stringResource(R.string.family_read_size, detail.size))
                                if (detail.subcategory.isNotBlank()) Text(detail.subcategory)
                                if (detail.location.isNotBlank()) Text(detail.location)
                                if (detail.description.isNotBlank()) Text(detail.description)
                                Text(stringResource(label(detail.season)))
                                Text(stringResource(label(detail.style)))
                                Text(stringResource(label(detail.availability)))
                                detail.priceMinor?.let { Text(stringResource(R.string.family_add_price, it / 100.0, detail.currency)) }
                                Text(stringResource(R.string.family_add_flags, stringResource(if (detail.favorite) R.string.family_add_yes else R.string.family_add_no),
                                    stringResource(if (detail.rarelyWorn) R.string.family_add_yes else R.string.family_add_no), stringResource(if (detail.suggestionExcluded) R.string.family_add_yes else R.string.family_add_no)))
                            }
                        }
                        if (draft.submitted) item { Text(stringResource(R.string.family_edit_pending)) }
                        item { Button(onClick = { confirm = "send" }, enabled = !state.running && !state.recoverable && state.queue.firstOrNull() == draft, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (draft.submitted) R.string.family_edit_retry else R.string.family_add_send))
                        } }
                        if (state.queue.firstOrNull() != draft) item { Text(stringResource(R.string.family_media_queue_wait)) }
                        if (draft.change == null && (state.recoverable || (!draft.submitted && state.queue.firstOrNull() == draft))) item { OutlinedButton(onClick = { confirm = "rebase" }, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.family_add_rebase))
                        } }
                        item { TextButton(onClick = { confirm = "discard" }, enabled = !state.running && (!draft.submitted || state.recoverable), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.family_edit_discard))
                        } }
                    } else if (readable && !state.synced) {
                        item {
                            OutlinedButton(onClick = { creating = !creating }, enabled = !state.running, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.family_new_open))
                            }
                        }
                        if (creating) item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.family_new_hint), style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(newName, { newName = TextLimits.input(it, 120) }, singleLine = true, label = { Text(stringResource(R.string.family_edit_name)) }, modifier = Modifier.fillMaxWidth())
                                Choice(newCategory, Matching.categories, { newCategory = it; newCustomCategory = "" }, R.string.category, enabled = !state.running)
                                if (customCategories.isNotEmpty()) NamedChoice(newCustomCategory, customCategories.map { it.id to it.name },
                                    { newCustomCategory = it }, R.string.family_new_custom_category, !state.running)
                                Choice(newColor, Matching.colors, { newColor = it; newCustomColor = "" }, R.string.color, enabled = !state.running)
                                if (customColors.isNotEmpty()) NamedChoice(newCustomColor, customColors.map { it.id to it.name },
                                    { newCustomColor = it }, R.string.family_new_custom_color, !state.running)
                                OutlinedTextField(newSize, { newSize = TextLimits.input(it, 40) }, singleLine = true, label = { Text(stringResource(R.string.family_edit_size)) }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(newLocation, { newLocation = TextLimits.input(it, 200) }, label = { Text(stringResource(R.string.family_edit_location)) }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(newDescription, { newDescription = TextLimits.input(it, 1000) }, label = { Text(stringResource(R.string.family_edit_description)) }, modifier = Modifier.fillMaxWidth())
                                Choice(newSeason, Matching.seasons, { newSeason = it }, R.string.season, enabled = !state.running)
                                Choice(newStyle, Matching.styles, { newStyle = it }, R.string.style, enabled = !state.running)
                                if (!fields.valid) Text(stringResource(R.string.family_new_name_first), style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { pendingGeneration = state.generation; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                        enabled = canAdd && fields.valid, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                        Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.gallery))
                                    }
                                    OutlinedButton(onClick = {
                                        runCatching { vm.familyAdd.newCapture() }.onSuccess { pendingGeneration = state.generation; pendingCapture = it.toString(); camera.launch(it) }
                                            .onFailure { vm.reportFailure(OperationKind.PHOTO, it as? Exception ?: IllegalStateException()) }
                                    }, enabled = canAdd && fields.valid, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                        Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.camera))
                                    }
                                }
                            }
                        }
                        item {
                            Text(stringResource(R.string.family_add_choose, state.wardrobeName))
                            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(stringResource(R.string.family_read_search)) }, modifier = Modifier.fillMaxWidth())
                        }
                        val matches = state.choices.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                        if (matches.isEmpty()) item { Text(stringResource(R.string.family_add_empty)) }
                        items(matches, key = { it.id }) { garment ->
                            OutlinedButton(onClick = { vm.familyAdd.prepare(garment.id) }, enabled = canAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(garment.name) }
                        }
                    }
                }
            }
        }
    }
    if (confirm.isNotEmpty()) AlertDialog(onDismissRequest = { confirm = "" },
        text = { Text(stringResource(when (confirm) { "send" -> R.string.family_add_send_confirm; "rebase" -> R.string.family_add_rebase_confirm; else -> R.string.family_edit_discard_confirm })) },
        confirmButton = { TextButton(onClick = {
            when (confirm) { "send" -> vm.familyAdd.sync(); "rebase" -> vm.familyAdd.rebase(); else -> vm.familyAdd.discard() }; confirm = ""
        }, enabled = !state.running && readable) { Text(stringResource(R.string.family_confirm_action)) } },
        dismissButton = { TextButton(onClick = { confirm = "" }) { Text(stringResource(R.string.cancel)) } })
}

/** Dropdown of named options (shared custom labels); "" means none. */
@Composable
private fun NamedChoice(value: String, options: List<Pair<String, String>>, change: (String) -> Unit, title: Int, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val none = stringResource(R.string.family_new_custom_none)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.labelSmall)
                Text(options.find { it.first == value }?.second ?: none)
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            (listOf("" to none) + options).forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; change(id) })
            }
        }
    }
}
