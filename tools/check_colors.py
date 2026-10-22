#!/usr/bin/env python3
"""Exercise production Java search and production SQL migration/guards. Not an Android run."""
from pathlib import Path
import sqlite3, subprocess, tempfile
from host_schema import SOURCE, migrate

ROOT = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='wardrobe-colors-') as output:
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '17', '-d', output,
                    str(SOURCE / 'CategoryRules.java'), str(SOURCE / 'TextLimits.java'), str(SOURCE / 'ColorRules.java'),
                    str(ROOT / 'app/src/test/java/com/familywardrobe/app/ColorCases.java')], check=True)
    subprocess.run(['java', '-cp', output, 'com.familywardrobe.app.ColorCases'], check=True)
    path = Path(output) / 'migration.db'
    db = sqlite3.connect(path, isolation_level=None)
    db.execute('PRAGMA foreign_keys=ON')
    migrate(db, target=9)
    db.execute("INSERT INTO wardrobes VALUES ('a','A',1,0,0)")
    db.execute("INSERT INTO wardrobes VALUES ('b','B',2,0,0)")
    db.execute("INSERT INTO garments(id,wardrobeId,name,category,color,size,location,description,photoId,season,style,availability,favorite,rarelyWorn,archived,createdAt,subcategory) VALUES ('item','a','Shoe','shoes','navy','','','','photo','all','casual','available',0,0,0,1,'Trail')")
    db.execute("INSERT INTO outfits(id,wardrobeId,name,signature,createdAt) VALUES ('look','a','Look','item',1)")
    db.execute("INSERT INTO outfit_parts VALUES ('look','item')")
    db.execute("INSERT INTO wears VALUES ('item','2026-10-12','item:item')")
    db.execute("INSERT INTO custom_categories VALUES ('cat-a','a','Running','running','shoes')")
    db.execute("UPDATE garments SET customCategoryId='cat-a'")
    before = {table: db.execute(f'SELECT * FROM {table}').fetchall() for table in ('garments','wardrobes','outfits','outfit_parts','wears','custom_categories')}
    migrate(db, start=9, target=10)
    for table, rows in before.items():
        assert [r[:len(rows[0])] for r in db.execute(f'SELECT * FROM {table}').fetchall()] == rows
    assert db.execute('SELECT customColorId FROM garments').fetchone() == (None,)
    assert db.execute("SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'sync_%'").fetchone()[0] == 48
    db.execute('UPDATE cloud_sync_meta SET dirty=0')
    revision = db.execute('SELECT localRevision FROM cloud_sync_meta').fetchone()[0]
    db.execute("INSERT INTO custom_colors VALUES ('color-a','a','Midnight blue','midnight blue','navy')")
    assert db.execute('SELECT dirty,localRevision FROM cloud_sync_meta').fetchone() == (1, revision + 1)
    db.execute("INSERT INTO custom_colors VALUES ('color-b','b','Midnight blue','midnight blue','navy')")
    db.execute("UPDATE garments SET customColorId='color-a' WHERE id='item'")
    rejected = 0
    def deny(sql):
        global rejected
        try: db.execute(sql)
        except sqlite3.IntegrityError: rejected += 1
        else: raise AssertionError('Invalid color write accepted: ' + sql)
    deny("INSERT INTO custom_colors VALUES ('duplicate','a','MIDNIGHT BLUE','midnight blue','navy')")
    deny("UPDATE garments SET customColorId='color-b' WHERE id='item'")
    deny("UPDATE garments SET customColorId='missing' WHERE id='item'")
    deny("UPDATE garments SET color='red' WHERE id='item'")
    deny("UPDATE garments SET wardrobeId='b' WHERE id='item'")
    deny("DELETE FROM custom_colors WHERE id='color-a'")
    deny("UPDATE custom_colors SET baseColor='red' WHERE id='color-a'")
    deny("UPDATE custom_colors SET wardrobeId='b' WHERE id='color-a'")
    revision = db.execute('SELECT localRevision FROM cloud_sync_meta').fetchone()[0]
    db.execute('BEGIN'); db.execute("UPDATE garments SET name='Uncommitted' WHERE id='item'")
    deny("UPDATE garments SET customColorId='color-b' WHERE id='item'"); db.execute('ROLLBACK')
    assert db.execute('SELECT name,customColorId FROM garments').fetchone() == ('Shoe','color-a')
    assert db.execute('SELECT localRevision FROM cloud_sync_meta').fetchone()[0] == revision
    db.execute('UPDATE cloud_sync_meta SET applying=1,dirty=0')
    db.execute("INSERT INTO custom_colors VALUES ('unused','b','Powder pink','powder pink','red')")
    db.execute('UPDATE cloud_sync_meta SET applying=0')
    assert db.execute('SELECT localRevision,dirty FROM cloud_sync_meta').fetchone() == (revision,0)
    db.close(); db = sqlite3.connect(path, isolation_level=None); db.execute('PRAGMA foreign_keys=ON')
    assert db.execute('SELECT subcategory,customColorId FROM garments').fetchone() == ('Trail','color-a')
    db.execute("DELETE FROM wardrobes WHERE id='b'")
    assert db.execute('SELECT id FROM custom_colors').fetchall() == [('color-a',)]
    assert not db.execute('PRAGMA foreign_key_check').fetchall()
    db.close()
print(f'PASS: production 9→10 migration preserves seeded garments/outfit/wear links; {rejected} invalid writes rejected; color uniqueness, wardrobe isolation, reopen, rollback, FK and cloud dirty/echo checks.')
print('NOT RUN: Room validation/code generation, Kotlin/Compose, native backup/restore/cloud or Firebase emulator.')
