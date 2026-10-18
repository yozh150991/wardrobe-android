import copy
import io
import json
import zipfile
from PIL import Image
from family.schema import FIELDS


def uid(n):
    return f'00000000-0000-0000-0000-{n:012d}'

REMOTE, WARDROBE, GARMENT, PHOTO, OUTFIT, WISH, PLAN, CAPSULE, CATEGORY, COLOR = [uid(i) for i in range(1,11)]


def snapshot():
    s = {k:[] for k in FIELDS}
    s.update(format='family-wardrobe', version=10, photoFiles={PHOTO:'png'})
    s['wardrobes'] = [dict(id=WARDROBE,name='Home',createdAt=0,usesDefaultName=False,noRepeatDays=7)]
    s['customCategories'] = [dict(id=CATEGORY,wardrobeId=WARDROBE,name='My tops',nameKey='my tops',baseCategory='top')]
    s['customColors'] = [dict(id=COLOR,wardrobeId=WARDROBE,name='Ink',nameKey='ink',baseColor='black')]
    g = dict(id=GARMENT,wardrobeId=WARDROBE,name='Shirt',category='top',color='black',size='L',location='Shelf',description='',
             photoId=PHOTO,season='all',style='casual',availability='available',favorite=False,rarelyWorn=False,archived=False,
             createdAt=0,priceMinor=None,currency='PLN',subcategory='',suggestionExcluded=False,photoFingerprint='',
             customCategoryId=CATEGORY,customColorId=COLOR)
    s['garments'] = [g]
    s['outfits'] = [dict(id=OUTFIT,wardrobeId=WARDROBE,name='Look',signature=GARMENT,createdAt=0,occasion='everyday',
                         weather='any_weather',source='owned',layout='',background='paper')]
    s['parts'] = [dict(outfitId=OUTFIT,garmentId=GARMENT)]
    s['wishes'] = [dict(id=WISH,wardrobeId=WARDROBE,name='Shirt',category='top',kind='product',priceMinor=None,currency='PLN',
                        link='',color='',inspiration='',photoId=PHOTO,needName='',needCategory='',requirements='',budgetMinor=None,
                        budgetCurrency='PLN',createdAt=0,ownedId=GARMENT,needPhotoId='',needLink='',needInspiration='')]
    s['plans'] = [dict(id=PLAN,wardrobeId=WARDROBE,outfitId=OUTFIT,date='2026-10-13',notes='',worn=True,wornItemIds=GARMENT)]
    s['wears'] = [dict(garmentId=GARMENT,date='2026-10-13',source='plan:'+PLAN)]
    s['feedback'] = [dict(id=uid(11),wardrobeId=WARDROBE,signature=GARMENT,weather='any_weather',occasion='everyday',reason='hot')]
    s['capsules'] = [dict(id=CAPSULE,wardrobeId=WARDROBE,name='Trip',kind='trip',startDate='2026-10-13',endDate='2026-10-15')]
    s['capsuleItems'] = [dict(capsuleId=CAPSULE,garmentId=GARMENT,manual=False,packed=False)]
    s['capsuleOutfits'] = [dict(capsuleId=CAPSULE,outfitId=OUTFIT)]
    s['purchaseUndos'] = [dict(wishId=WISH,garmentJson=json.dumps(g),createdAt=0)]
    s['wishEvents'] = [dict(id=uid(12),wishId=WISH,kind='purchased',name='Shirt',category='top',priceMinor=None,currency='PLN',
                            link='',inspiration='',photoId=PHOTO,createdAt=0)]
    return s


def envelope(base=0, owner='owner', state=None, remote=REMOTE):
    return dict(format='family-wardrobe-scoped',version=1,remoteId=remote,wardrobeId=WARDROBE,ownerUid=owner,
                baseRevision=base,snapshot=state if state is not None else snapshot())


def archive(env=None, extras=None, manifest=None, photo=None):
    if photo is None:
        p=io.BytesIO(); Image.new('RGBA',(3,4),(200,100,50,255)).save(p,format='PNG'); photo=p.getvalue()
    target=io.BytesIO()
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('manifest.json', manifest if manifest is not None else json.dumps(env or envelope()))
        z.writestr('photos/'+PHOTO+'.png', photo)
        for name, value in (extras or {}).items(): z.writestr(name,value)
    return target.getvalue()
