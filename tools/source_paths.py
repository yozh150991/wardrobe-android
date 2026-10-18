"""Source files owned by this project; never descend into caches or snapshots."""
import os
from pathlib import Path

IGNORED = {'build', 'node_modules', '__pycache__', 'venv', '.venv'}


def source_paths(root: Path):
    paths = set(root.glob('*.kts'))
    # Module Gradle configuration is source too, despite living outside app/src.
    paths.update((root / 'app').glob('*.kts'))
    for start in (root / 'app/src', root / 'tools'):
        if start.is_symlink():
            continue
        for directory, children, files in os.walk(start, followlinks=False):
            children[:] = sorted(name for name in children
                                 if not name.startswith('.') and name not in IGNORED
                                 and not (Path(directory) / name).is_symlink())
            paths.update(Path(directory) / name for name in files
                         if Path(name).suffix in {'.kt', '.kts', '.java'})
    return sorted(path for path in paths if path.is_file() and not path.is_symlink())
