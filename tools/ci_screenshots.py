#!/usr/bin/env python3
"""DEV-04: turn the screenshot tour's logcat lines back into pictures.

ScreenshotTourTest writes every screen as `WSHOT|name|part|parts|base64` lines under the logcat tag WardrobeShots.
CI dumps that tag after the emulator run (screenshots.log). This script

* reassembles each screen into OUT_DIR/<name>.jpg (uploaded as the `screenshots-<sha>` artifact);
* prints the WSHOT lines into the job log, so a reviewer that can read the log but not the artifact
  (Claude in a cloud session) can rebuild the same pictures;
* reports how many screens arrived, which are incomplete and which the tour could not reach (WSHOT-SKIP).

When the dump is missing it falls back to the per-test logcat files that the Android test runner keeps under
RESULTS_DIR. It never fails the build: the pictures are for review, the tour itself is the test.

Usage: ci_screenshots.py LOGCAT_DUMP RESULTS_DIR OUT_DIR [--quiet]
"""
import base64
import pathlib
import re
import sys

MARK = 'WSHOT|'
SKIP = 'WSHOT-SKIP|'
NAME = re.compile(r'^[a-z0-9-]{1,60}$')


def lines_from(dump: pathlib.Path, results: pathlib.Path):
    if dump.is_file():
        found = [line for line in dump.read_text(encoding='utf-8', errors='replace').splitlines() if MARK in line or SKIP in line]
        if found:
            return found, str(dump)
    found = []
    if results.is_dir():
        for path in sorted(results.rglob('*.txt')):
            found += [line for line in path.read_text(encoding='utf-8', errors='replace').splitlines() if MARK in line or SKIP in line]
    return found, str(results)


def collect(lines):
    """name -> {part: text, ...} and the expected number of parts; later runs of the same screen win."""
    shots, expected = {}, {}
    for line in lines:
        if MARK not in line:
            continue
        fields = line[line.index(MARK):].strip().split('|')
        if len(fields) != 5:
            continue
        _, name, part, parts, text = fields
        if not NAME.match(name) or not part.isdigit() or not parts.isdigit():
            continue
        part, parts = int(part), int(parts)
        if not 1 <= part <= parts or not re.fullmatch(r'[A-Za-z0-9+/=]+', text):
            continue
        if part == 1:
            shots[name] = {}
        shots.setdefault(name, {})[part] = text
        expected[name] = parts
    return shots, expected


def main(argv):
    quiet = '--quiet' in argv
    args = [a for a in argv if a != '--quiet']
    if len(args) != 3:
        print(__doc__)
        return 2
    dump, results, out = (pathlib.Path(a) for a in args)
    lines, source = lines_from(dump, results)
    shots, expected = collect(lines)
    out.mkdir(parents=True, exist_ok=True)
    complete, broken = [], []
    for name in sorted(shots):
        parts = shots[name]
        if sorted(parts) != list(range(1, expected[name] + 1)):
            broken.append(name)
            continue
        data = base64.b64decode(''.join(parts[i] for i in sorted(parts)))
        if not data.startswith(b'\xff\xd8'):
            broken.append(name)
            continue
        (out / f'{name}.jpg').write_bytes(data)
        complete.append(name)
        if not quiet:
            for i in sorted(parts):
                print(f'{MARK}{name}|{i}|{expected[name]}|{parts[i]}')
    print(f'::notice title=Screenshots::{len(complete)} screens from {source}'
          + (f'; incomplete: {", ".join(broken)}' if broken else ''))
    for line in lines:
        if SKIP in line:
            print(f'::warning title=Screenshot skipped::{line[line.index(SKIP) + len(SKIP):].strip()[:220]}')
    if not complete:
        print('::warning title=Screenshots::no screenshot arrived (did ScreenshotTourTest run?)')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
