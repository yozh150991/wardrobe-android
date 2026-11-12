@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.familywardrobe.app
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CapsuleListScreen(s: Snapshot, wardrobe: String, vm: WardrobeViewModel, open: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val capsules = s.capsules.filter { it.wardrobeId == wardrobe }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.capsule_hint))
            Button(onClick = { adding = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.capsule_add)) }
        }
        if (capsules.isEmpty()) item { Text(stringResource(R.string.capsule_empty), Modifier.padding(vertical = 24.dp)) }
        items(capsules, key = { it.id }) { capsule ->
            val items = s.capsuleItems.filter { it.capsuleId == capsule.id }
            Card(onClick = { open(capsule.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(capsule.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(if (capsule.kind == "trip") R.string.capsule_trip else R.string.capsule_kind))
                    if (capsule.startDate.isNotBlank()) Text(dateLabel(capsule.startDate) + " — " + dateLabel(capsule.endDate))
                    Text(stringResource(R.string.capsule_progress, items.count { it.packed }, items.size))
                }
            }
        }
    }
    if (adding) CapsuleEditor(null, wardrobe, vm, { adding = false }) { adding = false; open(it) }
}

@Composable
fun CapsuleDetailScreen(capsule: Capsule, s: Snapshot, vm: WardrobeViewModel, open: (Garment) -> Unit, openOutfit: (String) -> Unit, removed: () -> Unit, generate: () -> Unit, initialPickItems: Boolean = false,
    today: String = LocalDate.now().toString()) {
    var editing by remember { mutableStateOf(false) }
    var selectingOutfits by remember { mutableStateOf(false) }
    var selectingItems by rememberSaveable { mutableStateOf(initialPickItems) }
    var deleting by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val linked = s.capsuleOutfits.filter { it.capsuleId == capsule.id }.map { it.outfitId }.toSet()
    val packing = s.capsuleItems.filter { it.capsuleId == capsule.id }.sortedWith(compareBy<CapsuleItem> { it.packed }.thenBy { row -> s.garments.find { it.id == row.garmentId }?.category.orEmpty() })
    val missing = packing.count { row -> s.garments.find { it.id == row.garmentId }?.let { !WardrobeRules.ready(it.rulePiece()) } != false }
    // TRIP-02: does the packing cover every day? Computed off the UI thread, never saved.
    val forecast by vm.weather.forecast.collectAsStateWithLifecycle()
    val weatherEnabled by vm.weather.enabled.collectAsStateWithLifecycle()
    val choiceRevision by vm.personalChoices.revision.collectAsStateWithLifecycle()
    val choices = remember(capsule.wardrobeId, choiceRevision) { vm.personalChoices.read(capsule.wardrobeId) }
    // TRIP-01: the trip's occasion is saved with the capsule; "everyday" is the default and is not stored.
    val tripOccasion = capsule.occasion.ifEmpty { "everyday" }
    // TRIP-03: a compact packing suggestion, computed on request.
    var suggestion by remember(capsule.id) { mutableStateOf<PackingSuggestion?>(null) }
    var suggesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // TRIP-01: a trip with its own city uses that city's forecast (16 days ahead); otherwise the daily weather's city.
    val tripCity = capsule.tripCity
    var cityLoading by remember { mutableStateOf(false) }
    val cityForecast by produceState<Forecast?>(null, tripCity, weatherEnabled) {
        value = null
        if (weatherEnabled && tripCity != null) { cityLoading = true; try { value = vm.weather.forecastFor(tripCity) } finally { cityLoading = false } }
    }
    val tripForecast = if (tripCity != null) cityForecast.takeIf { weatherEnabled } else forecast.takeIf { weatherEnabled }
    val dated = capsule.startDate.isNotBlank()
    val coverage by produceState<TripCoverage?>(null, dated, capsule, s.garments, s.capsuleItems, s.wears, s.feedback, s.wardrobes,
        tripOccasion, tripForecast, choices, today) {
        value = if (!dated) null else withContext(Dispatchers.Default) { TripPlan.coverage(s, capsule, tripOccasion, today, tripForecast, choices) }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(capsule.name, style = MaterialTheme.typography.headlineSmall)
                if (capsule.startDate.isNotBlank()) Text(dateLabel(capsule.startDate) + " — " + dateLabel(capsule.endDate))
                Text(stringResource(R.string.capsule_progress, packing.count { it.packed }, packing.size))
                if (missing > 0) Text(stringResource(R.string.capsule_unavailable, missing), color = MaterialTheme.colorScheme.error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = generate, enabled = !busy && packing.isNotEmpty()) { Text(stringResource(R.string.capsule_generate)) }
                    OutlinedButton(onClick = { selectingOutfits = true }, enabled = !busy) { Text(stringResource(R.string.capsule_outfits)) }
                    OutlinedButton(onClick = { selectingItems = true }, enabled = !busy) { Text(stringResource(R.string.capsule_extras)) }
                    TextButton(onClick = { editing = true }, enabled = !busy) { Text(stringResource(R.string.edit)) }
                }
                s.outfits.filter { it.id in linked }.forEach { outfit ->
                    TextButton(onClick = { openOutfit(outfit.id) }, enabled = !busy) { Text(outfit.name) }
                    if (s.ideas.any { it.outfitId == outfit.id }) Text(stringResource(R.string.capsule_planned_warning), color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.capsule_unique_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (dated) item(key = "trip-days") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.trip_days_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.trip_days_hint), style = MaterialTheme.typography.bodySmall)
                Choice(tripOccasion, LookRules.OCCASIONS, { if (it != tripOccasion) vm.saveCapsule(capsule.copy(occasion = if (it == "everyday") "" else it)) {} },
                    R.string.look_occasion, enabled = !busy)
                val cityName = tripCity?.name
                Text(when {
                    cityName == null -> tripForecast?.let { stringResource(R.string.trip_forecast_city, it.city.name) } ?: stringResource(R.string.trip_no_weather)
                    !weatherEnabled -> stringResource(R.string.trip_city_weather_off, cityName)
                    cityForecast != null -> stringResource(R.string.trip_city_forecast, cityName, WeatherController.TRIP_DAYS)
                    cityLoading -> stringResource(R.string.trip_city_loading, cityName)
                    else -> stringResource(R.string.trip_city_unavailable, cityName)
                }, style = MaterialTheme.typography.bodySmall)
                when (val c = coverage) {
                    null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> {
                        if (c.days.isEmpty()) Text(stringResource(R.string.trip_over))
                        else Text(stringResource(R.string.trip_covered, c.covered, c.days.size), style = MaterialTheme.typography.titleMedium)
                        if (c.past > 0 && c.days.isNotEmpty()) Text(stringResource(R.string.trip_past, c.past), style = MaterialTheme.typography.bodySmall)
                        if (c.truncated) Text(stringResource(R.string.trip_truncated, TripPlan.MAX_DAYS), style = MaterialTheme.typography.bodySmall)
                        if (c.days.isNotEmpty()) OutlinedButton(onClick = {
                            suggesting = true
                            scope.launch {
                                suggestion = withContext(Dispatchers.Default) { TripPlan.packing(s, capsule, tripOccasion, today, tripForecast, choices) }
                                suggesting = false
                            }
                        }, enabled = !busy && !suggesting) { Text(stringResource(R.string.trip_suggest)) }
                    }
                }
            }
        }
        if (dated) items(coverage?.days.orEmpty(), key = { "trip:" + it.date }) { day ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dateLabel(day.date), style = MaterialTheme.typography.titleMedium)
                    Text(day.temperature?.let { stringResource(R.string.trip_weather_known, stringResource(label(day.weather)), it.roundToInt()) }
                        ?: stringResource(R.string.trip_weather_unknown), style = MaterialTheme.typography.bodySmall)
                    if (day.look.isNotEmpty()) OutfitGrid(day.look, vm, emptySet(), !busy) { g -> open(g) }
                    else Text(stringResource(dailyReasonLabel(day.reason ?: DailyDiagnostics.Reason.CONSTRAINTS)), color = MaterialTheme.colorScheme.error)
                    if (day.repeated) Text(stringResource(R.string.trip_repeated), style = MaterialTheme.typography.bodySmall)
                    if (day.noOuterwear) Text(stringResource(R.string.trip_no_outerwear), color = MaterialTheme.colorScheme.error)
                    day.rain?.takeIf { it >= TripPlan.RAIN_NOTE }?.let { Text(stringResource(R.string.trip_rain, it), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (packing.isEmpty()) item { Text(stringResource(R.string.capsule_no_items)) }
        items(packing, key = { it.garmentId }) { row ->
            s.garments.find { it.id == row.garmentId }?.let { g ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(row.packed, enabled = !busy && (row.packed || WardrobeRules.ready(g.rulePiece())), role = Role.Checkbox) { vm.pack(capsule.id, g.id, it) }
                        .padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(row.packed, onCheckedChange = null, enabled = !busy && (row.packed || WardrobeRules.ready(g.rulePiece())))
                        ItemPhoto(g.photoId, vm, Modifier.size(72.dp), description = g.name)
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(label(g.category)))
                            if (!WardrobeRules.ready(g.rulePiece())) Text(stringResource(if (g.archived) R.string.archive else label(g.availability)), color = MaterialTheme.colorScheme.error)
                            if (row.manual) Text(stringResource(R.string.capsule_manual_item), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { open(g) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.item_details)) }
                    }
                }
            }
        }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { resetting = true }, enabled = !busy && packing.any { it.packed }) { Text(stringResource(R.string.capsule_reset)) }
            TextButton(onClick = { deleting = true }, enabled = !busy) { Text(stringResource(R.string.delete)) }
        } }
    }
    suggestion?.let { proposal ->
        AlertDialog(onDismissRequest = { suggestion = null }, title = { Text(stringResource(R.string.trip_suggest)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (proposal.add.isEmpty()) R.string.trip_suggest_enough else R.string.trip_suggest_add,
                    proposal.add.size, proposal.covered, proposal.days))
                proposal.add.forEach { g -> Text("• " + g.name + " — " + (s.customCategory(g)?.name ?: stringResource(label(g.category)))) }
                if (proposal.covered < proposal.days) Text(stringResource(R.string.trip_suggest_gaps), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.trip_suggest_hint), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            if (proposal.add.isNotEmpty()) TextButton(onClick = { vm.addCapsuleItems(capsule.id, proposal.add.map { it.id }); suggestion = null }, enabled = !busy) {
                Text(stringResource(R.string.trip_suggest_confirm, proposal.add.size)) }
            else TextButton(onClick = { suggestion = null }) { Text(stringResource(R.string.feedback_ok)) }
        }, dismissButton = { if (proposal.add.isNotEmpty()) TextButton(onClick = { suggestion = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (editing) CapsuleEditor(capsule, capsule.wardrobeId, vm, { editing = false }) { editing = false }
    if (selectingOutfits) {
        val outfits = s.outfits.filter { it.wardrobeId == capsule.wardrobeId && (it.id in linked || s.parts.any { p -> p.outfitId == it.id } && s.ideas.none { p -> p.outfitId == it.id }) }
        VisualPickerDialog(R.string.capsule_outfits, outfits.map { outfitOption(it,s) }, vm, { selectingOutfits = false }, selected = linked, enabled = !busy) { id, checked -> vm.linkCapsule(capsule.id,id,checked) }
    }
    if (selectingItems) {
        val garments = s.garments.filter { it.wardrobeId == capsule.wardrobeId && (!it.archived || packing.any { row -> row.garmentId == it.id && row.manual }) }
        VisualPickerDialog(R.string.capsule_extras, garments.map { garmentOption(it) }, vm, { selectingItems = false }, selected = packing.filter { it.manual }.map { it.garmentId }.toSet(), enabled = !busy) { id, checked -> vm.manualCapsule(capsule.id,id,checked) }
    }
    if (deleting || resetting) AlertDialog(onDismissRequest = { deleting = false; resetting = false }, text = { Text(stringResource(if (deleting) R.string.capsule_remove_hint else R.string.capsule_reset_hint)) },
        confirmButton = { TextButton(onClick = {
            if (deleting) vm.removeCapsule(capsule.id, removed) else vm.resetPacking(capsule.id)
            deleting = false; resetting = false
        }, enabled = !busy) { Text(stringResource(if (deleting) R.string.delete else R.string.capsule_reset)) } },
        dismissButton = { TextButton(onClick = { deleting = false; resetting = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun CapsuleEditor(original: Capsule?, wardrobe: String, vm: WardrobeViewModel, dismiss: () -> Unit, saved: (String) -> Unit) {
    val id = rememberSaveable { original?.id ?: newId() }
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(original?.kind ?: "capsule") }
    var start by rememberSaveable { mutableStateOf(original?.startDate.orEmpty()) }
    var end by rememberSaveable { mutableStateOf(original?.endDate.orEmpty()) }
    var dateField by remember { mutableStateOf("") }
    // TRIP-01: an optional city of the trip, searched without changing the daily weather's city.
    var cityName by rememberSaveable { mutableStateOf(original?.city.orEmpty()) }
    var latitude by rememberSaveable { mutableStateOf(original?.latitude) }
    var longitude by rememberSaveable { mutableStateOf(original?.longitude) }
    var query by rememberSaveable { mutableStateOf("") }
    var found by remember { mutableStateOf<List<WeatherCity>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val trip = kind == "trip"
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(stringResource(if (original == null) R.string.capsule_add else R.string.edit)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = TextLimits.input(it, 80) }, label = { Text(stringResource(R.string.name)) }, singleLine = true, enabled = !busy)
            Choice(kind, listOf("capsule", "trip"), { kind = it }, R.string.capsule_type, enabled = !busy,
                optionLabels = mapOf("capsule" to R.string.capsule_kind, "trip" to R.string.capsule_trip))
            TextButton(onClick = { dateField = "start" }, enabled = !busy) { Text(stringResource(R.string.capsule_start, if (start.isBlank()) "—" else dateLabel(start))) }
            TextButton(onClick = { dateField = "end" }, enabled = !busy) { Text(stringResource(R.string.capsule_end, if (end.isBlank()) "—" else dateLabel(end))) }
            if (start.isNotBlank() || end.isNotBlank()) TextButton(onClick = { start = ""; end = "" }, enabled = !busy) { Text(stringResource(R.string.capsule_clear_dates)) }
            if (!RoadmapRules.period(start, end)) Text(stringResource(R.string.capsule_date_error), color = MaterialTheme.colorScheme.error)
            if (trip) {
                Text(stringResource(R.string.trip_city_label), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.trip_city_hint), style = MaterialTheme.typography.bodySmall)
                if (cityName.isNotEmpty()) {
                    Text(stringResource(R.string.trip_city_selected, cityName))
                    TextButton(onClick = { cityName = ""; latitude = null; longitude = null }, enabled = !busy) { Text(stringResource(R.string.trip_city_remove)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = TextLimits.input(it, 100) }, Modifier.weight(1f), label = { Text(stringResource(R.string.weather_city)) },
                        singleLine = true, enabled = !busy && !searching)
                    TextButton(onClick = {
                        searching = true; searchFailed = false; found = null
                        scope.launch {
                            try { found = vm.weather.cities(query) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { searchFailed = true }
                            finally { searching = false }
                        }
                    }, enabled = !busy && !searching && query.trim().length in 2..100) { Text(stringResource(R.string.trip_city_search)) }
                }
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (searchFailed) Text(stringResource(R.string.trip_city_failed), color = MaterialTheme.colorScheme.error)
                if (found?.isEmpty() == true) Text(stringResource(R.string.trip_city_none))
                found.orEmpty().forEach { place ->
                    TextButton(onClick = { cityName = place.name; latitude = place.latitude; longitude = place.longitude; found = null; query = "" }, enabled = !busy) {
                        Text(place.name)
                    }
                }
                Text(stringResource(R.string.trip_private_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }, confirmButton = { TextButton(onClick = { vm.saveCapsule(Capsule(id, wardrobe, name, kind, start, end,
        city = if (trip) cityName else "", latitude = if (trip && cityName.isNotEmpty()) latitude else null,
        longitude = if (trip && cityName.isNotEmpty()) longitude else null, occasion = original?.occasion.orEmpty()), saved) },
        enabled = !busy && TextLimits.hasVisibleText(name) && RoadmapRules.period(start, end)) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    if (dateField.isNotBlank()) DateChoiceDialog((if (dateField == "start") start else end).ifBlank { LocalDate.now().toString() }, { dateField = "" }) {
        if (dateField == "start") { start = it; if (end.isBlank()) end = it } else { end = it; if (start.isBlank()) start = it }; dateField = ""
    }
}
