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
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED); tick = SystemClock.elapsedRealtime() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) { while (foreground) { tick = SystemClock.elapsedRealtime(); delay(500) } }
    val readable = tick >= 0 && foreground && vm.familyAdd.readable() && state.remoteId == remote
    val draft = state.draft?.takeIf { readable }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.family_add_title), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) }
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text(stringResource(R.string.family_add_hint)) }
                    if (state.synced) item { Text(stringResource(R.string.family_add_synced)) }
                    state.problem?.let { problem -> item { Text(stringResource(when (problem) {
                        FamilyProblem.ACCESS -> R.string.family_access
                        FamilyProblem.CONFLICT -> R.string.family_add_conflict
                        FamilyProblem.EXPIRED -> R.string.family_add_expired
                        FamilyProblem.ARCHIVE -> R.string.family_archive
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
                            Text(stringResource(R.string.family_edit_base, draft.publication.baseRevision))
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
                        item { Button(onClick = { confirm = "send" }, enabled = !state.running && !state.recoverable, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (draft.submitted) R.string.family_edit_retry else R.string.family_add_send))
                        } }
                        if (state.recoverable) item { OutlinedButton(onClick = { confirm = "rebase" }, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.family_add_rebase))
                        } }
                        item { TextButton(onClick = { confirm = "discard" }, enabled = !state.running && (!draft.submitted || state.recoverable), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.family_edit_discard))
                        } }
                    } else if (readable && !state.synced) {
                        item {
                            Text(stringResource(R.string.family_add_choose, state.wardrobeName))
                            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(stringResource(R.string.family_read_search)) }, modifier = Modifier.fillMaxWidth())
                        }
                        val matches = state.choices.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                        if (matches.isEmpty()) item { Text(stringResource(R.string.family_add_empty)) }
                        items(matches, key = { it.id }) { garment ->
                            OutlinedButton(onClick = { vm.familyAdd.prepare(garment.id) }, enabled = !state.running, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(garment.name) }
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
