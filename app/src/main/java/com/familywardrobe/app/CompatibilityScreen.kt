@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class AdviceRequest(val snapshot: Snapshot, val wish: Wish, val occasion: String,
    val weather: String, val date: String, val hints: PurchaseHints, val refresh: Int)
private data class AdviceResult(val request: AdviceRequest, val assessment: PurchaseAssessment?, val failed: Boolean = false)

@Composable
fun CompatibilityScreen(wish: Wish, s: Snapshot, vm: WardrobeViewModel, open: (Garment) -> Unit,
    saved: (String) -> Unit, bought: (PurchaseHints) -> Unit, defer: () -> Unit) {
    var occasion by rememberSaveable(wish.id) { mutableStateOf("everyday") }
    var weather by rememberSaveable(wish.id) { mutableStateOf("any_weather") }
    var showAll by rememberSaveable(wish.id) { mutableStateOf(false) }
    var similarTab by rememberSaveable(wish.id) { mutableStateOf(false) }
    var showAllSimilar by rememberSaveable(wish.id) { mutableStateOf(false) }
    var moreFields by rememberSaveable(wish.id) { mutableStateOf(false) }
    var subcategory by rememberSaveable(wish.id) { mutableStateOf("") }
    var season by rememberSaveable(wish.id) { mutableStateOf("") }
    var style by rememberSaveable(wish.id) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val hints = PurchaseHints(subcategory, season, style)
    val date = LocalDate.now().toString()
    val request = AdviceRequest(s, wish, occasion, weather, date, hints, refresh)
    val response by produceState<AdviceResult?>(null, request) {
        value = null
        // Typing optional traits does not run bounded outfit search on the UI thread.
        delay(180)
        try { value = AdviceResult(request, withContext(Dispatchers.Default) {
            PurchaseCompatibility.analyze(s, wish, occasion, weather, date, hints)
        }) } catch (e: CancellationException) { throw e }
        catch (_: Exception) { value = AdviceResult(request, null, failed = true) }
    }
    val current = response?.takeIf { it.request == request }
    val report = current?.assessment
    val name = stringResource(R.string.compatibility_saved_name, wish.name).take(80)
    if (wish.kind != "product" || wish.ownedId.isNotBlank()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.advice_product_required))
            TextButton(onClick = defer, enabled = !busy) { Text(stringResource(R.string.back)) }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "candidate") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemPhoto(wish.photoId, vm, Modifier.size(80.dp), description = wish.name, fit = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(wish.name, style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.advice_price, if (wish.priceMinor != null) moneyLabel(wish.priceMinor, wish.currency, locale)
                            else stringResource(R.string.not_specified)))
                        if (wish.budgetMinor != null) Text(stringResource(R.string.wish_budget_value, moneyLabel(wish.budgetMinor, wish.budgetCurrency, locale)))
                    }
                }
                when (PurchaseAdviceRules.budgetStatus(wish.priceMinor, wish.currency, wish.budgetMinor, wish.budgetCurrency)) {
                    "above" -> Text(stringResource(R.string.advice_above_budget,
                        moneyLabel(wish.priceMinor!! - wish.budgetMinor!!, wish.currency, locale)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    "within" -> Text(stringResource(R.string.advice_within_budget))
                    "different_currency" -> Text(stringResource(R.string.advice_different_currency), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.advice_scope), style = MaterialTheme.typography.bodySmall)
                if (season.isBlank()) Text(stringResource(R.string.advice_unknown_season), style = MaterialTheme.typography.bodySmall)
                if (wish.color.isBlank() || wish.color == "other") Text(stringResource(R.string.advice_unknown_color), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { moreFields = !moreFields }, enabled = !busy) { Text(stringResource(R.string.advice_refine)) }
                if (moreFields) {
                    Text(stringResource(R.string.advice_traits_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(subcategory, { subcategory = it.take(60) }, label = { Text(stringResource(R.string.item_subcategory)) },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    Choice(season, listOf("") + Matching.seasons, { season = it }, R.string.season, enabled = !busy, emptyLabel = R.string.not_specified)
                    Choice(style, listOf("") + Matching.styles, { style = it }, R.string.style, enabled = !busy, emptyLabel = R.string.not_specified)
                }
                Choice(occasion, LookRules.OCCASIONS, { occasion = it }, R.string.look_occasion, enabled = !busy)
                Choice(weather, LookRules.WEATHERS, { weather = it }, R.string.look_weather, enabled = !busy)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = defer, enabled = !busy) { Text(stringResource(R.string.advice_defer)) }
                    OutlinedButton(onClick = { bought(hints) }, enabled = !busy) { Text(stringResource(R.string.i_bought_it)) }
                }
            }
        }
        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (report != null) {
                    Text(stringResource(R.string.advice_summary, report.previews.size, report.previews.count { it.saved }, report.partnerCount),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.advice_limited), style = MaterialTheme.typography.bodySmall)
                    if (report.previews.isNotEmpty()) Text(stringResource(R.string.advice_gap_count, report.withoutReplacement), style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !similarTab, onClick = { similarTab = false }, label = { Text(stringResource(R.string.advice_outfits)) })
                    FilterChip(selected = similarTab, onClick = { similarTab = true }, label = { Text(stringResource(R.string.advice_similar)) })
                }
                if (current == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.advice_loading)) }
                if (current?.failed == true) {
                    Text(stringResource(R.string.advice_failed))
                    TextButton(onClick = { refresh++ }, enabled = !busy) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        if (similarTab && report != null) {
            item(key = "similar-hint") {
                Text(stringResource(if (report.similar.isEmpty()) R.string.advice_no_matches else R.string.advice_similarity_hint), style = MaterialTheme.typography.bodySmall)
            }
            items(if (showAllSimilar) report.similar else report.similar.take(8), key = { "similar:${it.garment.id}" }) { match ->
                Card(onClick = { open(match.garment) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ItemPhoto(match.garment.photoId, vm, Modifier.size(72.dp), description = match.garment.name, fit = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(match.garment.name, style = MaterialTheme.typography.titleMedium)
                            match.reasons.forEach { reason -> Text(stringResource(adviceReason(reason)), style = MaterialTheme.typography.bodySmall) }
                            Text(stringResource(if (match.garment.archived) R.string.archived else label(match.garment.availability)), color = MaterialTheme.colorScheme.primary)
                            if (!match.eligible && !match.garment.archived && match.garment.availability == "available")
                                Text(stringResource(R.string.advice_not_in_suggestions), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (!showAllSimilar && report.similar.size > 8) item(key = "more-similar") {
                OutlinedButton(onClick = { showAllSimilar = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.advice_more_similar, report.similar.size)) }
            }
        } else if (report != null) {
            if (report.previews.isEmpty()) item(key = "empty") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (report.candidateSeasonConflict) R.string.advice_season_conflict else R.string.advice_no_outfits))
                    report.missingLayouts.forEach { missing ->
                        val labels = missing.map { stringResource(label(it)) }
                        Text(stringResource(R.string.advice_missing, labels.joinToString(" + ")))
                    }
                    if (report.unavailableCount > 0) Text(stringResource(R.string.advice_unavailable_count, report.unavailableCount))
                    if (report.filteredCount > 0) Text(stringResource(R.string.advice_filtered_count, report.filteredCount))
                }
            }
            itemsIndexed(if (showAll) report.previews else report.previews.take(6), key = { _, row -> DailyRules.signature(row.pieces.map { it.key }) }) { index, preview ->
                AdvicePreviewCard(index, preview, wish, s, vm, busy, name, occasion, weather, open, saved)
            }
            if (!showAll && report.previews.size > 6) item(key = "more-outfits") {
                OutlinedButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.compatibility_more)) }
            }
        }
    }
}

