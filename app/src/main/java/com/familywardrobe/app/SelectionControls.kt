@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.familywardrobe.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** One control owns the whole labelled row; the visual checkbox cannot toggle a second time. */
@Composable
fun SelectionRow(name: String, selected: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = change)
        .padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(selected, onCheckedChange = null, enabled = enabled)
        Text(name, Modifier.weight(1f).padding(start = 8.dp))
    }
}

@Composable
fun CategoryChoice(value: String, change: (String) -> Unit, enabled: Boolean = true,
    title: Int = R.string.category, allowAll: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(),
    showSelectedValue: Boolean = true, customCategories: List<CustomCategory> = emptyList(),
    onAdd: ((String) -> Unit)? = null) {
    var choosing by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val options = (if (allowAll) listOf("") else emptyList()) + Matching.categories + customCategories.map { it.id }
    val titles = options.associateWith { code -> customCategories.find { it.id == code }?.name
        ?: stringResource(if (code.isBlank()) R.string.all else label(code)) }
    val selectedTitle = titles[value] ?: stringResource(R.string.choose_category)
    OutlinedButton(onClick = { query = ""; choosing = true }, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).semantics {
            if (!showSelectedValue) stateDescription = selectedTitle
        }) {
        if (showSelectedValue) {
            Text(stringResource(title) + ": " + selectedTitle)
        } else {
            Text(stringResource(title), modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text(stringResource(R.string.all_categories)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it.take(100) }, label = { Text(stringResource(R.string.search_categories)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 340.dp).selectableGroup()) {
                items(options.filter { CategoryRules.matches(query, titles.getValue(it)) }, key = { it.ifBlank { "all" } }) { code ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(code == value, role = Role.RadioButton, onClick = { change(code); choosing = false }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(code == value, onClick = null)
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(titles.getValue(code))
                            customCategories.find { it.id == code }?.let { custom ->
                                Text(stringResource(label(custom.baseCategory)), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }, dismissButton = { if (onAdd != null) TextButton(onClick = { choosing = false; onAdd(query) }, enabled = enabled) {
        Text(stringResource(R.string.category_add))
    } }, confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.close)) } })
}

data class VisualOption(val id: String, val name: String, val context: String, val photoIds: List<String> = emptyList(), val category: String = "")

@Composable
fun VisualPickerDialog(title: Int, options: List<VisualOption>, vm: WardrobeViewModel, dismiss: () -> Unit,
    selected: Set<String>? = null, enabled: Boolean = true, choose: (String, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    val categories = options.map { it.category }.filter { it.isNotBlank() }.distinct()
    val visible = options.filter { (category.isBlank() || category == it.category) && (it.name + " " + it.context).contains(query.trim(), ignoreCase = true) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it.take(120) }, singleLine = true, label = { Text(stringResource(R.string.picker_search)) }, modifier = Modifier.fillMaxWidth())
            if (categories.size > 1) CategoryChoice(category, { category = it }, allowAll = true)
            Text(stringResource(R.string.picker_count, visible.size, options.size), style = MaterialTheme.typography.bodySmall)
            if (visible.isEmpty()) Text(stringResource(R.string.no_results))
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.id }) { option ->
                    val checked = selected?.contains(option.id) == true
                    val interaction = if (selected != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Checkbox) { choose(option.id, it) }
                        else Modifier.clickable(enabled = enabled, role = Role.Button) { choose(option.id, true) }
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).then(interaction).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selected != null) Checkbox(checked, onCheckedChange = null, enabled = enabled)
                        if (option.photoIds.isNotEmpty()) Row(Modifier.width(72.dp).height(64.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            option.photoIds.take(2).forEach { photo -> ItemPhoto(photo, vm, Modifier.weight(1f).fillMaxHeight(), description = null) }
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(option.name, style = MaterialTheme.typography.titleSmall)
                            Text(option.context, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
fun outfitOption(outfit: Outfit, s: Snapshot): VisualOption {
    val pieces = LookRules.saved(s, outfit)
    return VisualOption(outfit.id, outfit.name, stringResource(label(outfit.occasion)) + " · " + stringResource(R.string.picker_parts, pieces.size), pieces.map { it.photoId }.filter { it.isNotBlank() })
}
@Composable
fun garmentOption(g: Garment): VisualOption = VisualOption(g.id, g.name, stringResource(label(g.category)) + " · " + stringResource(if (g.archived) R.string.archived else label(g.availability)), listOf(g.photoId).filter { it.isNotBlank() }, g.category)

fun stateToken(value: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 255) }

@Composable
fun MissingSelection(back: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.selection_missing))
        OutlinedButton(onClick = back, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
    }
}
