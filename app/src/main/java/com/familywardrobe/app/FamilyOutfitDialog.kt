package com.familywardrobe.app

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import org.json.JSONObject

@Composable
internal fun FamilyOutfitDialog(vm: WardrobeViewModel, remote: String, dismiss: () -> Unit) {
    val state by vm.familyOutfit.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var confirm by remember { mutableStateOf("") }
    var confirmGeneration by remember { mutableLongStateOf(-1) }
    var confirmDraft by remember { mutableStateOf<FamilyOutfitDraft?>(null) }
    fun ask(action: String) { confirm = action; confirmGeneration = state.generation; confirmDraft = state.draft }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED); tick = SystemClock.elapsedRealtime()
        }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) { while (foreground) { tick = SystemClock.elapsedRealtime(); delay(500) } }
    val readable = tick >= 0 && foreground && state.remote == remote && vm.familyOutfit.readable()
    val draft = state.draft?.takeIf { readable }
    val form = state.form?.takeIf { readable }
    val view = state.view?.takeIf { readable && it.readable(tick) && vm.familyReader.readable(it) }
    val editing = form != null && view != null && form.base == view.remote.revision &&
        (form.outfit.isEmpty() || view.candidate.snapshot.outfits.any { it.id == form.outfit })
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.family_look_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) }
                }
                if (state.running) FamilyBusyRow(state.waiting) { vm.familyOutfit.cancel() }
                if (readable) Column(Modifier.padding(horizontal = 16.dp)) {
                    FamilyOutfitQueuePanel(state.queue, !state.running) { vm.familyOutfit.open(remote, queued = it.change.id) }
                    if (form == null) TextButton(onClick = { vm.familyOutfit.open(remote, startNew = true) },
                        enabled = !state.running && (state.hasForm || state.queue.size < FamilyOutfitQueue.LIMIT)) {
                        Text(stringResource(if (state.hasForm) R.string.family_look_resume_form else R.string.family_look_add_another))
                    }
                }
                if (editing && state.problem != FamilyProblem.ACCESS) {
                    if (state.problem != null) Text(stringResource(R.string.family_look_validation),
                        Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                    key(state.generation) {
                        val currentForm = requireNotNull(form); val currentView = requireNotNull(view)
                        val generation = state.generation
                        val port = remember {
                            LookEditorPort(currentForm.payload.takeIf { it.isNotEmpty() }?.let(::JSONObject),
                                keep = { vm.familyOutfit.keep(generation, it.toString()) },
                                clear = { vm.familyOutfit.discard(generation) },
                                save = { save -> vm.familyOutfit.prepare(generation, save.pieces, save.name, save.occasion,
                                    save.weather, save.source, save.target, save.layout, save.background) })
                        }
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.padding(horizontal = 16.dp)) {
                                Text(stringResource(R.string.family_look_draft_hint), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { ask("discard") }, enabled = !state.running) { Text(stringResource(R.string.family_edit_discard)) }
                            }
                            CompositionLocalProvider(LocalLookPhotos provides currentView.candidate.photos) {
                                LookEditor(currentView.candidate.snapshot, currentView.remote.identity.wardrobeId, "", currentForm.outfit,
                                    vm, onDirty = {}, onStage = {}, port = port, saving = state.running, done = { dismiss() })
                            }
                        }
                    }
                } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.family_look_queue_hint))
                    if (state.synced) Text(stringResource(R.string.family_look_synced))
                    state.problem?.let { problem -> Text(stringResource(when (problem) {
                        FamilyProblem.ACCESS -> R.string.family_access
                        FamilyProblem.CONFLICT -> R.string.family_look_conflict
                        FamilyProblem.EXPIRED -> R.string.family_add_expired
                        FamilyProblem.MISSING -> R.string.family_look_missing
                        FamilyProblem.ARCHIVE -> R.string.family_look_validation
                        FamilyProblem.CONFIG -> R.string.family_config
                        FamilyProblem.EMAIL -> R.string.family_email
                        FamilyProblem.LIMIT -> R.string.family_limit
                        else -> R.string.family_retry_error
                    }), color = MaterialTheme.colorScheme.error) }
                    if (!readable && !state.running) {
                        Text(stringResource(R.string.family_read_expired))
                        OutlinedButton(onClick = { vm.familyOutfit.open(remote, queued = state.draft?.change?.id) }) { Text(stringResource(R.string.family_read_refresh)) }
                    }
                    if (draft != null) {
                        Text(draft.change.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.picker_parts, draft.change.pieces.size))
                        Text(stringResource(R.string.family_edit_base, draft.publication.baseRevision))
                        if (draft.submitted) Text(stringResource(R.string.family_edit_pending))
                        val isHead = FamilyOutfitQueue.same(state.queue.firstOrNull(), draft)
                        if (!isHead) Text(stringResource(R.string.family_media_queue_wait))
                        Button(onClick = { ask("send") }, enabled = !state.running && !state.recoverable && isHead, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (draft.submitted) R.string.family_edit_retry else R.string.family_look_send))
                        }
                        if (state.recoverable || !draft.submitted) OutlinedButton(onClick = { ask("rebase") }, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.family_add_rebase))
                        }
                        TextButton(onClick = { ask("discard") }, enabled = !state.running && (!draft.submitted || state.recoverable)) {
                            Text(stringResource(R.string.family_edit_discard))
                        }
                    } else if (form != null) {
                        Text(stringResource(R.string.family_look_draft_hint))
                        OutlinedButton(onClick = { ask("rebase") }, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.family_add_rebase))
                        }
                        TextButton(onClick = { ask("discard") }, enabled = !state.running) { Text(stringResource(R.string.family_edit_discard)) }
                    } else if (readable && !state.running && !state.synced) {
                        Button(onClick = { vm.familyOutfit.open(remote) }) { Text(stringResource(R.string.family_look_open)) }
                    }
                }
            }
        }
    }
    if (confirm.isNotEmpty()) AlertDialog(onDismissRequest = { confirm = "" },
        text = { Text(stringResource(when (confirm) {
            "send" -> R.string.family_look_send_confirm
            "rebase" -> R.string.family_look_rebase_confirm
            else -> R.string.family_edit_discard_confirm
        })) },
        confirmButton = { TextButton(onClick = {
            when (confirm) { "send" -> vm.familyOutfit.sync(confirmDraft); "rebase" -> vm.familyOutfit.rebase(confirmGeneration); else -> vm.familyOutfit.discard(confirmGeneration) }
            confirm = ""
        }, enabled = readable && !state.running && state.generation == confirmGeneration) { Text(stringResource(R.string.family_confirm_action)) } },
        dismissButton = { TextButton(onClick = { confirm = "" }) { Text(stringResource(R.string.cancel)) } })
}