private fun adviceReason(reason: String): Int = when (reason) {
    "source" -> R.string.advice_same_source
    "subcategory" -> R.string.advice_same_subcategory
    "color" -> R.string.advice_same_color
    "season" -> R.string.advice_compatible_season
    "style" -> R.string.advice_same_style
    else -> R.string.advice_same_category
}

@Composable
private fun AdvicePreviewCard(index: Int, preview: PurchasePreview, wish: Wish, s: Snapshot,
    vm: WardrobeViewModel, busy: Boolean, plannedName: String, occasion: String, weather: String,
    open: (Garment) -> Unit, saved: (String) -> Unit) {
    val signature = DailyRules.signature(preview.pieces.map { it.key })
    var selectedAlternative by rememberSaveable(signature) { mutableStateOf("") }
    var choosing by remember { mutableStateOf(false) }
    val available = preview.replacements.filter { WardrobeRules.ready(it.rulePiece()) }
    val alternative = available.find { it.id == selectedAlternative }
    val pieces = if (alternative == null) preview.pieces else preview.pieces.map { if (it.wishId == wish.id) LookRules.owned(alternative) else it }
    val savedSignature = DailyRules.signature(pieces.map { it.ownedId ?: it.key })
    val existing = s.outfits.find { it.wardrobeId == wish.wardrobeId && it.signature == savedSignature }
    val ownName = stringResource(R.string.advice_owned_name, alternative?.name.orEmpty()).take(80)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.daily_option, index + 1), style = MaterialTheme.typography.titleLarge)
            if (existing != null) Text(stringResource(R.string.compatibility_already_saved))
            Text(stringResource(when { available.isNotEmpty() -> R.string.advice_replacement_ready
                preview.replacements.isNotEmpty() -> R.string.advice_replacement_unavailable
                else -> R.string.advice_replacement_missing }), style = MaterialTheme.typography.bodyMedium)
            if (preview.replacements.isNotEmpty()) Text(stringResource(R.string.advice_replacement_scope), style = MaterialTheme.typography.bodySmall)
            if (available.isNotEmpty()) Box {
                OutlinedButton(onClick = { choosing = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (alternative == null) stringResource(R.string.advice_try_owned) else stringResource(R.string.advice_using_owned, alternative.name))
                }
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.advice_use_candidate, wish.name)) }, onClick = { selectedAlternative = ""; choosing = false })
                    available.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { selectedAlternative = item.id; choosing = false }) }
                }
            } else if (preview.replacements.isNotEmpty()) {
                preview.replacements.take(3).forEach { item ->
                    TextButton(onClick = { open(item) }, enabled = !busy) {
                        Text(item.name + " · " + stringResource(if (item.archived) R.string.archived else label(item.availability)))
                    }
                }
            }
            OutfitComposition(pieces, vm, enabled = !busy) { piece -> piece.ownedId?.let { id -> s.garments.find { it.id == id }?.let(open) } }
            Text(stringResource(if (alternative == null) R.string.compatibility_owned_hint else R.string.advice_owned_only), style = MaterialTheme.typography.bodySmall)
            if (existing != null) OutlinedButton(onClick = { saved(existing.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.advice_open_saved))
            } else OutlinedButton(onClick = {
                vm.saveLook(wish.wardrobeId, pieces, if (alternative == null) plannedName else ownName, occasion, weather,
                    if (alternative == null) "mixed" else "owned", "", saved)
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (alternative == null) R.string.compatibility_save else R.string.advice_save_owned))
            }
        }
    }
}
