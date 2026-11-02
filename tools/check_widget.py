#!/usr/bin/env python3
"""WID-01: source guards for the «Образ на сьогодні» widget (privacy, intents, refresh, languages, contrast)."""
from pathlib import Path
import re, xml.etree.ElementTree as E

root = Path(__file__).resolve().parents[1]
main = root / 'app/src/main'
source = main / 'java/com/familywardrobe/app'
res = main / 'res'
A = '{http://schemas.android.com/apk/res/android}'
failures = []
def check(condition, message):
    if not condition: failures.append(message)

# The provider receives the system's broadcasts; the widget's own actions are reachable only by this app.
manifest = E.parse(main / 'AndroidManifest.xml').getroot()
receivers = {r.get(A + 'name'): r for r in manifest.iter('receiver')}
provider, actions = receivers.get('.TodayWidget'), receivers.get('.TodayWidgetActions')
check(provider is not None and provider.get(A + 'exported') == 'true', 'TodayWidget provider must be declared (exported for the launcher)')
check(actions is not None and actions.get(A + 'exported') == 'false', 'TodayWidgetActions must not be exported')
if provider is not None:
    names = {a.get(A + 'name') for a in provider.iter('action')}
    check(names == {'android.appwidget.action.APPWIDGET_UPDATE', 'android.intent.action.TIMEZONE_CHANGED',
                    'android.intent.action.TIME_SET', 'android.intent.action.LOCALE_CHANGED'}, f'unexpected provider actions {sorted(names)}')
    meta = [m for m in provider.iter('meta-data') if m.get(A + 'name') == 'android.appwidget.provider']
    check(len(meta) == 1 and meta[0].get(A + 'resource') == '@xml/widget_today_info', 'provider meta-data must point to widget_today_info')

# No periodic wake-ups: changes in the app and an inexact alarm after midnight refresh it.
info = E.parse(res / 'xml/widget_today_info.xml').getroot()
check(info.get(A + 'updatePeriodMillis') == '0', 'updatePeriodMillis must stay 0')
check(info.get(A + 'initialLayout') == '@layout/widget_today', 'initialLayout must be widget_today')

widget = (source / 'TodayWidget.kt').read_text()
model = (source / 'TodayWidgetModel.kt').read_text()
pending = [m.start() for m in re.finditer(r'PendingIntent\.get(?:Activity|Broadcast)\(', widget)]
check(len(pending) == 4, f'expected 4 PendingIntent factories (open/daily, launch, wear, midnight), found {len(pending)}')
for index, start in enumerate(pending):
    end = pending[index + 1] if index + 1 < len(pending) else len(widget)
    check('PendingIntent.FLAG_IMMUTABLE' in widget[start:min(end, start + 600)], f'PendingIntent at offset {start} must be FLAG_IMMUTABLE')
check('setExact' not in widget and 'SCHEDULE_EXACT_ALARM' not in (main / 'AndroidManifest.xml').read_text(),
      'the midnight refresh must stay an inexact alarm without exact-alarm rights')
check('AlarmManager.RTC,' in widget, 'the midnight alarm must not wake the device (RTC, not RTC_WAKEUP)')
# Signed out: nothing of the account is drawn.
signed_out = widget.split('TodayWidgetState.SignedOut ->', 1)[1].split('is TodayWidgetState.NoPlan', 1)[0]
check('state.' not in signed_out and 'picture' not in signed_out, 'the signed-out widget must not show account data')
check('signedInIdentity()' in widget and 'accountRegistry.known(' in widget and 'AccountErasure.pending' in widget,
      'the widget reads only the signed-in, already opened, not erased account')
check('accountRegistry.location(' not in widget, 'the widget must never register (create) an account location')
check('markPlanWorn(' in widget and 'identity.key == key' in widget, '«Одягнув сьогодні» must check the account the widget was drawn for')
check('AccountWriteLocks.forDatabase' in widget and 'enableMultiInstanceInvalidation' in widget,
      'the widget database must share the account write lock and invalidation')
