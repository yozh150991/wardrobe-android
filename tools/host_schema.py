"""Use the exported Room v2 schema and the SQL from the production migration chain."""
from pathlib import Path
import json, re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/com/familywardrobe/app'

def install_triggers(db):
    text = (SOURCE / 'ScenarioData.kt').read_text()
    tables = re.findall(r'"(\w+)"', text.split('val portableTables = listOf(', 1)[1].split(')', 1)[0])
    template = re.search(r'db.execSQL\("(CREATE TRIGGER [^"\n]+)"\)', text).group(1)
    db.execute('INSERT OR IGNORE INTO cloud_sync_meta (id) VALUES (1)')
    for table in tables:
        for operation in ('INSERT', 'UPDATE', 'DELETE'):
            sql = template.replace('${table}', table).replace('${operation.lowercase()}', operation.lower()).replace('$operation', operation).replace('$table', table)
            db.execute(sql)

def migrate(db, start=2, target=10):
    if start == 2:
        schema = json.loads((ROOT / 'app/schemas/com.familywardrobe.app.WardrobeDatabase/2.json').read_text())['database']
        for entity in schema['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for index in entity.get('indices', []):
                db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    text = (SOURCE / 'Data.kt').read_text()
    for version in range(start, target):
        body = text.split(f'val MIGRATION_{version}_{version+1}', 1)[1].split('val MIGRATION_', 1)[0]
        if version == 7:
            body = (SOURCE / 'ScenarioData.kt').read_text().split('fun create(', 1)[1].split('fun installTriggers', 1)[0]
        for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', body):
            if not sql.startswith('UPDATE cloud_sync_meta'): db.execute(sql)
        if version == 8:
            category_body = (SOURCE / 'CategoryData.kt').read_text()
            for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', category_body): db.execute(sql)
        if version == 9:
            color_body = (SOURCE / "ColorData.kt").read_text()
            for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', color_body): db.execute(sql)
        if version == 7:
            install_triggers(db)
            db.execute('UPDATE cloud_sync_meta SET dirty=1 WHERE id=1')

