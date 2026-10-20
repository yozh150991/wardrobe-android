package com.familywardrobe.app

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
private fun fieldLabel(field: String): String = stringResource(when (field) {
    "name" -> R.string.family_edit_name
    "size" -> R.string.family_edit_size
    "location" -> R.string.family_edit_location
    else -> R.string.family_edit_description
})

@Composable
internal fun FamilyEditDialog(vm: WardrobeViewModel, remote: String, dismiss: () -> Unit) {
    val state by vm.familyEdit.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmKeep by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED); tick = SystemClock.elapsedRealtime() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(foreground) { while (foreground) { tick = SystemClock.elapsedRealtime(); delay(500) } }
    val readable = tick >= 0 && foreground && vm.familyEdit.readable()
    val draft = state.draft?.takeIf { state.remoteId == remote && readable }
    val close = { if (dirty) confirmClose = true else dismiss() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.family_edit_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.family_edit_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = close) { Text(stringResource(R.string.close)) }
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.synced) Text(stringResource(R.string.family_edit_synced))
                if (state.saved) Text(stringResource(R.string.family_edit_saved))
                state.problem?.let { problem -> Text(stringResource(when (problem) {
                    FamilyProblem.ACCESS -> R.string.family_access
                    FamilyProblem.CONFLICT -> R.string.family_edit_conflict
                    FamilyProblem.MISSING -> R.string.family_edit_missing
                    FamilyProblem.CONFIG -> R.string.family_members_config
                    FamilyProblem.LIMIT -> R.string.family_limit
                    FamilyProblem.ARCHIVE -> R.string.family_edit_invalid
                    FamilyProblem.EMAIL -> R.string.family_email
                    FamilyProblem.EXPIRED -> R.string.family_read_expired
                    else -> R.string.family_members_retry
                }), color = MaterialTheme.colorScheme.error) }
                if (!readable && !state.running) {
                    Text(stringResource(R.string.family_read_expired))
                    OutlinedButton(onClick = { vm.familyEdit.open(remote) }) { Text(stringResource(R.string.family_read_refresh)) }
                }
                if (readable && draft == null && !state.running && !state.synced) Text(stringResource(R.string.family_edit_no_draft))
                if (draft != null) key(draft.editId) {
                    var name by rememberSaveable { mutableStateOf(draft.values.getValue("name")) }
                    var size by rememberSaveable { mutableStateOf(draft.values.getValue("size")) }
                    var location by rememberSaveable { mutableStateOf(draft.values.getValue("location")) }
                    var description by rememberSaveable { mutableStateOf(draft.values.getValue("description")) }
                    val values = mapOf("name" to name, "size" to size, "location" to location, "description" to description)
                    val unsaved = values != draft.values
                    SideEffect { dirty = unsaved }
                    Text(draft.original.getValue("name"), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.family_edit_base, draft.baseRevision))
                    val editable = !state.running && !draft.submitted
                    OutlinedTextField(name, { name = it.take(120) }, enabled = editable, label = { Text(fieldLabel("name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(size, { size = it.take(40) }, enabled = editable, label = { Text(fieldLabel("size")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(location, { location = it.take(200) }, enabled = editable, label = { Text(fieldLabel("location")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description = it.take(1000) }, enabled = editable, label = { Text(fieldLabel("description")) }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.familyEdit.save(values) }, enabled = editable && name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.family_edit_save))
                    }
                    if (draft.submitted) Text(stringResource(R.string.family_edit_pending), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.familyEdit.sync() }, enabled = !state.running && !unsaved && draft.changes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (draft.submitted) R.string.family_edit_retry else R.string.family_edit_sync))
                    }
                    if (state.conflict) {
                        OutlinedButton(onClick = { vm.familyEdit.reviewConflict() }, enabled = !state.running, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_edit_review)) }
                        state.latest?.let { latest ->
                            Text(stringResource(R.string.family_edit_comparison))
                            draft.changes.forEach { (field, local) ->
                                Text(fieldLabel(field), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.family_edit_server_value, latest.values.getValue(field)))
                                Text(stringResource(R.string.family_edit_local_value, local))
                            }
                            OutlinedButton(onClick = { confirmKeep = true }, enabled = !state.running, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_edit_keep)) }
                        }
                    }
                    TextButton(onClick = { confirmDiscard = true }, enabled = !state.running && (!draft.submitted || state.conflict), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.family_edit_discard))
                    }
                }
            }
        }
    }
    if (confirmDiscard || confirmKeep || confirmClose) AlertDialog(onDismissRequest = { confirmDiscard = false; confirmKeep = false; confirmClose = false },
        text = { Text(stringResource(when { confirmClose -> R.string.family_edit_close_confirm; confirmKeep -> R.string.family_edit_keep_confirm; else -> R.string.family_edit_discard_confirm })) },
        confirmButton = { TextButton(onClick = {
            when { confirmClose -> dismiss(); confirmKeep -> vm.familyEdit.keepLocalChanges(); else -> { dirty = false; vm.familyEdit.discard() } }
            confirmDiscard = false; confirmKeep = false; confirmClose = false
        }, enabled = !state.running) { Text(stringResource(R.string.family_confirm_action)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false; confirmKeep = false; confirmClose = false }) { Text(stringResource(R.string.cancel)) } })
}
