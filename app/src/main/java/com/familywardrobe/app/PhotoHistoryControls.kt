package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Small command strings only; no bitmaps or file copies in SavedInstanceState. */
internal fun encodePhotoHistory(history: PhotoEditHistory): String =
    JSONObject().put("steps", JSONArray(history.steps.toList())).put("cursor", history.cursor).toString()

internal fun decodePhotoHistory(saved: String): PhotoEditHistory = runCatching {
        val json = JSONObject(saved); val steps = json.getJSONArray("steps")
        PhotoEditHistory(Array(steps.length()) { steps.getString(it) }, json.getInt("cursor"))
    }.getOrElse { PhotoEditHistory() }

val PhotoHistorySaver = Saver<PhotoEditHistory, String>(save = { encodePhotoHistory(it) }, restore = ::decodePhotoHistory)

@Composable
fun PhotoHistoryControls(history: PhotoEditHistory, enabled: Boolean, onChange: (PhotoEditHistory) -> Unit) {
    val undo = stringResource(R.string.photo_history_undo_description)
    val redo = stringResource(R.string.photo_history_redo_description)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onChange(history.undo()) }, enabled = enabled && history.canUndo(),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = undo }) {
            Icon(Icons.AutoMirrored.Outlined.Undo, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.photo_history_undo))
        }
        OutlinedButton(onClick = { onChange(history.redo()) }, enabled = enabled && history.canRedo(),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = redo }) {
            Icon(Icons.AutoMirrored.Outlined.Redo, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.photo_history_redo))
        }
    }
}
