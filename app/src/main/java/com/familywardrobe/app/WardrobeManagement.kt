@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun Wardrobe.displayName(): String = if (usesDefaultName) stringResource(R.string.my_wardrobe) else name

@Composable
fun ManageWardrobes(snapshot: Snapshot, activeId: String, vm: WardrobeViewModel, open: (String) -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var renameId by rememberSaveable { mutableStateOf("") }
    var editedName by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf("") }
    var confirmed by rememberSaveable(deleteId) { mutableStateOf(false) }
    val canDelete = snapshot.wardrobes.size > 1
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.wardrobe_total, snapshot.wardrobes.size), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.manage_wardrobes_hint))
            if (!canDelete) Text(stringResource(R.string.keep_one_wardrobe))
        }
        items(snapshot.wardrobes, key = { it.id }) { w ->
            val title = w.displayName()
            val clothes = snapshot.garments.filter { it.wardrobeId == w.id }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.wardrobe_reference, w.id.take(8)), style = MaterialTheme.typography.bodySmall)
                    if (w.id == activeId) Text(stringResource(R.string.selected_wardrobe), color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.wardrobe_contents, clothes.size, clothes.count { it.archived },
                        snapshot.outfits.count { it.wardrobeId == w.id }, snapshot.wishes.count { it.wardrobeId == w.id }))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { open(w.id) }, enabled = !busy) { Text(stringResource(R.string.open_wardrobe)) }
                        TextButton(onClick = { editedName = title; renameId = w.id }, enabled = !busy) { Text(stringResource(R.string.rename_wardrobe)) }
                        TextButton(onClick = { deleteId = w.id }, enabled = !busy && canDelete) {
                            Text(stringResource(R.string.delete_wardrobe), color = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    snapshot.wardrobes.find { it.id == renameId }?.let {
        AlertDialog(onDismissRequest = { if (!busy) renameId = "" }, title = { Text(stringResource(R.string.rename_wardrobe)) },
            text = { Field(editedName, { value -> editedName = TextLimits.input(value, 80) }, R.string.wardrobe_name, enabled = !busy) },
            confirmButton = { TextButton(onClick = {
                vm.renameWardrobe(renameId, editedName); renameId = ""
            }, enabled = !busy && editedName.isNotBlank()) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renameId = "" }, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    }
    snapshot.wardrobes.find { it.id == deleteId }?.let { w ->
        val clothes = snapshot.garments.filter { it.wardrobeId == w.id }
        AlertDialog(onDismissRequest = { if (!busy) deleteId = "" },
            title = { Text(stringResource(R.string.delete_wardrobe_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(w.displayName(), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.wardrobe_reference, w.id.take(8)))
                    Text(stringResource(R.string.wardrobe_contents, clothes.size, clothes.count { it.archived },
                        snapshot.outfits.count { it.wardrobeId == w.id }, snapshot.wishes.count { it.wardrobeId == w.id }))
                    Text(stringResource(R.string.delete_wardrobe_warning))
                    Toggle(R.string.delete_wardrobe_ack, confirmed) { if (!busy) confirmed = it }
                }
            },
            confirmButton = { TextButton(onClick = {
                vm.deleteWardrobe(w.id); deleteId = ""
            }, enabled = !busy && canDelete && confirmed) { Text(stringResource(R.string.delete_wardrobe)) } },
            dismissButton = { TextButton(onClick = { deleteId = "" }, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    }
}
