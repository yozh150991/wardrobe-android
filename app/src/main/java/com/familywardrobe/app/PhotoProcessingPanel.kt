package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PhotoProcessingPanel(photoId: String, vm: WardrobeViewModel, onPhoto: (String) -> Unit,
    onSuggestions: (String?, String?) -> Unit, previous: PhotoAnalysis? = null,
    missingReport: Boolean = false, onManual: (() -> Unit)? = null) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val work by vm.photoWork.collectAsStateWithLifecycle()
    val latest by vm.photoAnalysis.collectAsStateWithLifecycle()
    val result = latest?.takeIf { it.sourceId == photoId } ?: previous?.takeIf { it.sourceId == photoId }
    var privacy by remember { mutableStateOf(false) }
    if (photoId.isNotBlank()) Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.photo_resolution_hint, vm.photos.finalResolution), style = MaterialTheme.typography.bodySmall)
        if (work.running && work.source == photoId) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.photo_working), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::cancelPhotoWork) { Text(stringResource(R.string.photo_keep_current)) }
        } else {
            if (result == null && missingReport) Text(stringResource(R.string.photo_report_missing), style = MaterialTheme.typography.bodySmall)
            if (result == null) TextButton(onClick = { vm.analyzePhoto(photoId) }, enabled = !busy) { Text(stringResource(R.string.photo_analyze)) }
            if (work.original.isNotBlank() && work.source == photoId) {
                Text(stringResource(R.string.photo_automatic_ready), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = vm::keepPhotoBackground, enabled = !busy) { Text(stringResource(R.string.photo_restore_background)) }
            }
            if (result != null) PhotoDiagnosticSummary(result)
            if (result?.cutoutStatus == CutoutStatus.READY) {
                Text(stringResource(R.string.photo_backdrop_hint), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { vm.analyzePhoto(photoId) }, enabled = !busy) { Text(stringResource(R.string.photo_clean_balanced)) }
            }
            TextButton(onClick = { vm.cleanPhotoStronger(photoId) }, enabled = !busy) { Text(stringResource(R.string.photo_clean_stronger)) }
            Text(stringResource(R.string.photo_clean_stronger_hint), style = MaterialTheme.typography.bodySmall)
            if (result != null && (result.cutoutStatus != CutoutStatus.READY || result.labelFailed || result.category == null)) {
                /* Recovery is optional: a readable photo can be saved with manual details. */
                TextButton(onClick = { if (result.cutoutStatus == CutoutStatus.MODEL_NEEDED) vm.preparePhotoModel(photoId) else vm.analyzePhoto(photoId) }, enabled = !busy) {
                    Text(stringResource(if (result.cutoutStatus == CutoutStatus.MODEL_NEEDED) R.string.photo_prepare_model else R.string.retry))
                }
            }
            if (onManual != null) TextButton(onClick = { vm.cancelPhotoWork(); onManual() }, enabled = !busy) {
                Text(stringResource(R.string.photo_manual_details))
            }
            if (result != null && (result.category != null || result.color != null)) {
                Text(stringResource(R.string.photo_suggestions, result.category?.let { stringResource(label(it)) } ?: stringResource(R.string.photo_not_recognized),
                    result.color?.let { stringResource(label(it)) } ?: stringResource(R.string.photo_not_recognized)), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onSuggestions(result.category, result.color) }, enabled = !busy) { Text(stringResource(R.string.photo_apply_suggestions)) }
            }
        }
        TextButton(onClick = { privacy = true }) { Text(stringResource(R.string.photo_about_processing)) }
    }
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { Text(stringResource(R.string.photo_about_processing)) },
        text = { Text(stringResource(R.string.photo_privacy)) }, confirmButton = { TextButton(onClick = { privacy = false }) { Text(stringResource(R.string.close)) } })
}

/** Same retained explanation in the queue and its review screen. */
@Composable
fun PhotoDiagnosticSummary(result: PhotoAnalysis) {
    if (result.failure != PhotoFailureKind.NONE) {
        Text(stringResource(photoFailureMessage(result.failure)), style = MaterialTheme.typography.bodySmall)
    } else if (result.cutoutStatus != CutoutStatus.READY) {
        Text(stringResource(when (result.cutoutStatus) {
            CutoutStatus.MODEL_NEEDED -> R.string.photo_model_needed
            CutoutStatus.PLAY_SERVICES_NEEDED -> R.string.photo_play_services_needed
            CutoutStatus.NOT_FOUND -> R.string.photo_cutout_not_found
            CutoutStatus.TIMED_OUT -> R.string.photo_error_timeout
            else -> R.string.photo_cutout_failed
        }), style = MaterialTheme.typography.bodySmall)
    }
    if (result.failure == PhotoFailureKind.NONE && (result.labelFailed || result.category == null)) {
        Text(stringResource(if (result.labelFailed) R.string.photo_labels_failed else R.string.photo_category_unknown),
            style = MaterialTheme.typography.bodySmall)
    }
}
