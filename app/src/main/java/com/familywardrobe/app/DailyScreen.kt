@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DailyScreen(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, today: String,
    open: (Garment) -> Unit, saved: (String) -> Unit, schedule: (String) -> Unit, capsule: Capsule? = null,
    addItems: () -> Unit = {}, editCapsule: () -> Unit = {}) {
    var occasion by rememberSaveable { mutableStateOf("everyday") }
    var manualWeather by rememberSaveable { mutableStateOf("any_weather") }
    var fixedIds by rememberSaveable(wardrobe, capsule?.id) { mutableStateOf(arrayListOf<String>()) }
    var packedOnly by rememberSaveable(capsule?.id) { mutableStateOf(false) }
    val fullCapsule = capsule?.let { c -> s.capsuleItems.filter { it.capsuleId == c.id }.map { it.garmentId }.toSet() }
    val allowed = capsule?.let { c -> s.capsuleItems.filter { it.capsuleId == c.id && (!packedOnly || it.packed) }.map { it.garmentId }.toSet() }
    val eligible = DailyOutfits.items(s, today, allowed).filter { it.piece.wardrobe == wardrobe && WardrobeRules.ready(it.piece) }.map { it.piece.id }.toSet()
    LaunchedEffect(eligible) { fixedIds = ArrayList(fixedIds.filter { it in eligible }) }
    val fixed = fixedIds.mapNotNull { id -> s.garments.find { it.id == id } }.associate { it.category to it.id }
    var revision by rememberSaveable { mutableIntStateOf(0) }
    val refresh by vm.recommendationRefresh.collectAsStateWithLifecycle()
    val weatherEnabled by vm.weather.enabled.collectAsStateWithLifecycle()
    val forecast by vm.weather.forecast.collectAsStateWithLifecycle()
    val weather = if (weatherEnabled) forecast?.mode(today) ?: manualWeather else manualWeather
    val busy by vm.busy.collectAsStateWithLifecycle()
    // Both generated and manually replaced variants survive a detail/back transition.
    var generatedFor by rememberSaveable { mutableStateOf("") }
    var lookIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val input = stateToken(listOf(wardrobe, occasion, weather, today, s.garments, s.wears, s.feedback, s.wardrobes, allowed?.sorted(), fixed.toSortedMap(), revision, refresh).toString())
    LaunchedEffect(input) {
        if (generatedFor != input) {
            lookIds = ArrayList(DailyOutfits.generate(s, wardrobe, occasion, weather, today, "$today:$revision", allowed = allowed, fixed = fixed).map { it.joinToString(",") { g -> g.id } })
            generatedFor = input
        }
    }
    val looks = lookIds.map { ids -> ids.split(',').mapNotNull { id -> s.garments.find { it.id == id } } }.filter { it.isNotEmpty() }
    var configuring by rememberSaveable { mutableStateOf(false) }
    var actionItem by rememberSaveable { mutableStateOf("") }
    var actionIndex by rememberSaveable { mutableIntStateOf(-1) }
    var namingIds by rememberSaveable { mutableStateOf("") }
    var namingSchedule by rememberSaveable { mutableStateOf(false) }
    var proposedName by rememberSaveable { mutableStateOf("") }
    var noReplacement by remember { mutableStateOf(false) }
    val dateText = dateLabel(today)
    val currentRules = s.wardrobes.find { it.id == wardrobe }?.noRepeatDays ?: 0
    fun beginSave(items: List<Garment>, plan: Boolean, autoName: String) {
        val existing = s.outfits.find { it.wardrobeId == wardrobe && it.signature == DailyRules.signature(items.map { g -> g.id }) }
        if (existing != null) {
            vm.saveDaily(wardrobe, items, existing.name, occasion, weather, if (plan) schedule else saved, capsule?.id.orEmpty(), packedOnly)
            vm.notice.value = R.string.look_existing_opened
        } else {
            proposedName = LookNameRules.unique(autoName, s.outfits.filter { it.wardrobeId == wardrobe }.map { it.name })
            namingIds = items.joinToString(",") { it.id }; namingSchedule = plan
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (capsule != null) Text(capsule.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(label(occasion)) + " · " + stringResource(label(weather)) +
                    if (packedOnly) " · " + stringResource(R.string.capsule_packed_only) else "", style = MaterialTheme.typography.bodyMedium)
                if (weather in setOf("cold", "cool", "rain") && s.garments.none { it.wardrobeId == wardrobe && it.category == "outerwear" && WardrobeRules.ready(it.rulePiece()) })
                    Text(stringResource(R.string.daily_no_outerwear), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { configuring = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.configure_conditions)) }
            }
        }
        if (looks.isEmpty() && generatedFor == input) item {
            val reason = DailyDiagnostics.explain(DailyOutfits.rawItems(s, today), wardrobe, occasion, weather, today, "$today:$revision",
                allowed, fullCapsule, packedOnly, s.garments.filter { it.wardrobeId == wardrobe && it.suggestionExcluded }.map { it.id }.toSet(),
                currentRules, DailyOutfits.rejected(s, wardrobe, occasion, weather), fixed)
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(dailyReasonLabel(reason)), style = MaterialTheme.typography.titleMedium)
                    when (reason) {
                        DailyDiagnostics.Reason.CAPSULE_EMPTY -> Button(onClick = editCapsule) { Text(stringResource(R.string.capsule_add_items)) }
                        DailyDiagnostics.Reason.PACKED -> Button(onClick = { packedOnly = false }) { Text(stringResource(R.string.capsule_show_all)) }
                        DailyDiagnostics.Reason.NO_ITEMS, DailyDiagnostics.Reason.SHOES, DailyDiagnostics.Reason.BASE -> Button(onClick = if (capsule == null) addItems else editCapsule) { Text(stringResource(R.string.add_item)) }
                        DailyDiagnostics.Reason.PINS -> Button(onClick = { fixedIds = arrayListOf() }) { Text(stringResource(R.string.rules_clear_locks)) }
                        DailyDiagnostics.Reason.FEEDBACK -> Button(onClick = { vm.resetFeedback(wardrobe) }, enabled = !busy) { Text(stringResource(R.string.daily_reset_feedback)) }
                        else -> Button(onClick = { configuring = true }) { Text(stringResource(R.string.configure_conditions)) }
                    }
                    if (reason == DailyDiagnostics.Reason.UNAVAILABLE) TextButton(onClick = addItems) { Text(stringResource(R.string.open_wardrobe)) }
                    if (reason == DailyDiagnostics.Reason.CONSTRAINTS) Text(stringResource(R.string.daily_constraints_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        itemsIndexed(looks, key = { index, _ -> index }) { index, items ->
            val autoName = stringResource(R.string.daily_saved_variant, dateText, index + 1)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.daily_option, index + 1), style = MaterialTheme.typography.titleLarge)
                    OutfitComposition(items.map(LookRules::owned), vm, locks = fixedIds.map { "owned:$it" }.toSet()) { piece -> actionItem = piece.ownedId.orEmpty(); actionIndex = index }
                    Text(stringResource(R.string.daily_reason, stringResource(label(weather)), stringResource(label(occasion))), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { beginSave(items, false, autoName) }, enabled = !busy) { Text(stringResource(R.string.save_outfit)) }
                        OutlinedButton(onClick = { beginSave(items, true, autoName) }, enabled = !busy) { Text(stringResource(R.string.calendar_plan)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { vm.feedback(wardrobe, items.map { it.id }, occasion, weather, "dislike") }, enabled = !busy) { Text(stringResource(R.string.daily_dislike)) }
                        TextButton(onClick = { vm.feedback(wardrobe, items.map { it.id }, occasion, weather, "hot"); manualWeather = "warm"; vm.weather.setEnabled(false) }, enabled = !busy) { Text(stringResource(R.string.daily_hot)) }
                    }
                }
            }
        }
        if (looks.isNotEmpty()) item { OutlinedButton(onClick = { revision++ }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.daily_refresh)) } }
    }
    if (configuring) ModalBottomSheet(onDismissRequest = { configuring = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.configure_conditions), style = MaterialTheme.typography.titleLarge)
            Choice(occasion, LookRules.OCCASIONS, { occasion = it }, R.string.look_occasion, enabled = !busy)
            Choice(weather, LookRules.WEATHERS, { manualWeather = it; vm.weather.setEnabled(false) }, R.string.look_weather, enabled = !busy)
            if (capsule != null) {
                Text(stringResource(R.string.capsule_scope_hint), style = MaterialTheme.typography.bodySmall)
                if (capsule.kind == "trip") SelectionRow(stringResource(R.string.capsule_packed_only), packedOnly, !busy) { packedOnly = it }
            }
            RecommendationPanel(s, wardrobe, vm)
            if (fixedIds.isNotEmpty()) TextButton(onClick = { fixedIds = arrayListOf() }) { Text(stringResource(R.string.rules_clear_locks)) }
            WeatherPanel(vm.weather, today)
            Button(onClick = { configuring = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.show_results)) }
        }
    }
    s.garments.find { it.id == actionItem && it.wardrobeId == wardrobe }?.let { item ->
        ModalBottomSheet(onDismissRequest = { actionItem = "" }) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { actionItem = ""; open(item) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.item_details)) }
                OutlinedButton(onClick = {
                    val items = looks.getOrNull(actionIndex).orEmpty()
                    val others = looks.filterIndexed { index, _ -> index != actionIndex }.map { DailyRules.signature(it.map { g -> g.id }) }.toSet()
                    val replacement = DailyOutfits.replace(s, wardrobe, occasion, weather, today, items, item.category, others, allowed)
                    if (replacement.isEmpty()) noReplacement = true
                    else lookIds = ArrayList(lookIds.mapIndexed { index, old -> if (index == actionIndex) replacement.joinToString(",") { it.id } else old })
                    actionItem = ""
                }, enabled = !busy && item.id !in fixedIds, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.replace)) }
                OutlinedButton(onClick = { fixedIds = ArrayList(if (item.id in fixedIds) fixedIds - item.id else fixedIds.filter { id -> s.garments.find { it.id == id }?.category != item.category } + item.id); actionItem = "" },
                    enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (item.id in fixedIds) R.string.unlock_piece else R.string.lock_piece)) }
                TextButton(onClick = { vm.excludeFromRecommendations(item.id, true); actionItem = "" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.rules_exclude)) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (namingIds.isNotBlank()) AlertDialog(onDismissRequest = { namingIds = "" }, title = { Text(stringResource(R.string.outfit_name)) },
        text = { Field(proposedName, { proposedName = TextLimits.input(it, 80) }, R.string.outfit_name, enabled = !busy) },
        confirmButton = { TextButton(onClick = {
            val items = namingIds.split(',').mapNotNull { id -> s.garments.find { it.id == id } }
            vm.saveDaily(wardrobe, items, proposedName, occasion, weather, { id -> namingIds = ""; if (namingSchedule) schedule(id) else saved(id) }, capsule?.id.orEmpty(), packedOnly)
        }, enabled = !busy && proposedName.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { namingIds = "" }, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    if (noReplacement) AlertDialog(onDismissRequest = { noReplacement = false }, text = { Text(stringResource(R.string.daily_no_replacement)) }, confirmButton = { TextButton(onClick = { noReplacement = false }) { Text(stringResource(R.string.close)) } })
}

private fun dailyReasonLabel(reason: DailyDiagnostics.Reason): Int = when (reason) {
    DailyDiagnostics.Reason.NO_ITEMS -> R.string.daily_empty_items; DailyDiagnostics.Reason.CAPSULE_EMPTY -> R.string.daily_empty_capsule
    DailyDiagnostics.Reason.PACKED -> R.string.daily_empty_packed; DailyDiagnostics.Reason.UNAVAILABLE -> R.string.daily_empty_availability
    DailyDiagnostics.Reason.SHOES -> R.string.daily_empty_shoes; DailyDiagnostics.Reason.BASE -> R.string.daily_empty_base
    DailyDiagnostics.Reason.WEATHER -> R.string.daily_empty_weather; DailyDiagnostics.Reason.SEASONS -> R.string.daily_empty_seasons
    DailyDiagnostics.Reason.PINS -> R.string.daily_empty_pins; DailyDiagnostics.Reason.REPEATS -> R.string.daily_empty_repeats
    DailyDiagnostics.Reason.EXCLUDED -> R.string.daily_empty_exclusions; DailyDiagnostics.Reason.FEEDBACK -> R.string.daily_empty_feedback
    else -> R.string.daily_empty
}

@Composable
fun WeatherPanel(controller: WeatherController, date: String) {
    val enabled by controller.enabled.collectAsStateWithLifecycle()
    val city by controller.city.collectAsStateWithLifecycle()
    val forecast by controller.forecast.collectAsStateWithLifecycle()
    val loading by controller.loading.collectAsStateWithLifecycle()
    val failed by controller.failed.collectAsStateWithLifecycle()
    var choosing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(date) { controller.refreshIfOld() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(city?.name ?: stringResource(R.string.weather_no_city), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.weather_choose_city)) }
            if (city != null) TextButton(onClick = { controller.setEnabled(!enabled) }, enabled = !loading) { Text(stringResource(if (enabled) R.string.weather_manual else R.string.weather_use_forecast)) }
        }
        if (enabled) {
            forecast?.let { f ->
                if (date == f.currentDate) Text(stringResource(R.string.weather_feels_like, f.feelsLike.toInt()))
                f.days.find { it.date == date }?.let { day ->
                    Text(stringResource(R.string.weather_range, day.low.toInt(), day.high.toInt()))
                    day.rain?.let { Text(stringResource(R.string.weather_rain_chance, it)) }
                }
                val updated = timestampLabel(f.fetchedAt)
                Text(stringResource(R.string.weather_source, updated), style = MaterialTheme.typography.bodySmall)
                if (f.mode(date) == null) Text(stringResource(R.string.weather_missing_date))
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.weather_failed), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = controller::refresh, enabled = !loading) { Text(stringResource(R.string.weather_refresh)) }
        }
    }
    if (choosing) CityDialog(controller) { choosing = false }
}

@Composable
private fun CityDialog(controller: WeatherController, dismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    val results by controller.results.collectAsStateWithLifecycle()
    val loading by controller.loading.collectAsStateWithLifecycle()
    val failed by controller.failed.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.weather_choose_city)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.weather_city_hint))
            OutlinedTextField(query, { query = TextLimits.input(it, 100); searched = false }, singleLine = true, label = { Text(stringResource(R.string.weather_city)) })
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.weather_failed))
            if (searched && !loading && !failed && results.isEmpty()) Text(stringResource(R.string.weather_city_empty))
            results.forEach { value -> TextButton(onClick = { controller.select(value); dismiss() }, modifier = Modifier.fillMaxWidth()) { Text(value.name) } }
        }
    }, confirmButton = { TextButton(onClick = { searched = true; controller.search(query) }, enabled = !loading && query.trim().length >= 2) { Text(stringResource(R.string.weather_search)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
