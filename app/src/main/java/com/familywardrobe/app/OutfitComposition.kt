package com.familywardrobe.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Movable, resizable composition with an accessible reading-order alternative for large text. */
@Composable
fun OutfitComposition(pieces: List<LookPiece>, vm: WardrobeViewModel, selectedKey: String = "",
    locks: Set<String> = emptySet(), enabled: Boolean = true, layout: String = "", background: String = "paper",
    editable: Boolean = false, onLayout: (String) -> Unit = {}, onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {}, onSelect: (LookPiece) -> Unit) {
    val largeText = LocalConfiguration.current.fontScale >= 1.4f
    val placements = OutfitCanvasState.placements(pieces, layout).associateBy { it.key }
    val latestLayout by rememberUpdatedState(layout)
    val updateLayout by rememberUpdatedState(onLayout)
    val selectPiece by rememberUpdatedState(onSelect)
    val gestureStart by rememberUpdatedState(onGestureStart)
    val gestureEnd by rememberUpdatedState(onGestureEnd)
    val boardColor = when (background) {
        "sand" -> Color(0xffefe1ca); "sage" -> Color(0xffdce8d9); "rose" -> Color(0xfff1dfe3); "slate" -> Color(0xff465365)
        else -> Color(0xfffaf8f4)
    }
    if (largeText) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            pieces.forEach { p ->
                val ownership = stringResource(ownershipLabel(p))
                val lockState = stringResource(if (p.key in locks) R.string.look_locked else R.string.look_unlocked)
                OutlinedCard(onClick = { onSelect(p) }, enabled = enabled,
                    border = androidx.compose.foundation.BorderStroke(if (p.key == selectedKey) 2.dp else 1.dp,
                        if (p.key == selectedKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "${p.name}, $ownership"; stateDescription = lockState; selected = p.key == selectedKey
                    }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ItemPhoto(p.photoId, vm, Modifier.size(96.dp), description = p.name, fit = true)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.name)
                            Text(stringResource(ownershipLabel(p)), style = MaterialTheme.typography.bodySmall)
                            if (!p.ready) Text(stringResource(unavailableReason(p)), color = MaterialTheme.colorScheme.error)
                            if (p.key in locks) Text(stringResource(R.string.look_locked))
                        }
                    }
                }
            }
        }
    } else BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.95f).clip(MaterialTheme.shapes.large)
        .background(boardColor)) {
        pieces.forEach { p ->
            val placement = requireNotNull(placements[p.key])
            val width = CanvasRules.width(p.category, placement.scale)
            val height = CanvasRules.height(p.category, placement.scale)
            val ownership = stringResource(ownershipLabel(p))
            val lockState = stringResource(if (p.key in locks) R.string.look_locked else R.string.look_unlocked)
            val density = androidx.compose.ui.platform.LocalDensity.current
            val boardWidthPx = with(density) { maxWidth.toPx() }
            val boardHeightPx = with(density) { maxHeight.toPx() }
            Box(Modifier.offset(x = maxWidth * (placement.x - width / 2), y = maxHeight * (placement.y - height / 2))
                .size(width = maxOf(48.dp, maxWidth * width), height = maxOf(48.dp, maxHeight * height)).zIndex(placement.z.toFloat())
                .pointerInput(p.key, editable, enabled, boardWidthPx, boardHeightPx, pieces.map { it.key }) {
                    if (editable && enabled) awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var started = false; var totalPan = Offset.Zero; var totalZoom = 1f
                        try {
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.isConsumed }) break
                                val pan = event.calculatePan(); val zoom = event.calculateZoom()
                                totalPan += pan; totalZoom *= zoom
                                if (!started && (totalPan.getDistance() > viewConfiguration.touchSlop ||
                                    kotlin.math.abs(1f - totalZoom) * minOf(boardWidthPx, boardHeightPx) > viewConfiguration.touchSlop)) {
                                    gestureStart(); started = true
                                }
                                if (started) {
                                    selectPiece(p)
                                    updateLayout(OutfitCanvasState.change(pieces, latestLayout, p.key, pan.x / boardWidthPx, pan.y / boardHeightPx, zoom))
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        } finally { if (started) gestureEnd() }
                    }
                }
                .clip(MaterialTheme.shapes.medium)
                .border(if (p.key == selectedKey) 2.dp else 0.dp, if (p.key == selectedKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .clickable(enabled = enabled, role = Role.Button) { onSelect(p) }
                .semantics { contentDescription = "${p.name}, $ownership"; stateDescription = lockState; selected = p.key == selectedKey }) {
                ItemPhoto(p.photoId, vm, Modifier.fillMaxSize().padding(6.dp), fit = true, backgroundColor = Color.Transparent)
                Surface(Modifier.align(Alignment.BottomCenter), shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                    Text(stringResource(label(p.category)), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
                if (!p.ready) Surface(Modifier.align(Alignment.TopStart).padding(4.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(stringResource(unavailableReason(p)), Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
                if (p.key in locks) Surface(Modifier.align(Alignment.TopEnd).padding(6.dp), shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Outlined.Lock, null, Modifier.padding(4.dp).size(16.dp))
                }
            }
        }
    }
}
