"""Regression: broken generated/backup sources must not enter the project scan."""
from pathlib import Path
import tempfile
import unittest
from source_paths import source_paths


class SourcePathsTest(unittest.TestCase):
    def test_sources_and_excluded_trees(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            included = ['build.gradle.kts', 'settings.gradle.kts', 'app/build.gradle.kts',
                        'app/src/main/java/a/Thing.kt', 'app/src/test/java/a/Test.java',
                        'app/src/androidTest/java/a/Device.kt', 'tools/cases/Case.java']
            excluded = ['.wardrobe-old-backup-1/Bad.kt', 'app/build/generated/Bad.kt',
                        '.gradle/Bad.kts', 'node_modules/Bad.java', 'tools/node_modules/Bad.java',
                        'tools/.cache/Bad.kt', 'cloud/node_modules/Bad.java']
            for name in included + excluded:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('broken {' if name in excluded else '// source')
            self.assertEqual(set(included), {p.relative_to(root).as_posix() for p in source_paths(root)})
            try:
                (root / 'tools/linked').symlink_to(root / 'node_modules', target_is_directory=True)
            except OSError:
                pass
            self.assertEqual(set(included), {p.relative_to(root).as_posix() for p in source_paths(root)})


if __name__ == '__main__':
    unittest.main()
