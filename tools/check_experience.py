#!/usr/bin/env python3
"""Execute production Java rules and migration 6→7. Kotlin/Android are separate checks."""
from pathlib import Path
import json, re, shutil, sqlite3, subprocess, tempfile
root = Path(__file__).resolve().parents[1]
source = root/'app/src/main/java/com/familywardrobe/app'
with tempfile.TemporaryDirectory(prefix='wardrobe-experience-') as output:
    names = ['WardrobeRules', 'DailyRules', 'PreferenceRules', 'LookSelection', 'RecommendationRules', 'PhotoMaskRules', 'CanvasRules', 'InspirationCatalog']
    sources = [source/(name+'.java') for name in names]
    sources.append(root/'app/src/test/java/com/familywardrobe/app/ExperienceRulesCases.java')
    subprocess.run([shutil.which('java'), '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str,sources)], check=True)
    subprocess.run([shutil.which('java'), '-cp', output, 'com.familywardrobe.app.ExperienceRulesCases'], check=True)
schema = json.loads((root/'app/schemas/com.familywardrobe.app.WardrobeDatabase/2.json').read_text())['database']
data = (source/'Data.kt').read_text()
def migration(v):
    block = data.split(f'val MIGRATION_{v}_{v+1}',1)[1].split('val MIGRATION_',1)[0]
    return re.findall(r'db.execSQL\("([^"\n]+)"\)',block)
db = sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')
for entity in schema['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    for index in entity.get('indices',[]): db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
for v in range(2,6):
    for sql in migration(v):db.execute(sql)
db.execute("INSERT INTO wardrobes VALUES ('home','Home',1,0)")
db.execute("INSERT INTO wardrobes VALUES ('other','Other',2,0)")
db.execute("INSERT INTO garments VALUES ('dress','home','Dress','other_item','navy','M','Shelf','Notes','photo','all','casual','available',1,0,0,1,10000,'EUR')")
db.execute("INSERT INTO outfits VALUES ('look','home','Look','dress',1,'everyday','any_weather','owned')")
db.execute("INSERT INTO outfit_parts VALUES ('look','dress')")
db.execute("INSERT INTO wears VALUES ('dress','2026-10-02','item:dress')")
db.execute("INSERT INTO import_drafts VALUES ('draft','home','batch',0,'content://photo','','','Draft','','other','all','casual','queued',1)")
db.execute("INSERT INTO capsules VALUES ('capsule','home','Trip','trip','','')")
db.execute("INSERT INTO capsule_outfits VALUES ('capsule','look')")
db.execute("INSERT INTO capsule_items VALUES ('capsule','dress',0,1)")
tables = ['wardrobes','garments','outfits','outfit_parts','wears','import_drafts','capsules','capsule_outfits','capsule_items']
before = {table:db.execute(f'SELECT * FROM {table}').fetchall() for table in tables}
statements = migration(6);assert len(statements)==7
for sql in statements:db.execute(sql)
for table, rows in before.items():
    current = db.execute(f'SELECT * FROM {table}').fetchall()
    assert [row[:len(old)] for row,old in zip(current,rows)]==rows,table
assert db.execute('SELECT noRepeatDays FROM wardrobes').fetchall()==[(0,),(0,)]
assert db.execute('SELECT subcategory,suggestionExcluded,photoFingerprint FROM garments').fetchone()==('',0,'')
assert db.execute('SELECT layout,background FROM outfits').fetchone()==('','paper')
query = re.search(r'@Query\("([^"]+)"\) suspend fun setNoRepeatDays', data).group(1)
db.execute(query,{'days':7,'id':'home'})
assert db.execute("SELECT noRepeatDays FROM wardrobes WHERE id='other'").fetchone()[0]==0
db.execute("UPDATE garments SET subcategory='Midi',suggestionExcluded=1,photoFingerprint=? WHERE id='dress'",('a'*64,))
assert db.execute('SELECT priceMinor,currency FROM garments').fetchone()==(10000,'EUR')
assert db.execute('SELECT packed FROM capsule_items').fetchone()[0]==1
assert db.execute('SELECT source FROM wears').fetchone()[0]=='item:dress'
assert not db.execute('PRAGMA foreign_key_check').fetchall()
print('PASS: actual schema 2→7 migrations preserve all seeded clothing, wear, looks, pending import and packed capsule rows; new defaults and per-wardrobe DAO updates verified.')
print('NOT RUN: Kotlin, Room code generation, WorkManager/provider, native masks, Compose gestures, APK and device checks.')
