"""Labels for 0.14.40 UX-03: the score's explanation on request.

New keys are appended; existing keys are left as they are.
"""
STRINGS = {
'style_why_show': ('Чому така оцінка', 'Why this score', 'Skąd ta ocena'),
'style_why_hide': ('Сховати пояснення', 'Hide the explanation', 'Ukryj wyjaśnienie'),
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
