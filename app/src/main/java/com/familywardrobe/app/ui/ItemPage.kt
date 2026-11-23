@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.familywardrobe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familywardrobe.app.Garment
import com.familywardrobe.app.R
import com.familywardrobe.app.Snapshot
import com.familywardrobe.app.data.customCategory
import com.familywardrobe.app.data.customColor
import com.familywardrobe.app.photo.PhotoExportDialog
import com.familywardrobe.app.photo.ZoomableItemPhoto
import com.familywardrobe.app.rules.ItemRemovalRules
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** UI-04: the item page by the approved design — the photo on top, three numbers, everyday actions in a bar at the bottom. */
@Composable
fun Detail(g: Garment, s: Snapshot, vm: WardrobeViewModel, today: String, onBack: () -> Unit, onEdit: () -> Unit, onMatch: () -> Unit, removed: () -> Unit = {}) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }
    // UX-15: delete for good only what nothing was built on; the rest is archived.
    var deleting by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val outfitCount = s.parts.count { it.garmentId == g.id }
    val deleteBlocker = ItemRemovalRules.blocker(s.wears.count { it.garmentId == g.id }, outfitCount, s.wishes.any { it.ownedId == g.id })
    // WEAR-01: wear noticed later (another day), and a day's wear recorded by mistake.
    var wearOtherDay by rememberSaveable { mutableStateOf(false) }
    var removingWear by rememberSaveable { mutableStateOf("") }
    val dates = s.wears.filter { it.garmentId == g.id }.map { it.date }.distinct().sortedDescending()
    val outfitsWithItem = s.parts.filter { it.garmentId == g.id }.map { it.outfitId }.distinct().size
    val daysSince = dates.firstOrNull()?.let { last ->
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(last), LocalDate.parse(today)).coerceAtLeast(0) }.getOrNull() }
    val compact = LocalConfiguration.current.screenHeightDp < 700
    val largeText = LocalConfiguration.current.fontScale >= 1.3f
    val categoryTitle = s.customCategory(g)?.name ?: stringResource(label(g.category))

    // BUG-61-03: once the page is scrolled, a band of the page colour sits under the status bar, so text never runs
    // under the clock (the app is edge-to-edge and this page has no top bar).
    val scroll = rememberScrollState()
    val statusBarPx = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val pageColour = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).drawWithContent {
            drawContent()
            if (scroll.value > 0 && statusBarPx > 0f) drawRect(pageColour, size = Size(size.width, statusBarPx))
        }.verticalScroll(scroll)) {
            // UI-04: the photo is the top of the page, with back, share, edit and «⋮» over it.
            // BUG-61-01: the app draws under the status bar, so the backdrop goes there but the picture and the buttons
            // start below it; the picture also clears the button row, so nothing covers the garment.
            val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(Modifier.fillMaxWidth().height((if (compact) 252.dp else 320.dp) + statusBar + PHOTO_BUTTON_BAND)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))) {
                ZoomableItemPhoto(g.photoId, vm, Modifier.fillMaxSize(), title = g.name, garmentColour = g.color,
                    inset = PaddingValues(top = statusBar + PHOTO_BUTTON_BAND, bottom = 12.dp))
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), enabled = true, onClick = onBack)
                    Spacer(Modifier.weight(1f))
                    if (g.photoId.isNotBlank()) {
                        RoundButton(Icons.Outlined.Share, stringResource(R.string.photo_export_open), !busy) { sharing = true }
                        Spacer(Modifier.width(8.dp))
                    }
                    RoundButton(Icons.Outlined.Edit, stringResource(R.string.edit), !busy, onClick = onEdit)
                    Spacer(Modifier.width(8.dp))
                    Box {
                        RoundButton(Icons.Outlined.MoreVert, stringResource(R.string.item_more), !busy) { moreMenu = true }
                        // Rare decisions live here: out of the suggestions, into the archive, delete.
                        DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(if (g.suggestionExcluded) R.string.rules_include else R.string.rules_exclude)) },
                                onClick = { moreMenu = false; vm.excludeFromRecommendations(g.id, !g.suggestionExcluded) })
                            DropdownMenuItem(text = { Text(stringResource(if (g.archived) R.string.restore_item else R.string.archive)) },
                                onClick = { moreMenu = false; if (g.archived) vm.changeAvailability(g, archived = false) else confirmArchive = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.item_delete)) }, onClick = { moreMenu = false; deleting = true })
                        }
                    }
                }
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(g.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() })
                        Text(listOfNotNull(g.subcategory.takeIf { it.isNotBlank() }, categoryTitle,
                            g.size.takeIf { it.isNotBlank() }?.let { stringResource(R.string.item_size_short, it) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconToggleButton(g.favorite, { vm.update(g) { it.copy(favorite = !it.favorite) } }, enabled = !busy) {
                        Icon(if (g.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, stringResource(R.string.favorite))
                    }
                }
                // UX-10: plain labels (they are not buttons). BUG-61-02: «Ношу рідко» is a switch, so it lives with the
                // actions below, not among the labels.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemLabel(stringResource(if (g.archived) R.string.archived else label(g.availability)),
                        status = !g.archived && g.availability == "available")
                    ItemLabel(stringResource(label(g.style)))
                    ItemLabel(stringResource(label(g.season)))
                    ItemLabel(s.customColor(g)?.name ?: stringResource(label(g.color)))
                }
                if (g.suggestionExcluded) Text(stringResource(R.string.rules_manual_allowed), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // UI-04: three numbers at a glance — wear days, days since, outfits with the item.
                val stats = listOf(
                    dates.size.toString() to stringResource(R.string.item_stat_wears),
                    (daysSince?.toString() ?: "—") to stringResource(R.string.item_stat_days),
                    outfitsWithItem.toString() to stringResource(R.string.item_stat_outfits))
                if (largeText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { stats.forEach { (value, title) -> StatTile(value, title, Modifier.fillMaxWidth()) } }
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { stats.forEach { (value, title) -> StatTile(value, title, Modifier.weight(1f)) } }
                // UX-10: the everyday actions that are not in the bottom bar, as a short list.
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        ActionRow(stringResource(R.string.wear_other_day), !busy) { wearOtherDay = true }
                        if (!g.archived && g.availability in setOf("available", "laundry")) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ActionRow(stringResource(if (g.availability == "laundry") R.string.return_after_wash else R.string.mark_laundry), !busy) {
                                vm.changeAvailability(g, availability = if (g.availability == "laundry") "available" else "laundry") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        // BUG-61-02: a switch row like the others; UX-06: when it is on, it says what it does.
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(g.rarelyWorn, enabled = !busy, role = Role.Switch,
                            onValueChange = { vm.update(g) { it.copy(rarelyWorn = !it.rarelyWorn) } }).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(R.string.rare), style = MaterialTheme.typography.bodyLarge)
                                if (g.rarelyWorn) Text(stringResource(R.string.rare_hint), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = g.rarelyWorn, onCheckedChange = null, enabled = !busy)
                        }
                    }
                }
                s.wishes.find { it.ownedId == g.id }?.let { PurchaseHistory(it, vm) }
                Text(stringResource(R.string.item_about), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Info(R.string.size, g.size.ifBlank { "—" }); Info(R.string.color, s.customColor(g)?.name ?: stringResource(label(g.color)))
                Info(R.string.location, g.location.ifBlank { "—" }); Info(R.string.season, stringResource(label(g.season)))
                Info(R.string.style, stringResource(label(g.style)))
                if (g.description.isNotBlank()) Info(R.string.description, g.description)
                Info(R.string.last_worn, dates.firstOrNull()?.let { dateLabel(it) }
                    ?: stringResource(R.string.never_worn))
                if (dates.isNotEmpty()) {
                    Text(stringResource(R.string.wear_history_title), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        dates.take(8).forEach { day ->
                            val remove = stringResource(R.string.wear_remove_day, dateLabel(day))
                            InputChip(selected = false, onClick = { removingWear = day }, enabled = !busy, label = { Text(dateLabel(day)) },
                                shape = RoundedCornerShape(50),
                                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.semantics { contentDescription = remove })
                        }
                    }
                }
            }
        }
        // UI-04: «Вдягнув сьогодні» and «Підібрати комплект» stay at the bottom while the page scrolls.
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val wearButton: @Composable (Modifier) -> Unit = { modifier ->
                    // DEV-05 review: 12 dp side padding, so «Вдягаю сьогодні» fits on one line beside «Підібрати образ».
                    OutlinedButton(onClick = { vm.wear(g) }, enabled = !busy, modifier = modifier.heightIn(min = 54.dp),
                        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(stringResource(if (today in dates) R.string.undo_wear else R.string.wear), fontWeight = FontWeight.Bold)
                    }
                }
                val matchButton: @Composable (Modifier) -> Unit = { modifier ->
                    Button(onClick = onMatch, enabled = !busy && !g.archived && g.availability == "available", modifier = modifier.heightIn(min = 54.dp),
                        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.match), fontWeight = FontWeight.Bold)
                    }
                }
                if (largeText) Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    matchButton(Modifier.fillMaxWidth()); wearButton(Modifier.fillMaxWidth())
                } else Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    wearButton(Modifier.weight(1f)); matchButton(Modifier.weight(1.3f))
                }
            }
        }
    }
    if (sharing) PhotoExportDialog(g.photoId, g.name, vm.photos, onDismiss = { sharing = false })
    if (deleting) {
        if (deleteBlocker == null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.item_delete_title, g.name)) },
            text = { Text(stringResource(R.string.item_delete_hint)) },
            confirmButton = { TextButton(onClick = { deleting = false; vm.removeItem(g.id, removed) }, enabled = !busy) { Text(stringResource(R.string.item_delete_confirm)) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
        else AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.item_delete_blocked_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (deleteBlocker) {
                    ItemRemovalRules.Blocker.WORN -> stringResource(R.string.item_delete_worn, dates.size)
                    ItemRemovalRules.Blocker.OUTFITS -> stringResource(R.string.item_delete_outfits, outfitCount)
                    else -> stringResource(R.string.item_delete_purchase)
                })
                Text(stringResource(if (g.archived) R.string.item_delete_archived else R.string.item_delete_archive_instead))
            } },
            confirmButton = { if (g.archived) TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.close)) }
                else TextButton(onClick = { deleting = false; vm.changeAvailability(g, archived = true) }, enabled = !busy) { Text(stringResource(R.string.archive)) } },
            dismissButton = { if (!g.archived) TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (wearOtherDay) DateChoiceDialog(today, { wearOtherDay = false }, latest = today) { day ->
        wearOtherDay = false; vm.wearItems(g.wardrobeId, listOf(g), day)
    }
    if (removingWear.isNotEmpty()) AlertDialog(onDismissRequest = { removingWear = "" },
        text = { Text(stringResource(R.string.wear_remove_day, dateLabel(removingWear))) },
        confirmButton = { TextButton(onClick = { val day = removingWear; removingWear = ""; if (day in dates) vm.wear(g, day) }, enabled = !busy) {
            Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { removingWear = "" }) { Text(stringResource(R.string.cancel)) } })
    if (confirmArchive) AlertDialog(onDismissRequest = { confirmArchive = false }, title = { Text(stringResource(R.string.archive_title)) },
        text = { Text(stringResource(R.string.archive_hint)) }, confirmButton = { TextButton(onClick = {
            vm.changeAvailability(g, archived = true); confirmArchive = false
        }) { Text(stringResource(R.string.archive)) } }, dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    // LOOK-04: a thin outline and a light shadow keep the white circle visible on a white photo background.
    FilledIconButton(onClick = onClick, enabled = enabled, shape = CircleShape,
        modifier = Modifier.size(48.dp).shadow(2.dp, CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(icon, description, Modifier.size(22.dp))
    }
}

@Composable
private fun ItemLabel(text: String, status: Boolean = false) {
    Surface(shape = RoundedCornerShape(50), color = if (status) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (status) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (status) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = if (status) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

@Composable
private fun StatTile(value: String, title: String, modifier: Modifier) {
    Surface(modifier.clearAndSetSemantics { contentDescription = "$value $title" }, shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionRow(text: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** BUG-61-01: the band at the top of the photo that holds back, share, edit and «⋮» (48 dp buttons, 16 dp padding). */
private val PHOTO_BUTTON_BAND = 64.dp
