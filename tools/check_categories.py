#!/usr/bin/env python3
"""Exercise production Java search and production SQL migration/guards. Not an Android run."""
from pathlib import Path
import sqlite3, subprocess, tempfile
from host_schema import SOURCE, migrate

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='wardrobe-categories-') as output:
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output,
                    str(SOURCE / 'CategoryRules.java'),
                    str(ROOT / 'app/src/test/java/com/familywardrobe/app/CategoryCases.java')], check=True)
    subprocess.run(['java', '-cp', output, 'com.familywardrobe.app.CategoryCases'], check=True)
    path = Path(output) / 'migration.db'
    db = sqlite3.connect(path, isolation_level=None)
    db.execute('PRAGMA foreign_keys=ON')
    migrate(db, target=8)
    db.execute("INSERT INTO wardrobes VALUES ('a','A',1,0,0)")
    db.execute("INSERT INTO wardrobes VALUES ('b','B',2,0,0)")
    db.execute("INSERT INTO garments(id,wardrobeId,name,category,color,size,location,description,photoId,season,style,availability,favorite,rarelyWorn,archived,createdAt,subcategory) VALUES ('item','a','Shoe','shoes','navy','','','','photo','all','casual','available',0,0,0,1,'Trail')")
    db.execute("INSERT INTO outfits(id,wardrobeId,name,signature,createdAt) VALUES ('look','a','Look','item',1)")
    db.execute("INSERT INTO outfit_parts VALUES ('look','item')")
    db.execute("INSERT INTO wears VALUES ('item','2026-10-12','item:item')")
    before = {table: db.execute(f'SELECT * FROM {table}').fetchall() for table in ('garments','wardrobes','outfits','outfit_parts','wears')}
    migrate(db, start=8, target=9)
    for table, rows in before.items():
        assert [r[:len(rows[0])] for r in db.execute(f'SELECT * FROM {table}').fetchall()] == rows
    assert db.execute('SELECT customCategoryId FROM garments').fetchone() == (None,)
    assert db.execute("SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'sync_%'").fetchone()[0] == 45
    db.execute('UPDATE cloud_sync_meta SET dirty=0')
    revision = db.execute('SELECT localRevision FROM cloud_sync_meta').fetchone()[0]
    db.execute("INSERT INTO custom_categories VALUES ('cat-a','a','Running','running','shoes')")
    assert db.execute('SELECT dirty,localRevision FROM cloud_sync_meta').fetchone() == (1, revision + 1)
    db.execute("INSERT INTO custom_categories VALUES ('cat-b','b','Running','running','shoes')")
    db.execute("UPDATE garments SET customCategoryId='cat-a' WHERE id='item'")
    rejected = 0
    def deny(sql):
        global rejected
        try: db.execute(sql)
        except sqlite3.IntegrityError: rejected += 1
        else: raise AssertionError('Invalid category write accepted: ' + sql)
    deny("INSERT INTO custom_categories VALUES ('duplicate','a','RUNNING','running','top')")
    deny("UPDATE garments SET customCategoryId='cat-b' WHERE id='item'")
    deny("UPDATE garments SET customCategoryId='missing' WHERE id='item'")
    deny("UPDATE garments SET category='top' WHERE id='item'")
    deny("UPDATE garments SET wardrobeId='b' WHERE id='item'")
    deny("DELETE FROM custom_categories WHERE id='cat-a'")
    # BUG-10: a used category definition is protected like a used colour definition.
    deny("UPDATE custom_categories SET baseCategory='bottom' WHERE id='cat-a'")
    deny("UPDATE custom_categories SET wardrobeId='b' WHERE id='cat-a'")
    db.execute("UPDATE custom_categories SET name='Running shoes' WHERE id='cat-a'")  # renaming stays allowed
    db.execute("UPDATE custom_categories SET baseCategory='top' WHERE id='cat-b'")  # unused definition stays editable
    revision = db.execute('SELECT localRevision FROM cloud_sync_meta').fetchone()[0]
    db.execute('BEGIN'); db.execute("UPDATE garments SET name='Uncommitted' WHERE id='item'")
    deny("UPDATE garments SET customCategoryId='cat-b' WHERE id='item'"); db.execute('ROLLBACK')
    assert db.execute('SELECT name,customCategoryId FROM garments').fetchone() == ('Shoe','cat-a')
    assert db.execute('SELECT localRevision FROM cloud_sync_meta').fetchone()[0] == revision
    db.execute('UPDATE cloud_sync_meta SET applying=1,dirty=0')
    db.execute("INSERT INTO custom_categories VALUES ('unused','b','Winter','winter','layer')")
    db.execute('UPDATE cloud_sync_meta SET applying=0')
    assert db.execute('SELECT localRevision,dirty FROM cloud_sync_meta').fetchone() == (revision,0)
    db.close(); db = sqlite3.connect(path, isolation_level=None); db.execute('PRAGMA foreign_keys=ON')
    assert db.execute('SELECT subcategory,customCategoryId FROM garments').fetchone() == ('Trail','cat-a')
    db.execute("DELETE FROM wardrobes WHERE id='b'")
    assert db.execute('SELECT id FROM custom_categories').fetchall() == [('cat-a',)]
    assert not db.execute('PRAGMA foreign_key_check').fetchall()
    db.close()
print(f'PASS: production 8→9 migration preserves seeded garments/outfit/wear links; {rejected} invalid writes rejected; category uniqueness, wardrobe isolation, reopen, rollback, FK and cloud dirty/echo checks.')
print('NOT RUN: Room validation/code generation, Kotlin/Compose, native backup/restore/cloud or Firebase emulator.')
