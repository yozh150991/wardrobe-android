"""Strict v10 wire schema and single-wardrobe reference validation.

No paths/URLs in metadata are fetched. Unknown fields (including memberships)
are rejected, including inside purchase receipts and collage JSON.
"""
import datetime
import json
import math
import re
import unicodedata
from urllib.parse import urlsplit
from .errors import require

ID = re.compile(r'[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}')
HASH = re.compile(r'[a-f0-9]{64}')
CATEGORIES = 'top bottom dress jumpsuit shoes layer outerwear bag tights jewelry hat accessory other_item'.split()
COLORS = 'white black grey beige navy brown olive blue red other'.split()
OCCASIONS = 'everyday work occasion active_day'.split()
WEATHERS = 'any_weather warm cool cold rain'.split()
FIELDS = {
 'wardrobes': 'id name createdAt usesDefaultName noRepeatDays',
 'customCategories': 'id wardrobeId name nameKey baseCategory',
 'customColors': 'id wardrobeId name nameKey baseColor',
 'garments': 'id wardrobeId name category color size location description photoId season style availability favorite rarelyWorn archived createdAt priceMinor currency subcategory suggestionExcluded photoFingerprint customCategoryId customColorId',
 'outfits': 'id wardrobeId name signature createdAt occasion weather source layout background',
 'parts': 'outfitId garmentId',
 'wears': 'garmentId date source',
 'wishes': 'id wardrobeId name category kind priceMinor currency link color inspiration photoId needName needCategory requirements budgetMinor budgetCurrency createdAt ownedId needPhotoId needLink needInspiration',
 'ideas': 'outfitId partId wishId category name color photoId',
 'plans': 'id wardrobeId outfitId date notes worn wornItemIds',
 'feedback': 'id wardrobeId signature weather occasion reason',
 'capsules': 'id wardrobeId name kind startDate endDate',
 'capsuleItems': 'capsuleId garmentId manual packed',
 'capsuleOutfits': 'capsuleId outfitId',
 'purchaseUndos': 'wishId garmentJson createdAt',
 'wishEvents': 'id wishId kind name category priceMinor currency link inspiration photoId createdAt',
}
BOOLS = set('usesDefaultName favorite rarelyWorn archived suggestionExcluded worn manual packed'.split())
OPTIONAL_IDS = {'wishId', 'customCategoryId', 'customColorId'}
LIMITS = dict(name=120, needName=120, size=40, location=200, description=1000, subcategory=60,
              photoFingerprint=64, link=2000, needLink=2000, inspiration=2000, needInspiration=2000,
              requirements=2000, notes=1000, layout=12000, garmentJson=20000, signature=1000,
              wornItemIds=443, partId=60, nameKey=180)


def valid_id(value):
    return isinstance(value, str) and ID.fullmatch(value) is not None


def integer(value, low=0, high=9007199254740991):
    return type(value) is int and low <= value <= high


def strict_json(text):
    def pairs(items):
        result = {}
        for k, v in items:
            require(k not in result, 'Duplicate JSON key')
            result[k] = v
        return result
    def invalid_constant(_):
        require(False, 'Non-finite JSON number')
    try:
        return json.loads(text, object_pairs_hook=pairs, parse_constant=invalid_constant)
    except (ValueError, RecursionError, UnicodeError):
        require(False, 'Invalid JSON')


BIDI_CONTROLS = frozenset('\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069')


def visible_name(value):
    """No bidi embedding/override/isolate and at least one visible character (BUG-12).

    ZWJ inside emoji sequences stays allowed; a name made only of spaces, format
    characters, combining marks or variation selectors is refused. Mirrors TextLimits.java."""
    if any(c in BIDI_CONTROLS for c in value):
        return False
    return any(not (c.isspace() or unicodedata.category(c) in ('Cc', 'Cf', 'Mn', 'Me', 'Zs', 'Zl', 'Zp')
                    or 0xfe00 <= ord(c) <= 0xfe0f or 0xe0100 <= ord(c) <= 0xe01ef) for c in value)


def label_key(value):
    """Java String.toLowerCase(ROOT) writes a final sigma (ς) where Python lower() writes σ (BUG-09)."""
    return value.replace('\u03c2', '\u03c3')


def text_length(text):
    try:
        return len(text.encode('utf-16-le')) // 2
    except UnicodeError:
        require(False, 'Invalid Unicode')


