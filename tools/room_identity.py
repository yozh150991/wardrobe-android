"""Room's schema identityHash, recomputed from an exported schema JSON (TRIP-01, 0.14.44).

Room writes the hash into each exported schema and into room_master_table. Schemas are written here without an
Android SDK, so the hash is recomputed the way the Room compiler builds it (SchemaIdentityKey: md5 over
"key?:?" parts; entities, fields, indices and foreign keys sorted). It reproduces every schema Room exported for
this app (1, 2, 3, 4, 7, 8, 10); check_trip_details.py verifies that for each run.
"""
import hashlib, json, sys
from pathlib import Path

def _key(parts): return hashlib.md5(''.join(p + '?:?' for p in parts).encode()).hexdigest()
def _b(value): return 'true' if value else 'false'

def identity_hash(database: dict) -> str:
    def field(f):
        text = f"{f['columnName']}-{f.get('affinity', 'TEXT')}-{_b(f.get('notNull', False))}"
        return text + (f"-defaultValue={f['defaultValue']}" if 'defaultValue' in f else '')
    def entity(e):
        primary = f"{_b(e['primaryKey']['autoGenerate'])}-[{', '.join(e['primaryKey']['columnNames'])}]"
        indices = [f"{_b(i['unique'])}-{i['name']}-{','.join(i['columnNames'])}" for i in e.get('indices', [])]
        keys = [f"{k['table']}-{','.join(k['referencedColumns'])}-{','.join(k['columns'])}-{k['onDelete']}-{k['onUpdate']}-false"
                for k in e.get('foreignKeys', [])]
        return _key([e['tableName'], primary] + sorted(map(field, e['fields'])) + sorted(indices) + sorted(keys))
    return _key(sorted(map(entity, database['entities'])))

if __name__ == '__main__':
    for path in sys.argv[1:]:
        database = json.loads(Path(path).read_text(encoding='utf-8'))['database']
        print(path, identity_hash(database), 'ok' if identity_hash(database) == database['identityHash'] else 'DIFFERS')
