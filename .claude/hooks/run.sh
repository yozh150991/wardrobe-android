#!/bin/sh
# Runs a harness hook with the Python this clone already uses for Git Safety (git config wardrobe.gitSafetyPython),
# else python3, else python. On Windows Claude Code runs this through Git Bash.
dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
python=$(git -C "$dir" config --local --get wardrobe.gitSafetyPython 2>/dev/null || :)
if [ -z "$python" ] || ! "$python" -c "" >/dev/null 2>&1; then
    if python3 -c "" >/dev/null 2>&1; then python=python3; else python=python; fi
fi
exec "$python" "$dir/$1.py"
