"""Labels for 0.14.29 ROT-01: outfits around items not worn for a long time.

New keys are appended; existing keys are left as they are.
"""
STRINGS = {
'rotation_title': ('Давно не вдягали', 'Not worn for a while', 'Dawno nienoszone'),
'rotation_anchor_title': ('Образ із цією річчю', 'An outfit with this item', 'Zestaw z tą rzeczą'),
'rotation_reason_worn': ('«%1$s» востаннє вдягали %2$s. Днів без носіння: %3$d.',
    '“%1$s” was last worn on %2$s. Days without wearing: %3$d.',
    '„%1$s” noszono ostatnio %2$s. Dni bez noszenia: %3$d.'),
'rotation_reason_never': ('«%1$s» ще жодного разу не вдягали. У гардеробі з %2$s, днів: %3$d.',
    '“%1$s” hasn’t been worn yet. In the wardrobe since %2$s, days: %3$d.',
    '„%1$s” nie była jeszcze noszona. W szafie od %2$s, dni: %3$d.'),
'rotation_fits': ('Пасує до умов: %1$s · %2$s.', 'Fits the conditions: %1$s · %2$s.', 'Pasuje do warunków: %1$s · %2$s.'),
'rotation_saved_name': ('%1$s · %2$s', '%1$s · %2$s', '%1$s · %2$s'),
'rotation_other': ('Інша річ', 'Another item', 'Inna rzecz'),
'rotation_pick_today': ('Підібрати на сьогодні', 'Pick for today', 'Dobierz na dziś'),
'rotation_problem_unavailable': ('Зараз її не вдягнути: вона в пранні, ремонті, позичена чи в архіві.',
    'It can’t be worn now: it’s in the laundry, under repair, lent or archived.',
    'Teraz nie da się jej założyć: jest w praniu, naprawie, pożyczona lub w archiwum.'),
'rotation_problem_excluded': ('Ця річ виключена з рекомендацій. Повернути її можна в «Налаштувати умови».',
    'This item is excluded from recommendations. You can bring it back in “Configure conditions”.',
    'Ta rzecz jest wykluczona z propozycji. Przywrócić ją można w „Ustaw warunki”.'),
'rotation_problem_weather': ('За сезоном вона не пасує до погоди «%1$s». Змініть умови або оберіть її іншого дня.',
    'By season it doesn’t suit “%1$s” weather. Change the conditions or choose it another day.',
    'Ze względu na sezon nie pasuje do pogody „%1$s”. Zmień warunki lub wybierz ją innego dnia.'),
'rotation_problem_repeat': ('Її вдягали нещодавно, а правило гардероба не повторює речі кілька днів.',
    'It was worn recently, and the wardrobe’s rule doesn’t repeat items for a few days.',
    'Była noszona niedawno, a reguła szafy nie powtarza rzeczy przez kilka dni.'),
'rotation_problem_items': ('Зараз бракує доступних речей, які з нею поєднуються.',
    'Some available items that go with it are missing right now.',
    'Brakuje teraz dostępnych rzeczy, które do niej pasują.'),
}
if __name__ == '__main__':
    from pathlib import Path
    from xml.sax.saxutils import escape
    root = Path(__file__).resolve().parents[1] / 'app/src/main/res'
    def encode(value):
        return escape(value).replace("'", "\\'").replace('"', '\\"')
    for folder, column in [('values', 0), ('values-uk', 0), ('values-en', 1), ('values-pl', 2)]:
        path = root / folder / 'strings.xml'; text = path.read_text()
        for key, values in STRINGS.items():
            if f'name="{key}"' in text: continue
            text = text.replace('</resources>', f'    <string name="{key}">{encode(values[column])}</string>\n</resources>', 1)
        path.write_text(text)
