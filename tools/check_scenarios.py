#!/usr/bin/env python3
"""Production Java rules and file-backed SQLite queue tests. Does not compile Kotlin."""
from pathlib import Path
import shutil, sqlite3, subprocess, tempfile
from host_schema import SOURCE, migrate, install_triggers
ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='wardrobe-scenarios-') as output:
    java=shutil.which('java')
    files=[SOURCE/(name+'.java') for name in ('WardrobeRules','LookSelection','InspirationCatalog','CloudSyncRules')]
    files.append(ROOT/'app/src/test/java/com/familywardrobe/app/ScenarioRulesCases.java')
    subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',output,*map(str,files)],check=True)
    subprocess.run([java,'-cp',output,'com.familywardrobe.app.ScenarioRulesCases'],check=True)
    path=Path(output)/'wardrobe.db'
    db=sqlite3.connect(path,isolation_level=None);db.execute('PRAGMA foreign_keys=ON');migrate(db,target=7)
    db.execute("INSERT INTO wardrobes VALUES ('home','Home',1,0,7)")
    db.execute("INSERT INTO garments(id,wardrobeId,name,category,color,size,location,description,photoId,season,style,availability,favorite,rarelyWorn,archived,createdAt) VALUES ('item','home','Top','top','navy','','','','photo','all','casual','available',0,0,0,1)")
    db.execute("INSERT INTO wishes(id,wardrobeId,name,category,kind,ownedId) VALUES ('wish','home','Top','top','product','item')")
    db.execute("INSERT INTO wears VALUES ('item','2026-10-01','item:item')")
    db.execute("INSERT INTO outfits(id,wardrobeId,name,signature,createdAt) VALUES ('look','home','Look','item',1)")
    db.execute("INSERT INTO outfit_parts VALUES ('look','item')")
    before={table:db.execute(f'SELECT * FROM {table}').fetchall() for table in ('wardrobes','garments','wishes','wears','outfits','outfit_parts')}
    migrate(db,start=7,target=8)
    for table, rows in before.items():
        after=db.execute(f'SELECT * FROM {table}').fetchall()
        assert [row[:len(rows[0])] for row in after]==rows, f'migration changed {table}'
    assert db.execute('SELECT needPhotoId,needLink,needInspiration FROM wishes').fetchone()==('','','')
    assert db.execute('SELECT dirty,enabled,baseRevision FROM cloud_sync_meta').fetchone()==(1,0,0)
    assert db.execute("SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'sync_%'").fetchone()[0]==42
    db.execute("UPDATE garments SET availability='laundry' WHERE id='item'")
    revision=db.execute('SELECT localRevision FROM cloud_sync_meta').fetchone()[0];assert revision>0
    install_triggers(db);assert db.execute('SELECT localRevision FROM cloud_sync_meta').fetchone()[0]==revision
    db.close();db=sqlite3.connect(path,isolation_level=None);db.execute('PRAGMA foreign_keys=ON')
    assert db.execute('SELECT dirty,localRevision FROM cloud_sync_meta').fetchone()==(1,revision)
    db.execute('BEGIN')
    try:
        db.execute("UPDATE garments SET name='Lost change' WHERE id='item'")
        db.execute("INSERT INTO purchase_undo VALUES ('missing-wish','{}',1)")
        raise AssertionError('Broken receipt reference accepted')
    except sqlite3.IntegrityError:db.execute('ROLLBACK')
    assert db.execute('SELECT localRevision FROM cloud_sync_meta').fetchone()[0]==revision
    assert db.execute('SELECT name FROM garments').fetchone()[0]=='Top'
    db.execute('BEGIN');db.execute('UPDATE cloud_sync_meta SET applying=1')
    db.execute("UPDATE garments SET availability='available' WHERE id='item'")
    db.execute('UPDATE cloud_sync_meta SET applying=0,dirty=0,baseRevision=7');db.execute('COMMIT')
    assert db.execute('SELECT localRevision,dirty,applying,baseRevision FROM cloud_sync_meta').fetchone()==(revision,0,0,7)
    db.execute("INSERT INTO purchase_undo VALUES ('wish','receipt',2)")
    db.execute("INSERT INTO wish_events VALUES ('event','wish','candidate','Candidate','top',1999,'EUR','https://example.com/coat','Rain','photo',2)")
    assert db.execute('SELECT dirty,localRevision FROM cloud_sync_meta').fetchone()==(1,revision+2)
    db.execute("UPDATE cloud_sync_meta SET uploadPath='pending',uploadHash='hash',uploadLocalRevision=localRevision")
    db.execute("UPDATE garments SET name='Edited during upload' WHERE id='item'")
    assert db.execute('SELECT uploadLocalRevision<localRevision FROM cloud_sync_meta').fetchone()[0]==1
    db.execute("DELETE FROM wishes WHERE id='wish'")
    assert db.execute('SELECT count(*) FROM purchase_undo').fetchone()[0]==0
    assert db.execute('SELECT count(*) FROM wish_events').fetchone()[0]==0
    assert db.execute('SELECT count(*) FROM garments').fetchone()[0]==1
    assert not db.execute('PRAGMA foreign_key_check').fetchall();db.close()
print('PASS: production schema 2→8 retains seeded wardrobe, items, purchase, wear and outfit references; 42 durable triggers, restart queue, transaction rollback, remote echo suppression, edit-during-upload and receipt/history cascades.')
print('NOT RUN: Kotlin/Room generation, Compose, Android bitmap/URI, Firebase transactions/rules/emulators, WorkManager, APK or two real devices.')
