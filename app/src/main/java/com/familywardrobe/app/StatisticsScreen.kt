package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ItemUsage(val garment: Garment, val days: Int, val last: String?, val forgotten: Boolean)
object WardrobeStats {
    fun usage(s: Snapshot, wardrobe: String, today: String, includeArchive: Boolean): List<ItemUsage> {
        val actual = s.wears.filter { it.date <= today }.groupBy { it.garmentId }
        return s.garments.filter { it.wardrobeId == wardrobe && (includeArchive || !it.archived) }.map { g ->
            val dates = actual[g.id].orEmpty().map { it.date }.distinct()
            val last = dates.maxOrNull()
            val since = last?.let(LocalDate::parse) ?: Instant.ofEpochMilli(g.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            ItemUsage(g, dates.size, last, ChronoUnit.DAYS.between(since, LocalDate.parse(today)) >= 90)
        }
    }
}

@Composable
fun StatisticsScreen(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, today: String, open: (Garment) -> Unit, build: (Garment) -> Unit) {
    var includeArchive by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf("most") }
    val locale = LocalConfiguration.current.locales[0]
    val busy by vm.busy.collectAsStateWithLifecycle()
    val usage = remember(s.garments, s.wears, wardrobe, today, includeArchive) { WardrobeStats.usage(s, wardrobe, today, includeArchive) }
    val rows = when (group) {
        "forgotten" -> usage.filter { it.forgotten }.sortedWith(compareBy<ItemUsage> { it.last.orEmpty() }.thenBy { it.garment.name })
        "never" -> usage.filter { it.days == 0 }.sortedBy { it.garment.name }
        "cost" -> usage.sortedWith(compareBy<ItemUsage> { it.garment.currency }.thenBy { RoadmapRules.costPerWear(it.garment.priceMinor, it.days) ?: Long.MAX_VALUE }.thenBy { it.garment.name })
        else -> usage.filter { it.days > 0 }.sortedWith(compareByDescending<ItemUsage> { it.days }.thenBy { it.garment.name })
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.statistics_hint))
                SelectionRow(stringResource(R.string.statistics_archive), includeArchive) { includeArchive = it }
                Text(stringResource(R.string.statistics_summary, usage.size, usage.count { it.days > 0 }, usage.sumOf { it.days }), style = MaterialTheme.typography.titleMedium)
                usage.filter { it.garment.priceMinor != null }.groupBy { it.garment.currency }.toSortedMap().forEach { (currency, items) ->
                    Text(stringResource(R.string.statistics_spend, moneyLabel(items.sumOf { it.garment.priceMinor!! }, currency, locale), items.size))
                }
                Text(stringResource(R.string.statistics_prices_missing, usage.count { it.garment.priceMinor == null }), style = MaterialTheme.typography.bodySmall)
                Choice(group, listOf("most", "forgotten", "never", "cost"), { group = it }, R.string.statistics_show,
                    optionLabels = mapOf("most" to R.string.statistics_most, "forgotten" to R.string.statistics_forgotten,
                        "never" to R.string.statistics_never, "cost" to R.string.statistics_cost))
            }
        }
        if (rows.isEmpty()) item { Text(stringResource(R.string.statistics_empty), Modifier.padding(vertical = 24.dp)) }
        items(rows, key = { it.garment.id }) { row ->
            val g = row.garment
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ItemPhoto(g.photoId, vm, Modifier.size(84.dp), description = g.name)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.statistics_wears, row.days))
                            Text(row.last?.let { stringResource(R.string.statistics_last, dateLabel(it)) } ?: stringResource(R.string.statistics_no_wears))
                            val cost = RoadmapRules.costPerWear(g.priceMinor, row.days)
                            Text(when { g.priceMinor == null -> stringResource(R.string.statistics_unknown_price)
                                cost == null -> stringResource(R.string.statistics_cost_no_wears)
                                else -> stringResource(R.string.statistics_cost_value, moneyLabel(cost, g.currency, locale)) })
                            if (g.archived) Text(stringResource(R.string.archive))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { open(g) }, enabled = !busy) { Text(stringResource(R.string.item_details)) }
                        if (WardrobeRules.ready(g.rulePiece())) TextButton(onClick = { build(g) }, enabled = !busy) { Text(stringResource(R.string.statistics_build)) }
                    }
                }
            }
        }
    }
}
