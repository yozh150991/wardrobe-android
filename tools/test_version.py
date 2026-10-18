import unittest
from check_version import read_version, validate


class VersionTests(unittest.TestCase):
    def test_current_and_matching_tag(self):
        current = read_version('versionCode = 34\nversionName = "0.14.4-photo-hotfix3"\n')
        validate(current, 'v0.14.4-photo-hotfix3', (33, '0.14.3-family-hotfix2'))

    def test_wrong_tag(self):
        with self.assertRaises(ValueError): validate((34, '0.14.4-photo-hotfix3'), 'v0.14.4')

    def test_new_name_without_code_bump(self):
        with self.assertRaises(ValueError): validate((34, '0.14.5'), previous=(34, '0.14.4-photo-hotfix3'))

    def test_decreasing_code(self):
        with self.assertRaises(ValueError): validate((33, '0.14.5'), previous=(34, '0.14.4-photo-hotfix3'))

    def test_same_version_development_commit(self):
        validate((34, '0.14.4-photo-hotfix3'), previous=(34, '0.14.4-photo-hotfix3'))

    def test_bad_literals(self):
        for text in ['versionCode = 0\nversionName = "1.0.0"', 'versionCode = 34\nversionName = "tomorrow"',
                     'versionCode = 34\nversionCode = 35\nversionName = "1.0.0"']:
            with self.assertRaises(ValueError): read_version(text)


if __name__ == '__main__': unittest.main()
