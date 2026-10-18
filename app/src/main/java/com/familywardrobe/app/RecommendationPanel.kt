package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecommendationPanel(s: Snapshot, wardrobe: String, vm: WardrobeViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var reviewing by remember { mutableStateOf(false) }
    val current = s.wardrobes.find { it.id == wardrobe } ?: return
    val excluded = s.garments.filter { it.wardrobeId == wardrobe && it.suggestionExcluded }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.titleMedium)
            Choice(current.noRepeatDays.toString(), listOf("0", "1", "3", "7", "14", "30"),
                { vm.setNoRepeatDays(wardrobe, it.toInt()) }, R.string.rules_repeat_label, enabled = !busy,
                optionLabels = mapOf("0" to R.string.rules_repeat_any, "1" to R.string.rules_repeat_1,
                    "3" to R.string.rules_repeat_3, "7" to R.string.rules_repeat_7, "14" to R.string.rules_repeat_14, "30" to R.string.rules_repeat_30))
            Text(stringResource(R.string.rules_repeat_hint), style = MaterialTheme.typography.bodySmall)
            if (excluded.isNotEmpty()) TextButton(onClick = { reviewing = true }, enabled = !busy) {
                Text(stringResource(R.string.rules_excluded_count, excluded.size))
            }
        }
    }
    if (reviewing) AlertDialog(onDismissRequest = { reviewing = false }, title = { Text(stringResource(R.string.rules_excluded_title)) }, text = {
        LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(excluded, key = { it.id }) { g -> Column {
                Text(g.name)
                TextButton(onClick = { vm.excludeFromRecommendations(g.id, false) }, enabled = !busy) { Text(stringResource(R.string.rules_include)) }
            } }
        }
    }, confirmButton = { TextButton(onClick = { reviewing = false }) { Text(stringResource(R.string.close)) } })
}
