#!/usr/bin/env python3
"""Strings for 0.14.62-qa-fixes (QA-EXPLORATORY-0.14.61): new and corrected texts in Ukrainian, English and Polish.

Run after an edit to this file; it is idempotent. values/strings.xml stays a copy of values-uk (TXT-01).
UPDATED replaces existing values, ADDED appends new strings before </resources>. None keeps that language as it is.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from consistency_strings import ENTRY, ENTRY_NAME, decode, encode  # noqa: E402

RES = ROOT / 'app/src/main/res'
LOCALES = [('values-uk', 0), ('values-en', 1), ('values-pl', 2)]

UPDATED = {
    # QA-61-18: back to the wording before TXT-05; after «із» the noun is genitive for any count, and an outfit has 2+ items.
    'style_mixed': ('Стилі змішані: %1$s — %2$d із %3$d речей', 'Mixed styles: %1$s — %2$d of %3$d items',
                    'Style mieszane: %1$s — %2$d z %3$d rzeczy'),
    # QA-61-19: gender-neutral.
    'draft_restored': ('Чернетку відновлено — продовжуй з того самого місця.', None, None),
    'auth_reauth_hint': ('Сеанс завершився. Увійди в той самий акаунт, щоб продовжити.',
                         'Your session has ended. Sign in to the same account to continue.', None),
    # Found by the extended TXT-03 guard: «Ти вийшов» is gendered.
    'family_member_left': ('Спільний гардероб залишено.', None, None),
    # QA-61-04: the form still needs a photo, so the entry says what it is.
    'shop_manual': ('Заповнити вручну', 'Fill in manually', 'Wypełnij ręcznie'),
    # QA-61-05: invitations exist since 0.14.6 and sending changes is automatic since 0.14.23.
    'family_hint': ('Створює спільну копію одного гардероба на сервері. Твої локальні дані залишаються на телефоні. '
                    'Запрошення та спільні зміни — у розділі «Сімейні гардероби та запрошення».',
                    'Creates a shared server copy of one wardrobe. Your local data stays on this phone. '
                    'Invitations and shared changes are under “Family wardrobes and invitations”.',
                    'Tworzy wspólną kopię jednej garderoby na serwerze. Twoje dane lokalne zostają na telefonie. '
                    'Zaproszenia i wspólne zmiany są w sekcji „Rodzinne garderoby i zaproszenia”.'),
    'family_local_changes': ('Після підготовки копії гардероб змінився. Ці зміни не потраплять у копію; щоб їх врахувати, '
                             'натисни «Підготувати заново».',
                             'The wardrobe has changed since the copy was prepared. Those changes will not be in the copy; '
                             'to include them, tap “Prepare again”.',
                             'Garderoba zmieniła się od przygotowania kopii. Tych zmian nie będzie w kopii; '
                             'aby je uwzględnić, stuknij „Przygotuj ponownie”.'),
    # QA-61-11: say what leaving does, without «чернетка» and «записи».
    'discard_item_title': ('Вийти без збереження?', 'Leave without saving?', 'Wyjść bez zapisywania?'),
    'discard_item_hint': ('Зміни в цій формі ще не збережено. Якщо вийти, вони зникнуть.',
                          'The changes in this form are not saved yet. If you leave, they will be lost.',
                          'Zmiany w tym formularzu nie są jeszcze zapisane. Jeśli wyjdziesz, przepadną.'),
}

ADDED = {
    # QA-61-08: a state on the photo, not the action button «Вдягаю сьогодні».
    'worn_today_badge': ('Вдягнено сьогодні', 'Worn today', 'Noszone dziś'),
    # QA-61-04: the photo is required like the category.
    'item_photo_required': ('Фото *', 'Photo *', 'Zdjęcie *'),
    # QA-61-06: two wardrobes with one name cannot be told apart in the switcher.
    'wardrobe_name_taken': ('Гардероб із такою назвою вже є.', 'A wardrobe with this name already exists.',
                            'Garderoba o tej nazwie już istnieje.'),
}


def main():
    changed = added = 0
    for folder, column in LOCALES:
        path = RES / folder / 'strings.xml'
        text = path.read_text(encoding='utf-8')
        known = set(ENTRY_NAME.findall(text))
        missing = [k for k in UPDATED if k not in known]
        assert not missing, (folder, missing)

        def apply(m):
            nonlocal changed
            key = m.group(2)
            if key in UPDATED and UPDATED[key][column] is not None:
                new = encode(UPDATED[key][column])
                if new != m.group(3):
                    changed += 1
                    return m.group(1) + new + m.group(4)
            if key in ADDED and encode(ADDED[key][column]) != m.group(3):
                changed += 1
                return m.group(1) + encode(ADDED[key][column]) + m.group(4)
            return m.group(0)
        text = ENTRY.sub(apply, text)
        new_lines = ''.join(f'    <string name="{k}">{encode(v[column])}</string>\n' for k, v in ADDED.items() if k not in known)
        if new_lines:
            added += new_lines.count('\n')
            text = re.sub(r'</resources>\s*$', new_lines + '</resources>\n', text)
        path.write_text(text, encoding='utf-8')
    (RES / 'values' / 'strings.xml').write_text((RES / 'values-uk' / 'strings.xml').read_text(encoding='utf-8'), encoding='utf-8')
    print(f'qa-fixes strings: {changed} values changed, {added} added; default locale = values-uk')


if __name__ == '__main__':
    main()
