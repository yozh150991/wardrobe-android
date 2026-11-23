@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.familywardrobe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familywardrobe.app.Garment
import com.familywardrobe.app.R
import com.familywardrobe.app.SavedItemNotice
import com.familywardrobe.app.Snapshot
import com.familywardrobe.app.data.customCategory
import com.familywardrobe.app.data.subcategoryOptions
import com.familywardrobe.app.outfits.Matching
import com.familywardrobe.app.outfits.rulePiece
import com.familywardrobe.app.photo.ZoomableItemPhoto
import com.familywardrobe.app.rules.CategoryRules
import com.familywardrobe.app.rules.ColorRules
import com.familywardrobe.app.rules.WardrobeRules

@Composable
fun Catalog(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, today: String, initialCategory: String,
    onOpen: (Garment) -> Unit, onAdd: (String) -> Unit, onFirstOutfit: () -> Unit, onCategoryConsumed: () -> Unit, onBatch: () -> Unit,
    onStatistics: () -> Unit, onAddMethods: () -> Unit, onReprocess: () -> Unit) {
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var bulkMenu by remember { mutableStateOf(false) }
    var seasonMenu by remember { mutableStateOf(false) }
    var chosenSeason by rememberSaveable { mutableStateOf("all") }
    var styleMenu by remember { mutableStateOf(false) }
    var chosenStyle by rememberSaveable { mutableStateOf("casual") }
    var query by rememberSaveable(wardrobe) { mutableStateOf("") }
    var category by rememberSaveable(wardrobe) { mutableStateOf("") }
    var subcategory by rememberSaveable(wardrobe) { mutableStateOf("") }
    var color by rememberSaveable(wardrobe) { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var season by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var collection by rememberSaveable { mutableStateOf("wardrobe") }
    var filters by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("newest") }
    var collectionMenu by remember { mutableStateOf(false) }
    var columns by rememberSaveable { mutableIntStateOf(2) }
    val largeText = LocalConfiguration.current.fontScale >= 1.4f
    val busy by vm.busy.collectAsStateWithLifecycle()
    val saved by vm.savedItem.collectAsStateWithLifecycle()
    val reprocessing by vm.photoReprocessor.state.collectAsStateWithLifecycle()
    val reprocessRunning by vm.photoReprocessor.running.collectAsStateWithLifecycle()
    LaunchedEffect(initialCategory) {
        if (initialCategory in Matching.categories) { if (category != initialCategory) subcategory = ""; category = initialCategory; collection = "wardrobe"; onCategoryConsumed() }
    }
    val owned = s.garments.filter { it.wardrobeId == wardrobe }
    val categoryTitles = Matching.categories.associateWith { stringResource(label(it)) } +
        s.customCategories.filter { it.wardrobeId == wardrobe }.associate { it.id to it.name }
    val colorTitles = Matching.colors.associateWith { stringResource(label(it)) } +
        s.customColors.filter { it.wardrobeId == wardrobe }.associate { it.id to it.name }
    fun chooseCategory(next: String) { if (category != next) subcategory = ""; category = next }
    val subcategories = subcategoryOptions(owned, wardrobe, category)
    val startingCategories = WardrobeRules.startingCategories(wardrobe, owned.map { it.rulePiece() })
    val readyCategories = startingCategories.filter { c -> owned.any { it.category == c && WardrobeRules.ready(it.rulePiece()) } }
    val nextCategory = startingCategories.firstOrNull { it !in readyCategories }
    val byId = s.garments.associateBy { it.id }
    val firstOutfitSaved = s.outfits.any { outfit ->
        outfit.wardrobeId == wardrobe && Matching.complete(s.parts.filter { it.outfitId == outfit.id }
            .mapNotNull { byId[it.garmentId] })
    }
    val newWardrobe = owned.isEmpty()
    val wearCounts = remember(s.wears) { s.wears.groupBy { it.garmentId }
        .mapValues { (_, events) -> WardrobeRules.wearDays(events.map { it.date }) } }
    val found = owned.filter { g ->
        WardrobeRules.inCollection(collection, g.rulePiece()) && CategoryRules.categoryMatches(category, g.category, g.customCategoryId) &&
            CategoryRules.subcategoryMatches(subcategory, g.subcategory) &&
            ColorRules.matchesFilter(color, g.color, g.customColorId) && (season.isEmpty() || g.season == season || g.season == "all") &&
            (!favorites || g.favorite) && g.size.contains(size, true) && g.location.contains(location, true) &&
            CategoryRules.matches(query, g.name, g.subcategory, categoryTitles[g.category],
                g.customCategoryId?.let { categoryTitles[it] }, colorTitles[g.color],
                g.customColorId?.let { colorTitles[it] }, g.description, g.location)
    }.let { rows -> when (sort) {
        "least_worn" -> rows.sortedWith(compareBy<Garment> { wearCounts[it.id] ?: 0 }.thenBy { it.name })
        "most_worn" -> rows.sortedWith(compareByDescending<Garment> { wearCounts[it.id] ?: 0 }.thenBy { it.name })
        else -> rows.sortedByDescending { it.createdAt }
    } }
    LaunchedEffect(wardrobe, found.map { it.id }) { selectedIds = selectedIds.filter { id -> found.any { it.id == id } } }
    fun applyBulk(action: String, seasonValue: String = "all", styleValue: String = "casual") {
        vm.bulk(selectedIds.toSet(), action, seasonValue, styleValue); bulkMenu = false; seasonMenu = false; styleMenu = false; selectedIds = emptyList(); selecting = false
    }
    val hasFilters = query.isNotBlank() || subcategory.isNotEmpty() || category.isNotEmpty() || color.isNotEmpty() || season.isNotEmpty() ||
        size.isNotEmpty() || location.isNotEmpty() || favorites
    fun resetFilters() { query = ""; category = ""; subcategory = ""; color = ""; season = ""; size = ""; location = ""; favorites = false }

    // UI-03: how many filters are on, so the button can say it; the sort counts once it is not the default.
    val activeFilters = listOf(subcategory.isNotBlank(), color.isNotBlank(), season.isNotBlank(), size.isNotBlank(),
        location.isNotBlank(), favorites, sort != "newest").count { it }
    val inWash = found.count { !it.archived && it.availability == "laundry" }
    var moreMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(columns = if (largeText) GridCells.Adaptive(240.dp) else GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "catalog_header", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // UX-09: «Що вдягнути сьогодні» lived here as a button; it is the first tab now.
                    if (reprocessing.entries.isNotEmpty() || reprocessing.unreadable) OutlinedButton(onClick = onReprocess,
                        enabled = !busy) { Text(stringResource(R.string.photo_reprocess_return)) }
                    saved?.takeIf { it.wardrobe == wardrobe }?.let { message ->
                        SavedItemConfirmation(message, onOpen = { byId[message.id]?.let(onOpen) },
                            onAddNext = { vm.savedItem.value = null; onAdd(message.category) },
                            onDismiss = { vm.savedItem.value = null }, enabled = !busy)
                    }
                    if (newWardrobe) {
                        Text(stringResource(R.string.start_three), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.start_three_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!firstOutfitSaved && collection == "wardrobe") {
                        FirstOutfitChecklist(readyCategories, startingCategories, enabled = !busy, compact = !newWardrobe,
                            onNext = { if (nextCategory != null) onAdd(nextCategory) else onFirstOutfit() })
                    }
                    if (!newWardrobe) {
                        // UI-03: which items, how many, and the screen's tools in one row: selecting, statistics, «⋮».
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Box {
                                    val currentTitle = when (collection) { "laundry" -> R.string.laundry; "archive" -> R.string.archive_collection; else -> R.string.collection_all }
                                    TextButton(onClick = { collectionMenu = true }, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp)) {
                                        Text(stringResource(currentTitle), style = MaterialTheme.typography.titleSmall); Icon(Icons.Outlined.ArrowDropDown, null) }
                                    DropdownMenu(collectionMenu, onDismissRequest = { collectionMenu = false }) {
                                        listOf("wardrobe" to R.string.collection_all, "laundry" to R.string.laundry, "archive" to R.string.archive_collection).forEach { (code, title) ->
                                            DropdownMenuItem(text = { Text(stringResource(title)) }, onClick = { collection = code; collectionMenu = false }) }
                                    }
                                }
                                Text(stringResource(R.string.item_count, found.size) +
                                    (if (collection == "wardrobe" && inWash > 0) " · " + stringResource(R.string.catalog_in_wash, inWash) else ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
                            }
                            ToolButton(if (selecting) Icons.Outlined.Done else Icons.Outlined.Checklist,
                                stringResource(if (selecting) R.string.bulk_finish else R.string.bulk_select), !busy, selected = selecting) {
                                selecting = !selecting; selectedIds = emptyList() }
                            ToolButton(Icons.Outlined.BarChart, stringResource(R.string.statistics_title), !busy, onClick = onStatistics)
                            Box {
                                ToolButton(Icons.Outlined.MoreVert, stringResource(R.string.catalog_more), !busy) { moreMenu = true }
                                DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.batch_title)) }, onClick = { moreMenu = false; onBatch() })
                                    if (!largeText) DropdownMenuItem(text = { Text(stringResource(if (columns == 2) R.string.grid_three else R.string.grid_two)) },
                                        leadingIcon = { Icon(if (columns == 2) Icons.Outlined.Apps else Icons.Outlined.GridView, null) },
                                        onClick = { moreMenu = false; columns = if (columns == 2) 3 else 2 })
                                }
                            }
                        }
                        if (selecting) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.bulk_count, selectedIds.size), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { selectedIds = found.map { it.id } }, enabled = !busy) { Text(stringResource(R.string.bulk_all)) }
                            Button(onClick = { bulkMenu = true }, enabled = !busy && selectedIds.isNotEmpty()) { Text(stringResource(R.string.bulk_actions)) }
                        }
                        // UI-03: the search on a white field and the filters as a filled button with the number that are on.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                                placeholder = { Text(stringResource(R.string.catalog_search_hint)) },
                                leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(16.dp),
                                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.catalog_search_clear)) } }) else null,
                                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface, disabledContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent))
                            val filterText = if (activeFilters > 0) stringResource(R.string.catalog_filters_active, activeFilters) else stringResource(R.string.filter)
                            BadgedBox(badge = { if (activeFilters > 0) Badge(containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary) { Text("$activeFilters", Modifier.clearAndSetSemantics { }) } }) {
                                FilledIconButton(onClick = { filters = true }, modifier = Modifier.size(56.dp), shape = RoundedCornerShape(16.dp)) {
                                    Icon(Icons.Outlined.Tune, filterText) }
                            }
                        }
                        // UI-03: categories in one row that scrolls sideways instead of wrapping.
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CategoryChip(category.isEmpty(), stringResource(R.string.all)) { chooseCategory("") }
                            (owned.map { it.customCategoryId ?: it.category }.distinct().take(4) + listOf(category).filter { it.isNotBlank() }).distinct().forEach { c ->
                                CategoryChip(category == c, categoryTitles[c] ?: c) { chooseCategory(c) }
                            }
                            WardrobeCategoryChoice(category.takeIf { it in Matching.categories }.orEmpty(),
                                category.takeUnless { it in Matching.categories }.orEmpty(), s, wardrobe, vm,
                                { base, custom -> chooseCategory(custom.ifBlank { base }) }, title = R.string.browse_categories,
                                allowAll = true, modifier = Modifier, showSelectedValue = false)
                        }
                        SubcategoryChoice(subcategory, subcategories, { subcategory = it })
                    }
                }
            }
            if (!newWardrobe && found.isEmpty()) item(key = "empty_collection", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Checkroom, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(if (hasFilters) R.string.no_results else when (collection) {
                        "laundry" -> R.string.empty_laundry; "archive" -> R.string.empty_archive; else -> R.string.empty_active
                    }))
                    if (hasFilters) OutlinedButton(onClick = ::resetFilters) { Text(stringResource(R.string.reset_filters)) }
                }
            }
            items(found, key = { it.id }) { g ->
                val picked = selecting && g.id in selectedIds
                Box(Modifier.border(if (picked) 3.dp else 0.dp, if (picked) MaterialTheme.colorScheme.primary else Color.Transparent, CardShape)) {
                    GarmentCard(g, vm, wearCounts[g.id] ?: 0, s.wears.any { it.garmentId == g.id && it.date == today }, largeText,
                        photoRatio = if (largeText || columns == 2) 0.9f else 0.8f, categoryTitle = s.customCategory(g)?.name,
                        selection = if (selecting) g.id in selectedIds else null, enabled = !selecting || !busy) {
                        if (selecting) selectedIds = if (g.id in selectedIds) selectedIds - g.id else selectedIds + g.id else onOpen(g)
                    }
                    if (selecting) Checkbox(checked = g.id in selectedIds, enabled = !busy,
                        onCheckedChange = null,
                        modifier = Modifier.align(Alignment.TopEnd).clearAndSetSemantics { })
                }
            }
        }
        // UI-03: «Додати річ» floats over the grid at the bottom right; while choosing items it steps aside.
        if (!selecting) Button(onClick = onAddMethods, enabled = !busy, shape = RoundedCornerShape(28.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 8.dp),
            contentPadding = PaddingValues(start = 18.dp, end = 22.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp).heightIn(min = 56.dp)) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp))
            Text(stringResource(if (newWardrobe) R.string.add_first_item else R.string.add_item), style = MaterialTheme.typography.titleSmall)
        }
    }
    if (bulkMenu) AlertDialog(onDismissRequest = { bulkMenu = false }, title = { Text(stringResource(R.string.bulk_count, selectedIds.size)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            listOf("laundry" to R.string.mark_laundry, "available" to R.string.return_available, "archive" to R.string.archive,
                "restore" to R.string.restore_from_archive).forEach { (action, title) ->
                TextButton(onClick = { applyBulk(action) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(title)) }
            }
            TextButton(onClick = { bulkMenu = false; seasonMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.bulk_season)) }
            // UX-13: items added before 0.14.47 are all «Повсякденний»; one step sets the style for many.
            TextButton(onClick = { bulkMenu = false; styleMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.bulk_style)) }
            TextButton(onClick = {
                bulkMenu = false
                if (reprocessing.entries.isNotEmpty() || reprocessing.unreadable) onReprocess()
                else vm.reprocessPhotos(wardrobe, selectedIds) { selectedIds = emptyList(); selecting = false; onReprocess() }
            }, enabled = !busy && !reprocessRunning && selectedIds.isNotEmpty() && selectedIds.size <= 500,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.photo_reprocess_title)) }
            if (selectedIds.size > 500) Text(stringResource(R.string.photo_reprocess_limit), style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = { bulkMenu = false }) { Text(stringResource(R.string.close)) } })
    if (seasonMenu) AlertDialog(onDismissRequest = { seasonMenu = false }, title = { Text(stringResource(R.string.bulk_season)) },
        text = { Choice(chosenSeason, Matching.seasons, { chosenSeason = it }, R.string.season) },
        confirmButton = { TextButton(onClick = { applyBulk("season", chosenSeason) }, enabled = !busy) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { seasonMenu = false }) { Text(stringResource(R.string.cancel)) } })
    if (styleMenu) AlertDialog(onDismissRequest = { styleMenu = false }, title = { Text(stringResource(R.string.bulk_style)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(chosenStyle, Matching.styles, { chosenStyle = it }, R.string.style)
            Text(stringResource(R.string.bulk_style_hint), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { applyBulk("style", styleValue = chosenStyle) }, enabled = !busy) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { styleMenu = false }) { Text(stringResource(R.string.cancel)) } })
    if (filters) AlertDialog(onDismissRequest = { filters = false }, title = { Text(stringResource(R.string.filter)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WardrobeCategoryChoice(category.takeIf { it in Matching.categories }.orEmpty(),
                category.takeUnless { it in Matching.categories }.orEmpty(), s, wardrobe, vm,
                { base, custom -> chooseCategory(custom.ifBlank { base }) }, allowAll = true)
            SubcategoryChoice(subcategory, subcategories, { subcategory = it })
            Choice(sort, listOf("newest", "least_worn", "most_worn"), { sort = it }, R.string.sort)
            WardrobeColorChoice(color.takeIf { it in Matching.colors }.orEmpty(),
                color.takeUnless { it in Matching.colors }.orEmpty(), s, wardrobe, vm,
                { base, custom -> color = custom.ifBlank { base } }, allowAll = true)
            Choice(season, listOf("") + Matching.seasons, { season = it }, R.string.season)
            OutlinedTextField(size, { size = it }, label = { Text(stringResource(R.string.filter_size)) }, singleLine = true)
            OutlinedTextField(location, { location = it }, label = { Text(stringResource(R.string.filter_location)) }, singleLine = true)
            Toggle(R.string.favorites_only, favorites) { favorites = it }
        } }, confirmButton = { TextButton(onClick = { filters = false }) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = { resetFilters(); sort = "newest" }) { Text(stringResource(R.string.reset_filters)) } })
}