check('it.wardrobeId == wardrobe && it.date == today' in model, 'only the active wardrobe\'s plans for today count')
calendar = (source / 'CalendarOperations.kt').read_text()
mark = calendar.split('suspend fun Repository.markPlanWorn', 1)[1].split('\n}\n', 1)[0]
check('if (plan.worn) false' in mark and 'clearSourceDay' not in mark, 'markPlanWorn must only mark, never unmark')
check('plan.wardrobeId == wardrobe && plan.date == today' in mark, 'markPlanWorn must refuse another wardrobe or day')
# Every tap is handled only for the signed-in account; sign-out drops a pending tap.
activity = (source / 'MainActivity.kt').read_text()
check('account == app.authBackend.signedInIdentity()?.key' in activity, 'MainActivity must check the widget account')
storage = (source / 'AccountStorage.kt').read_text()
check('app.widgetRequests.value = null' in storage and 'TodayWidgets.request(app)' in storage, 'sign-out must clear the widget')

# Languages: the same keys and placeholders in every locale.
keys = ['widget_title', 'widget_description', 'widget_signed_out', 'widget_open_app', 'widget_no_plan', 'widget_pick', 'widget_open',
        'widget_wear', 'widget_picture', 'widget_worn', 'widget_unavailable', 'widget_ideas', 'widget_no_photos', 'widget_more']
placeholders = {}
for folder in ('values', 'values-uk', 'values-en', 'values-pl'):
    strings = {s.get('name'): ''.join(s.itertext()) for s in E.parse(res / folder / 'strings.xml').getroot().iter('string')}
    for key in keys:
        check(bool(strings.get(key, '').strip()), f'{folder}: {key} missing')
        found = sorted(re.findall(r'%\d\$[sd]', strings.get(key, '')))
        check(placeholders.setdefault(key, found) == found, f'{folder}: {key} placeholders {found} differ')
for name in re.findall(r'R\.string\.(widget_\w+)', widget):
    check(name in keys, f'R.string.{name} is not in the checked key list')

# Large fonts: sizes in sp, touch targets ≥ 48dp, text that can wrap or end with an ellipsis.
layout = E.parse(res / 'layout/widget_today.xml').getroot()
for view in layout.iter():
    size = view.get(A + 'textSize')
    if size: check(size.endswith('sp'), f'{view.get(A + "id")}: textSize must be in sp')
    if view.tag == 'Button':
        check(view.get(A + 'minHeight') == '48dp', f'{view.get(A + "id")}: touch target must be ≥ 48dp')
    if view.tag in ('TextView', 'Button'):
        check(view.get(A + 'ellipsize') == 'end', f'{view.get(A + "id")}: long text must end with an ellipsis')

# Contrast in light and dark: text ≥ 4.5:1 on its background.
def colours(folder):
    return {c.get('name'): c.text.strip() for c in E.parse(res / folder / 'colors.xml').getroot().iter('color')}
def luminance(value):
    rgb = [int(value[i:i + 2], 16) / 255 for i in (3, 5, 7)]
    rgb = [c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4 for c in rgb]
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
def contrast(a, b):
    x, y = sorted((luminance(a), luminance(b)), reverse=True)
    return (x + 0.05) / (y + 0.05)
light, dark = colours('values'), colours('values-night')
check(set(light) == set(dark), 'values-night/colors.xml must define the same widget colours')
for palette, name in ((light, 'light'), (dark, 'dark')):
    for text, background in (('widget_text', 'widget_surface'), ('widget_secondary', 'widget_surface'),
                             ('widget_accent', 'widget_surface'), ('widget_on_accent', 'widget_accent')):
        ratio = contrast(palette[text], palette[background])
        check(ratio >= 4.5, f'{name}: {text} on {background} contrast {ratio:.2f} < 4.5')

if failures:
    raise SystemExit('FAIL widget:\n' + '\n'.join(failures))
print(f'PASS widget: {len(keys)} strings × 4 locales, intents, privacy, refresh and contrast')
