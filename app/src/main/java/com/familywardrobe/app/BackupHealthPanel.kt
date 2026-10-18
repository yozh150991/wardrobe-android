package com.familywardrobe.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupHealthPanel(vm: WardrobeViewModel, createNow: () -> Unit) {
    val health by vm.backupHealth.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val checked by vm.backupVerification.collectAsStateWithLifecycle()
    val scheduled by vm.scheduledBackup.collectAsStateWithLifecycle()
    var details by rememberSaveable { mutableStateOf(false) }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.chooseBackupFolder(uri)
    }
    val verify = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.verifyBackup(uri)
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.backup_health_title), style = MaterialTheme.typography.titleMedium)
            Text(if (health.lastSuccess == 0L) stringResource(R.string.backup_never) else
                stringResource(R.string.backup_last_verified, timestampLabel(health.lastSuccess)))
            if (scheduled == ScheduledBackupState.RUNNING) {
                Text(stringResource(R.string.backup_background_running))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (health.failed) Text(stringResource(R.string.backup_failed_safe), color = MaterialTheme.colorScheme.error)
            else if (health.overdue()) Text(stringResource(R.string.backup_reminder))
            Button(onClick = createNow, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_create_now)) }
            TextButton(onClick = { details = !details }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(if (details) R.string.backup_hide_details else R.string.backup_details)) }
            Text(stringResource(R.string.local_notice), style = MaterialTheme.typography.bodySmall)
            if (details) {
            Text(stringResource(R.string.backup_notice), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.backup_schedule_hint), style = MaterialTheme.typography.bodySmall)
            if (health.lastAttempt > 0) Text(stringResource(R.string.backup_last_attempt, timestampLabel(health.lastAttempt)), style = MaterialTheme.typography.bodySmall)
            if (health.enabled) Text(stringResource(when (scheduled) {
                ScheduledBackupState.RUNNING -> R.string.backup_background_running
                ScheduledBackupState.QUEUED -> R.string.backup_background_queued
                ScheduledBackupState.BLOCKED -> R.string.backup_background_blocked
                ScheduledBackupState.SCHEDULED -> R.string.backup_background_scheduled
                else -> R.string.backup_background_idle
            }))
            OutlinedButton(onClick = { choose.launch(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.backup_choose_folder))
            }
            if (health.folder.isNotBlank()) Text(health.folderName.ifBlank { stringResource(R.string.backup_selected_folder) })
            if (health.folder.isBlank()) Text(stringResource(R.string.backup_choose_folder_first), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(health.enabled, enabled = !busy && health.folder.isNotBlank(), role = Role.Switch,
                onValueChange = vm::enableScheduledBackup), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.backup_daily), Modifier.weight(1f).padding(end = 8.dp))
                Switch(checked = health.enabled, onCheckedChange = null,
                    enabled = !busy && health.folder.isNotBlank())
            }
            OutlinedButton(onClick = { verify.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_verify)) }
            }
        }
    }
    checked?.let { result -> AlertDialog(onDismissRequest = { vm.backupVerification.value = null },
        title = { Text(stringResource(R.string.backup_verified)) },
        text = { Text(stringResource(R.string.backup_verified_counts, result.wardrobes, result.garments, result.outfits)) },
        confirmButton = { TextButton(onClick = { vm.backupVerification.value = null }) { Text(stringResource(R.string.close)) } }) }
}
