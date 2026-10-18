package com.familywardrobe.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

/** Portable copy; this action does not create or transfer family membership. */
@Composable
fun SingleWardrobeBackupControl(vm: WardrobeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var choosing by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf(false) }
    var wardrobeId by rememberSaveable { mutableStateOf("") }
    var requestSession by rememberSaveable { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val accepted = pending && requestSession == vm.sessionToken && wardrobeId.isNotBlank()
        pending = false
        if (uri != null && accepted) vm.export(uri, wardrobeId)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { choosing = true }, enabled = !busy && !pending && state.wardrobes.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_one_wardrobe)) }
        Text(stringResource(R.string.backup_one_wardrobe_hint), style = MaterialTheme.typography.bodySmall)
    }
    if (choosing) {
        val options = state.wardrobes.map { wardrobe ->
            VisualOption(wardrobe.id, wardrobe.displayName(), stringResource(R.string.backup_scope_counts,
                state.garments.count { it.wardrobeId == wardrobe.id }, state.outfits.count { it.wardrobeId == wardrobe.id }))
        }
        VisualPickerDialog(R.string.backup_choose_wardrobe, options, vm, dismiss = { choosing = false }, enabled = !busy && !pending) { id, _ ->
            if (!busy && !pending) {
                wardrobeId = id; requestSession = vm.sessionToken; pending = true; choosing = false
                export.launch("wardrobe-${id.take(8)}-${LocalDate.now()}.zip")
            }
        }
    }
}
