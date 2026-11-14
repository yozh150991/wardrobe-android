#!/usr/bin/env python3
"""TRIP-02: guard the trip-day coverage — the trip's items only, the same rules as the daily screen, nothing saved."""
from pathlib import Path
import re, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
failures = []
def check(condition, message):
    if not condition: failures.append(message)
def read(name): return (source / name).read_text(encoding='utf-8')

plan = read('outfits/TripPlan.kt')
check('val allowed = s.capsuleItems.filter { it.capsuleId == capsule.id }.map { it.garmentId }.toSet()' in plan, "only the trip's items")
check('DailyOutfits.daily(s, wardrobe, occasion, weather, date, seed, skip, allowed,' in plan, 'the same generator as the daily screen')
check('forecast?.temperature(date)?.let { WeatherRules.shifted(it, warmth.shift) }' in plan and '?: "any_weather"' in plan,
      'the forecast where it exists; otherwise no weather, said so')
check('val fresh = first(used)' in plan and 'first(emptySet())' in plan, 'a different outfit each day while there is one')
check('DailyDiagnostics.explain(' in plan, 'a day without an outfit says why')
check(not re.search(r'\b(save|insert|wear|schedule|toggle)\w*\(', plan.replace('DailyOutfits.daily', '')), 'nothing is saved or recorded as worn')
screen = read('ui/CapsuleScreen.kt')
check('withContext(Dispatchers.Default) { TripPlan.coverage(s, capsule, tripOccasion, today, tripForecast, choices) }' in screen, 'computed off the UI thread')
check('val tripForecast = if (tripCity != null) cityForecast.takeIf { weatherEnabled } else forecast.takeIf { weatherEnabled }' in screen, 'the forecast only when weather is on (TRIP-01: the trip city’s own when it has one)')
check('R.string.trip_forecast_city' in screen and 'R.string.trip_weather_unknown' in screen and 'R.string.trip_rain' in screen,
      'the forecast city, unknown days and the rain caveat are shown')
keys = ['trip_days_title', 'trip_days_hint', 'trip_covered', 'trip_forecast_city', 'trip_no_weather', 'trip_weather_known', 'trip_weather_unknown',
        'trip_repeated', 'trip_no_outerwear', 'trip_rain', 'trip_past', 'trip_over', 'trip_truncated',
        'trip_suggest', 'trip_suggest_add', 'trip_suggest_enough', 'trip_suggest_gaps', 'trip_suggest_hint', 'trip_suggest_confirm']
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(root / 'app/src/main/res' / folder / 'strings.xml').getroot().iter('string')}
    for key in keys:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
# TRIP-03: reuse first, from ready items of the wardrobe; added in one transaction, only on confirmation.
check('val pick = options.minByOrNull { look -> look.count { it !in chosen } } ?: continue' in plan and '.take(REUSE_CHOICES)' in plan,
      'among the best variants the one needing the fewest new items wins')
check('val chosen = inCapsule.toMutableSet()' in plan, 'items already in the capsule count as packed')
ops = read('data/CapsuleOperations.kt')
check('suspend fun Repository.addCapsuleItems' in ops and 'require(garmentIds.all { dao.garment(it)?.wardrobeId == capsule.wardrobeId })' in ops,
      'suggested items are added at once, only from this wardrobe')
check('vm.addCapsuleItems(capsule.id, proposal.add.map { it.id })' in screen, 'added only when the person confirms')
from trip_details_strings import STRINGS as TRIP_DETAILS  # TRIP-01 keys, checked by check_trip_details.py
for name in set(re.findall(r'R\.string\.(trip_\w+)', screen)):
    check(name in keys or name in TRIP_DETAILS, f'R.string.{name} is not in the checked key list')
if failures: raise SystemExit('FAIL trip:\n' + '\n'.join(failures))
print(f'PASS trip guards: {len(keys)} strings × 4 locales, trip items only, same rules, forecast honest, compact packing on confirmation')
