package com.familywardrobe.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** A photo tap is distinct from the containing card's details/selection action. */
@Composable
fun ZoomableItemPhoto(photoId: String, vm: WardrobeViewModel, modifier: Modifier,
    title: String, enabled: Boolean = true, garmentColour: String? = null) {
    ZoomableItemPhoto(photoId, vm.photos, modifier, title, enabled, garmentColour)
}

@Composable
internal fun ZoomableItemPhoto(photoId: String, photos: PhotoStore, modifier: Modifier,
    title: String, enabled: Boolean = true, garmentColour: String? = null) {
    var opened by remember(photoId, photos) { mutableStateOf(false) }
    val canOpen = enabled && photoId.isNotBlank()
    val openLabel = stringResource(R.string.photo_view_open, title)
    Box(modifier.then(if (canOpen) Modifier.clickable(role = Role.Button, onClickLabel = openLabel) { opened = true } else Modifier)) {
        key(photos, photoId) {
            ItemPhoto(photoId, photos, Modifier.fillMaxSize(), description = title, fit = true, garmentColour = garmentColour)
        }
        if (canOpen) Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp),
            shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = .92f)) {
            Icon(Icons.Outlined.ZoomIn, contentDescription = null, modifier = Modifier.padding(6.dp).size(24.dp))
        }
    }
    if (opened && canOpen) PhotoViewer(photoId, title, photos, onDismiss = { opened = false }, garmentColour = garmentColour)
}

@Composable
private fun PhotoViewer(photoId: String, title: String, photos: PhotoStore, onDismiss: () -> Unit, garmentColour: String?) {
    var retry by remember(photoId, photos) { mutableIntStateOf(0) }
    var loading by remember(photoId, photos) { mutableStateOf(true) }
    val bitmap by produceState<Bitmap?>(null, photoId, photos, retry) {
        loading = true
        value = photos.bitmap(photoId, detailed = true)
        loading = false
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = true, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.close))
                    }
                }
                val ready = bitmap
                if (ready == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator()
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.photo_missing))
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                    }
                } else key(photoId, photos, ready) {
                    ZoomedPhoto(ready, title, Modifier.weight(1f).fillMaxWidth(), garmentColour)
                }
            }
        }
    }
}

/** Draws only the saved bitmap. No copies, original-photo retention or repository writes. */
@Composable
private fun ZoomedPhoto(bitmap: Bitmap, title: String, modifier: Modifier, garmentColour: String?) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val backdrop = rememberPhotoBackdrop(bitmap, garmentColour)
    val zoomIn = stringResource(R.string.mask_zoom_in)
    val zoomOut = stringResource(R.string.mask_zoom_out)
    val fit = stringResource(R.string.mask_fit)
    val moveLeft = stringResource(R.string.photo_view_left)
    val moveRight = stringResource(R.string.photo_view_right)
    val moveUp = stringResource(R.string.photo_view_up)
    val moveDown = stringResource(R.string.photo_view_down)
    val percent = stringResource(R.string.mask_zoom, (zoom * 100).roundToInt())
    fun transform(next: Float, anchor: Offset = Offset(viewportSize.width / 2f, viewportSize.height / 2f), drag: Offset = Offset.Zero) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return
        val state = PhotoZoomRules.transform(bitmap.width, bitmap.height, viewportSize.width.toFloat(), viewportSize.height.toFloat(),
            zoom, panX, panY, next, anchor.x, anchor.y, drag.x, drag.y)
        zoom = state[0]; panX = state[1]; panY = state[2]
    }
    Column(modifier) {
        Canvas(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(backdrop)
            .onSizeChanged {
                if (viewportSize != it) { viewportSize = it; zoom = 1f; panX = 0f; panY = 0f }
            }
            .semantics {
                contentDescription = title
                stateDescription = percent
                customActions = listOf(
                    CustomAccessibilityAction(zoomIn) { transform(zoom * 1.5f); true },
                    CustomAccessibilityAction(zoomOut) { transform(zoom / 1.5f); true },
                    CustomAccessibilityAction(fit) { transform(1f); true }
                ) + if (zoom > 1f) listOf(
                    CustomAccessibilityAction(moveLeft) { transform(zoom, drag = Offset(-viewportSize.width / 4f, 0f)); true },
                    CustomAccessibilityAction(moveRight) { transform(zoom, drag = Offset(viewportSize.width / 4f, 0f)); true },
                    CustomAccessibilityAction(moveUp) { transform(zoom, drag = Offset(0f, -viewportSize.height / 4f)); true },
                    CustomAccessibilityAction(moveDown) { transform(zoom, drag = Offset(0f, viewportSize.height / 4f)); true }
                ) else emptyList()
            }
            .pointerInput(bitmap) {
                detectTapGestures(onDoubleTap = { point ->
                    transform(if (zoom > 1f) 1f else PhotoZoomRules.DOUBLE_TAP_ZOOM, point)
                })
            }
            .pointerInput(bitmap) {
                detectTransformGestures { centroid, pan, factor, _ -> transform(zoom * factor, centroid, pan) }
            }) {
            if (viewportSize.width > 0 && viewportSize.height > 0) {
                val frame = PhotoViewportRules.viewport(bitmap.width, bitmap.height, this.size.width, this.size.height, zoom, panX, panY)
                drawImage(image, dstOffset = IntOffset(frame[0].roundToInt(), frame[1].roundToInt()),
                    dstSize = IntSize(frame[2].roundToInt().coerceAtLeast(1), frame[3].roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.High)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { transform(zoom / 1.5f) }, enabled = zoom > PhotoZoomRules.MIN_ZOOM,
                modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.ZoomOut, zoomOut) }
            Text(percent, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { transform(zoom * 1.5f) }, enabled = zoom < PhotoZoomRules.MAX_ZOOM,
                modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.ZoomIn, zoomIn) }
            IconButton(onClick = { transform(1f) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.FitScreen, fit) }
        }
        Text(stringResource(R.string.photo_view_hint), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall)
    }
}
