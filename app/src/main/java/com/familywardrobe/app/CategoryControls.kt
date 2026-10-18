package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WardrobeCategoryChoice(base: String, customId: String, s: Snapshot, wardrobe: String, vm: WardrobeViewModel,
    change: (String, String) -> Unit, title: Int = R.string.category, allowAll: Boolean = false,
    enabled: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth(), showSelectedValue: Boolean = true) {
    var creating by rememberSaveable(wardrobe) { mutableStateOf(false) }
    var proposedName by rememberSaveable(wardrobe) { mutableStateOf("") }
    var proposedBase by rememberSaveable(wardrobe) { mutableStateOf("") }
    var created by remember(wardrobe) { mutableStateOf<CustomCategory?>(null) }
    val categories = (s.customCategories.filter { it.wardrobeId == wardrobe } + listOfNotNull(created)).distinctBy { it.id }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val reserved = Matching.categories.map { stringResource(label(it)) }
    CategoryChoice(customId.ifBlank { base }, { selected ->
        val custom = categories.find { it.id == selected }
        change(custom?.baseCategory ?: selected, custom?.id.orEmpty())
    }, enabled = enabled && !busy, title = title, allowAll = allowAll, modifier = modifier,
        showSelectedValue = showSelectedValue, customCategories = categories, onAdd = { query ->
            proposedName = query.take(60); proposedBase = base.takeIf { it in Matching.categories }.orEmpty(); creating = true
        })
    if (creating) {
        val valid = CategoryRules.validName(proposedName)
        val duplicate = categories.any { it.nameKey == CategoryRules.key(proposedName) } ||
            reserved.any { CategoryRules.key(it) == CategoryRules.key(proposedName) }
        AlertDialog(onDismissRequest = { if (!busy) creating = false }, title = { Text(stringResource(R.string.category_add)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(proposedName, { proposedName = it.take(60) }, singleLine = true, enabled = !busy,
                    label = { Text(stringResource(R.string.category_custom_name)) }, modifier = Modifier.fillMaxWidth(),
                    isError = proposedName.isNotEmpty() && (!valid || duplicate), supportingText = {
                        if (duplicate) Text(stringResource(R.string.category_duplicate))
                        else if (proposedName.isNotEmpty() && !valid) Text(stringResource(R.string.category_name_invalid))
                    })
                CategoryChoice(proposedBase, { proposedBase = it }, title = R.string.category_base, enabled = !busy)
                Text(stringResource(R.string.category_base_hint), style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(enabled = !busy && valid && !duplicate && proposedBase in Matching.categories,
                onClick = { vm.addCustomCategory(wardrobe, proposedName, proposedBase) { category ->
                    created = category; change(category.baseCategory, category.id); creating = false
                } }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { creating = false }, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    }
}

/** Empty selection means all; custom values are displayed literally, never as resource keys. */
@Composable
fun SubcategoryChoice(value: String, options: List<String>, change: (String) -> Unit,
    enabled: Boolean = true, allowAll: Boolean = true, title: Int = R.string.filter_subcategory) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedButton(onClick = { query = ""; choosing = true }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if (!allowAll && value.isBlank()) stringResource(title)
            else stringResource(title) + ": " + value.ifBlank { stringResource(R.string.all) })
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text(stringResource(R.string.filter_subcategory)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it.take(100) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_subcategories)) })
            val visible = options.filter { CategoryRules.matches(query, it) }
            if (options.isEmpty()) Text(stringResource(R.string.subcategory_empty))
            else if (visible.isEmpty()) Text(stringResource(R.string.no_results))
            LazyColumn(Modifier.heightIn(max = 340.dp).selectableGroup()) {
                if (allowAll) item(key = "all") {
                    SubcategoryRow(stringResource(R.string.all), value.isEmpty()) { change(""); choosing = false }
                }
                items(visible, key = { "value:" + CategoryRules.key(it) }) { option ->
                    SubcategoryRow(option, CategoryRules.key(value) == CategoryRules.key(option)) { change(option); choosing = false }
                }
            }
        } }, confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun SubcategoryRow(name: String, selected: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = choose),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null); Text(name, Modifier.weight(1f))
    }
}