def row_shape(kind, row):
    fields = set(FIELDS[kind].split())
    require(type(row) is dict and set(row) == fields, 'Unexpected or missing row fields')
    for key, value in row.items():
        if key in BOOLS:
            require(type(value) is bool)
        elif key in ('createdAt', 'noRepeatDays'):
            require(integer(value, high=30 if key == 'noRepeatDays' else 9007199254740991))
        elif key in ('priceMinor', 'budgetMinor'):
            require(value is None or integer(value, high=99999999999))
        elif key in OPTIONAL_IDS and value is None and kind in ('garments', 'ideas'):
            pass
        elif key == 'id' or key.endswith('Id') and key not in ('photoId', 'needPhotoId', 'ownedId', 'partId'):
            require(valid_id(value), 'Invalid entity ID')
        elif key in ('photoId', 'needPhotoId', 'ownedId'):
            require(valid_id(value) or value == '' and not (key == 'photoId' and kind == 'garments'))
        else:
            require(type(value) is str and text_length(value) <= LIMITS.get(key, 1000), 'Invalid text')
    for key in ('category', 'needCategory', 'baseCategory'):
        if key in row:
            require(row[key] in CATEGORIES or key == 'needCategory' and row[key] == '')
    if 'color' in row:
        require(row['color'] in COLORS or row['color'] == '' and kind in ('wishes', 'ideas'))
    if 'baseColor' in row:
        require(row['baseColor'] in COLORS)
    for key, choices in {'season':['all','summer','winter','midseason'], 'style':['casual','sport','formal'],
                          'availability':['available','laundry','repair','lent'], 'occasion':OCCASIONS,
                          'weather':WEATHERS, 'currency':['PLN','EUR','USD','UAH','GBP'],
                          'budgetCurrency':['PLN','EUR','USD','UAH','GBP']}.items():
        if key in row:
            require(row[key] in choices)
    if 'name' in row:
        limit = 60 if kind in ('customCategories','customColors') else 80 if kind in ('wardrobes','capsules') else 120
        require(0 < text_length(row['name'].strip()) <= limit)
        require(visible_name(row['name']), 'Invalid name')
    for key in ('link', 'needLink'):
        if key in row and row[key].strip():
            try:
                value = row[key].strip()
                url = urlsplit(value)
                require(url.scheme.lower() in ('http','https') and url.hostname and url.username is None
                        and not any(c.isspace() or ord(c) < 32 for c in value), 'Invalid product link')
            except ValueError:
                require(False, 'Invalid product link')
    if kind == 'garments':
        require(row['photoFingerprint'] == '' or HASH.fullmatch(row['photoFingerprint']))
    if kind in ('customCategories', 'customColors'):
        name = row['name']
        clean = ' '.join(unicodedata.normalize('NFKC', name).split())
        require(name == clean and not any(unicodedata.category(c) in ('Cc','Cf') for c in name))
        expected = clean.lower().replace('’', "'").replace('ʼ', "'").replace('‘', "'")
        require(row['nameKey'] == row['nameKey'].lower() and label_key(row['nameKey']) == label_key(expected))
    if kind == 'wishes':
        require(row['kind'] in ('need','product'))
        require(not row['ownedId'] or row['kind'] == 'product')
        require(row['kind'] != 'need' or row['priceMinor'] is None and row['color'] == '')
    if kind == 'wishEvents':
        require(row['kind'] in ('candidate','candidate_cancelled','purchased','purchase_cancelled'))
    if kind == 'capsules':
        require(row['kind'] in ('capsule','trip'))
        if row['startDate'] or row['endDate']:
            require(valid_date(row['startDate']) and valid_date(row['endDate']) and row['startDate'] <= row['endDate'])


def valid_date(value):
    try:
        return re.fullmatch(r'\d{4}-\d{2}-\d{2}', value) is not None and datetime.date.fromisoformat(value).isoformat() == value
    except (ValueError, TypeError):
        return False


