@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.familywardrobe.app
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@Composable
fun CapsuleListScreen(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, open: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val capsules = s.capsules.filter { it.wardrobeId == wardrobe }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.capsule_hint))
            Button(onClick = { adding = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.capsule_add)) }
        }
        if (capsules.isEmpty()) item { Text(stringResource(R.string.capsule_empty), Modifier.padding(vertical = 24.dp)) }
        items(capsules, key = { it.id }) { capsule ->
            val items = s.capsuleItems.filter { it.capsuleId == capsule.id }
            Card(onClick = { open(capsule.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(capsule.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(if (capsule.kind == "trip") R.string.capsule_trip else R.string.capsule_kind))
                    if (capsule.startDate.isNotBlank()) Text(dateLabel(capsule.startDate) + " — " + dateLabel(capsule.endDate))
                    Text(stringResource(R.string.capsule_progress, items.count { it.packed }, items.size))
                }
            }
        }
    }
    if (adding) CapsuleEditor(null, wardrobe, vm, { adding = false }) { adding = false; open(it) }
}

@Composable
fun CapsuleDetailScreen(capsule: Capsule, s: Snapshot, vm: WardrobeViewModel, open: (Garment) -> Unit, openOutfit: (String) -> Unit, removed: () -> Unit, generate: () -> Unit, initialPickItems: Boolean = false) {
    var editing by remember { mutableStateOf(false) }
    var selectingOutfits by remember { mutableStateOf(false) }
    var selectingItems by rememberSaveable { mutableStateOf(initialPickItems) }
    var deleting by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val linked = s.capsuleOutfits.filter { it.capsuleId == capsule.id }.map { it.outfitId }.toSet()
    val packing = s.capsuleItems.filter { it.capsuleId == capsule.id }.sortedWith(compareBy<CapsuleItem> { it.packed }.thenBy { row -> s.garments.find { it.id == row.garmentId }?.category.orEmpty() })
    val missing = packing.count { row -> s.garments.find { it.id == row.garmentId }?.let { !WardrobeRules.ready(it.rulePiece()) } != false }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(capsule.name, style = MaterialTheme.typography.headlineSmall)
                if (capsule.startDate.isNotBlank()) Text(dateLabel(capsule.startDate) + " — " + dateLabel(capsule.endDate))
                Text(stringResource(R.string.capsule_progress, packing.count { it.packed }, packing.size))
                if (missing > 0) Text(stringResource(R.string.capsule_unavailable, missing), color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = generate, enabled = !busy && packing.isNotEmpty()) { Text(stringResource(R.string.capsule_generate)) }
                    OutlinedButton(onClick = { selectingOutfits = true }, enabled = !busy) { Text(stringResource(R.string.capsule_outfits)) }
                    OutlinedButton(onClick = { selectingItems = true }, enabled = !busy) { Text(stringResource(R.string.capsule_extras)) }
                    TextButton(onClick = { editing = true }, enabled = !busy) { Text(stringResource(R.string.edit)) }
                }
                s.outfits.filter { it.id in linked }.forEach { outfit ->
                    TextButton(onClick = { openOutfit(outfit.id) }, enabled = !busy) { Text(outfit.name) }
                    if (s.ideas.any { it.outfitId == outfit.id }) Text(stringResource(R.string.capsule_planned_warning), color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.capsule_unique_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (packing.isEmpty()) item { Text(stringResource(R.string.capsule_no_items)) }
        items(packing, key = { it.garmentId }) { row ->
            s.garments.find { it.id == row.garmentId }?.let { g ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(row.packed, enabled = !busy && (row.packed || WardrobeRules.ready(g.rulePiece())), role = Role.Checkbox) { vm.pack(capsule.id, g.id, it) }
                        .padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(row.packed, onCheckedChange = null, enabled = !busy && (row.packed || WardrobeRules.ready(g.rulePiece())))
                        ItemPhoto(g.photoId, vm, Modifier.size(72.dp), description = g.name)
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(label(g.category)))
                            if (!WardrobeRules.ready(g.rulePiece())) Text(stringResource(if (g.archived) R.string.archive else label(g.availability)), color = MaterialTheme.colorScheme.error)
                            if (row.manual) Text(stringResource(R.string.capsule_manual_item), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { open(g) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.item_details)) }
                    }
                }
            }
        }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { resetting = true }, enabled = !busy && packing.any { it.packed }) { Text(stringResource(R.string.capsule_reset)) }
            TextButton(onClick = { deleting = true }, enabled = !busy) { Text(stringResource(R.string.delete)) }
        } }
    }
    if (editing) CapsuleEditor(capsule, capsule.wardrobeId, vm, { editing = false }) { editing = false }
    if (selectingOutfits) {
        val outfits = s.outfits.filter { it.wardrobeId == capsule.wardrobeId && (it.id in linked || s.parts.any { p -> p.outfitId == it.id } && s.ideas.none { p -> p.outfitId == it.id }) }
        VisualPickerDialog(R.string.capsule_outfits, outfits.map { outfitOption(it,s) }, vm, { selectingOutfits = false }, selected = linked, enabled = !busy) { id, checked -> vm.linkCapsule(capsule.id,id,checked) }
    }
    if (selectingItems) {
        val garments = s.garments.filter { it.wardrobeId == capsule.wardrobeId && (!it.archived || packing.any { row -> row.garmentId == it.id && row.manual }) }
        VisualPickerDialog(R.string.capsule_extras, garments.map { garmentOption(it) }, vm, { selectingItems = false }, selected = packing.filter { it.manual }.map { it.garmentId }.toSet(), enabled = !busy) { id, checked -> vm.manualCapsule(capsule.id,id,checked) }
    }
    if (deleting || resetting) AlertDialog(onDismissRequest = { deleting = false; resetting = false }, text = { Text(stringResource(if (deleting) R.string.capsule_remove_hint else R.string.capsule_reset_hint)) },
        confirmButton = { TextButton(onClick = {
            if (deleting) vm.removeCapsule(capsule.id, removed) else vm.resetPacking(capsule.id)
            deleting = false; resetting = false
        }, enabled = !busy) { Text(stringResource(if (deleting) R.string.delete else R.string.capsule_reset)) } },
        dismissButton = { TextButton(onClick = { deleting = false; resetting = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun CapsuleEditor(original: Capsule?, wardrobe: String, vm: WardrobeViewModel, dismiss: () -> Unit, saved: (String) -> Unit) {
    val id = rememberSaveable { original?.id ?: newId() }
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(original?.kind ?: "capsule") }
    var start by rememberSaveable { mutableStateOf(original?.startDate.orEmpty()) }
    var end by rememberSaveable { mutableStateOf(original?.endDate.orEmpty()) }
    var dateField by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(stringResource(if (original == null) R.string.capsule_add else R.string.edit)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = TextLimits.input(it, 80) }, label = { Text(stringResource(R.string.name)) }, singleLine = true, enabled = !busy)
            Choice(kind, listOf("capsule", "trip"), { kind = it }, R.string.capsule_type, enabled = !busy,
                optionLabels = mapOf("capsule" to R.string.capsule_kind, "trip" to R.string.capsule_trip))
            TextButton(onClick = { dateField = "start" }, enabled = !busy) { Text(stringResource(R.string.capsule_start, if (start.isBlank()) "—" else dateLabel(start))) }
            TextButton(onClick = { dateField = "end" }, enabled = !busy) { Text(stringResource(R.string.capsule_end, if (end.isBlank()) "—" else dateLabel(end))) }
            if (start.isNotBlank() || end.isNotBlank()) TextButton(onClick = { start = ""; end = "" }, enabled = !busy) { Text(stringResource(R.string.capsule_clear_dates)) }
            if (!RoadmapRules.period(start, end)) Text(stringResource(R.string.capsule_date_error), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { vm.saveCapsule(Capsule(id, wardrobe, name, kind, start, end), saved) },
        enabled = !busy && TextLimits.hasVisibleText(name) && RoadmapRules.period(start, end)) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    if (dateField.isNotBlank()) DateChoiceDialog((if (dateField == "start") start else end).ifBlank { LocalDate.now().toString() }, { dateField = "" }) {
        if (dateField == "start") { start = it; if (end.isBlank()) end = it } else { end = it; if (start.isBlank()) start = it }; dateField = ""
    }
}
