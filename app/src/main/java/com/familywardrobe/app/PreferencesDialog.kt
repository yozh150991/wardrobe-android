@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.familywardrobe.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * PERS-02: everything that shapes the daily suggestions for [wardrobe], with where it comes from. Each preference can
 * be changed or reset; reasons can be taken back one by one.
 */
@Composable
internal fun PreferencesDialog(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, today: String, dismiss: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val revision by vm.personalChoices.revision.collectAsStateWithLifecycle()
    val choices = remember(wardrobe, revision) { vm.personalChoices.read(wardrobe) }
    val names = s.garments.filter { it.wardrobeId == wardrobe }.associate { it.id to it.name }
    // ROT-02: the «Давно не вдягали» card's frequency and the items postponed from it.
    val rotationRevision by vm.rotation.revision.collectAsStateWithLifecycle()
    val rotation = remember(wardrobe, rotationRevision, today) { vm.rotation.read(wardrobe, today) }
    val postponed = rotation.snoozes.values.mapNotNull { snooze -> names[snooze.garmentId]?.let { snooze to it } }.sortedBy { it.first.until }
    var resetting by rememberSaveable { mutableStateOf(false) }
    val feedback = s.feedback.filter { it.wardrobeId == wardrobe }
    val uncomfortable = feedback.filter { !it.signature.contains('|') && it.reason == PreferenceRules.DISLIKE }
    val hidden = feedback.filter { it.signature.contains('|') }
    val warmth = DailyOutfits.warmth(s, wardrobe, choices)
    val empty = feedback.isEmpty() && choices == PersonalChoices() && rotation.frequency == RotationRules.DAILY && postponed.isEmpty()
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.prefs_title), style = MaterialTheme.typography.titleLarge)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        if (empty) Text(stringResource(R.string.prefs_empty))
                        else if (feedback.isNotEmpty()) Text(stringResource(R.string.feedback_count, feedback.size))
                        Text(stringResource(R.string.prefs_personal), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { Text(stringResource(R.string.prefs_style_title), style = MaterialTheme.typography.titleMedium) }
                    items(LookRules.OCCASIONS, key = { "style:$it" }) { occasion ->
                        val style = DailyOutfits.style(s, wardrobe, occasion, choices)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(label(occasion)), style = MaterialTheme.typography.titleSmall)
                            Text(styleSource(style), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (listOf(PreferenceProfile.AUTO) + PreferenceProfile.STYLES).forEach { value ->
                                    FilterChip(selected = choices.style(occasion) == value, onClick = { vm.setStyleChoice(wardrobe, occasion, value) },
                                        enabled = !busy, label = { Text(stringResource(if (value == PreferenceProfile.AUTO) R.string.prefs_auto else label(value))) })
                                }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.prefs_warmth_title), style = MaterialTheme.typography.titleMedium)
                            Text(warmthSource(warmth), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.prefs_warmth_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (listOf(PreferenceProfile.AUTO) + PreferenceProfile.WARMTH).forEach { value ->
                                    FilterChip(selected = choices.warmth == value, onClick = { vm.setWarmthChoice(wardrobe, value) },
                                        enabled = !busy, label = { Text(stringResource(warmthLabel(value))) })
                                }
                            }
                        }
                    }
                    // PERS-03: the person's own colours, chosen here — no photo or selfie analysis.
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.prefs_colours_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.prefs_colours_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PaletteChoice(stringResource(R.string.prefs_colours_liked), choices, PersonalChoices.LIKED, !busy) { colour, value ->
                                vm.setPaletteColour(wardrobe, colour, value)
                            }
                            PaletteChoice(stringResource(R.string.prefs_colours_avoided), choices, PersonalChoices.AVOIDED, !busy) { colour, value ->
                                vm.setPaletteColour(wardrobe, colour, value)
                            }
                        }
                    }
                    item { Text(stringResource(R.string.prefs_uncomfortable_title, uncomfortable.size), style = MaterialTheme.typography.titleMedium) }
                    if (uncomfortable.isEmpty()) item { Text(stringResource(R.string.prefs_none), style = MaterialTheme.typography.bodySmall) }
                    items(uncomfortable, key = { "item:${it.id}" }) { row ->
                        PreferenceRow(names[row.signature].orEmpty(), stringResource(R.string.prefs_uncomfortable_scope), !busy) {
                            vm.undoFeedback(wardrobe, listOf(row.id))
                        }
                    }
                    item { Text(stringResource(R.string.prefs_hidden_title, hidden.size), style = MaterialTheme.typography.titleMedium) }
                    if (hidden.isEmpty()) item { Text(stringResource(R.string.prefs_none), style = MaterialTheme.typography.bodySmall) }
                    items(hidden, key = { "set:${it.id}" }) { row ->
                        val title = row.signature.split('|').mapNotNull { names[it] }.joinToString(" + ")
                        val scope = when (row.reason) {
                            PreferenceRules.OCCASION -> stringResource(R.string.prefs_scope_occasion, stringResource(R.string.feedback_formal), stringResource(label(row.occasion)))
                            PreferenceRules.HOT -> stringResource(R.string.prefs_scope_weather, stringResource(R.string.daily_hot), stringResource(label(row.weather)))
                            else -> stringResource(R.string.prefs_scope_everywhere, stringResource(R.string.daily_dislike))
                        }
                        PreferenceRow(title, scope, !busy) { vm.undoFeedback(wardrobe, listOf(row.id)) }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.rotation_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.prefs_rotation_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RotationRules.FREQUENCIES.forEach { value ->
                                    FilterChip(selected = rotation.frequency == value, onClick = { vm.rotation.setFrequency(wardrobe, value) },
                                        enabled = !busy, label = { Text(stringResource(frequencyLabel(value))) })
                                }
                            }
                            Text(stringResource(R.string.prefs_rotation_postponed, postponed.size), style = MaterialTheme.typography.titleSmall)
                            if (postponed.isEmpty()) Text(stringResource(R.string.prefs_none), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(postponed, key = { "postponed:${it.first.garmentId}" }) { (snooze, name) ->
                        PreferenceRow(name, stringResource(R.string.prefs_rotation_until, dateLabel(snooze.until), stringResource(postponeLabel(snooze.reason))), !busy) {
                            vm.rotation.restore(wardrobe, snooze.garmentId)
                        }
                    }
                    if (!empty) item {
                        OutlinedButton(onClick = { resetting = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.prefs_reset_all))
                        }
                    }
                }
                TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.close)) }
            }
        }
    }
    if (resetting) AlertDialog(onDismissRequest = { resetting = false }, text = { Text(stringResource(R.string.feedback_reset_confirm)) },
        confirmButton = { TextButton(onClick = { resetting = false; vm.resetPreferences(wardrobe) }, enabled = !busy) { Text(stringResource(R.string.prefs_reset_all)) } },
        dismissButton = { TextButton(onClick = { resetting = false }) { Text(stringResource(R.string.cancel)) } })
}

