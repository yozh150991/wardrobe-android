package com.familywardrobe.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private data class EditorPreview(val photoId: String, val key: String, val history: PhotoEditHistory, val image: PhotoSessionImage?, val failed: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoEditor(photoId: String, vm: WardrobeViewModel, onDismiss: () -> Unit, onPhoto: (String) -> Unit, beforePhotoId: String = photoId, owner: String) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val failure by vm.failure.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val saved = remember(vm, owner, photoId) { vm.photoSessionValue(owner, photoId) }
    val restored = remember(saved, photoId) { saved?.let { runCatching { PhotoSessionDraft.decode(it, photoId) }.getOrNull() } }
    val restoreFailed = saved != null && restored == null
    val sourceBefore = restored?.beforeId ?: beforePhotoId.takeIf(PhotoStore.ID::matches) ?: photoId
    // Durable commands are authoritative after recreation; an older instance-state bundle must not replace them.
    var history by remember(vm, owner, photoId) { mutableStateOf(restored?.history ?: PhotoEditHistory()) }
    var tool by remember(vm, owner, photoId) { mutableStateOf(restored?.tool ?: "crop") }
    var saveFailed by remember { mutableStateOf(false) }
    var precise by rememberSaveable { mutableStateOf(false) }
    var maskDrawing by remember { mutableStateOf(false) }
    var regionPending by remember { mutableStateOf(false) }
    var precisionPending by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val toolState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val key = remember(history) { encodePhotoHistory(history) }
    val plan = remember(history, photoId) { runCatching { photoSessionPlan(history, photoId) }.getOrNull() }
    val loaded by produceState<EditorPreview?>(null, vm.photos, photoId, history, refresh) {
        val previous = value
        try { value = EditorPreview(photoId, key, history, renderPhotoSession(vm.photos, photoId, history, previous?.history, previous?.image)) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { value = EditorPreview(photoId, key, history, null, true) }
    }
    val currentImage = loaded?.takeIf { it.photoId == photoId }?.image?.takeIf { it.owner === vm.photos }
    val preview = currentImage?.bitmap
    val ready = loaded?.key == key && currentImage != null && !restoreFailed
    val previewFailed = loaded?.photoId == photoId && loaded?.key == key && loaded?.failed == true
    var left by remember(key) { mutableFloatStateOf(0f) }
    var right by remember(key) { mutableFloatStateOf(1f) }
    var top by remember(key) { mutableFloatStateOf(0f) }
    var bottom by remember(key) { mutableFloatStateOf(1f) }
    val controlsEnabled = !busy && !maskDrawing && !cropping && !precisionPending && !regionPending && !restoreFailed
    fun persist(next: PhotoEditHistory = history, nextTool: String = tool): Boolean {
        val success = vm.keepPhotoSession(owner, PhotoSessionDraft(photoId, sourceBefore, next, nextTool))
        saveFailed = !success
        return success
    }
    fun changeHistory(next: PhotoEditHistory) { history = next; persist(next) }
    fun append(action: String) { if (history.canAppend(action)) changeHistory(history.append(action)) }
    fun pause() { if (restoreFailed || persist()) onDismiss() }
    fun resetBounds() { left = 0f; top = 0f; right = 1f; bottom = 1f; cropping = false }
    fun commitCrop() {
        if (preview != null && ready) {
            val box = PhotoGeometry.crop(preview.width, preview.height, left, top, right, bottom)
            if (box[0] != 0 || box[1] != 0 || box[2] != preview.width || box[3] != preview.height)
                append(JSONObject().put("crop", JSONArray().put(left).put(top).put(right).put(bottom)).toString())
        }
        resetBounds()
    }
    fun resetTo(id: String) { append(JSONObject().put("source", id).toString()) }
    Dialog(onDismissRequest = { if (!busy && !maskDrawing && !cropping && !regionPending && !precisionPending) pause() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = !busy)) {
        Surface(Modifier.fillMaxSize()) {
          BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableHeight = maxHeight
            val scrollPage = LocalDensity.current.fontScale > 1.3f || availableHeight < 600.dp
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .then(if (scrollPage) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.photo_edit), style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("crop" to R.string.photo_crop_tool, "rotate" to R.string.photo_rotate_tool, "background" to R.string.photo_background_tool,
                        "precision" to R.string.photo_precision_tool).forEach { (code, title) ->
                        FilterChip(tool == code, onClick = { tool = code; persist(nextTool = code) }, enabled = controlsEnabled, label = { Text(stringResource(title)) })
                    }
                }
                PhotoHistoryControls(history, controlsEnabled) { changeHistory(it); resetBounds() }
                Box(if (scrollPage) Modifier.fillMaxWidth().height(maxOf(400.dp, availableHeight * .65f)) else Modifier.weight(1f)) {
                    if (tool == "background") toolState.SaveableStateProvider("mask") {
                        PhotoMaskEditor(currentImage, ready && !busy, key, history, plan?.segments ?: 2048,
                            onStroke = ::append, onDrawing = { maskDrawing = it },
                            onSelectionPending = { regionPending = it },
                            onOriginal = if (sourceBefore != plan?.sourceId || plan?.actions?.isNotEmpty() == true) ({ resetTo(sourceBefore) }) else null,
                            onResetAll = { resetTo(photoId) })
                    } else if (tool == "precision") toolState.SaveableStateProvider("precision") {
                        PhotoPrecisionEditor(currentImage, ready && !busy, key, history, ::append) { precisionPending = it }
                    } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.photo_history_unified_hint), style = MaterialTheme.typography.bodySmall)
                        if (tool == "crop") {
                            Text(stringResource(R.string.crop_handles_hint), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.photo_crop_commit_hint), style = MaterialTheme.typography.bodySmall)
                        }
                        if (preview != null) {
                            CropPreview(preview, left, top, right, bottom, enabled = ready && !busy && tool == "crop",
                                onGestureStart = { cropping = true }, onGestureEnd = ::commitCrop, onGestureCancel = ::resetBounds
                            ) { l, t, r, b -> left = l; top = t; right = r; bottom = b }
                            val bounds = PhotoGeometry.crop(preview.width, preview.height, left, top, right, bottom)
                            Text(stringResource(R.string.photo_crop_size, bounds[2], bounds[3]), style = MaterialTheme.typography.bodySmall)
                        }
                        if (tool == "crop") TextButton(onClick = { precise = !precise }, enabled = controlsEnabled) { Text(stringResource(R.string.crop_precise_controls)) }
                        if (tool == "crop" && precise) {
                            val horizontalLabel = stringResource(R.string.photo_crop_horizontal)
                            Text(horizontalLabel)
                            RangeSlider(value = left..right, onValueChange = {
                                if (it.endInclusive - it.start >= .05f) { cropping = true; left = it.start; right = it.endInclusive }
                            }, onValueChangeFinished = ::commitCrop, enabled = ready && !busy,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = horizontalLabel })
                            val verticalLabel = stringResource(R.string.photo_crop_vertical)
                            Text(verticalLabel)
                            RangeSlider(value = top..bottom, onValueChange = {
                                if (it.endInclusive - it.start >= .05f) { cropping = true; top = it.start; bottom = it.endInclusive }
                            }, onValueChangeFinished = ::commitCrop, enabled = ready && !busy,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = verticalLabel })
                        }
                        if (tool == "rotate") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { append(JSONObject().put("rotate", -1).toString()) },
                                enabled = ready && controlsEnabled && history.canAppend("{\"rotate\":-1}"), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.photo_rotate_left)) }
                            OutlinedButton(onClick = { append(JSONObject().put("rotate", 1).toString()) },
                                enabled = ready && controlsEnabled && history.canAppend("{\"rotate\":1}"), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.photo_rotate_right)) }
                        }
                        TextButton(onClick = { resetTo(photoId) }, enabled = controlsEnabled && ready && history.canUndo() && history.canAppend("{\"source\":\"$photoId\"}")) {
                            Text(stringResource(R.string.photo_reset_all))
                        }
                        if (!history.canAppend("{}")) Text(stringResource(R.string.mask_limit))
                        Text(stringResource(R.string.photo_edit_notice), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!ready && !previewFailed && !restoreFailed) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (restoreFailed) {
                    Text(stringResource(R.string.photo_session_invalid), color = MaterialTheme.colorScheme.error)
                } else if (saveFailed) {
                    Text(stringResource(R.string.draft_save_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { persist() }, enabled = !busy) { Text(stringResource(R.string.retry)) }
                } else if (history.steps.isNotEmpty()) Text(stringResource(R.string.photo_session_saved), style = MaterialTheme.typography.bodySmall)
                if (previewFailed) {
                    Text(stringResource(R.string.photo_editor_preview_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { refresh++ }, enabled = !busy) { Text(stringResource(R.string.retry)) }
                }
                failure?.let { Text(stringResource(it.message), color = MaterialTheme.colorScheme.error) }
                if (busy) {
                    Text(stringResource(operation?.kind?.title ?: R.string.operation_photo))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (operation?.cancellable == true) TextButton(onClick = vm::cancelOperation) { Text(stringResource(R.string.cancel)) }
                }
                Button(onClick = {
                    if (!history.canUndo()) { if (vm.discardPhotoSession(owner, photoId)) onDismiss() else saveFailed = true }
                    else if (persist()) vm.editPhotoSession(photoId, history, owner) { onPhoto(it); onDismiss() }
                }, enabled = ready && controlsEnabled && !saveFailed, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.photo_apply_edit)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::pause, enabled = !busy && !maskDrawing && !cropping && !regionPending && !precisionPending) { Text(stringResource(R.string.photo_session_pause)) }
                    TextButton(onClick = {
                        if (vm.discardPhotoSession(owner, photoId)) onDismiss() else saveFailed = true
                    }, enabled = !busy && !maskDrawing && !cropping) { Text(stringResource(R.string.photo_session_discard)) }
                }
            }
          }
        }
    }
}