def validate_snapshot(snapshot, wardrobe_id):
    require(type(snapshot) is dict and set(snapshot) == set(FIELDS) | {'format','version','photoFiles'})
    require(snapshot['format'] == 'family-wardrobe' and type(snapshot['version']) is int and snapshot['version'] == 10)
    index, photos = {}, set()
    total_rows = 0
    for kind in FIELDS:
        rows = snapshot[kind]
        require(type(rows) is list and len(rows) <= 100000)
        total_rows += len(rows)
        require(total_rows <= 100000, 'Too many records')
        seen = set()
        for row in rows:
            row_shape(kind, row)
            key = row.get('id') or {
                'parts': lambda: (row['outfitId'], row['garmentId']),
                'wears': lambda: (row['garmentId'], row['date'], row['source']),
                'ideas': lambda: (row['outfitId'], row['partId']),
                'capsuleItems': lambda: (row['capsuleId'], row['garmentId']),
                'capsuleOutfits': lambda: (row['capsuleId'], row['outfitId']),
                'purchaseUndos': lambda: row['wishId'],
            }[kind]() if 'id' not in row else row['id']
            require(key not in seen, 'Duplicate row')
            seen.add(key)
            index[kind, key] = row
            if 'wardrobeId' in row:
                require(row['wardrobeId'] == wardrobe_id, 'Cross-wardrobe record')
            for field in ('photoId','needPhotoId'):
                if row.get(field):
                    photos.add(row[field])
    require(len(snapshot['wardrobes']) == 1 and snapshot['wardrobes'][0]['id'] == wardrobe_id)

    def ref(kind, identifier):
        require((kind, identifier) in index, 'Missing or cross-wardrobe reference')
        return index[kind, identifier]

    def garment_refs(g):
        require(g['wardrobeId'] == wardrobe_id)
        if g['customCategoryId']:
            require(ref('customCategories', g['customCategoryId'])['baseCategory'] == g['category'])
        if g['customColorId']:
            require(ref('customColors', g['customColorId'])['baseColor'] == g['color'])

    for kind in ('customCategories','customColors'):
        keys = [label_key(r['nameKey']) for r in snapshot[kind]]
        require(len(keys) == len(set(keys)), 'Duplicate custom name')
    for g in snapshot['garments']:
        garment_refs(g)
    for wish in snapshot['wishes']:
        if wish['ownedId']:
            ref('garments', wish['ownedId'])
    parts, ideas, wears, capsule_items, capsule_outfits = {}, {}, {}, {}, {}
    for p in snapshot['parts']:
        ref('outfits', p['outfitId']); ref('garments', p['garmentId'])
        parts.setdefault(p['outfitId'], []).append(p['garmentId'])
    for i in snapshot['ideas']:
        ref('outfits', i['outfitId'])
        if i['wishId']:
            require(not ref('wishes', i['wishId'])['ownedId'])
            require(i['partId'] == 'wish:' + i['wishId'])
        else:
            # Current generic inspiration keys. Specific catalog keys validated below.
            require(i['partId'] == 'idea:' + i['category'] or i['partId'] in INSPIRATION_KEYS.get(i['category'], set())
                    or i['partId'].startswith('wish:') and valid_id(i['partId'][5:]))
        ideas.setdefault(i['outfitId'], []).append(i['partId'])
    for outfit in snapshot['outfits']:
        ids = parts.get(outfit['id'], []) + ideas.get(outfit['id'], [])
        require(1 <= len(ids) <= 12 and outfit['signature'] == '|'.join(sorted(ids)))
        require(outfit['source'] in ('owned','mixed','inspiration') and outfit['background'] in ('paper','sand','sage','rose','slate'))
        layout = strict_json(outfit['layout']) if outfit['layout'].strip() else []
        require(type(layout) is list and len(layout) <= 12)
        keys = {'owned:' + i for i in parts.get(outfit['id'], [])} | set(ideas.get(outfit['id'], []))
        seen = set()
        for p in layout:
            require(type(p) is dict and set(p) == {'key','x','y','scale','z'} and p['key'] in keys and p['key'] not in seen)
            seen.add(p['key'])
            for f, lo, hi in [('x',0,1),('y',0,1),('scale',.6,1.8)]:
                require(type(p[f]) in (int,float) and math.isfinite(p[f]) and lo <= p[f] <= hi)
            require(integer(p['z'], high=11))
    for w in snapshot['wears']:
        ref('garments', w['garmentId']); require(valid_date(w['date']))
        prefix, sep, identifier = w['source'].partition(':')
        require(sep and prefix in ('item','outfit','plan') and valid_id(identifier))
        source = ref({'item':'garments','outfit':'outfits','plan':'plans'}[prefix], identifier)
        require(prefix != 'item' or identifier == w['garmentId'])
        require(prefix != 'plan' or source['date'] == w['date'])
        wears.setdefault((w['source'], w['date']), set()).add(w['garmentId'])
    seen_plans = set()
    for p in snapshot['plans']:
        ref('outfits', p['outfitId']); require(valid_date(p['date']))
        key = (p['date'], p['outfitId']); require(key not in seen_plans); seen_plans.add(key)
        ids = p['wornItemIds'].split('|') if p['wornItemIds'] else []
        require(len(ids) <= 12 and len(ids) == len(set(ids)) and p['wornItemIds'] == '|'.join(sorted(ids)))
        for i in ids: ref('garments', i)
        require(p['worn'] == bool(ids) and set(ids) == wears.get(('plan:' + p['id'],p['date']),set()))
    seen_feedback = set()
    for f in snapshot['feedback']:
        ids = f['signature'].split('|')
        require(1 <= len(ids) <= 12 and len(ids) == len(set(ids)) and f['signature'] == '|'.join(sorted(ids)))
        for i in ids: ref('garments', i)
        require(f['reason'] in ('dislike','hot','occasion'))
        key = tuple(f[k] for k in ('signature','weather','occasion','reason'))
        require(key not in seen_feedback); seen_feedback.add(key)
    for p in snapshot['capsuleItems']:
        ref('capsules', p['capsuleId']); ref('garments', p['garmentId'])
        capsule_items.setdefault(p['capsuleId'], []).append(p)
    for p in snapshot['capsuleOutfits']:
        ref('capsules', p['capsuleId']); ref('outfits', p['outfitId'])
        capsule_outfits.setdefault(p['capsuleId'], []).append(p['outfitId'])
    for c in snapshot['capsules']:
        rows = capsule_items.get(c['id'], [])
        expected = {r['garmentId'] for r in rows if r['manual']}
        for o in capsule_outfits.get(c['id'], []): expected.update(parts.get(o, []))
        require({r['garmentId'] for r in rows} == expected)
    for u in snapshot['purchaseUndos']:
        g = strict_json(u['garmentJson'])
        # Receipts from pre-category/pre-color versions legitimately omit these nullable fields.
        require(type(g) is dict)
        g.setdefault('customCategoryId', None); g.setdefault('customColorId', None)
        row_shape('garments', g); garment_refs(g)
        require(ref('wishes', u['wishId'])['ownedId'] == g['id'])
        ref('garments', g['id']); photos.add(g['photoId'])
    for e in snapshot['wishEvents']: ref('wishes', e['wishId'])
    require(type(snapshot['photoFiles']) is dict and set(snapshot['photoFiles']) == photos, 'Photo set mismatch')
    require(all(ext in ('jpg','png') for ext in snapshot['photoFiles'].values()))
    return {'wardrobes':1, 'garments':len(snapshot['garments']), 'outfits':len(snapshot['outfits']), 'photos':len(photos)}

