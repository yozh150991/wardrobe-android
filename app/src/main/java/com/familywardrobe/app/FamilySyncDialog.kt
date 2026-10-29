package com.familywardrobe.app

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun FamilySyncDialog(vm: WardrobeViewModel, remote: String, canSend: Boolean, dismiss: () -> Unit) {
    val state by vm.familySync.state.collectAsStateWithLifecycle()
    var background by remember { mutableStateOf(false) }
    var approved by remember { mutableStateOf<List<FamilySyncOperation>?>(null) }
    var approvedBackground by remember { mutableStateOf(false) }
    LaunchedEffect(remote) { vm.familySync.open(remote) }
    val current = state.remote == remote
    val plan = state.plan?.takeIf { current }
    val operations = state.operations.takeIf { current }.orEmpty()
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.family_sync_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.family_sync_hint))
                TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) }
                if (current && state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                plan?.let {
                    Text(stringResource(R.string.family_sync_progress, it.cursor, it.operations.size))
                    Text(stringResource(when (it.status) {
                        FamilySyncStatus.DONE -> R.string.family_sync_done
                        FamilySyncStatus.PAUSED, FamilySyncStatus.CANCELLED -> R.string.family_sync_paused
                        FamilySyncStatus.RETRY -> R.string.family_sync_retrying
                        else -> R.string.family_sync_queued
                    }))
                    if (it.active) OutlinedButton(onClick = { vm.familySync.cancel(remote) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.family_sync_stop))
                    }
                }
                val problem = if (current && state.problem.isNotEmpty()) state.problem else plan?.problem.orEmpty()
                // A finished batch that left whole-archive drafts queued is not an error (audit L-01).
                if (problem == "PARTIAL") Text(stringResource(R.string.family_sync_partial))
                else if (problem.isNotEmpty()) Text(stringResource(when (problem) {
                    "CONFLICT", "CHANGED", "EXPIRED" -> R.string.family_sync_review
                    "ACCESS" -> R.string.family_access
                    "EMAIL" -> R.string.family_email
                    "CONFIG" -> R.string.family_config
                    "LIMIT" -> R.string.family_limit
                    "RETRY", "RETRY_LIMIT" -> R.string.family_retry_error
                    "STOPPED", "SIGNED_OUT" -> R.string.family_sync_paused
                    else -> R.string.family_archive
                }), color = MaterialTheme.colorScheme.error)
                if (canSend && current) {
                    Text(stringResource(R.string.family_sync_pending, operations.size))
                    if (plan?.active != true) {
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(checked = background, onCheckedChange = { background = it }, enabled = !state.busy)
                            Text(stringResource(R.string.family_sync_background), Modifier.weight(1f).padding(top = 12.dp))
                        }
                        Button(onClick = { approved = operations.toList(); approvedBackground = background },
                            enabled = !state.busy && operations.isNotEmpty() && state.problem.isEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.family_sync_send))
                        }
                    }
                    operations.forEach { o ->
                        val kind = stringResource(when (o.kind) {
                            FamilySyncKind.EDIT -> R.string.family_sync_edit
                            FamilySyncKind.ADD -> R.string.family_sync_add
                            FamilySyncKind.PHOTO -> R.string.family_sync_photo
                            FamilySyncKind.OUTFIT -> R.string.family_sync_outfit
                        })
                        Text("$kind: ${state.labels["${o.kind}:${o.id}"].orEmpty()}")
                    }
                } else Text(stringResource(R.string.family_read_expired))
                OutlinedButton(onClick = { vm.familySync.open(remote) }, enabled = !state.busy) { Text(stringResource(R.string.family_read_refresh)) }
            }
        }
    }
    approved?.let { selection -> AlertDialog(onDismissRequest = { approved = null },
        text = { Text(stringResource(R.string.family_sync_confirm, selection.size)) },
        confirmButton = { TextButton(onClick = {
            vm.familySync.start(remote, selection, approvedBackground); approved = null
        }, enabled = canSend && current && !state.busy && plan?.active != true) { Text(stringResource(R.string.family_confirm_action)) } },
        dismissButton = { TextButton(onClick = { approved = null }) { Text(stringResource(R.string.cancel)) } }) }
}
