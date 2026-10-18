#!/bin/sh
set -eu
cd -- "$(dirname -- "$0")"
for wardrobe_python in python3 python; do
    if command -v "$wardrobe_python" >/dev/null 2>&1 && "$wardrobe_python" -c 'import sys; sys.exit(sys.version_info < (3,9))' </dev/null 2>/dev/null; then
        exec "$wardrobe_python" tools/git_safety.py install "$@"
    fi
done
if command -v py >/dev/null 2>&1 && py -3 -c 'import sys; sys.exit(sys.version_info < (3,9))' </dev/null 2>/dev/null; then
    exec py -3 tools/git_safety.py install "$@"
fi
echo 'Install Python 3.9+ from python.org, then run this installer again.' >&2
exit 2
