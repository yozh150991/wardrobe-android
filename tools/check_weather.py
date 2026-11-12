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
check('fun mode(date: String): String? = temperature(date)?.let { RoadmapRules.weatherMode(it) }' in weather, 'the mode is the band of that temperature')
daily = read('DailyScreen.kt')
check('forecast?.temperature(today)?.let { WeatherRules.shifted(it, warmth.shift) } else null' in daily, 'the person\'s warmth moves the temperature')
check('val weather = temperature?.let { RoadmapRules.weatherMode(it) } ?: manualWeather' in daily, 'a weather chosen by hand has no temperature')
check(daily.count('temperature = temperature') == 2 and 'rotationState, temperature)' in daily and daily.count('choices, temperature)') == 3,
      'variants, «Давно не вдягали», «Краще» and replacing a piece get the temperature')
check(daily.count('OutfitStyle.rate(') == 2 and daily.count(', target, temperature) }') == 2, 'both ratings get the temperature')
check('remember(wardrobe, occasion, weather, temperature, today,' in daily, 'a new temperature recomputes the variants')
rules = read('DailyRules.java')
check('if (WEATHER_PIECES.contains(category) && !fixed.containsKey(category)) continue;' in rules, 'what the weather adds is optional unless pinned')
check('WeatherRules.inSeason(p.season, weather, temperature)' in rules and 'compatibleSeasons(selected.piece.season, next.piece.season)' in rules,
      'seasons by temperature; pieces of different seasons are still never mixed')
if failures: raise SystemExit('FAIL weather:\n' + '\n'.join(failures))

with tempfile.TemporaryDirectory(prefix='wardrobe-weather-') as output:
    files = [source / name for name in ('WardrobeRules.java', 'WishRules.java', 'DailyRules.java', 'PreferenceRules.java', 'PreferenceProfile.java',
                                        'StyleRules.java', 'WeatherRules.java', 'RoadmapRules.java', 'RotationRules.java')]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/WeatherRulesCases.java')
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.WeatherRulesCases'], check=True)
