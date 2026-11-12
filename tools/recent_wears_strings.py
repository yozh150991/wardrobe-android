"""Labels for 0.14.39 UX-06: what «Ношу рідко» does.

New keys are appended; existing keys are left as they are.
"""
STRINGS = {
'rare_hint': ('Річ, яку ви свідомо носите рідко (костюм, лижний одяг): вона не потрапляє в «Давно не вдягали» й до забутих у статистиці.',
    'An item you wear rarely on purpose (a suit, ski wear): it is not offered in “Not worn for a while” or counted as forgotten in the statistics.',
    'Rzecz, którą celowo nosisz rzadko (garnitur, odzież narciarska): nie trafia do „Dawno nienoszone” ani do zapomnianych w statystykach.'),
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