private val PhotoShape = RoundedCornerShape(22.dp)
// BUG-58-02: the card clips to its shape, and the photo's 22 dp bottom corners cut the first letter of the last text line.
// The photo keeps PhotoShape; the card (its press ripple) has small bottom corners and the text ends above them.
private val CardShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 12.dp, bottomEnd = 12.dp)

@Composable
private fun ToolButton(icon: ImageVector, description: String, enabled: Boolean, selected: Boolean = false, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)) {
        Icon(icon, description, Modifier.size(22.dp))
    }
}

@Composable
private fun CategoryChip(selected: Boolean, title: String, click: () -> Unit) {
    // UI-03: white chips on the background; the chosen one is dark, as in the approved design.
    FilterChip(selected, onClick = click, modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(50), border = null,
        label = { Text(title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.surface))
}

@Composable
private fun FirstOutfitChecklist(ready: List<String>, required: List<String>, enabled: Boolean, compact: Boolean, onNext: () -> Unit) {
    val next = required.firstOrNull { it !in ready }
    var expanded by rememberSaveable { mutableStateOf(!compact) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.first_outfit), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.first_outfit_progress_flexible, ready.size, required.size), style = MaterialTheme.typography.bodySmall) }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                required.forEach { category ->
                    val isReady = category in ready
                    val status = stringResource(if (isReady) R.string.category_ready else R.string.category_missing)
                    Row(Modifier.semantics { stateDescription = status }, verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(if (isReady) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(18.dp))
                        Text(stringResource(label(category)))
                    }
                }
            }
            Button(onClick = onNext, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary)) {
                Text(stringResource(when (next) {
                    "top" -> R.string.add_top; "bottom" -> R.string.add_bottom; "shoes" -> R.string.add_shoes
                    else -> R.string.create_first_outfit
                }))
            }
            Text(stringResource(R.string.first_outfit_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GarmentCard(g: Garment, vm: WardrobeViewModel, wears: Int, wornToday: Boolean, largeText: Boolean, photoRatio: Float = 0.9f, categoryTitle: String? = null, selection: Boolean? = null, enabled: Boolean = true, open: () -> Unit) {
    // UI-03: the photo is the card: a rounded tile with the name and one line under it, no frame around them.
    Card(onClick = open, enabled = enabled, colors = CardDefaults.cardColors(containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent),
        modifier = Modifier.semantics { if (selection != null) { role = Role.Checkbox; toggleableState = if (selection) ToggleableState.On else ToggleableState.Off } }, shape = CardShape) {
        Box(Modifier.fillMaxWidth().clip(PhotoShape)) {
            ZoomableItemPhoto(g.photoId, vm, Modifier.fillMaxWidth().aspectRatio(photoRatio), title = g.name, enabled = enabled && selection == null, garmentColour = g.color)
            FlowRow(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (g.archived) PhotoBadge(R.string.archived)
                else if (g.availability != "available") PhotoBadge(label(g.availability))
                if (wornToday) PhotoBadge(R.string.wear)
            }
        }
        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.name, maxLines = if (largeText) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (g.favorite) Icon(Icons.Outlined.Favorite, stringResource(R.string.favorite), Modifier.size(18.dp))
            }
            Text("${categoryTitle ?: stringResource(label(g.category))} · ${stringResource(R.string.wear_count, wears)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (g.subcategory.isNotBlank()) Text(g.subcategory, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PhotoBadge(title: Int) {
    Text(stringResource(title), modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
        .padding(horizontal = 10.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SavedItemConfirmation(saved: SavedItemNotice, enabled: Boolean, onOpen: () -> Unit, onAddNext: () -> Unit, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.item_saved, saved.name), modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, stringResource(R.string.dismiss_confirmation)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAddNext, enabled = enabled) { Text(stringResource(R.string.add_next)) }
                TextButton(onClick = onOpen, enabled = enabled) { Text(stringResource(R.string.open_wardrobe)) }
            }
        }
    }
}
