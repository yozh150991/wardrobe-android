package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Content-sized header: the wardrobe selector is the heading, including at 200% font scale. */
@Composable
fun WardrobeTopBar(title: Int, current: Wardrobe?, wardrobes: List<Wardrobe>, nested: Boolean, enabled: Boolean,
    back: () -> Unit, select: (String) -> Unit, create: () -> Unit, manage: () -> Unit,
    statistics: (() -> Unit)? = null, imports: (() -> Unit)? = null) {
    var wardrobesOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (nested) {
                IconButton(onClick = back, enabled = enabled) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(title), modifier = Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            } else if (current != null) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { wardrobesOpen = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(title), style = MaterialTheme.typography.labelMedium)
                            Text(current.displayName(), style = MaterialTheme.typography.titleLarge)
                        }
                        Icon(Icons.Outlined.ArrowDropDown, null)
                    }
                    DropdownMenu(wardrobesOpen, onDismissRequest = { wardrobesOpen = false }) {
                        wardrobes.forEach { w -> DropdownMenuItem(text = { Text(w.displayName()) }, onClick = { wardrobesOpen = false; select(w.id) }) }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_wardrobe)) }, onClick = { wardrobesOpen = false; create() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.manage_wardrobes)) }, onClick = { wardrobesOpen = false; manage() })
                    }
                }
                if (statistics != null || imports != null) Box {
                    IconButton(onClick = { toolsOpen = true }, enabled = enabled) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.shop_tools)) }
                    DropdownMenu(toolsOpen, onDismissRequest = { toolsOpen = false }) {
                        statistics?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.statistics_title)) }, onClick = { toolsOpen = false; action() }) }
                        imports?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.batch_title)) }, onClick = { toolsOpen = false; action() }) }
                    }
                }
            } else Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemMethods(dismiss: () -> Unit, choose: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.add_item), style = MaterialTheme.typography.titleLarge)
            listOf("camera" to R.string.camera, "gallery" to R.string.gallery, "batch" to R.string.batch_pick,
                "shop" to R.string.shop_title, "manual" to R.string.shop_manual).forEach { (method, label) ->
                TextButton(onClick = { choose(method) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(label)) }
            }
        }
    }
}
