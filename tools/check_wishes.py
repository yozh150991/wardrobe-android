#!/usr/bin/env python3
"""Executes production money/URL rules and real v2->v3 migration against exported v2 SQL."""
from pathlib import Path
import json,re,shutil,sqlite3,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
java=shutil.which('java')
if not java: raise SystemExit('JDK 17 required')
with tempfile.TemporaryDirectory(prefix='wardrobe-wish-') as output:
    sources=[root/'app/src/main/java/com/familywardrobe/app'/name for name in ('WishRules.java','WardrobeRules.java','LookSelection.java')]
    sources += [root/'app/src/test/java/com/familywardrobe/app'/name for name in ('WishRulesCases.java','LookSelectionCases.java')]
    subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',output,*map(str,sources)],check=True)
    subprocess.run([java,'-cp',output,'com.familywardrobe.app.WishRulesCases'],check=True)
    subprocess.run([java,'-cp',output,'com.familywardrobe.app.LookSelectionCases'],check=True)
schema=json.loads((root/'app/schemas/com.familywardrobe.app.WardrobeDatabase/2.json').read_text())['database']
source=(root/'app/src/main/java/com/familywardrobe/app/Data.kt').read_text().split('val MIGRATION_2_3',1)[1]
sql=re.findall(r'db.execSQL\("([^"\n]+)"\)',source)
db=sqlite3.connect(':memory:',isolation_level=None);db.execute('PRAGMA foreign_keys=ON')
for entity in schema['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
    for index in entity.get('indices', []): db.execute(index['createSql'].replace('${TABLE_NAME}',entity['tableName']))
db.execute("INSERT INTO wardrobes(id,name,createdAt,usesDefaultName) VALUES ('home','Home',1,0),('other','Other',2,0)")
db.execute("INSERT INTO wishes VALUES ('need','home','Rain jacket','outerwear')")
db.execute("INSERT INTO outfits VALUES ('look','home','Planned','wish:need',2)")
db.execute('BEGIN')
for statement in sql: db.execute(statement)
db.execute('COMMIT')
wish=db.execute('SELECT id,name,category,kind,needName,needCategory,priceMinor,budgetMinor,currency,budgetCurrency FROM wishes').fetchone()
assert wish==('need','Rain jacket','outerwear','need','Rain jacket','outerwear',None,None,'PLN','PLN')
db.execute("UPDATE wishes SET requirements='Waterproof',budgetMinor=35000,budgetCurrency='PLN' WHERE id='need'")
db.execute("INSERT INTO outfit_ideas VALUES ('look','wish:need','need','outerwear','Rain jacket','','')")
# Simulate the transactional update's DB boundary. No assertion that Kotlin/Room has executed.
db.execute('BEGIN')
db.execute("UPDATE wishes SET kind='product',name='Blue jacket',priceMinor=8900,currency='EUR',link='https://example.org',color='blue',inspiration='Rainy walks' WHERE id='need'")
db.execute('COMMIT')
assert db.execute('SELECT COUNT(*) FROM wishes').fetchone()==(1,)
assert db.execute('SELECT needName,requirements,budgetMinor,budgetCurrency,priceMinor,currency FROM wishes').fetchone()==('Rain jacket','Waterproof',35000,'PLN',8900,'EUR')
assert db.execute('SELECT wishId FROM outfit_ideas').fetchone()==('need',)
# Cancellation / failed save cannot commit partial metadata.
before=db.execute('SELECT * FROM wishes').fetchall();db.execute('BEGIN');db.execute("UPDATE wishes SET priceMinor=1 WHERE id='need'");db.execute('ROLLBACK');assert db.execute('SELECT * FROM wishes').fetchall()==before
db.execute("DELETE FROM wishes WHERE id='need'")
assert db.execute('SELECT wishId,name FROM outfit_ideas').fetchone()==(None,'Rain jacket')
db.execute("DELETE FROM outfits WHERE id='look'");assert not db.execute('SELECT * FROM outfit_ideas').fetchall()
assert not db.execute('PRAGMA foreign_key_check').fetchall()
columns={r[1] for r in db.execute('PRAGMA table_info(wishes)')}
assert len(columns)==17 and {'priceMinor','budgetMinor','needName','inspiration','photoId'}<=columns
assert db.execute('SELECT name FROM wardrobes ORDER BY createdAt').fetchall()==[('Home',),('Other',)]
print(f'PASS: {len(sql)} real migration statements on exported v2 schema; stable IDs, original budget, rollback, planned-reference SET NULL and CASCADE checks.')
print('NOT RUN: Kotlin, Room-generated schema validation, Compose, Firebase and Android runtime tests.')
