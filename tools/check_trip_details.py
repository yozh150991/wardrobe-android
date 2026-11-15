#!/usr/bin/env python3
"""TRIP-01 (0.14.44): a trip's own city and saved occasion — Room 11, backup format 11 only with details, the cloud
head's format never goes down, shared wardrobes stay format 10 without the details."""
from pathlib import Path
import json, re, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
failures = []
def check(condition, message):
    if not condition: failures.append(message)
def read(name): return (source / name).read_text(encoding='utf-8')

# Data: four new capsule columns with a migration and an exported schema 11.
data = read('data/RoadmapData.kt'); db = read('Data.kt')
check('@ColumnInfo(defaultValue = "") val city: String = "", val latitude: Double? = null, val longitude: Double? = null' in data, 'capsule city and coordinates')
check('@ColumnInfo(defaultValue = "") val occasion: String = ""' in data, 'capsule occasion')
check('city.isEmpty() == (latitude == null)' in data and '(latitude == null) == (longitude == null)' in data and 'occasion in LookRules.OCCASIONS' in data,
      'a city needs both coordinates; the occasion is a known one')
check('version = 11' in db and 'MIGRATION_10_11' in db.split('ALL_MIGRATIONS', 1)[1], 'Room 11 with its migration in the chain')
for column in ("ADD COLUMN city TEXT NOT NULL DEFAULT ''", 'ADD COLUMN latitude REAL', 'ADD COLUMN longitude REAL', "ADD COLUMN occasion TEXT NOT NULL DEFAULT ''"):
    check(column in db, f'migration adds {column}')
schema = json.loads((root / 'app/schemas/com.familywardrobe.app.WardrobeDatabase/11.json').read_text(encoding='utf-8'))
capsules = next(e for e in schema['database']['entities'] if e['tableName'] == 'capsules')
check([f['columnName'] for f in capsules['fields']][-4:] == ['city', 'latitude', 'longitude', 'occasion'], 'schema 11 has the capsule columns')
check(schema['database']['version'] == 11, 'schema 11 is version 11')
from room_identity import identity_hash
for exported in sorted((root / 'app/schemas/com.familywardrobe.app.WardrobeDatabase').glob('*.json')):
    database = json.loads(exported.read_text(encoding='utf-8'))['database']
    check(identity_hash(database) == database['identityHash'], f'{exported.name}: identityHash is not the one Room computes')
    check(any(database['identityHash'] in q for q in database['setupQueries']), f'{exported.name}: room_master_table hash differs')

# Backup: format 11 only with details; the readers accept it; validation on save and restore.
backup = read('backup/Backup.kt')
check('const val CURRENT = 11' in backup, 'the app writes and reads format 11')
check('if (maximum >= 11 && s.capsules.any { it.hasTripDetails }) 11 else 10' in backup, 'format 11 only when a capsule has details')
check('if (version >= 11) put("city", it.city)' in backup, 'details are written only in format 11')
check('j.getInt("version") in 1..CURRENT' in backup and backup.count('if (j.getInt("version") >= 11)') == 2, 'format 11 is read, older formats get empty details')
check('&& it.validTripDetails })' in read('backup/BackupStateRules.kt') and '&& value.validTripDetails)' in read('data/CapsuleOperations.kt'), 'details are validated on restore and save')
check('fingerprint, json.getInt("version"))' in backup and 'val format: Int = 10' in read('BackupHealth.kt'), 'verification reports the format')

# Cloud: the head carries the archive's format and never lowers it; the rules accept 11.
cloud = read('cloud/CloudBackend.kt'); sync = read('CloudSync.kt')
check('require(it in 8L..Backup.CURRENT.toLong())' in cloud and 'val format: Int = 10)' in cloud, 'the head is read up to format 11')
check(cloud.count('maxOf(existing?.format ?: 10, value.format, 10)') == 2, 'the head format never goes down')
check('v.outfits,v.format))' in sync and 'verification.outfits,verification.format))' in sync, 'both commits pass the archive format')
check('in 8..Backup.CURRENT)' in read('cloud/CloudArchive.kt'), 'a format 11 cloud archive is restored')
rules = (root / 'cloud/firestore.rules').read_text(encoding='utf-8')
check('d.format in [8, 9, 10, 11]' in rules and 'request.resource.data.format >= resource.data.format' in rules, 'rules accept 11 and forbid downgrades')
check("format: 12" in (root / 'cloud/rules.test.mjs').read_text(encoding='utf-8'), 'the rules test covers 11 and an unknown 12')

# Family: every server-bound copy is format 10, so the details never leave the owner's own copies.
check('backup.encode(state, FamilyRules.CONTENT_VERSION)' in read('family/FamilyEnvelope.kt'), 'shared copies are format 10')
check('encode(chosen, FamilyRules.CONTENT_VERSION)' in read('family/FamilyAddArchive.kt') and 'encode(single, FamilyRules.CONTENT_VERSION)' in read('family/FamilyOutfitStore.kt'),
      'family additions and outfit changes are format 10')
for name in source.rglob('Family*.kt'):
    text = name.read_text(encoding='utf-8')
    for call in re.findall(r'Backup\([^)]*\)\.encode\(([^()]*(?:\([^()]*\))?[^()]*)\)', text) + re.findall(r'backup\.encode\(([^()]*)\)', text):
        check('CONTENT_VERSION' in call, f'{name.name}: family encode without the format 10 cap: {call}')

# Weather: the trip city's forecast never changes the daily weather's city or cache.
weather = read('outfits/Weather.kt')
check('suspend fun cities(query: String)' in weather and 'suspend fun forecastFor(place: WeatherCity)' in weather, 'standalone search and forecast')
body = weather.split('suspend fun forecastFor', 1)[1].split('fun select', 1)[0]
check('city.value' not in body and 'prefs' not in body and 'forecast.value' not in body, 'the trip forecast leaves the daily weather alone')
check('const val TRIP_DAYS = 16' in weather and 'forecast_days=$days' in weather, 'up to 16 days for a trip')

# Screens: the occasion is saved; the trip city is used only with the weather on.
screen = read('ui/CapsuleScreen.kt')
check('val tripOccasion = capsule.occasion.ifEmpty { "everyday" }' in screen, 'the saved occasion is used')
check('capsule.copy(occasion = if (it == "everyday") "" else it)' in screen, 'the default occasion is not stored (format stays 10)')
check('if (weatherEnabled && tripCity != null)' in screen and 'cityForecast.takeIf { weatherEnabled }' in screen, 'the trip city forecast only with the weather on')
check('vm.weather.cities(query)' in screen and 'R.string.trip_private_hint' in screen, 'city search and the privacy note in the editor')

from trip_details_strings import STRINGS
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(root / 'app/src/main/res' / folder / 'strings.xml').getroot().iter('string')}
    for key in STRINGS:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
for name in set(re.findall(r'R\.string\.(trip_city_\w+|trip_private_hint)', screen)):
    check(name in STRINGS, f'R.string.{name} is not in trip_details_strings.py')
if failures: raise SystemExit('FAIL trip details:\n' + '\n'.join(failures))
print(f'PASS trip-details guards: Room 11, format 11 only with details, head never lowers, family stays 10, {len(STRINGS)} strings × 4 locales')
