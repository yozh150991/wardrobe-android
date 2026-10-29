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

/** FAM-03: replace the photo of one existing shared item (gallery, camera or own item's final photo). */
@Composable
internal fun FamilyPhotoDialog(vm: WardrobeViewModel, remote: String, dismiss: () -> Unit) {
    val state by vm.familyPhoto.state.collectAsStateWithLifecycle()
    val reader by vm.familyReader.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var query by rememberSaveable { mutableStateOf("") }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf("") }
    var pendingCapture by rememberSaveable { mutableStateOf("") }
    var pendingGeneration by rememberSaveable { mutableLongStateOf(-1L) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED); tick = SystemClock.elapsedRealtime() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) { while (foreground) { tick = SystemClock.elapsedRealtime(); delay(500) } }
    val readable = tick >= 0 && foreground && vm.familyPhoto.readable() && state.remoteId == remote
    val draft = state.draft?.takeIf { readable }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.familyPhoto.prepareFromUri(uri, pendingGeneration)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingCapture.isNotEmpty()) vm.familyPhoto.prepareFromUri(Uri.parse(pendingCapture), pendingGeneration)
        pendingCapture = ""
    }
    // The current shared photo, from the read-only server copy the reader already holds.
    val view = reader.view?.takeIf { readable && reader.remoteId == remote && it.readable(tick) && vm.familyReader.readable(it) }
    val current = view?.candidate?.snapshot?.garments?.find { it.id == state.garmentId }
    val choose = readable && draft == null && !state.synced && !state.running && state.queue.size < FamilyMediaQueue.LIMIT
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.family_photo_title), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) }
                if (state.running) FamilyBusyRow(state.waiting) { vm.familyPhoto.cancel() }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text(stringResource(R.string.family_media_photo_hint)) }
                    if (readable && state.queue.isNotEmpty()) item {
                        FamilyMediaQueuePanel(state.queue, !state.running) { pending ->
                            choosing = false
                            vm.familyPhoto.open(remote, pending.garmentId, pending.name, state.localWardrobe)
                        }
                    }
                    if (state.garmentName.isNotBlank()) item { Text(stringResource(R.string.family_photo_item, state.garmentName), style = MaterialTheme.typography.titleMedium) }
                    if (state.synced) item { Text(stringResource(R.string.family_photo_synced)) }
                    state.problem?.let { problem -> item { Text(stringResource(when (problem) {
                        FamilyProblem.ACCESS -> R.string.family_access
                        FamilyProblem.CONFLICT -> if (draft?.change != null) R.string.family_change_conflict else R.string.family_photo_conflict
                        FamilyProblem.EXPIRED -> R.string.family_add_expired
                        FamilyProblem.MISSING -> R.string.family_edit_missing
                        FamilyProblem.ARCHIVE -> if (state.unreadablePhoto) R.string.photo_error_unreadable else R.string.family_archive
                        FamilyProblem.CONFIG -> R.string.family_members_config
                        FamilyProblem.LIMIT -> R.string.family_limit
                        FamilyProblem.EMAIL -> R.string.family_email
                        else -> R.string.family_members_retry
                    }), color = MaterialTheme.colorScheme.error) } }
                    if (!readable && !state.running) item {
                        Text(stringResource(R.string.family_read_expired))
                        OutlinedButton(onClick = {
                            vm.familyPhoto.open(remote, state.garmentId, state.garmentName, state.localWardrobe.ifEmpty { vm.active.value })
                        }, enabled = state.garmentId.isNotEmpty()) { Text(stringResource(R.string.family_read_refresh)) }
                    }
                    if (current != null && (draft == null || draft.garmentId == current.id)) item {
                        Text(stringResource(R.string.family_photo_current), style = MaterialTheme.typography.labelLarge)
                        ZoomableItemPhoto(current.photoId, view.candidate.photos, Modifier.fillMaxWidth().height(200.dp), current.name, garmentColour = current.color)
                    }
                    if (draft != null) {
                        item {
                            Text(stringResource(R.string.family_photo_new), style = MaterialTheme.typography.labelLarge)
                            state.preview?.let { ZoomableItemPhoto(draft.photoId, it, Modifier.fillMaxWidth().height(240.dp), draft.name) }
                            // A change draft is applied by the server to its newest head; only a full archive has a fixed base.
                            Text(if (draft.change != null) stringResource(R.string.family_change_latest)
                                else stringResource(R.string.family_edit_base, draft.publication.baseRevision))
                            Text(stringResource(R.string.family_photo_saved))
                        }
                        if (draft.submitted) item { Text(stringResource(R.string.family_edit_pending)) }
                        item { Button(onClick = { confirm = "send" }, enabled = !state.running && !state.recoverable && state.queue.firstOrNull() == draft, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(if (draft.submitted) R.string.family_edit_retry else R.string.family_photo_send))
                        } }
                        if (state.queue.firstOrNull() != draft) item { Text(stringResource(R.string.family_media_queue_wait)) }
                        if (draft.change == null && (state.recoverable || (!draft.submitted && state.queue.firstOrNull() == draft))) item { OutlinedButton(onClick = { confirm = "rebase" }, enabled = !state.running, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.family_add_rebase))
                        } }
                        item { TextButton(onClick = { confirm = "discard" }, enabled = !state.running && (!draft.submitted || state.recoverable), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.family_edit_discard))
                        } }
                    } else if (readable && !state.synced) {
                        item {
                            Text(stringResource(R.string.family_photo_import_note), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pendingGeneration = state.generation; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    enabled = choose, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                    Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.gallery))
                                }
                                OutlinedButton(onClick = {
                                    runCatching { vm.familyPhoto.newCapture() }.onSuccess { pendingGeneration = state.generation; pendingCapture = it.toString(); camera.launch(it) }
                                        .onFailure { vm.reportFailure(OperationKind.PHOTO, it as? Exception ?: IllegalStateException()) }
                                }, enabled = choose, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                    Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.camera))
                                }
                            }
                            OutlinedButton(onClick = { choosing = !choosing }, enabled = choose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.family_photo_from_item))
                            }
                        }
                        if (choosing) {
                            item {
                                Text(stringResource(R.string.family_photo_choose))
                                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(stringResource(R.string.family_read_search)) }, modifier = Modifier.fillMaxWidth())
                            }
                            val matches = state.choices.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                            if (matches.isEmpty()) item { Text(stringResource(R.string.family_add_empty)) }
                            items(matches, key = { it.id }) { garment ->
                                OutlinedButton(onClick = { vm.familyPhoto.prepareFromItem(garment.id); choosing = false }, enabled = choose,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    ItemPhoto(garment.photoId, vm, Modifier.size(48.dp)); Spacer(Modifier.width(8.dp)); Text(garment.name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm.isNotEmpty()) AlertDialog(onDismissRequest = { confirm = "" },
        text = { Text(stringResource(when (confirm) { "send" -> R.string.family_photo_send_confirm; "rebase" -> R.string.family_photo_rebase_confirm; else -> R.string.family_edit_discard_confirm })) },
        confirmButton = { TextButton(onClick = {
            when (confirm) { "send" -> vm.familyPhoto.sync(); "rebase" -> vm.familyPhoto.rebase(); else -> vm.familyPhoto.discard() }; confirm = ""
        }, enabled = !state.running && readable) { Text(stringResource(R.string.family_confirm_action)) } },
        dismissButton = { TextButton(onClick = { confirm = "" }) { Text(stringResource(R.string.cancel)) } })
}
