package com.familywardrobe.app

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

private data class CloudChoice(val choice: String, val local: Long, val remote: Long)

@Composable
fun CloudSyncPanel(vm: WardrobeViewModel) {
    val state by vm.cloud.state.collectAsStateWithLifecycle()
    val running by vm.cloud.running.collectAsStateWithLifecycle()
    val online by vm.network.online.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val initialized by vm.initialized.collectAsStateWithLifecycle()
    val snapshot by vm.state.collectAsStateWithLifecycle()
    var choice by remember { mutableStateOf<CloudChoice?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.cloud_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(state.enabled, { vm.enableCloud(it) }, enabled = initialized && !busy)
            }
            Text(stringResource(R.string.cloud_opt_in), style = MaterialTheme.typography.bodySmall)
            if (state.enabled) {
                val status = when {
                    state.conflictRevision != 0L -> R.string.cloud_conflict
                    !online -> R.string.cloud_offline
                    running -> R.string.cloud_running
                    state.error == "DRAFT" -> R.string.cloud_draft
                    state.error == "CONFIG" -> R.string.cloud_config
                    state.error == "INVALID" -> R.string.cloud_invalid
                    state.error.isNotEmpty() -> R.string.cloud_retry_hint
                    state.dirty -> R.string.cloud_pending
                    state.lastSuccess == 0L -> R.string.cloud_pending
                    else -> R.string.cloud_current
                }
                Text(stringResource(status), color = if (state.conflictRevision != 0L || state.error.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.lastSuccess > 0L) Text(stringResource(R.string.cloud_last_success,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(state.lastSuccess))), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.cloud.retry() }, enabled = !busy && !running, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cloud_retry)) }
                if (state.conflictRevision != 0L) {
                    Text(stringResource(R.string.cloud_conflict_hint), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.cloud_local_counts, snapshot.wardrobes.size, snapshot.garments.size, snapshot.outfits.size))
                    Text(stringResource(R.string.cloud_remote_counts, state.conflictWardrobes, state.conflictGarments, state.conflictOutfits))
                    listOf("both" to R.string.cloud_keep_both, "local" to R.string.cloud_keep_local, "cloud" to R.string.cloud_keep_remote).forEach { (code, title) ->
                        OutlinedButton(onClick = { choice = CloudChoice(code, state.localRevision, state.conflictRevision) }, enabled = !busy && !running && online, modifier = Modifier.fillMaxWidth()) { Text(stringResource(title)) }
                    }
                }
            }
            vm.cloud.rescueFiles().forEach { rescue ->
                TextButton(onClick = { vm.previewRestore(Uri.fromFile(rescue)) }, enabled = !busy) { Text(stringResource(R.string.cloud_rescue_at,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(rescue.lastModified())))) }
            }
        }
    }
    choice?.let { selected ->
        val text = when(selected.choice) { "local" -> R.string.cloud_confirm_local; "cloud" -> R.string.cloud_confirm_remote; else -> R.string.cloud_confirm_both }
        AlertDialog(onDismissRequest = { choice = null }, title = { Text(stringResource(R.string.cloud_conflict)) }, text = { Text(stringResource(text)) },
            confirmButton = { TextButton(onClick = { choice = null; vm.resolveCloud(selected.choice, selected.local, selected.remote) }, enabled = !busy) { Text(stringResource(R.string.cloud_apply)) } },
            dismissButton = { TextButton(onClick = { choice = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
