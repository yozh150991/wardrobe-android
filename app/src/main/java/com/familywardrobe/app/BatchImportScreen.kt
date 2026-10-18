package com.familywardrobe.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun BatchImportScreen(rows: List<ImportDraft>, vm: WardrobeViewModel, review: (ImportDraft) -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val paused by vm.batchImporter.paused.collectAsStateWithLifecycle()
    val running by vm.batchImporter.running.collectAsStateWithLifecycle()
    val reports by vm.batchImporter.diagnostics.reports.collectAsStateWithLifecycle()
    var skip by remember { mutableStateOf<ImportDraft?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.enqueueImports(uris)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.batch_hint))
                ImportTools(rows, vm)
                Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !busy && rows.size < 200, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.batch_pick)) }
                Text(stringResource(R.string.batch_progress, rows.count { it.status == "review" }, rows.size),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.review_queue_counts, rows.count { it.status == "failed" }, rows.count { it.status in setOf("queued", "working") }), style = MaterialTheme.typography.bodySmall)
                rows.firstOrNull { it.status == "review" }?.let { first -> Button(onClick = { review(first) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.review_start)) } }
                if (rows.any { it.status in setOf("queued", "working") }) {
                    if (running && !paused) LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { if (paused) vm.batchImporter.resume() else vm.batchImporter.pause() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(if (paused) R.string.batch_resume else R.string.batch_pause)) }
                }
                if (rows.isEmpty()) Text(stringResource(R.string.batch_empty))
            }
        }
        items(rows, key = { it.id }) { row ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ItemPhoto(row.photoId, vm, Modifier.size(80.dp), fit = true)
                        Column(Modifier.weight(1f)) {
                            Text(row.name.ifBlank { stringResource(R.string.batch_item_name, row.position + 1) })
                            Text(stringResource(when (row.status) { "working" -> R.string.batch_working; "review" -> R.string.batch_ready;
                                "failed" -> R.string.batch_failed; else -> R.string.batch_queued }), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (row.status in setOf("review", "failed")) reports[row.id]?.takeIf { it.sourceId == row.photoId }?.let { PhotoDiagnosticSummary(it) }
                    if (row.status in setOf("review", "failed")) Button(onClick = { review(row) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(if (row.status == "failed") R.string.photo_replace_review else R.string.batch_review)) }
                    if (row.status == "failed") OutlinedButton(onClick = { vm.retryImport(row.id) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
                    if (row.status != "working") TextButton(onClick = { skip = row }, enabled = !busy) { Text(stringResource(R.string.batch_skip)) }
                }
            }
        }
    }
    skip?.let { row -> AlertDialog(onDismissRequest = { skip = null }, title = { Text(stringResource(R.string.batch_skip)) },
        text = { Text(stringResource(R.string.batch_skip_hint)) }, confirmButton = { TextButton(onClick = { vm.skipImport(row.id); skip = null }) { Text(stringResource(R.string.batch_skip)) } },
        dismissButton = { TextButton(onClick = { skip = null }) { Text(stringResource(R.string.cancel)) } }) }
}