/**
 * One palette list: every base colour as a chip with its swatch. Choosing a colour here takes it out of the other list;
 * choosing it again clears it.
 */
@Composable
private fun PaletteChoice(title: String, choices: PersonalChoices, kind: String, enabled: Boolean, set: (String, String) -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PersonalChoices.PALETTE.forEach { colour ->
            val selected = choices.palette(colour) == kind
            FilterChip(selected = selected, onClick = { set(colour, if (selected) PersonalChoices.NONE else kind) }, enabled = enabled,
                leadingIcon = { Box(Modifier.size(18.dp).clip(CircleShape).background(swatch(colour)).border(1.dp, outline, CircleShape)) },
                label = { Text(stringResource(label(colour))) })
        }
    }
}

@Composable
private fun PreferenceRow(title: String, scope: String, enabled: Boolean, restore: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.ifBlank { stringResource(R.string.prefs_unknown_item) }, style = MaterialTheme.typography.bodyLarge)
            Text(scope, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = restore, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.prefs_restore)) }
        }
    }
}

@Composable
private fun styleSource(style: PreferenceProfile.Style): String {
    val name = stringResource(label(style.style))
    return when (style.source) {
        PreferenceProfile.Source.CHOICE -> stringResource(R.string.prefs_style_choice, name)
        PreferenceProfile.Source.AUTO -> stringResource(R.string.prefs_style_auto, name, style.evidence)
        PreferenceProfile.Source.DEFAULT -> stringResource(R.string.prefs_style_default, name)
    } + if (style.avoidFormal) " " + stringResource(R.string.prefs_style_avoid_formal) else ""
}

@Composable
private fun warmthSource(warmth: PreferenceProfile.Warmth): String = when (warmth.source) {
    PreferenceProfile.Source.CHOICE -> stringResource(R.string.prefs_warmth_choice, stringResource(warmthLabel(warmth.shift)))
    PreferenceProfile.Source.AUTO -> stringResource(R.string.prefs_warmth_auto, warmth.evidence)
    PreferenceProfile.Source.DEFAULT -> stringResource(R.string.prefs_warmth_default, PreferenceProfile.DERIVE)
}

private fun frequencyLabel(value: String): Int = when (value) {
    RotationRules.WEEKLY -> R.string.rotation_weekly
    RotationRules.OFF -> R.string.rotation_off
    else -> R.string.rotation_daily
}

private fun postponeLabel(reason: String): Int = when (reason) {
    RotationRules.SIZE -> R.string.rotation_reason_size
    RotationRules.WAITING -> R.string.rotation_reason_waiting
    RotationRules.NO_MATCH -> R.string.rotation_reason_no_match
    else -> R.string.rotation_reason_later
}

private fun warmthLabel(value: String): Int = when (value) {
    "forecast" -> R.string.prefs_warmth_forecast
    "lighter" -> R.string.prefs_warmth_lighter
    "warmer" -> R.string.prefs_warmth_warmer
    else -> R.string.prefs_auto
}
