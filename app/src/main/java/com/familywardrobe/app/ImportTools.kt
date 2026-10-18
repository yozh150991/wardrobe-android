package com.familywardrobe.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ImportTools(rows: List<ImportDraft>, vm: WardrobeViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var capturing by rememberSaveable { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableIntStateOf(0) }
    var template by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var category by rememberSaveable { mutableStateOf("") }
    var season by rememberSaveable { mutableStateOf("") }
    var style by rememberSaveable { mutableStateOf("") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val captured = pending
        pending = ""
        if (success && captured.isNotBlank()) vm.captureImport(Uri.parse(captured)) { count++ }
    }
    fun capture() {
        runCatching { vm.photos.newCapture() }.onSuccess { pending = it.toString(); camera.launch(it) }
            .onFailure { vm.reportFailure(OperationKind.PHOTO, it as? Exception ?: IllegalStateException()) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { capturing = true; count = 0; capture() }, enabled = !busy && rows.size < 200,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.import_camera_series)) }
        OutlinedButton(onClick = { selected = ArrayList(rows.map { it.id }); template = true }, enabled = !busy && rows.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.import_shared_fields)) }
        if (capturing) OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_camera_count, count))
                OutlinedButton(onClick = ::capture, enabled = !busy && pending.isBlank() && count < 20 && rows.size < 200, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_camera_next)) }
                TextButton(onClick = { capturing = false }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_camera_review)) }
            }
        }
    }
    if (template) AlertDialog(onDismissRequest = { template = false }, title = { Text(stringResource(R.string.import_shared_fields)) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.import_template_hint))
            Choice(category, Matching.categories, { category = it }, R.string.category, emptyLabel = R.string.import_keep_field, enabled = !busy)
            Choice(season, Matching.seasons, { season = it }, R.string.season, emptyLabel = R.string.import_keep_field, enabled = !busy)
            Choice(style, Matching.styles, { style = it }, R.string.style, emptyLabel = R.string.import_keep_field, enabled = !busy)
            TextButton(onClick = { category = ""; season = ""; style = "" }, enabled = !busy) { Text(stringResource(R.string.import_reset_fields)) }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 180.dp)) {
                items(rows.size, key = { rows[it].id }) { index -> val row = rows[index]
                    SelectionRow(row.name.ifBlank { stringResource(R.string.batch_item_name, row.position + 1) }, row.id in selected, !busy) { checked ->
                        selected = ArrayList(if (checked) selected + row.id else selected - row.id)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = {
        val ids = selected.filter { id -> rows.any { it.id == id } }.toSet()
        if (ids.isNotEmpty()) vm.applyImportTemplate(ids, category, season, style)
        template = false
    }, enabled = !busy && selected.any { id -> rows.any { it.id == id } } && (category.isNotBlank() || season.isNotBlank() || style.isNotBlank())) { Text(stringResource(R.string.apply)) } },
        dismissButton = { TextButton(onClick = { template = false }) { Text(stringResource(R.string.cancel)) } })
}
