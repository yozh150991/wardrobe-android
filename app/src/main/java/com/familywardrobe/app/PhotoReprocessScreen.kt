package com.familywardrobe.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PhotoReprocessScreen(snapshot: Snapshot, vm: WardrobeViewModel) {
    val controller = vm.photoReprocessor
    val state by controller.state.collectAsStateWithLifecycle()
    val running by controller.running.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val entry = state.entries.firstOrNull()
    var cancelDialog by rememberSaveable { mutableStateOf(false) }
    var before by rememberSaveable(state.token, entry?.itemId, entry?.candidateId) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.photo_reprocess_hint))
        Text(stringResource(R.string.photo_resolution_hint, vm.photos.finalResolution), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.photo_reprocess_progress, state.saved, state.skipped, state.entries.size),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (state.unreadable) Text(stringResource(R.string.photo_reprocess_unreadable))
        else if (entry == null) Text(stringResource(R.string.photo_reprocess_finished))
        else key(state.token, entry.itemId) {
            val item = snapshot.garments.find { it.id == entry.itemId && it.wardrobeId == entry.wardrobeId }
            val name = item?.name ?: stringResource(R.string.photo_reprocess_missing)
            val wardrobe = snapshot.wardrobes.find { it.id == entry.wardrobeId }?.name.orEmpty()
            Text(name, style = MaterialTheme.typography.titleLarge)
            if (wardrobe.isNotEmpty()) Text(wardrobe, style = MaterialTheme.typography.bodySmall)
            val changed = item == null || item.photoId != entry.sourceId
            val ready = entry.status == "review" && entry.candidateId.isNotBlank()
            val shown = if (ready && !before) entry.candidateId else entry.sourceId
            Text(stringResource(if (ready && !before) R.string.photo_reprocess_after else R.string.photo_reprocess_before))
            ZoomableItemPhoto(shown, vm, Modifier.fillMaxWidth().height(280.dp), name, garmentColour = item?.color)
            PhotoFileInformation(shown, vm)
            if (ready) OutlinedButton(onClick = { before = !before }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (before) R.string.photo_reprocess_show_after else R.string.photo_reprocess_show_before))
            }
            if (running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.photo_working))
                TextButton(onClick = controller::pause, enabled = !busy) { Text(stringResource(R.string.photo_reprocess_pause)) }
            } else if (changed || entry.status == "conflict") Text(stringResource(R.string.photo_reprocess_conflict))
            else {
                if (entry.status == "failed") PhotoDiagnosticSummary(PhotoAnalysis(entry.sourceId,
                    category = item?.category, cutoutStatus = entry.cutout, failure = entry.failure))
                if (ready) Button(onClick = { vm.acceptReprocessedPhoto(state.token, entry) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.photo_reprocess_accept))
                }
                OutlinedButton(onClick = { controller.prepare(installModel = entry.cutout == CutoutStatus.MODEL_NEEDED) },
                    enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (entry.cutout == CutoutStatus.MODEL_NEEDED && entry.status == "failed") R.string.photo_prepare_model
                        else if (entry.status == "pending") R.string.photo_reprocess_resume else R.string.photo_clean_balanced))
                }
                TextButton(onClick = { controller.prepare(stronger = true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.photo_clean_stronger))
                }
            }
            OutlinedButton(onClick = { vm.skipReprocessedPhoto(state.token, entry) }, enabled = !busy && !running, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.photo_reprocess_skip))
            }
        }
        if (entry != null || state.unreadable) TextButton(onClick = { cancelDialog = true }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.photo_reprocess_cancel)) }
    }
    if (cancelDialog) AlertDialog(onDismissRequest = { cancelDialog = false },
        title = { Text(stringResource(R.string.photo_reprocess_cancel)) },
        text = { Text(stringResource(R.string.photo_reprocess_cancel_hint)) },
        confirmButton = { TextButton(onClick = { cancelDialog = false; vm.cancelReprocessing(state.token) }, enabled = !busy) { Text(stringResource(R.string.photo_reprocess_cancel)) } },
        dismissButton = { TextButton(onClick = { cancelDialog = false }) { Text(stringResource(R.string.back)) } })
}

@Composable
private fun PhotoFileInformation(id: String, vm: WardrobeViewModel) {
    val information by produceState<Triple<Int, Int, Long>?>(null, id, vm.photos) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = vm.photos.currentFile(id)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                Triple(bounds.outWidth, bounds.outHeight, (file.length() + 1023) / 1024)
            }.getOrNull()
        }
    }
    information?.let { Text(stringResource(R.string.photo_file_information, it.first, it.second, it.third), style = MaterialTheme.typography.bodySmall) }
}
