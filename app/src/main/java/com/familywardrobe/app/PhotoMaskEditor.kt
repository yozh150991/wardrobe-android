package com.familywardrobe.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

private data class MaskPreview(val frame: PhotoSessionRules.Frame, val stroke: String, val bitmap: Bitmap)
private data class RegionPreview(val frame: PhotoSessionRules.Frame, val point: Offset, val tolerance: Float,
    val selection: PhotoRegionRules.Selection? = null, val mask: Bitmap? = null, val leavesVisible: Boolean = false, val failed: Boolean = false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoMaskEditor(image: PhotoSessionImage?, enabled: Boolean, historyKey: String, history: PhotoEditHistory, segmentCount: Int,
    onStroke: (String) -> Unit, onDrawing: (Boolean) -> Unit, onSelectionPending: (Boolean) -> Unit,
    onOriginal: (() -> Unit)?, onResetAll: () -> Unit) {
    val latestHistory by rememberUpdatedState(history)
    val addStroke by rememberUpdatedState(onStroke)
    val updateDrawing by rememberUpdatedState(onDrawing)
    val updateSelectionPending by rememberUpdatedState(onSelectionPending)
    var pending by remember { mutableStateOf("") }
    var drawing by remember { mutableStateOf(false) }
    var limitReached by remember { mutableStateOf(false) }
    var restore by rememberSaveable { mutableStateOf(false) }
    var selectedDiameter by rememberSaveable { mutableIntStateOf(16) }
    var softness by rememberSaveable { mutableFloatStateOf(0f) }
    var strength by rememberSaveable { mutableFloatStateOf(1f) }
    var loupe by rememberSaveable { mutableStateOf(true) }
    var comparison by rememberSaveable { mutableStateOf(false) }
    var comparisonFraction by rememberSaveable { mutableFloatStateOf(.5f) }
    var backdropMode by rememberSaveable { mutableStateOf("auto") }
    var regionMode by rememberSaveable { mutableStateOf(false) }
    var tolerance by rememberSaveable { mutableFloatStateOf(.1f) }
    var regionPoint by remember(historyKey, image?.frame) { mutableStateOf<Offset?>(null) }
    var regionRetry by remember { mutableIntStateOf(0) }
    var moving by rememberSaveable { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val source = image?.bitmap
    val protection = rememberPhotoProtection(image?.frame)
    val maximumDiameter = source?.let { PhotoBrushRules.maximumDiameter(it.width, it.height) } ?: 1
    val diameter = selectedDiameter.coerceIn(1, maximumDiameter)
    val radius = source?.let { PhotoBrushRules.radius(diameter, it.width, it.height) } ?: .01f
    val controlsEnabled = enabled && !drawing
    val hasRoom = (regionMode || segmentCount < 2048) && history.canAppend("{\"s\":[]}")
    // A new operation may change dimensions; ordinary brush steps keep the user's zoom.
    LaunchedEffect(source?.width, source?.height) { zoom = 1f; panX = 0f; panY = 0f; cursor = null }
    LaunchedEffect(historyKey) { limitReached = false }
    LaunchedEffect(regionPoint) { updateSelectionPending(regionPoint != null) }
    DisposableEffect(Unit) { onDispose { updateSelectionPending(false) } }
    val painted by produceState<MaskPreview?>(null, image, pending) {
        val base = image
        val stroke = pending
        if (base != null && stroke.isEmpty()) value = MaskPreview(base.frame, stroke, base.bitmap)
        else if (base != null) value = withContext(Dispatchers.Default) {
            val frame = base.frame
            val work = coroutineContext
            val pixels = PhotoSessionRules.apply(frame, PhotoSessionRules.Action.stroke(
                maskStrokes(JSONObject(stroke).getJSONArray("s").toString()))) { work.ensureActive() }.pixels
            MaskPreview(frame, stroke, Bitmap.createBitmap(pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888))
        }
    }
    val original by produceState<MaskPreview?>(null, image, comparison) {
        value = if (image != null && comparison) withContext(Dispatchers.Default) {
            MaskPreview(image.frame, "", Bitmap.createBitmap(image.frame.original, image.frame.width, image.frame.height, Bitmap.Config.ARGB_8888))
        } else null
    }
    val edited = painted?.takeIf { it.frame === image?.frame }?.bitmap
    val unpainted = original?.takeIf { it.frame === image?.frame }?.bitmap
    val shown = edited ?: source
    val automaticBackdrop = rememberPhotoBackdrop(source)
    val backdrop = when (backdropMode) {
        "white" -> Color.White
        "cream" -> Color(0xffeee5d5)
        "dark" -> Color(0xff202124)
        "contrast" -> Color(0xffb538ce)
        else -> automaticBackdrop
    }
    val transparencyGrid = backdropMode == "grid"
    val selectedRegion by produceState<RegionPreview?>(null, image, regionPoint, tolerance, regionRetry) {
        val base = image; val point = regionPoint; val threshold = tolerance
        value = if (base == null || point == null) null else try {
            withContext(Dispatchers.Default) {
                val work = coroutineContext; val frame = base.frame
                val selected = PhotoRegionRules.select(frame.pixels, frame.width, frame.height, point.x, point.y, threshold, frame.protection) { work.ensureActive() }
                val leavesVisible = selected.leavesVisiblePixels(frame.pixels) { work.ensureActive() }
                val mask = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ALPHA_8)
                try {
                    mask.copyPixelsFromBuffer(ByteBuffer.wrap(selected.alphaMask(mask.rowBytes) { work.ensureActive() }))
                    work.ensureActive()
                    RegionPreview(frame, point, threshold, selected, mask, leavesVisible)
                } catch (error: Throwable) { mask.recycle(); throw error }
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { RegionPreview(base.frame, point, threshold, failed = true) }
    }
    val regionPreview = selectedRegion?.takeIf { it.frame === image?.frame && it.point == regionPoint && it.tolerance == tolerance }
    val regionCommand = regionPoint?.let { JSONObject().put("region", JSONArray().put(it.x).put(it.y).put(tolerance)).toString() }
    val description = stringResource(R.string.mask_preview)
    val zoomIn = stringResource(R.string.mask_zoom_in); val zoomOut = stringResource(R.string.mask_zoom_out)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.photo_history_unified_hint), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!regionMode, onClick = { regionMode = false; regionPoint = null }, enabled = controlsEnabled && !comparison,
                label = { Text(stringResource(R.string.photo_brush_tool)) })
            FilterChip(regionMode, onClick = { regionMode = true }, enabled = controlsEnabled && !comparison,
                label = { Text(stringResource(R.string.photo_region_tool)) })
        }
        if (comparison) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.photo_compare_before), Modifier.weight(1f))
            Text(stringResource(R.string.photo_compare_after), Modifier.weight(1f))
        }
        Canvas(Modifier.fillMaxWidth().height(330.dp).clipToBounds().onSizeChanged { canvasSize = it }
            .semantics { contentDescription = description }
            .pointerInput(source, restore, radius, softness, strength, enabled, comparison, regionMode, moving, historyKey) {
                val bitmap = source ?: return@pointerInput
                fun frame() = PhotoViewportRules.viewport(bitmap.width, bitmap.height, canvasSize.width.toFloat(), canvasSize.height.toFloat(), zoom, panX, panY)
                fun point(p: Offset): Offset? {
                    if (canvasSize.width <= 0 || canvasSize.height <= 0 || p.x < 0 || p.y < 0 ||
                        p.x > canvasSize.width || p.y > canvasSize.height) return null
                    val xy = PhotoViewportRules.point(p.x, p.y, frame(), false) ?: return null
                    return Offset(xy[0], xy[1])
                }
                if (moving) {
                    if (enabled) detectTransformGestures { _, pan, factor, _ ->
                        zoom = (zoom * factor).coerceIn(1f, 8f)
                        val f = PhotoViewportRules.viewport(bitmap.width, bitmap.height, canvasSize.width.toFloat(), canvasSize.height.toFloat(), zoom, panX + pan.x, panY + pan.y)
                        panX = f[4]; panY = f[5]
                    }
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!enabled || comparison) return@awaitEachGesture
                    val first = point(down.position) ?: return@awaitEachGesture
                    val base = latestHistory
                    if (regionMode) {
                        drawing = true; updateDrawing(true); down.consume()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.id != down.id && (it.pressed || it.previousPressed) }) break
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                                if (point(change.position) == null) break
                                change.consume()
                                if (!change.pressed) { regionPoint = first; break }
                            }
                        } finally { drawing = false; updateDrawing(false) }
                        return@awaitEachGesture
                    }
                    if (segmentCount >= 2048 || !base.canAppend("{\"s\":[]}")) return@awaitEachGesture
                    val segments = JSONArray()
                    fun segment(a: Offset, b: Offset) {
                        if (segmentCount + segments.length() >= 2048) { limitReached = true; return }
                        val next = JSONArray(segments.toString()).put(JSONArray().put(a.x).put(a.y).put(b.x).put(b.y)
                            .put(radius).put(if (restore) 1 else 0).put(1f - softness).put(strength))
                        val action = JSONObject().put("s", next).toString()
                        if (!base.canAppend(action)) { limitReached = true; return }
                        segments.put(next.getJSONArray(next.length() - 1)); pending = action
                    }
                    var last: Offset? = first
                    var completed = false
                    drawing = true; updateDrawing(true); limitReached = false; down.consume()
                    try {
                        cursor = first; segment(first, first)
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.id != down.id && (it.pressed || it.previousPressed) }) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            val next = point(change.position)
                            if (next != null && next != last) segment(last ?: next, next)
                            last = next; cursor = next; change.consume()
                            if (!change.pressed) { completed = true; break }
                        }
                        if (completed && pending.isNotEmpty()) addStroke(pending)
                    } finally {
                        pending = ""; drawing = false; updateDrawing(false); cursor = null
                    }
                }
            }) {
            drawRect(backdrop)
            if (transparencyGrid) drawTransparencyTiles()
            shown?.let { bitmap ->
                val f = PhotoViewportRules.viewport(bitmap.width, bitmap.height, size.width, size.height, zoom, panX, panY)
                val offset = IntOffset(f[0].roundToInt(), f[1].roundToInt())
                val targetSize = IntSize(f[2].roundToInt().coerceAtLeast(1), f[3].roundToInt().coerceAtLeast(1))
                if (comparison) {
                    unpainted?.let { beforeImage ->
                        val split = PhotoInspectionRules.split(f[0], f[2], size.width, comparisonFraction)
                        clipRect(right = split) { drawImage(beforeImage.asImageBitmap(), dstOffset = offset, dstSize = targetSize) }
                        clipRect(left = split) { drawImage(bitmap.asImageBitmap(), dstOffset = offset, dstSize = targetSize) }
                        val top = f[1].coerceIn(0f, size.height); val bottom = (f[1] + f[3]).coerceIn(top, size.height)
                        drawLine(Color.Black, Offset(split, top), Offset(split, bottom), 4.dp.toPx())
                        drawLine(Color.White, Offset(split, top), Offset(split, bottom), 2.dp.toPx())
                    }
                } else {
                    drawImage(bitmap.asImageBitmap(), dstOffset = offset, dstSize = targetSize)
                    regionPreview?.mask?.let { mask ->
                        drawImage(mask.asImageBitmap(), dstOffset = offset, dstSize = targetSize,
                            colorFilter = ColorFilter.tint(Color(0xffff3366).copy(alpha = .5f)))
                    }
                    protection?.let { mask ->
                        drawImage(mask.asImageBitmap(), dstOffset = offset, dstSize = targetSize,
                            colorFilter = ColorFilter.tint(Color.Cyan.copy(alpha = .35f)))
                    }
                }
                if (!moving && !comparison && !regionMode) {
                    val p = cursor ?: Offset(.5f, .5f)
                    val center = Offset(f[0] + p.x * f[2], f[1] + p.y * f[3])
                    val brush = radius * minOf(f[2], f[3])
                    drawCircle(Color.Black, brush, center, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, brush, center, style = Stroke(1.dp.toPx()))
                    if (softness > 0f) drawCircle(Color.White.copy(alpha = .7f), brush * (1f - softness), center, style = Stroke(1.dp.toPx()))
                    if (loupe && drawing && cursor != null) drawPhotoLoupe(bitmap, p, f, radius, backdrop, transparencyGrid)
                }
            }
        }
        if (pending.isNotEmpty() && painted?.stroke != pending) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (comparison && unpainted == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (protection != null) Text(stringResource(R.string.photo_protection_hint), style = MaterialTheme.typography.bodySmall)
        if (regionMode && !comparison) {
            Text(stringResource(R.string.photo_region_hint), style = MaterialTheme.typography.bodySmall)
            val toleranceLabel = stringResource(R.string.photo_region_tolerance, (tolerance * 100).roundToInt())
            Text(toleranceLabel)
            Slider(tolerance, { tolerance = it }, steps = 99, enabled = controlsEnabled, valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = toleranceLabel })
            if (regionPoint != null) {
                when {
                    regionPreview == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    regionPreview.failed -> {
                        Text(stringResource(R.string.photo_region_failed), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { regionRetry++ }, enabled = controlsEnabled) { Text(stringResource(R.string.retry)) }
                    }
                    regionPreview.selection?.count == 0 -> Text(stringResource(R.string.photo_region_empty))
                    !regionPreview.leavesVisible -> Text(stringResource(R.string.photo_region_all), color = MaterialTheme.colorScheme.error)
                    else -> Text(stringResource(R.string.photo_region_selected,
                        regionPreview.selection?.count ?: 0))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        regionCommand?.let { if (latestHistory.canAppend(it)) { addStroke(it); regionPoint = null } }
                    }, enabled = controlsEnabled && regionPreview?.leavesVisible == true && (regionPreview?.selection?.count ?: 0) > 0 &&
                        regionCommand?.let(history::canAppend) == true) { Text(stringResource(R.string.photo_region_erase)) }
                    OutlinedButton(onClick = { regionPoint = null }, enabled = controlsEnabled) { Text(stringResource(R.string.photo_region_clear)) }
                }
                Text(stringResource(R.string.photo_region_pending), style = MaterialTheme.typography.bodySmall)
            }
        }
        SelectionRow(stringResource(R.string.photo_compare), comparison, controlsEnabled && regionPoint == null) { comparison = it }
        if (comparison) {
            val compareLabel = stringResource(R.string.photo_compare_position, (comparisonFraction * 100).roundToInt())
            Text(compareLabel)
            Slider(comparisonFraction, { comparisonFraction = it }, enabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = compareLabel })
            Text(stringResource(R.string.photo_compare_hint), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.photo_inspection_backdrop))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("auto" to R.string.photo_backdrop_auto, "white" to R.string.photo_backdrop_white,
                "cream" to R.string.photo_backdrop_cream, "dark" to R.string.photo_backdrop_dark,
                "contrast" to R.string.photo_backdrop_contrast, "grid" to R.string.photo_backdrop_grid).forEach { (code, label) ->
                FilterChip(backdropMode == code, onClick = { backdropMode = code }, enabled = controlsEnabled,
                    label = { Text(stringResource(label)) })
            }
        }
        Text(stringResource(R.string.photo_inspection_hint), style = MaterialTheme.typography.bodySmall)
        if (!regionMode && !comparison) {
        SelectionRow(stringResource(R.string.photo_loupe), loupe, controlsEnabled) { loupe = it }
        Text(stringResource(R.string.photo_loupe_hint), style = MaterialTheme.typography.bodySmall)
        }
        SelectionRow(stringResource(R.string.mask_move_mode), moving, controlsEnabled) { moving = it; cursor = null }
        if (moving || (!comparison && !regionMode))
            Text(stringResource(if (moving) R.string.mask_move_hint else R.string.mask_draw_hint), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { zoom = (zoom / 1.5f).coerceAtLeast(1f) }, enabled = controlsEnabled && zoom > 1f, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = zoomOut }) { Text("−") }
            Text(stringResource(R.string.mask_zoom, (zoom * 100).roundToInt()), Modifier.weight(1f))
            OutlinedButton(onClick = { zoom = (zoom * 1.5f).coerceAtMost(8f) }, enabled = controlsEnabled && zoom < 8f, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = zoomIn }) { Text("+") }
        }
        TextButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }, enabled = controlsEnabled) { Text(stringResource(R.string.mask_fit)) }
        if (!regionMode && !comparison) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !restore, onClick = { restore = false }, enabled = controlsEnabled, label = { Text(stringResource(R.string.mask_erase)) })
            FilterChip(selected = restore, onClick = { restore = true }, enabled = controlsEnabled, label = { Text(stringResource(R.string.mask_restore)) })
        }
        val brushLabel = stringResource(R.string.photo_brush_diameter, diameter)
        val smaller = stringResource(R.string.photo_brush_smaller); val larger = stringResource(R.string.photo_brush_larger)
        Text(brushLabel)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = { selectedDiameter = (diameter - 1).coerceAtLeast(1) }, enabled = controlsEnabled && diameter > 1,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = smaller }) { Text("−") }
            Slider(modifier = Modifier.weight(1f).semantics { contentDescription = brushLabel }, value = PhotoBrushRules.position(diameter, maximumDiameter),
                onValueChange = { selectedDiameter = PhotoBrushRules.diameter(it, maximumDiameter) }, valueRange = 0f..1f, enabled = controlsEnabled && maximumDiameter > 1)
            OutlinedButton(onClick = { selectedDiameter = (diameter + 1).coerceAtMost(maximumDiameter) }, enabled = controlsEnabled && diameter < maximumDiameter,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = larger }) { Text("+") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(1, 4, 16, 64).filter { it <= maximumDiameter } + maximumDiameter).distinct().forEach { size ->
                FilterChip(selected = diameter == size, onClick = { selectedDiameter = size }, enabled = controlsEnabled,
                    label = { Text(stringResource(R.string.photo_brush_preset, size)) })
            }
        }
        val softnessLabel = stringResource(R.string.photo_brush_softness, (softness * 100).roundToInt())
        val strengthLabel = stringResource(R.string.photo_brush_strength, (strength * 100).roundToInt())
        Text(softnessLabel)
        Slider(softness, { softness = it }, enabled = controlsEnabled, valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = softnessLabel })
        Text(strengthLabel)
        Slider(strength, { strength = it }, enabled = controlsEnabled, valueRange = .01f..1f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = strengthLabel })
        Text(stringResource(R.string.photo_brush_soft_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.photo_brush_hint), style = MaterialTheme.typography.bodySmall)
        }
        val canReset = controlsEnabled && history.canAppend("{\"source\":\"00000000-0000-0000-0000-000000000000\"}")
        if (onOriginal != null) TextButton(onClick = onOriginal, enabled = canReset) { Text(stringResource(R.string.mask_start_original)) }
        TextButton(onClick = onResetAll, enabled = canReset && history.canUndo()) { Text(stringResource(R.string.photo_reset_all)) }
        if (!hasRoom || limitReached || regionCommand?.let { !history.canAppend(it) } == true) Text(stringResource(R.string.mask_limit))
    }
}

