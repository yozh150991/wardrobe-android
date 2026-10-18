#!/usr/bin/env python3
"""Production resolution rules and actual photo replacement SQL; Android lifecycle runs separately."""
from pathlib import Path
import re, sqlite3, subprocess, tempfile
from host_schema import migrate

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/familywardrobe/app'
with tempfile.TemporaryDirectory(prefix='wardrobe-photo-resolution-') as output:
    files = [source / name for name in ['PhotoResolutionRules.java', 'PhotoPresentationGeometry.java', 'PhotoCutoutRules.java']]
    files += [root / 'app/src/test/java/com/familywardrobe/app/PhotoResolutionCases.java']
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output, *map(str, files)], check=True)
    subprocess.run(['java', '-Xmx256m', '-cp', output, 'com.familywardrobe.app.PhotoResolutionCases'], check=True)

sql = re.search(r'@Query\("([^"\n]+)"\)\s*suspend fun replaceProcessedPhoto', (source / 'Data.kt').read_text()).group(1)
checks = 0
def check(condition):
    global checks
    checks += 1
    assert condition, checks
def database():
    db = sqlite3.connect(':memory:')
    migrate(db, target=8)
    db.execute("INSERT INTO wardrobes (id,name,createdAt) VALUES ('wardrobe','Home',1)")
    db.execute("INSERT INTO garments (id,wardrobeId,name,category,color,size,location,description,photoId,season,style,availability,favorite,rarelyWorn,archived,createdAt) VALUES ('item','wardrobe','Renamed top','top','blue','L','box','keep notes','original','winter','casual','laundry',1,1,0,123)")
    return db
first, second = database(), database()
parameters = dict(id='item', wardrobe='wardrobe', source='original', replacement='candidate')
before = first.execute('SELECT * FROM garments').fetchone()
columns = [row[1] for row in first.execute('PRAGMA table_info(garments)')]
for overrides in [dict(wardrobe='other'), dict(id='missing'), dict(source='stale')]:
    check(first.execute(sql, parameters | overrides).rowcount == 0)
    check(first.execute('SELECT * FROM garments').fetchone() == before)
check(first.execute(sql, parameters).rowcount == 1)
after = first.execute('SELECT * FROM garments').fetchone()
check([c for c,a,b in zip(columns,before,after) if a != b] == ['photoId'])
check(first.execute(sql, parameters).rowcount == 0)  # repeated completion
check(second.execute('SELECT * FROM garments').fetchone() == before)  # account DB isolation
first.execute("UPDATE garments SET name='Edited meanwhile', photoId='manual' WHERE id='item'")
check(first.execute(sql, parameters).rowcount == 0)
check(first.execute("SELECT name,photoId FROM garments").fetchone() == ('Edited meanwhile','manual'))
first.execute("DELETE FROM garments WHERE id='item'")
check(first.execute(sql, parameters).rowcount == 0)
check(first.execute('SELECT count(*) FROM garments').fetchone()[0] == 0)  # never resurrect a removed item
first.close(); second.close()
print(f'PASS: {checks} actual SQLite replacement checks (stale, cross-wardrobe/account, metadata, repeat, deletion).')
