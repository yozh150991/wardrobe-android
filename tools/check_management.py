"""Host-side checks of the real migration/DAO SQL, with SQLite foreign keys enabled.

Does not compile Kotlin, run Room, or exercise the Android UI.
"""
from pathlib import Path
import re
import sqlite3

source = (Path(__file__).resolve().parents[1] / 'app/src/main/java/com/familywardrobe/app/Data.kt').read_text()
queries = dict((name, sql) for sql, name in re.findall(r'@Query\("([^"\n]+)"\)\s*(?:suspend\s+)?fun\s+(\w+)', source))
migration = re.findall(r'db.execSQL\("([^"\n]+)"\)', source.split('val MIGRATION_1_2',1)[1].split('val MIGRATION_2_3',1)[0])

def fixture():
    db = sqlite3.connect(':memory:', isolation_level=None)
    db.execute('PRAGMA foreign_keys=ON')
    db.executescript('''
CREATE TABLE wardrobes(id TEXT PRIMARY KEY,name TEXT NOT NULL,createdAt INTEGER NOT NULL);
CREATE TABLE garments(id TEXT PRIMARY KEY,wardrobeId TEXT NOT NULL,photoId TEXT NOT NULL,
 FOREIGN KEY(wardrobeId) REFERENCES wardrobes(id) ON DELETE RESTRICT);
CREATE TABLE outfits(id TEXT PRIMARY KEY,wardrobeId TEXT NOT NULL,
 FOREIGN KEY(wardrobeId) REFERENCES wardrobes(id) ON DELETE RESTRICT);
CREATE TABLE outfit_parts(outfitId TEXT,garmentId TEXT,PRIMARY KEY(outfitId,garmentId),
 FOREIGN KEY(outfitId) REFERENCES outfits(id) ON DELETE CASCADE,
 FOREIGN KEY(garmentId) REFERENCES garments(id) ON DELETE RESTRICT);
CREATE TABLE wears(garmentId TEXT,date TEXT,source TEXT,PRIMARY KEY(garmentId,date,source),
 FOREIGN KEY(garmentId) REFERENCES garments(id) ON DELETE CASCADE);
CREATE TABLE wishes(id TEXT PRIMARY KEY,wardrobeId TEXT NOT NULL,
 FOREIGN KEY(wardrobeId) REFERENCES wardrobes(id) ON DELETE CASCADE);
INSERT INTO wardrobes VALUES ('00000000-0000-0000-0000-000000000001','Moja garderoba',1),('keep','Family',2);
INSERT INTO garments VALUES ('a','00000000-0000-0000-0000-000000000001','shared'),('b','00000000-0000-0000-0000-000000000001','remove'),('c','keep','shared');
INSERT INTO outfits VALUES ('remove-outfit','00000000-0000-0000-0000-000000000001'),('keep-outfit','keep');
INSERT INTO outfit_parts VALUES ('remove-outfit','a'),('remove-outfit','b'),('keep-outfit','c');
INSERT INTO wears VALUES ('a','2026-09-22','outfit:remove-outfit'),('b','2026-09-22','item:b'),('c','2026-09-22','outfit:keep-outfit');
INSERT INTO wishes VALUES ('remove-wish','00000000-0000-0000-0000-000000000001'),('keep-wish','keep');
''')
    return db

def contents(db):
    return {t: db.execute(f'SELECT * FROM {t} ORDER BY 1').fetchall() for t in
            ['wardrobes','garments','outfits','outfit_parts','wears','wishes']}

target = '00000000-0000-0000-0000-000000000001'
db = fixture()
before = contents(db)
assert len(migration) == 2
for sql in migration:
    db.execute(sql)
assert db.execute('SELECT name, usesDefaultName FROM wardrobes WHERE id=?',(target,)).fetchone() == ('Moja garderoba',1)
assert db.execute('SELECT usesDefaultName FROM wardrobes WHERE id="keep"').fetchone() == (0,)
assert contents(db)['garments'] == before['garments']
db.execute(queries['renameWardrobe'], {'id':target,'name':'My custom name'})
assert db.execute('SELECT name, usesDefaultName FROM wardrobes WHERE id=?',(target,)).fetchone() == ('My custom name',0)
db.execute('BEGIN')
for operation in ['deleteWardrobeOutfits','deleteWardrobeGarments','deleteWardrobeRow']:
    db.execute(queries[operation], {'wardrobe':target,'id':target})
db.execute('COMMIT')
after = contents(db)
assert [r[0] for r in after['wardrobes']] == ['keep']
assert after['garments'] == [('c','keep','shared')]
assert after['outfits'] == [('keep-outfit','keep')]
assert after['outfit_parts'] == [('keep-outfit','c')]
assert after['wears'] == [('c','2026-09-22','outfit:keep-outfit')]
assert after['wishes'] == [('keep-wish','keep')]
assert not db.execute('PRAGMA foreign_key_check').fetchall()
db.close()

db = fixture()
before = contents(db)
db.execute("CREATE TRIGGER fail_delete BEFORE DELETE ON garments BEGIN SELECT RAISE(ABORT, 'simulated storage failure'); END")
db.execute('BEGIN')
try:
    for operation in ['deleteWardrobeOutfits','deleteWardrobeGarments','deleteWardrobeRow']:
        db.execute(queries[operation], {'wardrobe':target,'id':target})
except sqlite3.IntegrityError:
    db.execute('ROLLBACK')
else:
    raise AssertionError('Expected injected deletion failure')
assert contents(db) == before
db.close()
print('PASS: real DAO deletion SQL isolates wardrobes and removes dependents; failure rolls back; migration preserves data; rename disables automatic naming.')
print('NOT RUN: Room schema validation on Android, Kotlin compilation, Android tests and UI.')
