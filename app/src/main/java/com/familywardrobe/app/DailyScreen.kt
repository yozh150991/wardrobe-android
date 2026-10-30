@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val allowed = capsule?.let { c -> s.capsuleItems.filter { it.capsuleId == c.id && (!packedOnly || it.packed) }.map { it.garmentId }.toSet() }
    val eligible = remember(s.garments, s.wears, s.wardrobes, today, allowed, wardrobe) {
        DailyOutfits.items(s, today, allowed).filter { it.piece.wardrobe == wardrobe && WardrobeRules.ready(it.piece) }.map { it.piece.id }.toSet()
    }
    LaunchedEffect(eligible) { fixedIds = ArrayList(fixedIds.filter { it in eligible }) }
    val fixed = fixedIds.mapNotNull { id -> s.garments.find { it.id == id } }.associate { it.category to it.id }
    var revision by rememberSaveable { mutableIntStateOf(0) }
    val refresh by vm.recommendationRefresh.collectAsStateWithLifecycle()
    val weatherEnabled by vm.weather.enabled.collectAsStateWithLifecycle()
    val forecast by vm.weather.forecast.collectAsStateWithLifecycle()
    // PERS-02: the person's choices for this wardrobe; the forecast is shifted to how they feel it (never a manual choice).
    val choiceRevision by vm.personalChoices.revision.collectAsStateWithLifecycle()
    val choices = remember(wardrobe, choiceRevision) { vm.personalChoices.read(wardrobe) }
    val warmth = remember(wardrobe, s.feedback, choices) { DailyOutfits.warmth(s, wardrobe, choices) }
    val forecastWeather = if (weatherEnabled) forecast?.mode(today) else null
    val weather = forecastWeather?.let { PreferenceProfile.apply(it, warmth.shift) } ?: manualWeather
    val busy by vm.busy.collectAsStateWithLifecycle()
    // Both generated and manually replaced variants survive a detail/back transition.
    var generatedFor by rememberSaveable { mutableStateOf("") }
    var lookIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var hiddenShown by rememberSaveable { mutableStateOf(false) }
    val input = remember(wardrobe, occasion, weather, today, s.garments, s.wears, s.feedback, s.wardrobes, allowed, fixed, revision, refresh, choices) {
        stateToken(listOf(wardrobe, occasion, weather, today, s.garments, s.wears, s.feedback, s.wardrobes, allowed?.sorted(), fixed.toSortedMap(), revision, refresh, choices).toString())
    }
    val fullCapsule = capsule?.let { c -> s.capsuleItems.filter { it.capsuleId == c.id }.map { it.garmentId }.toSet() }
    val currentRules = s.wardrobes.find { it.id == wardrobe }?.noRepeatDays ?: 0
    // The outfit search and the "why no outfit" diagnosis are CPU-bound: run them off the UI thread
    // (audit A-03). A short delay absorbs the intermediate snapshots of a multi-table write.
    var emptyReason by remember { mutableStateOf<DailyDiagnostics.Reason?>(null) }
    var emptyReasonFor by remember { mutableStateOf("") }
    LaunchedEffect(input) {
        delay(80)
        var ids = lookIds
        if (generatedFor != input) {
            val result = withContext(Dispatchers.Default) {
                DailyOutfits.daily(s, wardrobe, occasion, weather, today, "$today:$revision", allowed = allowed, fixed = fixed, choices = choices)
            }
            ids = ArrayList(result.looks.map { it.joinToString(",") { g -> g.id } })
            lookIds = ids; hiddenShown = result.hiddenShown; generatedFor = input
        }
        if (ids.none { row -> row.split(',').any { id -> s.garments.any { it.id == id } } }) {
            emptyReason = withContext(Dispatchers.Default) {
                DailyDiagnostics.explain(DailyOutfits.rawItems(s, today), wardrobe, occasion, weather, today, "$today:$revision",
                    allowed, fullCapsule, packedOnly, s.garments.filter { it.wardrobeId == wardrobe && it.suggestionExcluded }.map { it.id }.toSet(),
                    currentRules, DailyOutfits.rejected(s, wardrobe, occasion, weather), fixed)
            }
            emptyReasonFor = input
        }
    }
    val looks = remember(lookIds, s.garments) { lookIds.map { ids -> ids.split(',').mapNotNull { id -> s.garments.find { it.id == id } } }.filter { it.isNotEmpty() } }
    val scope = rememberCoroutineScope()
    var configuring by rememberSaveable { mutableStateOf(false) }
    var actionItem by rememberSaveable { mutableStateOf("") }
    var actionIndex by rememberSaveable { mutableIntStateOf(-1) }
    var namingIds by rememberSaveable { mutableStateOf("") }
    var namingSchedule by rememberSaveable { mutableStateOf(false) }
    var proposedName by rememberSaveable { mutableStateOf("") }
    var noReplacement by remember { mutableStateOf(false) }
    // PERS-01: the variant a reason is being chosen for, and the last reason recorded (its scope and undo).
    var reasonFor by rememberSaveable { mutableIntStateOf(-1) }
    var preferencesOpen by rememberSaveable { mutableStateOf(false) }
    var choosingItems by rememberSaveable { mutableStateOf(false) }
    var uncomfortable by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var lastReason by rememberSaveable(wardrobe) { mutableStateOf("") }
    var lastIds by rememberSaveable(wardrobe) { mutableStateOf(arrayListOf<String>()) }
    var lastContext by rememberSaveable(wardrobe) { mutableStateOf("") }
    var lastWeatherAfter by rememberSaveable(wardrobe) { mutableStateOf("") }
    var lastWeatherBefore by rememberSaveable(wardrobe) { mutableStateOf("") }
    var lastForecastBefore by rememberSaveable(wardrobe) { mutableStateOf(false) }
    fun record(items: List<Garment>, reason: FeedbackReason) {
        val chosen = if (reason == FeedbackReason.UNCOMFORTABLE) uncomfortable.filter { id -> items.any { it.id == id } } else emptyList()
        val context = when (reason) { FeedbackReason.FORMAL -> occasion; FeedbackReason.TOO_WARM -> weather; else -> "" }
        val lighter = if (reason == FeedbackReason.TOO_WARM) PreferenceRules.lighter(weather) else weather
        val before = manualWeather; val forecastBefore = weatherEnabled
        reasonFor = -1; choosingItems = false
        vm.feedback(wardrobe, items.map { it.id }, chosen, occasion, weather, reason) { created ->
            lastIds = ArrayList(created); lastReason = reason.name; lastContext = context; lastWeatherAfter = ""
            if (lighter != weather) {
                // "Надто тепло" also moves the conditions one step lighter; the undo puts them back.
                lastWeatherBefore = before; lastForecastBefore = forecastBefore; lastWeatherAfter = lighter
                manualWeather = lighter; vm.weather.setEnabled(false)
            }
        }
    }
    fun undoReason() {
        if (lastIds.isNotEmpty()) vm.undoFeedback(wardrobe, lastIds)
        if (lastWeatherAfter.isNotEmpty()) { manualWeather = lastWeatherBefore; vm.weather.setEnabled(lastForecastBefore) }
        lastReason = ""; lastIds = arrayListOf(); lastWeatherAfter = ""
    }
    val dateText = dateLabel(today)
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
                if (forecastWeather != null && forecastWeather != weather) Text(stringResource(R.string.prefs_weather_shifted,
                    stringResource(label(forecastWeather)), stringResource(label(weather))), style = MaterialTheme.typography.bodySmall)
                if (weather in setOf("cold", "cool", "rain") && s.garments.none { it.wardrobeId == wardrobe && it.category == "outerwear" && WardrobeRules.ready(it.rulePiece()) })
                    Text(stringResource(R.string.daily_no_outerwear), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { configuring = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.configure_conditions)) }
            }
        }
        FeedbackReason.entries.find { it.name == lastReason }?.let { reason -> item {
            OutlinedCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.feedback_done, stringResource(reasonTitle(reason))), style = MaterialTheme.typography.titleMedium)
                    Text(reasonScope(reason, lastContext, lastWeatherAfter), style = MaterialTheme.typography.bodySmall)
                    if (lastIds.isEmpty()) Text(stringResource(R.string.feedback_already), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (lastIds.isNotEmpty() || lastWeatherAfter.isNotEmpty()) TextButton(onClick = { undoReason() }, enabled = !busy) { Text(stringResource(R.string.undo_action)) }
                        TextButton(onClick = { lastReason = ""; lastIds = arrayListOf(); lastWeatherAfter = "" }) { Text(stringResource(R.string.feedback_ok)) }
                    }
                }
            }
        } }
        if (hiddenShown && looks.isNotEmpty() && generatedFor == input) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.prefs_hidden_shown), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { preferencesOpen = true }) { Text(stringResource(R.string.prefs_open)) }
                }
            }
        }
        if (generatedFor != input || looks.isEmpty() && emptyReasonFor != input) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        val diagnosis = emptyReason.takeIf { emptyReasonFor == input }
        if (looks.isEmpty() && generatedFor == input && diagnosis != null) item {
            val reason = diagnosis
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
                    TextButton(onClick = { reasonFor = index; choosingItems = false; uncomfortable = arrayListOf() }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.feedback_open)) }
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
            OutlinedButton(onClick = { configuring = false; preferencesOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.prefs_open))
            }
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
                    val index = actionIndex; val before = lookIds; val category = item.category
                    actionItem = ""
                    scope.launch {
                        val replacement = withContext(Dispatchers.Default) { DailyOutfits.replace(s, wardrobe, occasion, weather, today, items, category, others, allowed, choices) }
                        // Apply only to the same variants the user acted on.
                        if (lookIds != before) return@launch
                        if (replacement.isEmpty()) noReplacement = true
                        else lookIds = ArrayList(lookIds.mapIndexed { i, old -> if (i == index) replacement.joinToString(",") { it.id } else old })
                    }
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
        }, enabled = !busy && TextLimits.hasVisibleText(proposedName)) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { namingIds = "" }, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    if (noReplacement) AlertDialog(onDismissRequest = { noReplacement = false }, text = { Text(stringResource(R.string.daily_no_replacement)) }, confirmButton = { TextButton(onClick = { noReplacement = false }) { Text(stringResource(R.string.close)) } })
    if (preferencesOpen) PreferencesDialog(s, wardrobe, vm) { preferencesOpen = false; lastReason = "" }
    looks.getOrNull(reasonFor)?.let { items ->
        ModalBottomSheet(onDismissRequest = { reasonFor = -1; choosingItems = false }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!choosingItems) {
                    Text(stringResource(R.string.feedback_title), style = MaterialTheme.typography.titleLarge)
                    for (reason in FeedbackReason.entries) {
                        val lighter = if (reason == FeedbackReason.TOO_WARM) PreferenceRules.lighter(weather) else ""
                        ReasonOption(stringResource(reasonTitle(reason)),
                            reasonScope(reason, if (reason == FeedbackReason.FORMAL) occasion else weather, lighter.takeIf { it != weather }.orEmpty()),
                            enabled = !busy) {
                            if (reason == FeedbackReason.UNCOMFORTABLE) choosingItems = true else record(items, reason)
                        }
                    }
                } else {
                    Text(stringResource(R.string.feedback_which), style = MaterialTheme.typography.titleLarge)
                    items.forEach { item ->
                        val checked = item.id in uncomfortable
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = !busy, role = Role.Checkbox) {
                            uncomfortable = ArrayList(if (it) uncomfortable + item.id else uncomfortable - item.id)
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = !busy)
                            Spacer(Modifier.width(8.dp)); Text(item.name)
                        }
                    }
                    Text(reasonScope(FeedbackReason.UNCOMFORTABLE, "", ""), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { record(items, FeedbackReason.UNCOMFORTABLE) }, enabled = !busy && uncomfortable.any { id -> items.any { it.id == id } },
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
                    TextButton(onClick = { choosingItems = false }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** PERS-01: one reason with what it changes and where, so its effect is predictable before it is chosen. */
@Composable
private fun ReasonOption(title: String, scope: String, enabled: Boolean, choose: () -> Unit) {
    OutlinedCard(onClick = choose, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(scope, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun reasonTitle(reason: FeedbackReason): Int = when (reason) {
    FeedbackReason.COMBINATION -> R.string.daily_dislike
    FeedbackReason.UNCOMFORTABLE -> R.string.feedback_uncomfortable
    FeedbackReason.FORMAL -> R.string.feedback_formal
    FeedbackReason.TOO_WARM -> R.string.daily_hot
}

/** [context]: the occasion (FORMAL) or weather (TOO_WARM) the reason applies to; [lighter]: the new weather, if it changes. */
@Composable
private fun reasonScope(reason: FeedbackReason, context: String, lighter: String): String = when (reason) {
    FeedbackReason.COMBINATION -> stringResource(R.string.feedback_combination_scope)
    FeedbackReason.UNCOMFORTABLE -> stringResource(R.string.feedback_uncomfortable_scope, stringResource(R.string.rules_exclude))
    FeedbackReason.FORMAL -> stringResource(R.string.feedback_formal_scope, stringResource(label(context)))
    FeedbackReason.TOO_WARM -> stringResource(R.string.feedback_warm_scope, stringResource(label(context))) +
        if (lighter.isNotEmpty()) " " + stringResource(R.string.feedback_warm_step, stringResource(label(lighter))) else ""
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
