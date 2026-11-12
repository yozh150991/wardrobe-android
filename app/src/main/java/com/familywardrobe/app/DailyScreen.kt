@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.familywardrobe.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Event
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
import androidx.compose.ui.semantics.contentDescription
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
import kotlin.math.roundToInt

@Composable
fun DailyScreen(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, today: String,
    open: (Garment) -> Unit, saved: (String) -> Unit, schedule: (String) -> Unit, capsule: Capsule? = null,
    addItems: () -> Unit = {}, editCapsule: () -> Unit = {}, anchor: String = "", clearAnchor: () -> Unit = {},
    buildWith: (Garment) -> Unit = {}) {
    // UX-05: the screen opens on the occasion last chosen for this wardrobe.
    var occasion by rememberSaveable(wardrobe) { mutableStateOf(vm.lastOccasion(wardrobe)) }
    LaunchedEffect(wardrobe, occasion) { vm.rememberOccasion(wardrobe, occasion) }
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
    // WTH-01: the forecast's temperature as this person feels it travels with the mode and decides the coat, the
    // second layer and the seasons; a weather chosen by hand has no temperature.
    val temperature = if (weatherEnabled) forecast?.temperature(today)?.let { WeatherRules.shifted(it, warmth.shift) } else null
    val weather = temperature?.let { RoadmapRules.weatherMode(it) } ?: manualWeather
    val busy by vm.busy.collectAsStateWithLifecycle()
    // Both generated and manually replaced variants survive a detail/back transition.
    var generatedFor by rememberSaveable { mutableStateOf("") }
    var lookIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var hiddenShown by rememberSaveable { mutableStateOf(false) }
    // GEN-02: «Інші варіанти» skips the outfits already shown for these conditions, until every one has been seen.
    var skipped by rememberSaveable(wardrobe, occasion, weather, today) { mutableStateOf(arrayListOf<String>()) }
    var skippedAll by remember { mutableStateOf(false) }
    val input = remember(wardrobe, occasion, weather, temperature, today, s.garments, s.wears, s.feedback, s.wardrobes, allowed, fixed, revision, refresh, choices, skipped) {
        stateToken(listOf(wardrobe, occasion, weather, temperature, today, s.garments, s.wears, s.feedback, s.wardrobes, allowed?.sorted(), fixed.toSortedMap(), revision, refresh, choices, skipped).toString())
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
            val skip = skipped.toSet()
            val (result, exhausted) = withContext(Dispatchers.Default) {
                val other = DailyOutfits.daily(s, wardrobe, occasion, weather, today, "$today:$revision", skip, allowed, fixed, choices, temperature = temperature)
                // Nothing new is left (or only combinations the person hid): start again from the best ones.
                if (skip.isEmpty() || other.looks.isNotEmpty() && !other.hiddenShown) other to false
                else DailyOutfits.daily(s, wardrobe, occasion, weather, today, "$today:$revision", allowed = allowed, fixed = fixed, choices = choices,
                    temperature = temperature) to true
            }
            skippedAll = exhausted
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
    // ROT-01: one outfit around an item not worn for 90 days (or the item chosen in the statistics), not in capsules.
    var rotationAfter by rememberSaveable(wardrobe) { mutableStateOf("") }
    var rotationFor by rememberSaveable { mutableStateOf("") }
    var rotationAnchorId by rememberSaveable { mutableStateOf("") }
    var rotationIds by rememberSaveable { mutableStateOf("") }
    var rotationOthers by rememberSaveable { mutableIntStateOf(0) }
    var rotationProblem by rememberSaveable { mutableStateOf("") }
    // ROT-02: how often the card appears, what is postponed and what it showed recently (this account's own settings).
    val rotationRevision by vm.rotation.revision.collectAsStateWithLifecycle()
    val rotationState = remember(wardrobe, rotationRevision, today) { vm.rotation.read(wardrobe, today) }
    // An item chosen in the statistics is shown whatever the frequency: the person asked for it.
    val rotationVisible = anchor.isNotEmpty() || RotationRules.visible(rotationState.frequency, rotationState.lastShown, rotationState.hiddenOn, today)
    val rotationInput = if (capsule != null || !rotationVisible) "" else stateToken("$input|$anchor|$rotationAfter|$rotationState")
    LaunchedEffect(rotationInput) {
        if (rotationInput.isEmpty() || rotationFor == rotationInput) return@LaunchedEffect
        delay(80)
        val result = withContext(Dispatchers.Default) {
            RotationOutfits.suggest(s, wardrobe, occasion, weather, today, rotationAfter, anchor, fixed, choices, rotationState, temperature)
        }
        rotationAnchorId = result?.anchor?.id.orEmpty(); rotationIds = result?.look.orEmpty().joinToString(",") { it.id }
        rotationOthers = result?.others ?: 0; rotationProblem = result?.problem.orEmpty(); rotationFor = rotationInput
    }
    val rotationItem = s.garments.find { it.id == rotationAnchorId && it.wardrobeId == wardrobe }.takeIf { rotationFor == rotationInput && capsule == null }
    val rotationLook = remember(rotationIds, s.garments) { rotationIds.split(',').mapNotNull { id -> s.garments.find { it.id == id } } }
    // The card showed this item today: tomorrow another one leads (no nagging); weekly mode counts from today.
    LaunchedEffect(rotationItem?.id, rotationLook.isNotEmpty(), today) {
        val shown = rotationItem ?: return@LaunchedEffect
        if (anchor.isEmpty() && rotationLook.isNotEmpty()) vm.rotation.markShown(wardrobe, shown.id, today)
    }
    // STYLE-01/02: each outfit's rating and the best single change from the person's own items, off the UI thread.
    val target = remember(wardrobe, occasion, s.feedback, choices) { OutfitStyle.target(s, wardrobe, occasion, choices) }
    var betters by remember { mutableStateOf<Map<String, BetterSwap>>(emptyMap()) }
    LaunchedEffect(lookIds, rotationIds, rotationAnchorId, input) {
        val variants = looks; val rotationNow = rotationLook; val pins = fixedIds.toSet(); val anchorId = rotationAnchorId
        delay(80)
        betters = withContext(Dispatchers.Default) {
            buildMap {
                variants.forEach { look -> OutfitStyle.better(s, wardrobe, occasion, weather, today, look, pins, allowed, choices, temperature)
                    ?.let { put(DailyRules.signature(look.map { g -> g.id }), it) } }
                // The item of «Давно не вдягали» stays: that is the point of the card.
                if (rotationNow.isNotEmpty()) OutfitStyle.better(s, wardrobe, occasion, weather, today, rotationNow, pins + anchorId, allowed, choices, temperature)
                    ?.let { put(ROTATION_KEY + DailyRules.signature(rotationNow.map { g -> g.id }), it) }
            }
        }
    }
    var occasionMenu by remember { mutableStateOf(false) }
    var snoozing by rememberSaveable { mutableStateOf("") }
    var archiving by rememberSaveable { mutableStateOf("") }
    var snoozedNotice by rememberSaveable(wardrobe) { mutableStateOf("") }
    fun postpone(item: Garment, days: Int, reason: String) {
        val until = vm.rotation.snooze(wardrobe, item.id, days, reason, today)
        snoozing = ""; archiving = ""; snoozedNotice = "${item.id}|$until"
        if (anchor.isNotEmpty()) clearAnchor()
    }
    // WEAR-01: what was worn today in this wardrobe, and the rows the last «Вдягаю сьогодні» added (its exact undo).
    val wornToday = remember(s.wears, s.garments, wardrobe, today) {
        val ids = s.wears.filter { it.date == today }.map { it.garmentId }.toSet()
        s.garments.filter { it.wardrobeId == wardrobe && it.id in ids }
    }
    var wornAdded by rememberSaveable(wardrobe) { mutableStateOf(arrayListOf<String>()) }
    var wornOn by rememberSaveable(wardrobe) { mutableStateOf("") }
    fun wearNow(items: List<Garment>) = vm.wearItems(wardrobe, items, today) { added -> wornAdded = ArrayList(added); wornOn = today }
    fun wornAll(items: List<Garment>) = items.isNotEmpty() && items.all { g -> wornToday.any { it.id == g.id } }
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
                // STYLE-03: the conditions as chips: the occasion is picked right here, the weather opens the conditions.
                val occasionDescription = stringResource(R.string.style_occasion_chip, stringResource(label(occasion)))
                // WTH-01: the chip shows the temperature the outfits are chosen for (the day and how it feels now), as forecast.
                val forecastTemperature = if (weatherEnabled) forecast?.temperature(today) else null
                val weatherText = stringResource(label(weather)) + (forecastTemperature?.let { " · ${it.roundToInt()}°" } ?: "")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        AssistChip(onClick = { occasionMenu = true }, enabled = !busy, label = { Text(stringResource(label(occasion))) },
                            modifier = Modifier.semantics { contentDescription = occasionDescription },
                            leadingIcon = { Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) })
                        DropdownMenu(occasionMenu, onDismissRequest = { occasionMenu = false }) {
                            LookRules.OCCASIONS.forEach { code ->
                                DropdownMenuItem(text = { Text(stringResource(label(code))) }, onClick = { occasion = code; occasionMenu = false })
                            }
                        }
                    }
                    AssistChip(onClick = { configuring = true }, label = { Text(weatherText) },
                        leadingIcon = { Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(18.dp)) })
                    if (packedOnly) AssistChip(onClick = { configuring = true }, label = { Text(stringResource(R.string.capsule_packed_only)) })
                }
                if (forecastWeather != null && forecastWeather != weather) Text(stringResource(R.string.prefs_weather_shifted,
                    stringResource(label(forecastWeather)), stringResource(label(weather))), style = MaterialTheme.typography.bodySmall)
                if (weather in setOf("cold", "cool", "rain") && s.garments.none { it.wardrobeId == wardrobe && it.category == "outerwear" && WardrobeRules.ready(it.rulePiece()) })
                    Text(stringResource(R.string.daily_no_outerwear), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { configuring = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.configure_conditions)) }
            }
        }
        if (wornToday.isNotEmpty()) item(key = "worn-today") {
            OutlinedCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.wear_today_title), style = MaterialTheme.typography.titleMedium)
                    OutfitGrid(wornToday, vm, emptySet(), !busy) { g -> open(g) }
                    Text(stringResource(R.string.wear_today_hint), style = MaterialTheme.typography.bodySmall)
                    // Only what the last «Вдягаю сьогодні» added: wear from an outfit, a plan or an item's own page stays.
                    val undo = wornAdded.filter { id -> wornToday.any { it.id == id } }
                    if (wornOn == today && undo.isNotEmpty()) TextButton(onClick = { vm.undoWears(wardrobe, undo, today); wornAdded = arrayListOf() },
                        enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.undo_action)) }
                }
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
        snoozedNotice.split('|').takeIf { it.size == 2 }?.let { (id, until) -> s.garments.find { it.id == id }?.let { g -> item(key = "rotation-snoozed") {
            OutlinedCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.rotation_snoozed, g.name, dateLabel(until)), style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.rotation.restore(wardrobe, id); snoozedNotice = "" }) { Text(stringResource(R.string.undo_action)) }
                        TextButton(onClick = { snoozedNotice = "" }) { Text(stringResource(R.string.feedback_ok)) }
                    }
                }
            }
        } } }
        if (capsule == null && anchor.isEmpty() && rotationState.hiddenOn == today && rotationState.frequency != RotationRules.OFF) item(key = "rotation-hidden") {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rotation_hidden), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { vm.rotation.unhide(wardrobe) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.rotation_show)) }
                }
            }
        }
        if (rotationItem != null && (rotationLook.isNotEmpty() || anchor.isNotEmpty())) item(key = "rotation") {
            val last = remember(s.wears, rotationItem.id, today) { RotationOutfits.lastWears(s, today)[rotationItem.id] }
            val added = RotationOutfits.added(rotationItem)
            val idle = RotationRules.idleDays(last.orEmpty(), added, today).toInt()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(if (anchor.isNotEmpty()) R.string.rotation_anchor_title else R.string.rotation_title),
                        style = MaterialTheme.typography.titleLarge)
                    // Why it is suggested: the last actual wear, or how long it has waited since it was added.
                    Text(if (last != null) stringResource(R.string.rotation_reason_worn, rotationItem.name, dateLabel(last), idle)
                        else stringResource(R.string.rotation_reason_never, rotationItem.name, dateLabel(added), idle), style = MaterialTheme.typography.bodyMedium)
                    if (rotationLook.isNotEmpty()) {
                        val rating = remember(rotationLook, weather, target, temperature) { OutfitStyle.rate(rotationLook, weather, target, temperature) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColourDots(OutfitStyle.colours(rotationLook)); ScoreBadge(rating)
                        }
                        OutfitGrid(rotationLook, vm, (fixedIds + rotationItem.id).toSet(), !busy) { g -> actionItem = g.id; actionIndex = ROTATION }
                        StyleReasons(rating, rotationLook, weather, target, choices)
                        betters[ROTATION_KEY + DailyRules.signature(rotationLook.map { it.id })]?.let { swap ->
                            BetterBlock(swap, !busy) { rotationIds = OutfitStyle.replaced(rotationIds, swap) }
                        }
                        val autoName = stringResource(R.string.rotation_saved_name, rotationItem.name, dateText)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { wearNow(rotationLook) }, enabled = !busy && !wornAll(rotationLook)) {
                                Text(stringResource(if (wornAll(rotationLook)) R.string.wear_today_marked else R.string.wear_look_today)) }
                            OutlinedButton(onClick = { beginSave(rotationLook, false, autoName) }, enabled = !busy) { Text(stringResource(R.string.save_outfit)) }
                            OutlinedButton(onClick = { beginSave(rotationLook, true, autoName) }, enabled = !busy) { Text(stringResource(R.string.calendar_plan)) }
                        }
                    } else {
                        Text(rotationProblemText(rotationProblem, weather), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { configuring = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.configure_conditions)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (rotationOthers > 0) TextButton(onClick = {
                            val current = rotationItem.id
                            if (anchor.isNotEmpty()) clearAnchor()
                            rotationAfter = current
                        }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.rotation_other)) }
                        // ROT-02: postpone this item, or say why it is not worn.
                        TextButton(onClick = { snoozing = rotationItem.id }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.rotation_snooze)) }
                        if (anchor.isEmpty()) TextButton(onClick = { vm.rotation.hideToday(wardrobe, today) }, enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.rotation_hide_today)) }
                    }
                    if (anchor.isEmpty()) TextButton(onClick = { preferencesOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.rotation_frequency_link))
                    }
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
            // STYLE-01–03: score, colours, the items as tiles, why, and «Добре → Краще».
            val rating = remember(items, weather, target, temperature) { OutfitStyle.rate(items, weather, target, temperature) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.daily_option, index + 1), style = MaterialTheme.typography.titleLarge)
                            ColourDots(OutfitStyle.colours(items))
                        }
                        ScoreBadge(rating)
                    }
                    OutfitGrid(items, vm, fixedIds.toSet(), !busy) { g -> actionItem = g.id; actionIndex = index }
                    StyleReasons(rating, items, weather, target, choices)
                    betters[DailyRules.signature(items.map { it.id })]?.let { swap ->
                        BetterBlock(swap, !busy) { lookIds = ArrayList(lookIds.mapIndexed { i, old -> if (i == index) OutfitStyle.replaced(old, swap) else old }) }
                    }
                    // WEAR-01: one tap marks the variant as worn today — no saved outfit, no name, no plan.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { wearNow(items) }, enabled = !busy && !wornAll(items)) {
                            Text(stringResource(if (wornAll(items)) R.string.wear_today_marked else R.string.wear_look_today)) }
                        OutlinedButton(onClick = { beginSave(items, false, autoName) }, enabled = !busy) { Text(stringResource(R.string.save_outfit)) }
                        OutlinedButton(onClick = { beginSave(items, true, autoName) }, enabled = !busy) { Text(stringResource(R.string.calendar_plan)) }
                    }
                    TextButton(onClick = { reasonFor = index; choosingItems = false; uncomfortable = arrayListOf() }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.feedback_open)) }
                }
            }
        }
        if (looks.isNotEmpty()) item {
            OutlinedButton(onClick = {
                val shown = looks.map { DailyRules.signature(it.map { g -> g.id }) }
                skipped = ArrayList(if (skippedAll) shown else (skipped + shown).distinct()); revision++
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.daily_other_variants)) }
        }
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
                val rotation = actionIndex == ROTATION
                OutlinedButton(onClick = {
                    val items = if (rotation) rotationLook else looks.getOrNull(actionIndex).orEmpty()
                    val others = looks.filterIndexed { index, _ -> rotation || index != actionIndex }.map { DailyRules.signature(it.map { g -> g.id }) }.toSet()
                    val index = actionIndex; val before = lookIds; val beforeRotation = rotationIds; val category = item.category
                    actionItem = ""
                    scope.launch {
                        // ROT-01: the other pieces stay, so the item not worn for a long time stays in its outfit.
                        val replacement = withContext(Dispatchers.Default) { DailyOutfits.replace(s, wardrobe, occasion, weather, today, items, category, others, allowed, choices, temperature) }
                        // Apply only to the same variants the user acted on.
                        if (rotation) {
                            if (rotationIds != beforeRotation) return@launch
                            if (replacement.isEmpty()) noReplacement = true else rotationIds = replacement.joinToString(",") { it.id }
                            return@launch
                        }
                        if (lookIds != before) return@launch
                        if (replacement.isEmpty()) noReplacement = true
                        else lookIds = ArrayList(lookIds.mapIndexed { i, old -> if (i == index) replacement.joinToString(",") { it.id } else old })
                    }
                }, enabled = !busy && item.id !in fixedIds && !(rotation && item.id == rotationAnchorId), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.replace)) }
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
    if (preferencesOpen) PreferencesDialog(s, wardrobe, vm, today) { preferencesOpen = false; lastReason = "" }
    s.garments.find { it.id == snoozing && it.wardrobeId == wardrobe }?.let { item ->
        ModalBottomSheet(onDismissRequest = { snoozing = "" }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.rotation_snooze_title, item.name), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.rotation_snooze_hint), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((days, title) in listOf(RotationRules.SNOOZE_WEEK to R.string.rotation_snooze_week,
                        RotationRules.SNOOZE_MONTH to R.string.rotation_snooze_month, RotationRules.SNOOZE_SEASON to R.string.rotation_snooze_season)) {
                        OutlinedButton(onClick = { postpone(item, days, RotationRules.LATER) }, enabled = !busy) { Text(stringResource(title)) }
                    }
                }
                Text(stringResource(R.string.rotation_why), style = MaterialTheme.typography.titleMedium)
                // «Незручно» is the PERS-01 reason itself: the same row, scope and undo as under a daily variant.
                ReasonOption(stringResource(R.string.feedback_uncomfortable), reasonScope(FeedbackReason.UNCOMFORTABLE, "", ""), enabled = !busy) {
                    snoozing = ""; uncomfortable = arrayListOf(item.id)
                    record(rotationLook.takeIf { look -> look.any { it.id == item.id } } ?: listOf(item), FeedbackReason.UNCOMFORTABLE)
                }
                ReasonOption(stringResource(R.string.rotation_reason_size), stringResource(R.string.rotation_reason_size_scope), enabled = !busy) {
                    snoozing = ""; archiving = item.id
                }
                ReasonOption(stringResource(R.string.rotation_reason_waiting), stringResource(R.string.rotation_reason_waiting_scope), enabled = !busy) {
                    postpone(item, RotationRules.reasonDays(RotationRules.WAITING), RotationRules.WAITING)
                }
                ReasonOption(stringResource(R.string.rotation_reason_no_match), stringResource(R.string.rotation_reason_no_match_scope), enabled = !busy) {
                    postpone(item, RotationRules.reasonDays(RotationRules.NO_MATCH), RotationRules.NO_MATCH); buildWith(item)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    s.garments.find { it.id == archiving && it.wardrobeId == wardrobe }?.let { item ->
        AlertDialog(onDismissRequest = { archiving = "" }, title = { Text(stringResource(R.string.rotation_archive_title, item.name)) },
            text = { Text(stringResource(R.string.rotation_archive_text)) },
            // The app's usual archive change: the snackbar offers «Скасувати».
            confirmButton = { TextButton(onClick = { archiving = ""; vm.changeAvailability(item, archived = true); if (anchor.isNotEmpty()) clearAnchor() },
                enabled = !busy) { Text(stringResource(R.string.archive)) } },
            dismissButton = { Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { postpone(item, RotationRules.reasonDays(RotationRules.SIZE), RotationRules.SIZE) }, enabled = !busy) {
                    Text(stringResource(R.string.rotation_snooze_instead))
                }
                TextButton(onClick = { archiving = "" }) { Text(stringResource(R.string.cancel)) }
            } })
    }
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

/** ROT-01: the action sheet acts on the «Давно не вдягали» outfit instead of a numbered variant. */
private const val ROTATION = -2
/** STYLE-02: the «Давно не вдягали» outfit's «Краще» is kept apart from a variant with the same items (its item is fixed). */
private const val ROTATION_KEY = "rotation:"

/** ROT-01: why the item chosen in the statistics makes no outfit today, in words the person can act on. */
@Composable
private fun rotationProblemText(code: String, weather: String): String = when (code) {
    RotationRules.UNAVAILABLE -> stringResource(R.string.rotation_problem_unavailable)
    RotationRules.EXCLUDED -> stringResource(R.string.rotation_problem_excluded)
    RotationRules.WEATHER -> stringResource(R.string.rotation_problem_weather, stringResource(label(weather)))
    RotationRules.REPEAT -> stringResource(R.string.rotation_problem_repeat)
    else -> stringResource(R.string.rotation_problem_items)
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
