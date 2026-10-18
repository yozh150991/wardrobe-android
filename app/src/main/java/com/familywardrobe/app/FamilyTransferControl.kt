package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun FamilyTransferControl(vm: WardrobeViewModel) {
    val content by vm.state.collectAsStateWithLifecycle()
    val state by vm.family.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val cloud by vm.cloud.state.collectAsStateWithLifecycle()
    var choosing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf("") }
    var restart by rememberSaveable { mutableStateOf(false) }
    val enabled = !busy && !state.running && !state.loading
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.family_title),style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.family_hint),style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { choosing = true },enabled = enabled && content.wardrobes.isNotEmpty(),modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.family_choose))
        }
        if (state.running) Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(when(state.step) {
                FamilyStep.CHECKING -> R.string.family_checking
                FamilyStep.PREPARING -> R.string.family_preparing
                FamilyStep.UPLOADING -> R.string.family_uploading
                FamilyStep.COMMITTING -> R.string.family_committing
                FamilyStep.VERIFYING -> R.string.family_verifying
                FamilyStep.DONE -> R.string.family_complete
            }))
            // Read one immutable state now. Compose may invoke progress later, after
            // the controller has moved to COMMITTING with bytes/total reset to zero.
            val progressState = state
            val uploadProgress = familyUploadProgress(
                progressState.step == FamilyStep.UPLOADING, progressState.bytes, progressState.total)
            if (uploadProgress != null) LinearProgressIndicator(
                progress = uploadProgress,modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.problem?.let { problem ->
            Text(stringResource(when(problem) {
                FamilyProblem.EMAIL -> R.string.family_email
                FamilyProblem.ACCESS -> R.string.family_access
                FamilyProblem.CONFIG -> R.string.family_config
                FamilyProblem.CONFLICT -> R.string.family_conflict
                FamilyProblem.EXPIRED -> R.string.family_expired
                FamilyProblem.CHANGED -> R.string.family_changed
                FamilyProblem.OLDER -> R.string.family_older
                FamilyProblem.ARCHIVE -> R.string.family_archive
                FamilyProblem.LIMIT -> R.string.family_limit
                else -> R.string.family_retry_error
            }),color = MaterialTheme.colorScheme.error,modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (problem == FamilyProblem.EMAIL) OutlinedButton(onClick = vm::sendFamilyVerification,enabled = enabled) { Text(stringResource(R.string.family_send_email)) }
        }
        if (state.emailSent) Text(stringResource(R.string.family_email_sent))
        state.records.forEach { record ->
            val wardrobe = content.wardrobes.find { it.id == record.wardrobeId }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(wardrobe?.displayName() ?: stringResource(R.string.family_missing_local),style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (record.phase == FamilyMigrationPhase.VERIFIED) R.string.family_complete else R.string.family_pending))
                if (record.phase != FamilyMigrationPhase.PREPARED && cloud.localRevision != record.sourceRevision)
                    Text(stringResource(R.string.family_local_changes),style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.transferFamily(record.wardrobeId) },enabled = enabled,modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.family_resume)) }
                if (record.phase != FamilyMigrationPhase.VERIFIED && wardrobe != null) TextButton(onClick = { selected = record.wardrobeId; restart = true },enabled = enabled) {
                    Text(stringResource(R.string.family_restart))
                }
            } }
        }
    }
    if (choosing) VisualPickerDialog(R.string.backup_choose_wardrobe,content.wardrobes.map { w ->
        VisualOption(w.id,w.displayName(),stringResource(R.string.backup_scope_counts,content.garments.count { it.wardrobeId == w.id },content.outfits.count { it.wardrobeId == w.id }))
    },vm,dismiss = { choosing = false },enabled = enabled) { id,_ -> choosing = false; selected = id; restart = false }
    if (selected.isNotEmpty()) AlertDialog(onDismissRequest = { selected = "" },
        title = { Text(content.wardrobes.find { it.id == selected }?.displayName().orEmpty()) },
        text = { Text(stringResource(if (restart) R.string.family_restart_hint else R.string.family_confirm)) },
        confirmButton = { TextButton(onClick = { val id = selected; selected = ""; vm.transferFamily(id,restart) },enabled = enabled) { Text(stringResource(if(restart) R.string.family_restart else R.string.family_start)) } },
        dismissButton = { TextButton(onClick = { selected = "" }) { Text(stringResource(R.string.cancel)) } })
}
