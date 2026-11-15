#!/usr/bin/env python3
"""WTH-01 / GEN-04: run the production weather, generator and rating rules with their cases (JDK 17), and guard the wiring."""
from pathlib import Path
import shutil, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
java = shutil.which('java')
if not java: raise SystemExit('Java 17 with jdk.compiler required')
failures = []
def check(condition, message):
    if not condition: failures.append(message)
def read(name): return (source / name).read_text(encoding='utf-8')

# The saved weather stays one of five modes: no new value reaches backups, shared wardrobes or stored reasons.
check('val WEATHERS = listOf("any_weather", "warm", "cool", "cold", "rain")' in read('LookRules.kt'), 'the saved weather modes are unchanged')
check("WEATHERS = 'any_weather warm cool cold rain'.split()" in (root / 'cloud/family-functions/family/schema.py').read_text(), 'the server modes are unchanged')
weather = read('Weather.kt')
check('WeatherRules.today(it.low, it.high, feelsLike)' in weather and 'WeatherRules.day(it.low, it.high)' in weather, 'the forecast gives a temperature to dress for')
check('fun mode(date: String): String? = temperature(date)?.let { WeatherRules.mode(it, rain(date)) }' in weather, 'the mode is the band of that temperature (WTH-02: mild and rainy — «Дощ»)')
check('fun rain(date: String): Int? = if (temperature(date) == null) null else' in weather, 'rain follows the forecast\'s freshness')
daily = read('DailyScreen.kt')
check('forecast?.temperature(today)?.let { WeatherRules.shifted(it, warmth.shift) } else null' in daily, 'the person\'s warmth moves the temperature')
check('val weather = temperature?.let { WeatherRules.mode(it, rainChance) } ?: manualWeather' in daily, 'a weather chosen by hand has no temperature')
check('val rainChance = if (temperature != null) forecast?.rain(today) else null' in daily, 'rain only with the forecast, never over a manual choice')
check('R.string.weather_rain_auto, rainChance' in daily, 'the screen says why it is «Дощ»')
trip = (source / 'TripPlan.kt').read_text(encoding='utf-8')
check(trip.count('WeatherRules.mode(it, forecast?.rain(date))') == 2, 'trip days use the same rain rule')
check(daily.count('temperature = temperature') == 2 and 'rotationState, temperature)' in daily and daily.count('choices, temperature)') == 3,
      'variants, «Давно не вдягали», «Краще» and replacing a piece get the temperature')
check(daily.count('OutfitStyle.rate(') == 2 and daily.count(', target, temperature) }') == 2, 'both ratings get the temperature')
check('remember(wardrobe, occasion, weather, temperature, today,' in daily, 'a new temperature recomputes the variants')
rules = read('DailyRules.java')
check('if (WEATHER_PIECES.contains(category) && !fixed.containsKey(category)) continue;' in rules, 'what the weather adds is optional unless pinned')
check('WeatherRules.inSeason(p.season, weather, temperature)' in rules and 'compatibleSeasons(selected.piece.season, next.piece.season)' in rules,
      'seasons by temperature; pieces of different seasons are still never mixed')
# WTH-02: the rain line in every locale, with its one number.
import re as _re, xml.etree.ElementTree as _E
from rain_strings import STRINGS as _RAIN
for _folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    _strings = {s.get('name'): ''.join(s.itertext()) for s in _E.parse(root / 'app/src/main/res' / _folder / 'strings.xml').getroot().iter('string')}
    for _key in _RAIN:
        check(_re.findall(r'%\d\$[sd]', _strings.get(_key, '')) == ['%1$d'] and '%%' in _strings.get(_key, ''), f'{_folder}: {_key}')
if failures: raise SystemExit('FAIL weather:\n' + '\n'.join(failures))

with tempfile.TemporaryDirectory(prefix='wardrobe-weather-') as output:
    files = [source / name for name in ('WardrobeRules.java', 'WishRules.java', 'DailyRules.java', 'PreferenceRules.java', 'PreferenceProfile.java',
                                        'StyleRules.java', 'WeatherRules.java', 'RoadmapRules.java', 'RotationRules.java')]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/WeatherRulesCases.java')
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.WeatherRulesCases'], check=True)
