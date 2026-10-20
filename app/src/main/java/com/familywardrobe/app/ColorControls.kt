package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WardrobeColorChoice(base: String, customId: String, s: Snapshot, wardrobe: String, vm: WardrobeViewModel,
    change: (String, String) -> Unit, title: Int = R.string.color, allowAll: Boolean = false,
    enabled: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth(), showSelectedValue: Boolean = true) {
    var creating by rememberSaveable(wardrobe) { mutableStateOf(false) }
    var proposedName by rememberSaveable(wardrobe) { mutableStateOf("") }
    var proposedBase by rememberSaveable(wardrobe) { mutableStateOf("") }
    var created by remember(wardrobe) { mutableStateOf<CustomColor?>(null) }
    val colors = (s.customColors.filter { it.wardrobeId == wardrobe } + listOfNotNull(created)).distinctBy { it.id }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val reserved = Matching.colors.map { stringResource(label(it)) } + Matching.colors
    ColorChoice(customId.ifBlank { base }, { selected ->
        val custom = colors.find { it.id == selected }
        change(custom?.baseColor ?: selected, custom?.id.orEmpty())
    }, enabled = enabled && !busy, title = title, allowAll = allowAll, modifier = modifier,
        showSelectedValue = showSelectedValue, customColors = colors, onAdd = { query ->
            proposedName = query.take(60); proposedBase = base.takeIf { it in Matching.colors }.orEmpty(); creating = true
        })
    if (creating) {
        val valid = CategoryRules.validName(proposedName)
        val duplicate = colors.any { it.nameKey == CategoryRules.key(proposedName) } ||
            reserved.any { CategoryRules.key(it) == CategoryRules.key(proposedName) }
        AlertDialog(onDismissRequest = { if (!busy) creating = false }, title = { Text(stringResource(R.string.color_add)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(proposedName, { proposedName = TextLimits.input(it, 60) }, singleLine = true, enabled = !busy,
                    label = { Text(stringResource(R.string.color_custom_name)) }, modifier = Modifier.fillMaxWidth(),
                    isError = proposedName.isNotEmpty() && (!valid || duplicate), supportingText = {
                        if (duplicate) Text(stringResource(R.string.color_duplicate))
                        else if (proposedName.isNotEmpty() && !valid) Text(stringResource(R.string.color_name_invalid))
                    })
                ColorChoice(proposedBase, { proposedBase = it }, title = R.string.color_base, enabled = !busy)
                Text(stringResource(R.string.color_base_hint), style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(enabled = !busy && valid && !duplicate && proposedBase in Matching.colors,
                onClick = { vm.addCustomColor(wardrobe, proposedName, proposedBase) { color ->
                    created = color; change(color.baseColor, color.id); creating = false
                } }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { creating = false }, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    }
}


@Composable
fun ColorChoice(value: String, change: (String) -> Unit, enabled: Boolean = true,
    title: Int = R.string.color, allowAll: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(),
    showSelectedValue: Boolean = true, customColors: List<CustomColor> = emptyList(),
    onAdd: ((String) -> Unit)? = null) {
    var choosing by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val options = (if (allowAll) listOf("") else emptyList()) + Matching.colors + customColors.map { it.id }
    val titles = options.associateWith { code -> customColors.find { it.id == code }?.name
        ?: stringResource(if (code.isBlank()) R.string.all else label(code)) }
    val selectedTitle = titles[value] ?: stringResource(R.string.choose_color)
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
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text(stringResource(title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = TextLimits.input(it, 100) }, label = { Text(stringResource(R.string.search_colors)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val visible = options.filter { CategoryRules.matches(query, titles.getValue(it)) }
            if (visible.isEmpty()) Text(stringResource(R.string.no_results))
            LazyColumn(Modifier.heightIn(max = 340.dp).selectableGroup()) {
                items(visible, key = { it.ifBlank { "all" } }) { code ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(code == value, role = Role.RadioButton, onClick = { change(code); choosing = false }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(code == value, onClick = null)
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(titles.getValue(code))
                            customColors.find { it.id == code }?.let { custom ->
                                Text(stringResource(label(custom.baseColor)), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }, dismissButton = { if (onAdd != null) TextButton(onClick = { choosing = false; onAdd(query) }, enabled = enabled) {
        Text(stringResource(R.string.color_add))
    } }, confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.close)) } })
}