# Populated from the versioned Android catalog, covered by a parity test.
INSPIRATION_KEYS = {'top': ['idea:top_shirt_white', 'idea:top_shirt_blue', 'idea:top_shirt_beige', 'idea:top_tee_white', 'idea:top_tee_black', 'idea:top_tee_grey', 'idea:top_knit_beige', 'idea:top_knit_grey', 'idea:top_knit_navy', 'idea:top_sporttop_black', 'idea:top_sporttop_blue', 'idea:top_sporttop_grey'], 'bottom': ['idea:bottom_trousers_navy', 'idea:bottom_trousers_black', 'idea:bottom_trousers_grey', 'idea:bottom_jeans_blue', 'idea:bottom_jeans_black', 'idea:bottom_jeans_navy', 'idea:bottom_shorts_beige', 'idea:bottom_shorts_navy', 'idea:bottom_shorts_olive', 'idea:bottom_joggers_grey', 'idea:bottom_joggers_black', 'idea:bottom_joggers_navy'], 'dress': ['idea:dress_dress_blue', 'idea:dress_dress_beige', 'idea:dress_dress_red', 'idea:dress_formaldress_black', 'idea:dress_formaldress_navy', 'idea:dress_formaldress_red'], 'jumpsuit': ['idea:jumpsuit_jumpsuit_black', 'idea:jumpsuit_jumpsuit_olive', 'idea:jumpsuit_jumpsuit_navy'], 'shoes': ['idea:shoes_sneakers_white', 'idea:shoes_sneakers_black', 'idea:shoes_sneakers_grey', 'idea:shoes_loafers_black', 'idea:shoes_loafers_brown', 'idea:shoes_loafers_beige', 'idea:shoes_boots_black', 'idea:shoes_boots_brown', 'idea:shoes_boots_beige', 'idea:shoes_trainers_white', 'idea:shoes_trainers_black', 'idea:shoes_trainers_blue'], 'outerwear': ['idea:outerwear_jacket_olive', 'idea:outerwear_jacket_navy', 'idea:outerwear_jacket_beige', 'idea:outerwear_coat_black', 'idea:outerwear_coat_brown', 'idea:outerwear_coat_grey', 'idea:outerwear_raincoat_navy', 'idea:outerwear_raincoat_black', 'idea:outerwear_raincoat_olive'], 'layer': ['idea:layer_cardigan_beige', 'idea:layer_cardigan_grey', 'idea:layer_cardigan_navy', 'idea:layer_blazer_black', 'idea:layer_blazer_navy', 'idea:layer_blazer_beige'], 'bag': ['idea:bag_bag_black', 'idea:bag_bag_brown', 'idea:bag_bag_beige'], 'hat': ['idea:hat_hat_black', 'idea:hat_hat_grey', 'idea:hat_hat_beige'], 'accessory': ['idea:accessory_scarf_grey', 'idea:accessory_scarf_beige', 'idea:accessory_scarf_navy']}
