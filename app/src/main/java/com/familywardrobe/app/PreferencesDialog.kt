@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
internal fun PreferencesDialog(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, dismiss: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val revision by vm.personalChoices.revision.collectAsStateWithLifecycle()
    val choices = remember(wardrobe, revision) { vm.personalChoices.read(wardrobe) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    val feedback = s.feedback.filter { it.wardrobeId == wardrobe }
    val names = s.garments.filter { it.wardrobeId == wardrobe }.associate { it.id to it.name }
    val uncomfortable = feedback.filter { !it.signature.contains('|') && it.reason == PreferenceRules.DISLIKE }
    val hidden = feedback.filter { it.signature.contains('|') }
    val warmth = DailyOutfits.warmth(s, wardrobe, choices)
    val empty = feedback.isEmpty() && choices == PersonalChoices()
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

private fun warmthLabel(value: String): Int = when (value) {
    "forecast" -> R.string.prefs_warmth_forecast
    "lighter" -> R.string.prefs_warmth_lighter
    "warmer" -> R.string.prefs_warmth_warmer
    else -> R.string.prefs_auto
}
