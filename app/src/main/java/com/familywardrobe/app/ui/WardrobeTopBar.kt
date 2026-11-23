package com.familywardrobe.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.familywardrobe.app.R
import com.familywardrobe.app.Wardrobe

/** Content-sized header: a tab shows its large title and the wardrobe pill (UI-03); a nested screen shows back and its title. */
@Composable
fun WardrobeTopBar(title: Int, current: Wardrobe?, wardrobes: List<Wardrobe>, nested: Boolean, enabled: Boolean,
    back: () -> Unit, select: (String) -> Unit, create: () -> Unit, manage: () -> Unit,
    statistics: (() -> Unit)? = null, imports: (() -> Unit)? = null) {
    var wardrobesOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    val largeText = LocalConfiguration.current.fontScale >= 1.4f
    // UI-03: a tab's header sits on the screen's own background, as in the approved design; nested screens keep a bar.
    Surface(color = if (nested || current == null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background) {
        if (nested) Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back, enabled = enabled) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(title), modifier = Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
        } else if (current != null) {
            // UI-03: a large title of the tab and the wardrobe as a pill on the right; at a large font they stack.
            val titleText: @Composable (Modifier) -> Unit = { modifier ->
                Text(stringResource(title), modifier = modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            }
            val pill: @Composable () -> Unit = {
                Box {
                    Surface(onClick = { wardrobesOpen = true }, enabled = enabled, shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface, modifier = Modifier.heightIn(min = 48.dp).widthIn(max = if (largeText) 360.dp else 200.dp)) {
                        Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(current.displayName(), style = MaterialTheme.typography.titleSmall, maxLines = if (largeText) 3 else 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
                        }
                    }
                    DropdownMenu(wardrobesOpen, onDismissRequest = { wardrobesOpen = false }) {
                        // QA-61-06: the open wardrobe is marked, in words for TalkBack as well.
                        wardrobes.forEach { w ->
                            val active = w.id == current.id
                            DropdownMenuItem(text = { Text(w.displayName(), fontWeight = if (active) FontWeight.Bold else null) },
                                trailingIcon = if (active) { { Icon(Icons.Outlined.Check, null) } } else null,
                                modifier = Modifier.semantics { selected = active },
                                onClick = { wardrobesOpen = false; select(w.id) })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_wardrobe)) }, onClick = { wardrobesOpen = false; create() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.manage_wardrobes)) }, onClick = { wardrobesOpen = false; manage() })
                    }
                }
            }
            val tools: @Composable () -> Unit = {
                if (statistics != null || imports != null) Box {
                    IconButton(onClick = { toolsOpen = true }, enabled = enabled) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.shop_tools)) }
                    DropdownMenu(toolsOpen, onDismissRequest = { toolsOpen = false }) {
                        statistics?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.statistics_title)) }, onClick = { toolsOpen = false; action() }) }
                        imports?.let { action -> DropdownMenuItem(text = { Text(stringResource(R.string.batch_title)) }, onClick = { toolsOpen = false; action() }) }
                    }
                }
            }
            if (largeText) Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                titleText(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { pill() }; tools() }
            } else Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                titleText(Modifier.weight(1f))
                pill(); tools()
            }
        } else Text(stringResource(title), modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.titleLarge)
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
                TextButton(onClick = { choose(method) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add-method-$method")) { Text(stringResource(label)) }
            }
        }
    }
}
