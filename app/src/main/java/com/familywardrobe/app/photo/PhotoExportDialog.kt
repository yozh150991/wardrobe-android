package com.familywardrobe.app.photo

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.familywardrobe.app.R
import com.familywardrobe.app.rules.PhotoExportRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * PHOTO-17 (0.14.46): share one saved photo as a file — PNG (a cut-out keeps its transparency) or JPEG on a real white
 * or cream background, in the saved size or smaller. Nothing in the wardrobe changes; see [PhotoStore.export].
 */
@Composable
internal fun PhotoExportDialog(photoId: String, title: String, photos: PhotoStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by produceState<PhotoExportInfo?>(null, photoId, photos) { value = photos.exportInfo(photoId) }
    var format by rememberSaveable(photoId) { mutableStateOf(PhotoExportRules.PNG) }
    var size by rememberSaveable(photoId) { mutableIntStateOf(0) }
    var preparing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }
    val chooserTitle = stringResource(R.string.photo_export_open)
    AlertDialog(onDismissRequest = { if (!preparing) onDismiss() }, title = { Text(chooserTitle) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val ready = info
            if (ready == null) { CircularProgressIndicator(); return@Column }
            Text(stringResource(R.string.photo_export_format), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                PhotoExportRules.FORMATS.forEach { code ->
                    ExportChoice(stringResource(formatLabel(code)), format == code, !preparing) { format = code }
                }
            }
            if (format == PhotoExportRules.PNG && !ready.transparent)
                Text(stringResource(R.string.photo_export_no_alpha), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.photo_export_size), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                PhotoExportRules.offered(ready.width, ready.height).forEach { option ->
                    val label = if (option == 0) stringResource(R.string.photo_export_original, ready.width, ready.height)
                        else PhotoExportRules.size(ready.width, ready.height, option).let { stringResource(R.string.photo_export_pixels, it[0], it[1]) }
                    ExportChoice(label, size == option, !preparing) { size = option }
                }
            }
            Text(stringResource(R.string.photo_export_note), style = MaterialTheme.typography.bodySmall)
            if (preparing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp)); Text(stringResource(R.string.photo_export_preparing))
            }
            problem?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(enabled = info != null && !preparing, onClick = {
            val chosen = format; val chosenSize = if (size in PhotoExportRules.SIZES) size else 0
            preparing = true; problem = null
            scope.launch {
                try {
                    val uri = photos.export(photoId, chosen, chosenSize, title)
                    val send = Intent(Intent.ACTION_SEND).setType(PhotoExportRules.mime(chosen)).putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    send.clipData = ClipData.newRawUri(title, uri)
                    try { context.startActivity(Intent.createChooser(send, chooserTitle)); onDismiss() }
                    catch (_: ActivityNotFoundException) { problem = R.string.photo_export_no_app }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { problem = R.string.photo_export_failed }
                finally { preparing = false }
            }
        }) { Text(stringResource(R.string.photo_export_share)) }
    }, dismissButton = { TextButton(onClick = onDismiss, enabled = !preparing) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ExportChoice(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

private fun formatLabel(code: String) = when (code) {
    PhotoExportRules.WHITE -> R.string.photo_export_white
    PhotoExportRules.CREAM -> R.string.photo_export_cream
    else -> R.string.photo_export_png
}
