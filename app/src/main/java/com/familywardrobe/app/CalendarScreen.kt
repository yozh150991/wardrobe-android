@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DateChoiceDialog(date: String, dismiss: () -> Unit, chosen: (String) -> Unit) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 1900..2200)
    DatePickerDialog(onDismissRequest = dismiss, confirmButton = {
        TextButton(onClick = { picker.selectedDateMillis?.let { chosen(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) } },
            enabled = picker.selectedDateMillis != null) { Text(stringResource(R.string.save)) }
    }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }) { DatePicker(picker) }
}

@Composable
fun ScheduleDialog(outfit: Outfit, vm: WardrobeViewModel, date: String, dismiss: () -> Unit, saved: (String) -> Unit) {
    DateChoiceDialog(date, dismiss) { chosen -> vm.schedule(outfit.id, chosen) { saved(chosen) } }
}

@Composable
fun CalendarScreen(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, today: String, initialDate: String,
    open: (String) -> Unit, daily: () -> Unit) {
    var date by rememberSaveable(wardrobe, initialDate) { mutableStateOf(initialDate.ifBlank { today }) }
    var chooseDate by remember { mutableStateOf(false) }
    var choosingOutfit by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<DayPlan?>(null) }
    var deleting by remember { mutableStateOf<DayPlan?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val locale = uiLocale()
    val chosen = LocalDate.parse(date)
    val monday = chosen.minusDays((chosen.dayOfWeek.value - 1).toLong())
    val ownPlans = s.plans.filter { it.wardrobeId == wardrobe }
    val plans = ownPlans.filter { it.date == date }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.calendar_hint))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { if (chosen.minusWeeks(1).year >= 1900) date = chosen.minusWeeks(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.calendar_previous_week)) }
                    TextButton(onClick = { chooseDate = true }, modifier = Modifier.weight(1f)) { Text(dateLabel(date)) }
                    IconButton(onClick = { if (chosen.plusWeeks(1).year <= 2200) date = chosen.plusWeeks(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.calendar_next_week)) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((0..6).map { monday.plusDays(it.toLong()) }.filter { it.year in 1900..2200 }) { day ->
                        FilterChip(selected = day.toString() == date, onClick = { date = day.toString() }, label = {
                            val count = ownPlans.count { it.date == day.toString() }
                            Text(day.format(DateTimeFormatter.ofPattern("EEE d", locale)) + if (count > 0) " · $count" else "")
                        })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { choosingOutfit = true }, enabled = !busy && s.outfits.any { it.wardrobeId == wardrobe }) { Text(stringResource(R.string.calendar_add)) }
                    OutlinedButton(onClick = daily, enabled = !busy) { Text(stringResource(R.string.daily_title)) }
                    TextButton(onClick = { date = today }) { Text(stringResource(R.string.calendar_today)) }
                }
            }
        }
        if (plans.isEmpty()) item { Text(stringResource(R.string.calendar_empty), Modifier.padding(vertical = 24.dp)) }
        items(plans, key = { it.id }) { plan ->
            val outfit = s.outfits.find { it.id == plan.outfitId }
            if (outfit != null) {
                val pieces = if (plan.worn) plan.wornItemIds.split('|').mapNotNull { id -> s.garments.find { it.id == id }?.let(LookRules::owned) }
                    else LookRules.saved(s, outfit)
                val ready = pieces.isNotEmpty() && pieces.all { it.ownedId != null && it.ready }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(outfit.name, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(if (plan.worn) R.string.calendar_worn else R.string.calendar_planned), color = MaterialTheme.colorScheme.primary)
                        val stored = LookRules.saved(s, outfit)
                        val sameComposition = pieces.size == stored.size && pieces.map { it.key }.toSet() == stored.map { it.key }.toSet()
                        OutfitComposition(pieces, vm, layout = if (sameComposition) outfit.layout else "", background = if (sameComposition) outfit.background else "paper") { open(outfit.id) }
                        if (plan.worn && !sameComposition) Text(stringResource(R.string.calendar_actual_composition), style = MaterialTheme.typography.bodySmall)
                        if (!plan.worn && !ready) Text(stringResource(R.string.calendar_unavailable), color = MaterialTheme.colorScheme.error)
                        if (plan.notes.isNotBlank()) Text(plan.notes)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { vm.wearPlan(plan.id) }, enabled = !busy && (plan.worn || ready && date <= today)) {
                                Text(stringResource(if (plan.worn) R.string.calendar_undo_wear else R.string.calendar_confirm_wear)) }
                            TextButton(onClick = { open(outfit.id) }, enabled = !busy) { Text(stringResource(R.string.open_outfit)) }
                            if (!plan.worn) {
                                TextButton(onClick = { moving = plan }, enabled = !busy) { Text(stringResource(R.string.calendar_move)) }
                                TextButton(onClick = { deleting = plan }, enabled = !busy) { Text(stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (chooseDate) DateChoiceDialog(date, { chooseDate = false }) { date = it; chooseDate = false }
    moving?.let { plan -> DateChoiceDialog(plan.date, { moving = null }) { next -> vm.movePlan(plan.id, next) { date = next; moving = null } } }
    deleting?.let { plan -> AlertDialog(onDismissRequest = { deleting = null }, text = { Text(stringResource(R.string.calendar_remove_hint)) },
        confirmButton = { TextButton(onClick = { vm.removePlan(plan.id); deleting = null }, enabled = !busy) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
    if (choosingOutfit) VisualPickerDialog(R.string.calendar_add, s.outfits.filter { it.wardrobeId == wardrobe }.map { outfitOption(it, s) }, vm,
        { choosingOutfit = false }, enabled = !busy) { id, _ -> vm.schedule(id, date) { choosingOutfit = false } }
}
