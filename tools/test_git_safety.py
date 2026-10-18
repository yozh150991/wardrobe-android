#!/usr/bin/env python3
"""Behavior tests against real temporary Git repos/remotes. No network or real secrets."""
import importlib.util
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

PROJECT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("guard", PROJECT / "tools/git_safety.py")
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


def fake_token():
    return "gh" + "p_" + "A7" * 20


class GitSafetyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="wardrobe-guard-test-")
        self.base = Path(self.temp.name)
        self.root = self.base / "project with spaces"
        self.root.mkdir()
        self.env = dict(os.environ)
        for key in list(self.env):
            if key.startswith("GIT_"):
                self.env.pop(key)
        self.env.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
                        GIT_AUTHOR_NAME="Guard Test", GIT_AUTHOR_EMAIL="guard@example.invalid",
                        GIT_COMMITTER_NAME="Guard Test", GIT_COMMITTER_EMAIL="guard@example.invalid")
        self.git("init", "-q", "-b", "main")
        for rel in ("tools/git_safety.py", "tools/git_safety_allowlist.json", ".githooks/pre-commit", ".githooks/pre-push",
                    "install-git-safety.sh", ".gitattributes"):
            dest = self.root / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(PROJECT / rel, dest)
        self.run_guard("install", expected=0)
        self.git("add", ".")
        self.git("commit", "-qm", "guard baseline")
        self.remote = self.base / "remote.git"
        self.call(["git", "init", "--bare", "-q", str(self.remote)])
        self.git("remote", "add", "origin", str(self.remote))

    def tearDown(self):
        self.temp.cleanup()

    def call(self, args, expected=0, input=None, cwd=None):
        result = subprocess.run(args, cwd=cwd or self.root, env=self.env, input=input,
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if expected is not None:
            self.assertEqual(result.returncode, expected, result.stdout + result.stderr)
        return result

    def git(self, *args, expected=0, input=None):
        return self.call(["git", *args], expected=expected, input=input)

    def run_guard(self, mode, expected=None, input=None):
        return self.call([sys.executable, "tools/git_safety.py", mode], expected=expected, input=input)

    def put(self, path, data):
        p = self.root / path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data if isinstance(data, bytes) else data.encode())

    def commit_unsafe(self, path, data):
        self.put(path, data)
        self.git("add", "-f", "--", path)
        self.git("-c", "core.hooksPath=" + str(self.base / "no-hooks"), "commit", "-qm", "synthetic unsafe fixture")

    def assert_push_blocked(self, *args):
        result = self.git("push", *(args or ("origin", "main")), expected=None)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Wardrobe Git Safety", result.stderr)
        self.assertNotIn(fake_token(), result.stderr)
        return result

    def test_clean_first_push_and_normal_update(self):
        self.git("push", "origin", "main")
        self.put("app/src/Main.kt", "class Main\n")
        self.git("add", ".")
        self.git("commit", "-qm", "safe source")
        self.git("push", "origin", "main")

    def test_key_force_added_blocks_commit_without_touching_index(self):
        self.put("signing/release.jks", b"synthetic key bytes")
        self.git("add", "-f", "signing/release.jks")
        before = self.git("ls-files", "--stage").stdout
        result = self.git("commit", "-qm", "must block", expected=None)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(before, self.git("ls-files", "--stage").stdout)
        self.assertTrue((self.root / "signing/release.jks").exists())

    def test_existing_ignore_does_not_allow_tracked_key_push(self):
        self.commit_unsafe("debug.keystore", "synthetic")
        self.assert_push_blocked()

    def test_deleted_secret_is_detected_in_earlier_commit(self):
        self.commit_unsafe("random.txt", fake_token())
        self.git("rm", "random.txt")
        self.git("commit", "-qm", "delete unsafe fixture")
        result = self.assert_push_blocked()
        self.assertIn("commit ", result.stderr)
        self.assertIn("/random.txt", (self.root / ".gitignore").read_text())

    def test_staged_blob_not_clean_working_copy_is_inspected(self):
        self.put("app/src/Config.kt", 'val token = "' + fake_token() + '"')
        self.git("add", "app/src/Config.kt")
        self.put("app/src/Config.kt", "val token = System.getenv(\"TOKEN\")")
        result = self.run_guard("pre-commit", expected=1)
        self.assertIn("Config.kt", result.stderr)
        self.assertNotIn("/app/src/Config.kt", (self.root / ".gitignore").read_text())

    def test_untracked_arbitrary_credentials_auto_ignore_idempotently(self):
        name = "exports/new credentials [prod]*?.json"
        self.put(name, json.dumps({"type": "service_" + "account", "private_key": "synthetic"}))
        self.run_guard("check", expected=1)
        self.git("check-ignore", "--", name)
        before = (self.root / ".gitignore").read_bytes()
        self.run_guard("check", expected=0)
        self.assertEqual(before, (self.root / ".gitignore").read_bytes())
        self.assertTrue((self.root / name).exists())

    def test_safe_new_source_is_not_ignored(self):
        self.put("app/src/Feature.kt", "class Feature")
        self.run_guard("check", expected=0)
        self.git("check-ignore", "app/src/Feature.kt", expected=1)

    def test_wrapper_and_safe_templates_allowed(self):
        self.put("gradle/wrapper/gradle-wrapper.jar", b"PK\x00synthetic jar")
        self.put("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https://example.invalid/gradle.zip")
        self.put(".env.example", 'TOKEN="YOUR_TOKEN_HERE"\n')
        self.git("add", ".")
        self.git("commit", "-qm", "wrapper and template")
        self.git("push", "origin", "main")

    def test_realistic_secret_in_template_still_blocks(self):
        self.put(".env.example", "TOKEN='" + fake_token() + "'")
        self.git("add", ".env.example")
        self.run_guard("pre-commit", expected=1)

    def test_all_branches_in_one_push_are_inspected(self):
        self.git("switch", "-qc", "unsafe")
        self.commit_unsafe("hidden.txt", fake_token())
        self.git("switch", "-q", "main")
        self.assert_push_blocked("origin", "main", "unsafe")
        result = self.call(["git", "--git-dir=" + str(self.remote), "show-ref"], expected=1)
        self.assertEqual(result.stdout, "")

    def test_annotated_tag_is_inspected(self):
        self.commit_unsafe("token.txt", fake_token())
        self.git("tag", "-a", "unsafe-tag", "-m", "synthetic tag")
        self.assert_push_blocked("origin", "unsafe-tag")

    def test_force_push_is_inspected(self):
        self.git("push", "origin", "main")
        self.git("switch", "--orphan", "replacement")
        self.git("add", ".")
        self.commit_unsafe("leak.txt", fake_token())
        self.assert_push_blocked("--force", "origin", "replacement:main")

    def test_other_remote_tracking_ref_does_not_hide_outgoing_secret(self):
        self.commit_unsafe("hidden.txt", fake_token())
        head = self.git("rev-parse", "HEAD").stdout.strip()
        self.git("update-ref", "refs/remotes/elsewhere/main", head)
        self.assert_push_blocked()

    def test_unknown_remote_base_scans_full_history(self):
        self.commit_unsafe("hidden.txt", fake_token())
        head = self.git("rev-parse", "HEAD").stdout.strip()
        result = self.run_guard("pre-push", input="refs/heads/main " + head + " refs/heads/main " + "a" * 40 + "\n", expected=1)
        self.assertIn("hidden.txt", result.stderr)

    def test_ref_deletion_does_not_scan_local_secrets(self):
        self.commit_unsafe("hidden.txt", fake_token())
        head = self.git("rev-parse", "HEAD").stdout.strip()
        self.run_guard("pre-push", input="(delete) " + "0" * 40 + " refs/heads/topic " + head + "\n", expected=0)

    def test_malformed_hook_input_fails_closed(self):
        self.run_guard("pre-push", input="invalid\n", expected=2)

    def test_noncommit_tag_fails_closed(self):
        blob = self.git("hash-object", "-w", "--stdin", input="synthetic").stdout.strip()
        self.git("tag", "blob-tag", blob)
        self.assert_push_blocked("origin", "blob-tag")

    def test_existing_hook_configuration_is_preserved(self):
        self.git("config", "core.hooksPath", "custom-hooks")
        before = (self.root / ".gitignore").read_bytes()
        self.run_guard("install", expected=2)
        self.assertEqual(self.git("config", "core.hooksPath").stdout.strip(), "custom-hooks")
        self.assertEqual(before, (self.root / ".gitignore").read_bytes())

    def test_default_existing_hooks_are_not_disabled(self):
        self.git("config", "--unset", "core.hooksPath")
        hook = self.root / ".git/hooks/post-commit"
        hook.write_text("#!/bin/sh\nexit 0\n")
        self.run_guard("install", expected=2)
        self.assertEqual(hook.read_text(), "#!/bin/sh\nexit 0\n")
        self.git("config", "--get", "core.hooksPath", expected=1)

    def test_user_ignore_rules_crlf_and_unicode_are_preserved(self):
        text = "# custom\r\nmy-cache/\r\n!keep.txt\r\n"
        (self.root / ".gitignore").write_bytes(text.encode())
        self.run_guard("install", expected=0)
        content = (self.root / ".gitignore").read_bytes()
        self.assertTrue(content.startswith(text.encode()))
        self.assertNotIn(b"\n", content.replace(b"\r\n", b""))
        self.put("експорт/новий файл.json", '{"type":"service_' + 'account"}')
        self.run_guard("check", expected=1)
        self.git("check-ignore", "експорт/новий файл.json")

    @unittest.skipIf(os.name == "nt", "POSIX symlink test")
    def test_symlink_ignore_never_overwrites_target(self):
        external = self.base / "outside.txt"
        external.write_text("keep")
        (self.root / ".gitignore").unlink()
        (self.root / ".gitignore").symlink_to(external)
        self.run_guard("check", expected=2)
        self.assertEqual(external.read_text(), "keep")

    def test_oversized_blob_blocks_instead_of_skipping(self):
        self.commit_unsafe("oversized.bin", b"0" * (guard.MAX_BLOB + 1))
        self.assertIn("10 MiB", self.assert_push_blocked().stderr)

    def test_utf16_credentials_detected(self):
        self.commit_unsafe("export.txt", ('{"type":"service_' + 'account"}').encode("utf-16"))
        self.assert_push_blocked()

    def test_new_clone_requires_install_and_then_blocks(self):
        self.git("push", "origin", "main")
        clone = self.base / "second computer"
        self.call(["git", "clone", "-q", "-b", "main", str(self.remote), str(clone)])
        self.call(["git", "config", "--get", "core.hooksPath"], cwd=clone, expected=1)
        self.call(["sh", "install-git-safety.sh"], cwd=clone)
        self.assertTrue(self.call(["git", "config", "core.hooksPath"], cwd=clone).stdout.strip().endswith("/.git/wardrobe-git-safety/hooks"))

    def test_subdirectory_project_installs_relative_hooks(self):
        # Reuse one repo with the entire guard moved under an Android subproject.
        project = self.root / "android project"
        project.mkdir()
        for rel in ("tools", ".githooks", "install-git-safety.sh"):
            shutil.move(str(self.root / rel), project / rel)
        self.git("config", "--unset", "core.hooksPath")
        self.call([sys.executable, str(project / "tools/git_safety.py"), "install"])
        self.assertTrue(self.git("config", "core.hooksPath").stdout.strip().endswith("/.git/wardrobe-git-safety/hooks"))
        self.git("add", ".")
        self.git("commit", "-qm", "nested guard")
        self.git("push", "origin", "main")

    def test_explicit_unignore_does_not_bypass_guard(self):
        with (self.root / ".gitignore").open("a") as stream:
            stream.write("\n!release.jks\n")
        self.put("release.jks", "synthetic")
        self.git("add", ".")
        self.run_guard("pre-commit", expected=1)
        self.git("check-ignore", "--no-index", "release.jks")

    def test_commit_merge_parent_history_is_scanned(self):
        self.git("switch", "-qc", "side")
        self.commit_unsafe("old.txt", fake_token())
        self.git("rm", "old.txt")
        self.git("commit", "-qm", "remove fixture")
        self.git("switch", "-q", "main")
        self.git("merge", "--no-ff", "side", "-m", "merge side")
        self.assert_push_blocked()

    def test_allowlist_matches_exact_file_hash_only(self):
        name = "fake-emulator-test.txt"
        body = 'pass' + 'word="synthetic-emulator-only"'
        policy = {"allow": [{"path": name, "sha256": hashlib.sha256(body.encode()).hexdigest(),
                              "reason": "possible hardcoded password/token; review the literal"}]}
        self.put("tools/git_safety_allowlist.json", json.dumps(policy))
        self.run_guard("install", expected=0)
        self.put(name, body)
        self.git("add", ".")
        self.git("commit", "-qm", "reviewed fixture")
        self.put(name, body + "\n# changed test")
        self.git("add", name)
        result = self.git("commit", "-qm", "must review again", expected=None)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(name, result.stderr)

    def test_allowlist_cannot_suppress_real_token_signature(self):
        body = fake_token()
        name = "test.txt"
        policy = {"allow": [{"path": name, "sha256": hashlib.sha256(body.encode()).hexdigest(),
                              "reason": "possible hardcoded password/token; review the literal"}]}
        self.put("tools/git_safety_allowlist.json", json.dumps(policy))
        self.run_guard("install", expected=0)
        self.put(name, body)
        self.git("add", ".")
        self.run_guard("pre-commit", expected=1)

    def test_push_discovers_new_local_credential_and_retry_succeeds(self):
        self.git("push", "origin", "main")
        self.put("new-export.txt", fake_token())
        self.put("Feature.kt", "class Feature")
        self.git("add", "Feature.kt")
        self.git("-c", "core.hooksPath=" + str(self.base / "no-hooks"), "commit", "-qm", "safe feature")
        self.assert_push_blocked()
        self.git("check-ignore", "new-export.txt")
        self.git("add", ".gitignore")
        self.git("commit", "-qm", "ignore local export")
        self.git("push", "origin", "main")
        self.assertTrue((self.root / "new-export.txt").is_file())
        tree = self.call(["git", "--git-dir=" + str(self.remote), "ls-tree", "-r", "--name-only", "main"]).stdout
        self.assertNotIn("new-export.txt", tree)

    def test_shallow_history_fails_closed(self):
        head = self.git("rev-parse", "HEAD").stdout.strip()
        (self.root / ".git/shallow").write_text(head + "\n")
        result = self.run_guard("pre-push", input="refs/heads/main " + head + " refs/heads/main " + "0" * 40 + "\n", expected=2)
        self.assertIn("Shallow", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
