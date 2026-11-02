#!/usr/bin/env python3
"""Execute production Java rules and real Room SQL migrations; no Android or Kotlin claims."""
from pathlib import Path
import json, re, shutil, sqlite3, subprocess, tempfile
root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
java = shutil.which('java')
if not java: raise SystemExit('Java 17 with jdk.compiler required')
with tempfile.TemporaryDirectory(prefix='wardrobe-roadmap-') as output:
    files = [source / name for name in ('WardrobeRules.java', 'WishRules.java', 'DailyRules.java', 'PreferenceRules.java', 'RoadmapRules.java')]
    files.append(root / 'app/src/test/java/com/familywardrobe/app/RoadmapRulesCases.java')
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)], check=True)
    subprocess.run([java, '-cp', output, 'com.familywardrobe.app.RoadmapRulesCases'], check=True)
schema = json.loads((root / 'app/schemas/com.familywardrobe.app.WardrobeDatabase/2.json').read_text())['database']
data = (source / 'Data.kt').read_text()
def migration(version):
    body = data.split(f'val MIGRATION_{version}_{version+1}', 1)[1].split('val MIGRATION_', 1)[0]
    return re.findall(r'db.execSQL\("([^"\n]+)"\)', body)
db = sqlite3.connect(':memory:', isolation_level=None)
db.execute('PRAGMA foreign_keys=ON')
for entity in schema['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    for index in entity.get('indices', []): db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
for version in (2, 3):
    for statement in migration(version): db.execute(statement)
db.execute("INSERT INTO wardrobes VALUES ('home','Home',1,0)")
db.execute("INSERT INTO garments(id,wardrobeId,name,category,color,size,location,description,photoId,season,style,availability,favorite,rarelyWorn,archived,createdAt) VALUES ('top','home','Top','top','navy','','','','photo','all','casual','available',0,0,0,1)")
db.execute("INSERT INTO outfits(id,wardrobeId,name,signature,createdAt,occasion,weather,source) VALUES ('look','home','Look','top',1,'everyday','any_weather','owned')")
db.execute("INSERT INTO outfit_parts VALUES ('look','top')")
db.execute("INSERT INTO wears VALUES ('top','2026-10-01','item:top')")
db.execute("INSERT INTO wishes(id,wardrobeId,name,category,kind,priceMinor,currency,ownedId) VALUES ('purchase','home','Top','top','product',8800,'EUR','top')")
before = db.execute('SELECT * FROM garments').fetchall()
for version in (4, 5):
    statements = migration(version); assert statements
    for statement in statements: db.execute(statement)
after = db.execute('SELECT * FROM garments').fetchall()
assert after[0][:-2] == before[0] and after[0][-2:] == (8800, 'EUR')
assert db.execute('SELECT * FROM wears').fetchone() == ('top', '2026-10-01', 'item:top')
assert db.execute('SELECT * FROM outfit_parts').fetchone() == ('look','top')
# Planning must not create actual wear; the production schema enforces unique plans.
db.execute("INSERT INTO day_plans VALUES ('plan','home','look','2026-10-01','',0,'')")
assert db.execute('SELECT COUNT(*) FROM wears').fetchone()[0] == 1
try:
    db.execute("INSERT INTO day_plans VALUES ('duplicate','home','look','2026-10-01','',0,'')")
    raise AssertionError('Duplicate day plan accepted')
except sqlite3.IntegrityError: pass
db.execute("INSERT INTO wears VALUES ('top','2026-10-01','plan:plan')")
assert db.execute('SELECT COUNT(DISTINCT date) FROM wears WHERE garmentId=\'top\'').fetchone()[0] == 1
db.execute("DELETE FROM wears WHERE source='plan:plan' AND date='2026-10-01'")
assert db.execute('SELECT source FROM wears').fetchone()[0] == 'item:top'
# A shared item appears only once and cannot be deleted while a capsule references it.
db.execute("INSERT INTO capsules VALUES ('trip','home','Trip','trip','2026-10-01','2026-10-08')")
db.execute("INSERT INTO capsule_outfits VALUES ('trip','look')")
db.execute("INSERT INTO capsule_items VALUES ('trip','top',0,1)")
try:
    db.execute("INSERT INTO capsule_items VALUES ('trip','top',1,0)")
    raise AssertionError('Duplicate packing item accepted')
except sqlite3.IntegrityError: pass
try:
    db.execute("DELETE FROM garments WHERE id='top'")
    raise AssertionError('Referenced item deleted')
except sqlite3.IntegrityError: pass
db.execute("DELETE FROM capsules WHERE id='trip'")
assert not db.execute('SELECT * FROM capsule_items').fetchall()
assert not db.execute('SELECT * FROM capsule_outfits').fetchall()
assert db.execute('SELECT name FROM garments').fetchone()[0] == 'Top'
assert not db.execute('PRAGMA foreign_key_check').fetchall()
print('PASS: production Room migrations 4→5→6 retain garments, wear and looks, carry purchase prices, and enforce calendar/packing keys and cascades.')
print('NOT RUN: Kotlin, generated Room validation, native photo queue, live weather, Compose/device UI or APK.')
