@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject

internal data class LookEditorSave(val pieces: List<LookPiece>, val name: String, val occasion: String,
    val weather: String, val source: String, val target: String, val layout: String, val background: String)
internal class LookEditorPort(val restored: JSONObject?, val keep: (JSONObject) -> Unit,
    val clear: () -> Unit, val save: (LookEditorSave) -> Unit)

@Composable
internal fun LookEditor(s: Snapshot, wardrobe: String, anchorId: String, outfitId: String, vm: WardrobeViewModel, onDirty: (Boolean) -> Unit,
    onStage: (Boolean) -> Unit, port: LookEditorPort? = null, saving: Boolean = false, done: (String) -> Unit) {
    val original = s.outfits.find { it.id == outfitId }
    val originalPieces = original?.let { LookRules.saved(s, it) }.orEmpty()
    val restored = remember { if (port != null) port.restored else vm.draftFor("look", wardrobe, outfitId) }
    val anchor = s.garments.find { it.id == anchorId }?.let(LookRules::owned)
    var stage by rememberSaveable { mutableIntStateOf(restored?.optInt("stage", if (anchor == null && original == null) 0 else 1) ?: if (anchor == null && original == null) 0 else 1) }
    var occasion by rememberSaveable { mutableStateOf(restored?.optString("occasion", original?.occasion ?: "everyday") ?: original?.occasion ?: "everyday") }
    var weather by rememberSaveable { mutableStateOf(restored?.optString("weather", original?.weather ?: "any_weather") ?: original?.weather ?: "any_weather") }
    var source by rememberSaveable { mutableStateOf(restored?.optString("source", original?.source ?: "owned") ?: original?.source ?: "owned") }
    var manual by rememberSaveable { mutableStateOf(restored?.optBoolean("manual", false) ?: false) }
    var layout by rememberSaveable { mutableStateOf(restored?.optString("layout", original?.layout.orEmpty()) ?: original?.layout.orEmpty()) }
    var background by rememberSaveable { mutableStateOf(restored?.optString("background", original?.background ?: "paper") ?: original?.background ?: "paper") }
    var composing by rememberSaveable { mutableStateOf(false) }
    var undoLayout by rememberSaveable { mutableStateOf<String?>(null) }
    var undoBackground by rememberSaveable { mutableStateOf<String?>(null) }
    var gestureActive by remember { mutableStateOf(false) }
    fun checkpoint() { undoLayout = layout; undoBackground = background }
    fun changeLayout(value: String) { if (value != layout) { checkpoint(); layout = value } }
    fun changeBackground(value: String) { if (value != background) { checkpoint(); background = value } }
    var name by rememberSaveable { mutableStateOf(restored?.optString("name") ?: original?.name.orEmpty()) }
    fun array(key: String) = restored?.optJSONArray(key)?.let { a -> ArrayList((0 until a.length()).map { a.getString(it) }) }
    var keys by rememberSaveable { mutableStateOf(array("keys") ?: ArrayList(if (anchor != null) Matching.build(requireNotNull(s.garments.find { it.id == anchor.ownedId }), s.garments).map { "owned:$it" } else originalPieces.map { it.key })) }
    var locks by rememberSaveable { mutableStateOf(array("locks") ?: arrayListOf<String>().apply { anchor?.let { add(it.key) } }) }
    var selected by rememberSaveable { mutableStateOf(keys.firstOrNull().orEmpty()) }
    var choosingCategory by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<LookPiece?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    var nameError by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    var duplicateLookId by rememberSaveable { mutableStateOf("") }
    val privateBusy by vm.busy.collectAsStateWithLifecycle()
    val busy = saving || privateBusy
    val ideas = inspirationPieces()
    val pool = LookRules.candidates(s, wardrobe, occasion, weather, source, ideas)
    val eligible = DailyOutfits.items(s, java.time.LocalDate.now().toString()).map { it.piece.id }.toSet()
    val autoPool = pool.filter { it.ownedId == null || it.ownedId in eligible }
    val all = s.garments.filter { it.wardrobeId == wardrobe }.map(LookRules::owned) +
        s.wishes.filter { it.wardrobeId == wardrobe && it.ownedId.isBlank() }.map(LookRules::wanted) + ideas + originalPieces.filter { it.kind in listOf("missing", "idea") }
    val pieces = keys.mapNotNull { key -> all.find { it.key == key } }
    val current = pieces.find { it.key == selected }
    LaunchedEffect(keys.toList()) { undoLayout = null; undoBackground = null }
    val payload = JSONObject().put("stage", stage).put("occasion", occasion).put("weather", weather).put("source", source)
        .put("manual", manual).put("name", name).put("keys", JSONArray(keys)).put("locks", JSONArray(locks)).put("layout", layout).put("background", background).toString()
    val baseline = remember { payload }
    LaunchedEffect(payload) { onStage(stage == 0); onDirty(payload != baseline || restored != null || original == null && pieces.isNotEmpty()); if (port != null) port.keep(JSONObject(payload)) else vm.keepDraft("look", wardrobe, outfitId, JSONObject(payload)) }
    fun submit(target: String) {
        if (port != null) port.save(LookEditorSave(pieces, name, occasion, weather, source, target, layout, background))
        else vm.saveCanvasLook(wardrobe, pieces, name, occasion, weather, source, target, layout, background) { onDirty(false); done(it) }
    }
    fun save() {
        if (!TextLimits.hasVisibleText(name)) { nameError = true; nameFocus.requestFocus(); return }
        val signature = pieces.map { it.ownedId ?: it.key }.sorted().joinToString("|")
        val duplicate = s.outfits.find { it.wardrobeId == wardrobe && it.signature == signature && it.id != outfitId }
        if (duplicate != null) duplicateLookId = duplicate.id else submit(outfitId)
    }
    fun alternatives(category: String) = pool.filter { it.category == category && pieces.filter { p -> p.category != category }
        .all { other -> Matching.compatibleSeasons(it.season, other.season) } }
    fun replace(category: String) {
        val old = pieces.find { it.key == selected && it.category == category } ?: pieces.find { it.category == category }
        if (old != null && old.key in locks) return
        val next = alternatives(category).firstOrNull { it.key != old?.key && it.key !in keys } ?: return
        keys = ArrayList(LookRules.choose(pieces, next, old?.key).map { it.key })
        selected = next.key
    }
    Column(Modifier.fillMaxSize().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (restored != null) Text(stringResource(R.string.draft_restored), color = MaterialTheme.colorScheme.primary)
        if (stage == 0) {
            Text(stringResource(R.string.look_setup_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Choice(occasion, LookRules.OCCASIONS, { occasion = it }, R.string.look_occasion, enabled = !busy)
            Choice(weather, LookRules.WEATHERS, { weather = it }, R.string.look_weather, enabled = !busy)
            Text(stringResource(R.string.look_weather_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.look_source), style = MaterialTheme.typography.titleMedium)
            LookRules.SOURCES.forEach { code ->
                OutlinedCard(onClick = {
                    source = code
                    val eligible = when (code) { "owned" -> pieces.filter { it.ownedId != null }; "inspiration" -> pieces.filter { it.kind == "idea" }; else -> pieces.filter { it.kind != "idea" } }
                    if (eligible.size != pieces.size) message = R.string.look_source_removed
                    keys = ArrayList(eligible.map { it.key }); locks = ArrayList(locks.filter { it in keys })
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = source == code, onClick = null)
                        Column(Modifier.padding(start = 10.dp)) { Text(stringResource(label(code))); Text(stringResource(when (code) {
                            "owned" -> R.string.look_owned_hint; "mixed" -> R.string.look_mixed_hint; else -> R.string.look_inspiration_hint
                        }), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick = { keys = ArrayList(LookRules.generate(autoPool, pieces, locks.toSet(), weather, false).map { it.key }); stage = 1; manual = false },
                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.generate_look)) }
            OutlinedButton(onClick = { stage = 1; manual = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.build_manually)) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { stage = 0 }, enabled = !busy) { Text(stringResource(R.string.look_settings)) }
                TextButton(onClick = { manual = !manual }, enabled = !busy) { Text(stringResource(if (manual) R.string.automatic_mode else R.string.build_manually)) }
            }
            Text(stringResource(LookRules.status(pieces)), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!composing, { composing = false }, label = { Text(stringResource(R.string.look_contents_mode)) })
                FilterChip(composing, { composing = true }, label = { Text(stringResource(R.string.look_composition_mode)) })
            }
            Text(stringResource(if (composing) R.string.canvas_gesture_hint else R.string.look_select_hint), style = MaterialTheme.typography.bodySmall)
            OutfitComposition(pieces, vm, selected, locks.toSet(), enabled = !busy, layout = layout, background = background,
                editable = composing, onLayout = { layout = it }, onGestureStart = { checkpoint(); gestureActive = true }, onGestureEnd = { gestureActive = false }) { selected = it.key }
            if (composing) {
                OutlinedButton(onClick = { layout = undoLayout.orEmpty(); background = undoBackground ?: background; undoLayout = null; undoBackground = null },
                    enabled = !busy && !gestureActive && undoLayout != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.canvas_undo)) }
                CanvasControls(pieces, selected, layout, background, !busy, ::changeLayout, ::changeBackground)
            }
            if (!composing) current?.let { p ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        val locked = p.key in locks
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { locks = ArrayList(if (locked) locks - p.key else locks + p.key) }, enabled = !busy) {
                                Text(stringResource(if (locked) R.string.unlock_piece else R.string.lock_piece)) }
                            OutlinedButton(onClick = { replace(p.category) }, enabled = !busy && !locked && alternatives(p.category).any { it.key != p.key }) { Text(stringResource(R.string.replace)) }
                            TextButton(onClick = { details = p }) { Text(stringResource(R.string.item_details)) }
                            if (manual) TextButton(onClick = { keys = ArrayList(keys - p.key); locks = ArrayList(locks - p.key); selected = "" }, enabled = !busy && !locked) { Text(stringResource(R.string.remove_from_look)) }
                        }
                        if (locked) Text(stringResource(R.string.replace_locked_hint), style = MaterialTheme.typography.bodySmall)
                        else if (alternatives(p.category).none { it.key != p.key }) Text(stringResource(R.string.no_alternative), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (manual || !WardrobeRules.completeCategories(pieces.map { it.category })) {
                Text(stringResource(R.string.manual_picker_hint), style = MaterialTheme.typography.bodyMedium)
                val relevant = (WardrobeRules.startingCategories(wardrobe, s.garments.map { it.rulePiece() }) + pool.filter { it.category !in pieces.map { p -> p.category } }.map { it.category }).distinct().take(4)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { relevant.forEach { category ->
                    OutlinedButton(onClick = { choosingCategory = category }, enabled = !busy && pieces.size < WardrobeRules.MAX_PARTS && (WardrobeRules.categoryLimit(category) > 1 || pieces.none { it.category == category && it.key in locks })) { Text(stringResource(label(category))) }
                } }
                CategoryChoice("", { choosingCategory = it }, enabled = !busy && pieces.size < WardrobeRules.MAX_PARTS, title = R.string.all_categories)
            }
            if (pieces.isEmpty()) Text(stringResource(R.string.look_no_candidates))
            if (!manual) OutlinedButton(onClick = { keys = ArrayList(LookRules.generate(autoPool, pieces, locks.toSet(), weather, true).map { it.key }) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.refresh_unlocked)) }
            message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(stringResource(R.string.flex_shape_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(if (source == "inspiration") R.string.look_inspiration_reason else R.string.look_match_reason), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = TextLimits.input(it, 80); nameError = false }, label = { Text(stringResource(R.string.outfit_name)) }, enabled = !busy, singleLine = true,
                isError = nameError, supportingText = { if (nameError) Text(stringResource(R.string.wish_name_required)) }, modifier = Modifier.fillMaxWidth().focusRequester(nameFocus))
            if (keys.size != pieces.size) TextButton(onClick = { keys = ArrayList(pieces.map { it.key }); locks = ArrayList(locks.filter { it in keys }) }, enabled = !busy) { Text(stringResource(R.string.remove_missing_pieces)) }
            if (keys.size != pieces.size || pieces.any { !it.ready }) Text(stringResource(R.string.look_unavailable_hint), color = MaterialTheme.colorScheme.error)
            if (LookRules.status(pieces) == R.string.look_incomplete) Text(stringResource(R.string.save_partial_hint), style = MaterialTheme.typography.bodySmall)
        }
      }
      if (stage != 0) {
            Button(onClick = ::save,
                enabled = !busy && !gestureActive && WardrobeRules.validCategories(pieces.map { it.category }) && pieces.all { it.ready } && keys.size == pieces.size, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.save_outfit)) }
      }
    }
    s.outfits.find { it.id == duplicateLookId && it.wardrobeId == wardrobe }?.let { existing ->
        AlertDialog(onDismissRequest = { if (!busy) duplicateLookId = "" }, title = { Text(stringResource(R.string.canvas_duplicate_title)) },
            text = { Text(stringResource(R.string.canvas_duplicate_hint, existing.name)) },
            confirmButton = { TextButton(onClick = { submit(existing.id); duplicateLookId = "" }, enabled = !busy) { Text(stringResource(R.string.canvas_update_existing)) } },
            dismissButton = { TextButton(onClick = { if (port != null) port.clear() else vm.clearDraft(); onDirty(false); done(existing.id) }, enabled = !busy) { Text(stringResource(R.string.open_outfit)) } })
    }
    choosingCategory?.let { category ->
        val choices = alternatives(category).filter { it.key !in keys }
        VisualPickerDialog(label(category), choices.map { p -> VisualOption(p.key, p.name, stringResource(ownershipLabel(p)), listOf(p.photoId).filter { it.isNotBlank() }, p.category) },
            vm, { choosingCategory = null }, enabled = !busy) { key, _ ->
            choices.find { it.key == key }?.let { p ->
                val next = LookRules.choose(pieces, p)
                if (locks.any { locked -> next.none { it.key == locked } }) message = R.string.flex_locked_conflict
                else { keys = ArrayList(next.map { it.key }); selected = p.key }
            }
            choosingCategory = null
        }
    }

    details?.let { p -> AlertDialog(onDismissRequest = { details = null }, title = { Text(p.name) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(ownershipLabel(p))); Text(stringResource(label(p.category)))
            ItemPhoto(p.photoId, vm, Modifier.fillMaxWidth().height(180.dp), description = p.name, fit = true)
            p.ownedId?.let { id -> s.garments.find { it.id == id }?.let { g -> Text(g.description); Text(g.location) } }
            p.wishId?.let { id -> s.wishes.find { it.id == id }?.let { w -> Text(w.requirements.ifBlank { w.inspiration }) } }
        }
    }, confirmButton = { TextButton(onClick = { details = null }) { Text(stringResource(R.string.close)) } }) }
}

fun ownershipLabel(p: LookPiece): Int = when (p.kind) {
    "owned" -> R.string.owned_badge; "need" -> R.string.wish_need_badge; "product" -> R.string.shopping
    "missing" -> R.string.look_needs_replacement; else -> R.string.idea_badge
}