@Composable
private fun CropPreview(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float, enabled: Boolean,
    onGestureStart: () -> Unit, onGestureEnd: () -> Unit, onGestureCancel: () -> Unit,
    onBounds: (Float, Float, Float, Float) -> Unit) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val latest by rememberUpdatedState(floatArrayOf(left, top, right, bottom))
    val update by rememberUpdatedState(onBounds)
    val start by rememberUpdatedState(onGestureStart)
    val finish by rememberUpdatedState(onGestureEnd)
    val cancel by rememberUpdatedState(onGestureCancel)
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val backdrop = rememberPhotoBackdrop(bitmap)
    val description = stringResource(R.string.photo_crop_preview)
    Canvas(Modifier.fillMaxWidth().height(280.dp).onSizeChanged { canvasSize = it }.semantics { contentDescription = description }
        .pointerInput(bitmap, enabled) {
            if (!enabled) return@pointerInput
            var corner = -1
            fun frame() = PhotoViewportRules.viewport(bitmap.width, bitmap.height, canvasSize.width.toFloat(), canvasSize.height.toFloat(), 1f, 0f, 0f)
            detectDragGestures(onDragStart = { point ->
                val f = frame(); val b = latest
                val handles = listOf(Offset(f[0] + b[0]*f[2], f[1] + b[1]*f[3]), Offset(f[0] + b[2]*f[2], f[1] + b[1]*f[3]),
                    Offset(f[0] + b[0]*f[2], f[1] + b[3]*f[3]), Offset(f[0] + b[2]*f[2], f[1] + b[3]*f[3]))
                corner = handles.indices.minByOrNull { (handles[it]-point).getDistance() }?.takeIf { (handles[it]-point).getDistance() <= 32.dp.toPx() } ?: -1
                if (corner >= 0) start()
            }, onDragEnd = { if (corner >= 0) finish(); corner = -1 }, onDragCancel = { if (corner >= 0) cancel(); corner = -1 }) { change, _ ->
                if (corner >= 0) {
                    change.consume(); val point = PhotoViewportRules.point(change.position.x, change.position.y, frame(), true)
                    val b = latest; val next = PhotoViewportRules.resize(b[0], b[1], b[2], b[3], corner, point[0], point[1])
                    update(next[0],next[1],next[2],next[3])
                }
            }
        }) {
        drawRect(backdrop)
        val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val x = ((size.width - width) / 2).roundToInt()
        val y = ((size.height - height) / 2).roundToInt()
        drawImage(image, dstOffset = IntOffset(x, y), dstSize = IntSize(width, height))
        val box = PhotoGeometry.crop(bitmap.width, bitmap.height, left, top, right, bottom)
        val l = x + box[0].toFloat() / bitmap.width * width
        val t = y + box[1].toFloat() / bitmap.height * height
        val r = x + (box[0] + box[2]).toFloat() / bitmap.width * width
        val b = y + (box[1] + box[3]).toFloat() / bitmap.height * height
        val shade = Color.Black.copy(alpha = .65f)
        drawRect(shade, Offset(x.toFloat(), y.toFloat()), Size(width.toFloat(), t - y))
        drawRect(shade, Offset(x.toFloat(), b), Size(width.toFloat(), y + height - b))
        drawRect(shade, Offset(x.toFloat(), t), Size(l - x, b - t))
        drawRect(shade, Offset(r, t), Size(x + width - r, b - t))
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
        if (enabled) listOf(Offset(l,t), Offset(r,t), Offset(l,b), Offset(r,b)).forEach { handle ->
            drawCircle(Color.Black, 9.dp.toPx(), handle); drawCircle(Color.White, 6.dp.toPx(), handle)
        }
        for (part in 1..2) {
            val gx = l + (r - l) * part / 3
            val gy = t + (b - t) * part / 3
            drawLine(Color.White.copy(alpha = .5f), Offset(gx, t), Offset(gx, b))
            drawLine(Color.White.copy(alpha = .5f), Offset(l, gy), Offset(r, gy))
        }
    }
}
