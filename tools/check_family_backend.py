#!/usr/bin/env python3
"""Executable backend domain/ZIP tests. Run Firebase integration separately."""
from pathlib import Path
import subprocess, sys
root = Path(__file__).resolve().parents[1]
subprocess.run([sys.executable, '-m', 'unittest', 'discover', '-s', 'tests', '-v'],
               cwd=root / 'cloud/family-functions', check=True)
print('Firebase adapters/callable/rules: see release-specific emulator evidence; not run by this script.')
