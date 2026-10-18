package com.familywardrobe.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

private data class PrecisionPreview(val source: PhotoSessionRules.Frame, val command: String,
    val image: Bitmap? = null, val changed: Boolean = false, val visible: Boolean = false, val failed: Boolean = false)

/** A polygon is built with taps (or accessible coordinate controls), then explicitly confirmed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoPrecisionEditor(image: PhotoSessionImage?, enabled: Boolean, historyKey: String, history: PhotoEditHistory,
    onAction: (String) -> Unit, onPending: (Boolean) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("edges") }
    var points by remember(historyKey) { mutableStateOf(emptyList<Offset>()) }
    var edge by remember(historyKey) { mutableIntStateOf(0) }
    var feather by remember(historyKey) { mutableIntStateOf(0) }
    var moving by rememberSaveable { mutableStateOf(false) }
    var coordinates by rememberSaveable { mutableStateOf(false) }
    var pointX by rememberSaveable { mutableFloatStateOf(.5f) }
    var pointY by rememberSaveable { mutableFloatStateOf(.5f) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    var backdrop by rememberSaveable { mutableStateOf("contrast") }
    var showBefore by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val source = image?.bitmap
    val protection = rememberPhotoProtection(image?.frame)
    val zoomOutLabel = stringResource(R.string.mask_zoom_out)
    val zoomInLabel = stringResource(R.string.mask_zoom_in)
    val updatePending by rememberUpdatedState(onPending)
    val pending = points.isNotEmpty() || edge != 0 || feather != 0
    LaunchedEffect(pending) { updatePending(pending) }
    DisposableEffect(Unit) { onDispose { updatePending(false) } }
    LaunchedEffect(source?.width, source?.height) { zoom = 1f; panX = 0f; panY = 0f }
    val command = remember(mode, points, edge, feather) {
        when {
            mode == "edges" && (edge != 0 || feather != 0) -> JSONObject().put("edges", JSONArray().put(edge).put(feather)).toString()
            mode != "edges" && points.size >= 3 -> JSONObject().put("polygon", JSONArray().also { array ->
                points.forEach { array.put(it.x).put(it.y) }
            }).put("mode", mode).toString()
            else -> null
        }
    }
    val preview by produceState<PrecisionPreview?>(null, image, command, retry) {
        val base = image; val edit = command
        value = if (base == null || edit == null) null else try {
            withContext(Dispatchers.Default) {
                val work = coroutineContext
                val action = photoSessionPlan(PhotoEditHistory().append(edit), base.sourceId).actions.single()
                val result = PhotoSessionRules.apply(base.frame, action) { work.ensureActive() }
                var changed = false; var visible = false
                for (i in result.pixels.indices) {
                    if ((i and 4095) == 0) work.ensureActive()
                    if (result.pixels[i] != base.frame.pixels[i] || result.protection[i] != base.frame.protection[i]) changed = true
                    if (result.pixels[i] ushr 24 != 0) visible = true
                }
                PrecisionPreview(base.frame, edit, Bitmap.createBitmap(result.pixels, result.width, result.height, Bitmap.Config.ARGB_8888), changed, visible)
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { PrecisionPreview(base.frame, edit, failed = true) }
    }
    val current = preview?.takeIf { it.source === image?.frame && it.command == command }
    val shown = if (showBefore) source else current?.image ?: source
    val automatic = rememberPhotoBackdrop(source)
    val background = when (backdrop) { "dark" -> Color(0xff202124); "white" -> Color.White; "cream" -> Color(0xffeee5d5); "contrast" -> Color(0xffb538ce); else -> automatic }
    fun reset() { points = emptyList(); edge = 0; feather = 0; showBefore = false }
    fun addPoint(point: Offset) { if (points.size < PhotoPrecisionRules.MAX_POINTS) points = points + point }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("edges" to R.string.photo_edge_tool, "erase" to R.string.photo_lasso_erase,
                "restore" to R.string.photo_lasso_restore, "protect" to R.string.photo_lasso_protect,
                "unprotect" to R.string.photo_lasso_unprotect).forEach { (code, label) ->
                FilterChip(mode == code, { mode = code; reset() }, enabled = enabled && !pending,
                    label = { Text(stringResource(label)) })
            }
        }
        Text(stringResource(if (mode == "edges") R.string.photo_edge_hint else R.string.photo_lasso_hint), style = MaterialTheme.typography.bodySmall)
        val description = stringResource(R.string.photo_precision_preview)
        Canvas(Modifier.fillMaxWidth().height(320.dp).clipToBounds().onSizeChanged { size = it }
            .semantics { contentDescription = description }
            .pointerInput(source, enabled, moving, mode, showBefore) {
                val bitmap = source ?: return@pointerInput
                if (!enabled) return@pointerInput
                fun viewport() = PhotoViewportRules.viewport(bitmap.width, bitmap.height, size.width.toFloat(), size.height.toFloat(), zoom, panX, panY)
                if (moving) detectTransformGestures { _, pan, factor, _ ->
                    zoom = (zoom * factor).coerceIn(1f, 8f)
                    val f = PhotoViewportRules.viewport(bitmap.width, bitmap.height, size.width.toFloat(), size.height.toFloat(), zoom, panX + pan.x, panY + pan.y)
                    panX = f[4]; panY = f[5]
                } else if (mode != "edges" && !showBefore) detectTapGestures { tap ->
                    PhotoViewportRules.point(tap.x, tap.y, viewport(), false)?.let { addPoint(Offset(it[0], it[1])) }
                }
            }) {
            drawRect(background)
            shown?.let { bitmap ->
                val f = PhotoViewportRules.viewport(bitmap.width, bitmap.height, this.size.width, this.size.height, zoom, panX, panY)
                val target = IntSize(f[2].roundToInt().coerceAtLeast(1), f[3].roundToInt().coerceAtLeast(1))
                val origin = IntOffset(f[0].roundToInt(), f[1].roundToInt())
                drawImage(bitmap.asImageBitmap(), dstOffset = origin, dstSize = target)
                if (!showBefore) {
                    protection?.let { drawImage(it.asImageBitmap(), dstOffset = origin, dstSize = target, colorFilter = ColorFilter.tint(Color.Cyan.copy(alpha = .35f))) }
                    val path = Path()
                    points.forEachIndexed { index, p ->
                        val x = f[0] + p.x * f[2]; val y = f[1] + p.y * f[3]
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    if (points.size >= 3) { path.close(); drawPath(path, Color(0xffff3366).copy(alpha = .18f)) }
                    drawPath(path, Color.Black, style = Stroke(4.dp.toPx()))
                    drawPath(path, Color.White, style = Stroke(2.dp.toPx()))
                    points.forEach { p ->
                        val center = Offset(f[0] + p.x * f[2], f[1] + p.y * f[3])
                        drawCircle(Color.Black, 5.dp.toPx(), center); drawCircle(Color.White, 3.dp.toPx(), center)
                    }
                }
            }
        }
        if (mode == "edges") {
            val edgeLabel = stringResource(R.string.photo_edge_offset, edge)
            val featherLabel = stringResource(R.string.photo_edge_feather, feather)
            Text(edgeLabel)
            Slider(edge.toFloat(), { edge = it.roundToInt() }, enabled = enabled, valueRange = -8f..8f, steps = 15,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = edgeLabel })
            Text(featherLabel)
            Slider(feather.toFloat(), { feather = it.roundToInt() }, enabled = enabled, valueRange = 0f..6f, steps = 5,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = featherLabel })
        } else {
            Text(stringResource(R.string.photo_lasso_points, points.size, PhotoPrecisionRules.MAX_POINTS))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ points = points.dropLast(1) }, enabled = enabled && points.isNotEmpty()) { Text(stringResource(R.string.photo_lasso_remove_point)) }
                TextButton({ coordinates = !coordinates }, enabled = enabled) { Text(stringResource(R.string.photo_lasso_coordinates)) }
            }
            if (coordinates) {
                val xLabel = stringResource(R.string.photo_lasso_x, (pointX * 100).roundToInt())
                val yLabel = stringResource(R.string.photo_lasso_y, (pointY * 100).roundToInt())
                Text(xLabel); Slider(pointX, { pointX = it }, enabled = enabled, modifier = Modifier.fillMaxWidth().semantics { contentDescription = xLabel })
                Text(yLabel); Slider(pointY, { pointY = it }, enabled = enabled, modifier = Modifier.fillMaxWidth().semantics { contentDescription = yLabel })
                OutlinedButton({ addPoint(Offset(pointX, pointY)) }, enabled = enabled && points.size < PhotoPrecisionRules.MAX_POINTS) { Text(stringResource(R.string.photo_lasso_add_point)) }
            }
        }
        if (command != null) when {
            current == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            current.failed -> {
                Text(stringResource(R.string.photo_editor_preview_failed), color = MaterialTheme.colorScheme.error)
                TextButton({ retry++ }, enabled = enabled) { Text(stringResource(R.string.retry)) }
            }
            !current.visible -> Text(stringResource(R.string.photo_region_all), color = MaterialTheme.colorScheme.error)
            !current.changed -> Text(stringResource(R.string.photo_precision_unchanged))
        }
        if (pending) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ command?.let { onAction(it); reset() } }, enabled = enabled && current?.changed == true && current?.visible == true && command?.let(history::canAppend) == true) {
                    Text(stringResource(R.string.photo_precision_confirm))
                }
                OutlinedButton(::reset, enabled = enabled) { Text(stringResource(R.string.photo_region_clear)) }
            }
            Text(stringResource(R.string.photo_precision_pending), style = MaterialTheme.typography.bodySmall)
        }
        if (command?.let { !history.canAppend(it) } == true) Text(stringResource(R.string.mask_limit))
        SelectionRow(stringResource(R.string.photo_precision_before), showBefore, enabled) { showBefore = it }
        SelectionRow(stringResource(R.string.mask_move_mode), moving, enabled) { moving = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ zoom = (zoom / 1.5f).coerceAtLeast(1f) }, enabled = enabled && zoom > 1f,
                modifier = Modifier.semantics { contentDescription = zoomOutLabel }) { Text("−") }
            Text(stringResource(R.string.mask_zoom, (zoom * 100).roundToInt()), Modifier.weight(1f))
            OutlinedButton({ zoom = (zoom * 1.5f).coerceAtMost(8f) }, enabled = enabled && zoom < 8f,
                modifier = Modifier.semantics { contentDescription = zoomInLabel }) { Text("+") }
        }
        TextButton({ zoom = 1f; panX = 0f; panY = 0f }, enabled = enabled) { Text(stringResource(R.string.mask_fit)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("white" to R.string.photo_backdrop_white, "cream" to R.string.photo_backdrop_cream,
                "dark" to R.string.photo_backdrop_dark, "contrast" to R.string.photo_backdrop_contrast).forEach { (code, label) ->
                FilterChip(backdrop == code, { backdrop = code }, enabled = enabled, label = { Text(stringResource(label)) })
            }
        }
        Text(stringResource(R.string.photo_protection_hint), style = MaterialTheme.typography.bodySmall)
        val clear = "{\"clearProtection\":true}"
        TextButton({ onAction(clear) }, enabled = enabled && !pending && protection != null && history.canAppend(clear)) { Text(stringResource(R.string.photo_protection_clear)) }
    }
}
