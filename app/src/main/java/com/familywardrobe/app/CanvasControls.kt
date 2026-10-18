@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun CanvasControls(pieces: List<LookPiece>, selected: String, layout: String, background: String,
    enabled: Boolean, onLayout: (String) -> Unit, onBackground: (String) -> Unit) {
    var confirmingReset by remember { mutableStateOf(false) }
    Choice(background, OutfitCanvasState.backgrounds, onBackground, R.string.canvas_background, enabled = enabled,
        optionLabels = mapOf("paper" to R.string.canvas_paper, "sand" to R.string.canvas_sand, "sage" to R.string.canvas_sage,
            "rose" to R.string.canvas_rose, "slate" to R.string.canvas_slate))
    Text(stringResource(R.string.canvas_gesture_hint), style = MaterialTheme.typography.bodySmall)
    val piece = pieces.find { it.key == selected }
    if (piece != null) {
        Text(stringResource(R.string.canvas_selected, piece.name), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onLayout(OutfitCanvasState.change(pieces, layout, selected, dx = -.04f)) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.canvas_left)) }
            OutlinedButton(onClick = { onLayout(OutfitCanvasState.change(pieces, layout, selected, dx = .04f)) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.canvas_right)) }
            OutlinedButton(onClick = { onLayout(OutfitCanvasState.change(pieces, layout, selected, dy = -.04f)) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.canvas_up)) }
            OutlinedButton(onClick = { onLayout(OutfitCanvasState.change(pieces, layout, selected, dy = .04f)) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.canvas_down)) }
            OutlinedButton(onClick = { onLayout(OutfitCanvasState.change(pieces, layout, selected, zoom = .9f)) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.canvas_smaller)) }
            OutlinedButton(onClick = { onLayout(OutfitCanvasState.change(pieces, layout, selected, zoom = 1.1f)) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.canvas_larger)) }
            TextButton(onClick = { onLayout(OutfitCanvasState.change(pieces, layout, selected, front = true)) }, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.canvas_front)) }
        }
    }
    TextButton(onClick = { confirmingReset = true }, enabled = enabled && layout.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.canvas_reset))
    }
    if (confirmingReset) AlertDialog(onDismissRequest = { confirmingReset = false }, text = { Text(stringResource(R.string.canvas_reset_confirm)) },
        confirmButton = { TextButton(onClick = { onLayout(""); confirmingReset = false }, enabled = enabled) { Text(stringResource(R.string.canvas_reset)) } },
        dismissButton = { TextButton(onClick = { confirmingReset = false }) { Text(stringResource(R.string.cancel)) } })
}
