#!/usr/bin/env python3
"""Runs production Java photo helpers and the real 3->4 migration. No Android claims."""
from pathlib import Path
import json, re, shutil, sqlite3, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/com/familywardrobe/app'
java=shutil.which('java')
if not java: raise SystemExit('Java 17 with jdk.compiler required')
with tempfile.TemporaryDirectory(prefix='wardrobe-ux-') as output:
    sources=[source/name for name in ('PhotoWorkGate.java','PhotoPresentationGeometry.java')]
    sources.append(root/'app/src/test/java/com/familywardrobe/app/UXRulesCases.java')
    subprocess.run([java,'-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-d',output,*map(str,sources)],check=True)
    subprocess.run([java,'-cp',output,'com.familywardrobe.app.UXRulesCases'],check=True)
schema=json.loads((root/'app/schemas/com.familywardrobe.app.WardrobeDatabase/2.json').read_text())['database']
data=(source/'Data.kt').read_text()
def migration(name):
    body=data.split('val '+name,1)[1].split('        val MIGRATION_',1)[0]
    return re.findall(r'db.execSQL\("([^"\n]+)"\)',body)
db=sqlite3.connect(':memory:',isolation_level=None)
db.execute('PRAGMA foreign_keys=ON')
for entity in schema['entities']:
    db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
    for index in entity.get('indices',[]): db.execute(index['createSql'].replace('${TABLE_NAME}',entity['tableName']))
for statement in migration('MIGRATION_2_3'): db.execute(statement)
db.execute("INSERT INTO wardrobes(id,name,createdAt,usesDefaultName) VALUES ('home','Home',1,0)")
db.execute("INSERT INTO wishes(id,wardrobeId,name,category,kind,priceMinor,budgetMinor) VALUES ('wish','home','Shoes','shoes','product',29900,35000)")
before=db.execute('SELECT * FROM wishes').fetchone()
sql=migration('MIGRATION_3_4');assert len(sql)==1
for statement in sql: db.execute(statement)
after=db.execute('SELECT * FROM wishes').fetchone()
assert after[:-1]==before and after[-1]==''
assert not db.execute('PRAGMA foreign_key_check').fetchall()
print('PASS: real Room migration 3->4 executed on migrated exported schema 2; every existing wish field retained.')
# Delimiter validation helps catch editing mistakes; it is not a Kotlin compiler.
for path in source.glob('*.kt'):
    s=path.read_text();stack=[];i=0;state='code'
    while i<len(s):
        ch=s[i];pair=s[i:i+2]
        if state=='line':
            if ch=='\n':state='code'
        elif state=='block':
            if pair=='*/':state='code';i+=1
        elif state in ('string','char'):
            if ch=='\\':i+=1
            elif ch==('"' if state=='string' else "'"):state='code'
        elif state=='raw':
            if s[i:i+3]=='"""':state='code';i+=2
        else:
            if pair=='//':state='line';i+=1
            elif pair=='/*':state='block';i+=1
            elif s[i:i+3]=='"""':state='raw';i+=2
            elif ch=='"':state='string'
            elif ch=="'":state='char'
            elif ch in '({[':stack.append((ch,i))
            elif ch in ')}]':
                assert stack and stack[-1][0]=={')':'(',']':'[','}':'{'}[ch],f'{path.name}: unmatched {ch} at {i}'
                stack.pop()
        i+=1
    assert not stack and state in ('code','line'),f'{path.name}: unterminated syntax'
print('PASS: Kotlin source delimiters. This does not establish type correctness or compilation.')
print('NOT RUN: Kotlin/Compose, Room-generated schema validation, Firebase, native ML, device UI or APK.')
