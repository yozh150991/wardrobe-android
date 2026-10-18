package com.familywardrobe.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppearanceScreen(vm: AppearanceViewModel, onBrowse: () -> Unit) {
    val stored by vm.appearance.collectAsStateWithLifecycle()
    val failed by vm.appearanceSaveFailed.collectAsStateWithLifecycle()
    val settings = stored ?: AppearanceSettings()
    val dark = settings.mode.usesDark(isSystemInDarkTheme())
    var modeMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.appearance_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.appearance_mode), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(settings.mode.title))
                }
                Icon(Icons.Outlined.ArrowDropDown, null)
            }
            DropdownMenu(modeMenu, onDismissRequest = { modeMenu = false }) {
                AppearanceMode.entries.forEach { mode ->
                    DropdownMenuItem(text = { Text(stringResource(mode.title)) }, onClick = {
                        vm.setAppearanceMode(mode); modeMenu = false
                    }, leadingIcon = { RadioButton(selected = mode == settings.mode, onClick = null) })
                }
            }
        }
        Text(stringResource(R.string.appearance_palette), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WardrobePalette.entries.forEach { palette ->
                if (palette == WardrobePalette.BLUE) {
                    Text(stringResource(R.string.appearance_classic), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp))
                }
                key(palette) {
                    val selected = settings.palette == palette
                    val tokens = paletteTokens(palette, dark)
                    Surface(
                        modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = { vm.setPalette(palette) }),
                        shape = MaterialTheme.shapes.medium,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(Modifier.heightIn(min = 72.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RadioButton(selected = selected, onClick = null)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(palette.title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(palette.description), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PaletteSwatches(tokens)
                            }
                        }
                    }
                }
            }
        }
        if (failed) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.appearance_save_failed), color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                OutlinedButton(onClick = vm::retryAppearanceSave) { Text(stringResource(R.string.retry)) }
            }
        }
        Button(onClick = onBrowse, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.appearance_browse))
        }
    }
}

@Composable
private fun PaletteSwatches(tokens: PaletteTokens) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(Modifier.size(20.dp), shape = CircleShape, color = tokens.canvas,
            border = BorderStroke(1.dp, tokens.outline)) { }
        Box(Modifier.size(20.dp).clip(CircleShape).background(tokens.primary))
        Box(Modifier.size(20.dp).clip(CircleShape).background(tokens.secondary))
        Surface(Modifier.size(20.dp), shape = CircleShape, color = tokens.details,
            border = BorderStroke(1.dp, tokens.outline)) { }
    }
}