private fun DrawScope.drawTransparencyTiles() {
    val tile = 16.dp.toPx()
    for (y in 0..(size.height / tile).toInt()) for (x in 0..(size.width / tile).toInt())
        drawRect(if ((x + y) % 2 == 0) Color(0xffeeeeee) else Color(0xffcccccc), Offset(x * tile, y * tile), Size(tile, tile))
}

private fun DrawScope.drawPhotoLoupe(bitmap: Bitmap, point: Offset, frame: FloatArray, radius: Float, backdrop: Color, grid: Boolean) {
    val touch = Offset(frame[0] + point.x * frame[2], frame[1] + point.y * frame[3])
    val lens = PhotoLoupeRules.bounds(size.width, size.height, touch.x, touch.y, 112.dp.toPx(), 36.dp.toPx())
    val center = Offset(lens[0] + lens[2] / 2, lens[1] + lens[2] / 2)
    val path = Path().apply { addOval(Rect(lens[0], lens[1], lens[0] + lens[2], lens[1] + lens[2])) }
    drawCircle(Color.Black.copy(alpha = .55f), lens[2] / 2 + 3.dp.toPx(), center)
    clipPath(path) {
        drawRect(backdrop)
        if (grid) drawTransparencyTiles()
        val width = frame[2] * 3f; val height = frame[3] * 3f
        drawImage(bitmap.asImageBitmap(), dstOffset = IntOffset((center.x - point.x * width).roundToInt(), (center.y - point.y * height).roundToInt()),
            dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)))
        val brush = radius * minOf(width, height)
        drawCircle(Color.Black, brush, center, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, brush, center, style = Stroke(1.dp.toPx()))
        drawLine(Color.Black, center - Offset(5.dp.toPx(), 0f), center + Offset(5.dp.toPx(), 0f), 3.dp.toPx())
        drawLine(Color.White, center - Offset(5.dp.toPx(), 0f), center + Offset(5.dp.toPx(), 0f), 1.dp.toPx())
        drawLine(Color.Black, center - Offset(0f, 5.dp.toPx()), center + Offset(0f, 5.dp.toPx()), 3.dp.toPx())
        drawLine(Color.White, center - Offset(0f, 5.dp.toPx()), center + Offset(0f, 5.dp.toPx()), 1.dp.toPx())
    }
    drawCircle(Color.White, lens[2] / 2, center, style = Stroke(2.dp.toPx()))
}
