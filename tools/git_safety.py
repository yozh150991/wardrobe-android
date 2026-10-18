#!/usr/bin/env python3
"""Local Git guard. Python 3.9+, standard library only; never edits the index/history."""
from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tempfile

VERSION = "1.0.0"
MAX_BLOB = 10 * 1024 * 1024
BEGIN = "# BEGIN WARDROBE GIT SAFETY"
END = "# END WARDROBE GIT SAFETY"
BASE_RULES = """# Signing keys and machine-specific credentials
*.jks
*.keystore
*.p12
*.pfx
*.key
id_rsa
id_dsa
id_ecdsa
id_ed25519
keystore.properties
key.properties
signing.properties
secrets.properties
credentials.json
application_default_credentials.json
*firebase-adminsdk*.json
*service-account*.json
*service_account*.json
.env
.env.*
!.env.example
!.env.sample
!.env.template
.netrc
# Client Firebase config is environment-specific, not an Admin SDK key
google-services.json
# Local tools, caches and generated output
local.properties
.gradle/
.idea/
.tools/
.android/
.kotlin/
.cxx/
.externalNativeBuild/
**/build/
node_modules/
.firebase/
.venv/
venv/
__pycache__/
*.pyc
*.pyo
.DS_Store
Thumbs.db
*.apk
*.aab
*.hprof
*.log
!**/release-evidence/*.log
# Keep personal exports outside the source tree
private/
secrets/
personal-data/
user-data/
backups/
wardrobe-backups/
.wardrobe-*-backup*/
.wardrobe-git-safety-backup-*/
*.db
*.db-wal
*.db-shm
*.sqlite
*.sqlite3
*.zip
*.7z
*.rar
*.tar
*.gz
*.bz2
*.xz
"""
DIRS = {".gradle", ".idea", ".tools", ".android", ".kotlin", ".cxx",
        ".externalnativebuild", "build", "node_modules", ".firebase", ".venv",
        "venv", "__pycache__", "private", "secrets", "personal-data", "user-data",
        "backups", "wardrobe-backups"}
NAMES = {"local.properties", "keystore.properties", "key.properties", "signing.properties",
         "secrets.properties", "credentials.json", "application_default_credentials.json",
         "google-services.json", ".netrc", ".ds_store", "thumbs.db",
         "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519"}
SUFFIXES = (".jks", ".keystore", ".p12", ".pfx", ".key", ".apk", ".aab", ".hprof",
            ".pyc", ".pyo", ".db", ".db-wal", ".db-shm", ".sqlite", ".sqlite3",
            ".zip", ".7z", ".rar", ".tar", ".gz", ".bz2", ".xz")
TEMPLATES = {".env.example", ".env.sample", ".env.template"}
SOURCE_SUFFIXES = {".kt", ".kts", ".java", ".py", ".sh", ".ps1", ".bat", ".cmd",
                   ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".xml", ".md",
                   ".yml", ".yaml", ".toml", ".gradle", ".html", ".css"}
PATTERNS = [
    ("private signing/SSH key", re.compile(rb"-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----")),
    ("Firebase/Google service-account credentials", re.compile(rb'"type"\s*:\s*"service_account"')),
    ("GitHub access token", re.compile(rb"\bgh[pousr]_[A-Za-z0-9]{30,}\b|\bgithub_pat_[A-Za-z0-9_]{30,}\b")),
    ("AWS access-key ID", re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b")),
    ("Slack access token", re.compile(rb"\bxox[baprs]-[A-Za-z0-9-]{20,}\b")),
    ("OpenAI-style secret key", re.compile(rb"\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{32,}\b")),
]
ASSIGNMENT = re.compile(
    rb'''(?i)(?:["']?)(?:password|passwd|storePassword|keyPassword|api[_-]?secret|client[_-]?secret|access[_-]?token|refresh[_-]?token|auth[_-]?token|api[_-]?key|token)(?:["']?)\s*[:=]\s*["']([^\r\n"']{8,256})["']''')
PLACEHOLDERS = {b"password", b"test-password", b"test-token", b"dummy-token", b"changeme",
                b"changeit", b"replace-me", b"your-secret-here", b"your_api_key"}


class GuardError(Exception):
    pass


def say(message):
    print("[Wardrobe Git Safety] " + message, file=sys.stderr)


def printable(path):
    return json.dumps(path, ensure_ascii=True)


def path_reason(path):
    parts = PurePosixPath(path).parts
    lower = [p.lower() for p in parts]
    name = lower[-1]
    if any(p in DIRS or fnmatch.fnmatchcase(p, ".wardrobe-*-backup*") for p in lower[:-1]):
        return "local cache, build output or private-data directory"
    if name in NAMES or name.endswith(SUFFIXES):
        return "private/local file or generated artifact"
    if name == ".env" or (name.startswith(".env.") and name not in TEMPLATES):
        return "environment credentials"
    if any(fnmatch.fnmatchcase(name, p) for p in ("*firebase-adminsdk*.json", "*service-account*.json", "*service_account*.json")):
        return "service-account credential filename"
    if name.endswith(".log") and (len(lower) < 2 or lower[-2] != "release-evidence"):
        return "local log (may contain personal data)"
    return None


def content_reasons(data):
    reasons = {reason for reason, pattern in PATTERNS if pattern.search(data)}
    # Inspect UTF-16 exports too; do not silently skip all binary files.
    if data.startswith((b"\xff\xfe", b"\xfe\xff")):
        try:
            reasons.update(content_reasons(data.decode("utf-16").encode("utf-8")))
        except UnicodeError:
            pass
    for match in ASSIGNMENT.finditer(data):
        value = match.group(1).strip()
        if value.lower() in PLACEHOLDERS or value.startswith((b"${", b"<", b"YOUR_", b"your_")):
            continue
        reasons.add("possible hardcoded password/token; review the literal")
    return sorted(reasons)


def auto_ignore_content(path, reasons):
    p = PurePosixPath(path)
    # Never hide application source/docs instead of fixing a hardcoded credential.
    if p.suffix.lower() in SOURCE_SUFFIXES or p.name.lower() in TEMPLATES:
        return False
    if p.name in {"gradle.properties", ".gitignore", ".gitattributes", "package.json", "package-lock.json"}:
        return False
    return bool(reasons) and p.suffix.lower() in {".json", ".pem", ".txt", ".ini", ".conf", ".cfg", ".properties", ".env"}


def literal_rule(path):
    # Root-anchored literal, not a wildcard over unrelated source files.
    if any(ord(c) < 32 or ord(c) == 127 for c in path):
        return None
    return "/" + "".join("\\" + c if c in "\\!*?[]# " else c for c in path)


def write_preserving(path, data):
    if path.is_symlink():
        raise GuardError("Refusing to modify a symlink: " + printable(str(path)))
    original = path.read_bytes() if path.exists() else None
    mode = stat.S_IMODE(path.stat().st_mode) if path.exists() else 0o644
    fd, temp = tempfile.mkstemp(prefix=".wardrobe-ignore-", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as out:
            out.write(data)
        os.chmod(temp, mode)
        if path.is_symlink() or (path.read_bytes() if path.exists() else None) != original:
            raise GuardError("File changed during the update; retry after checking local edits.")
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def merge_rules(path, new_paths=()):
    if path.is_symlink():
        raise GuardError("The repository .gitignore is a symlink; no changes made.")
    original = path.read_bytes() if path.exists() else b""
    text = original.decode("utf-8-sig")
    newline = "\r\n" if "\r\n" in text else "\n"
    normalized = text.replace("\r\n", "\n")
    if normalized.count(BEGIN) != normalized.count(END) or normalized.count(BEGIN) > 1:
        raise GuardError("Malformed managed block in .gitignore; fix its BEGIN/END markers.")
    if BEGIN in normalized and normalized.index(END) < normalized.index(BEGIN):
        raise GuardError("Reversed managed block markers in .gitignore.")
    block = BEGIN + "\n" + BASE_RULES + END
    if BEGIN in normalized:
        start = normalized.index(BEGIN)
        finish = normalized.index(END, start) + len(END)
        normalized = normalized[:start] + block + normalized[finish:]
    else:
        normalized = normalized.rstrip("\n") + ("\n\n" if normalized else "") + block + "\n"
    lines = set(normalized.splitlines())
    additions = sorted({rule for p in new_paths if (rule := literal_rule(p)) and rule not in lines})
    if additions:
        normalized = normalized.rstrip("\n") + "\n\n# Auto-detected local/private files (exact paths)\n" + "\n".join(additions) + "\n"
    encoded = normalized.replace("\n", newline).encode("utf-8")
    if original.startswith(b"\xef\xbb\xbf"):
        encoded = b"\xef\xbb\xbf" + encoded
    if encoded != original:
        write_preserving(path, encoded)
        return True
    return False


class Repo:
    def __init__(self, project):
        self.project = Path(project).resolve()
        self.env = dict(os.environ, GIT_NO_REPLACE_OBJECTS="1")
        self.env.pop("GIT_LITERAL_PATHSPECS", None)
        result = subprocess.run(["git", "-C", str(self.project), "rev-parse", "--show-toplevel"],
                                env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise GuardError("Open the project Git repository first (git init or git clone).")
        self.root = Path(os.fsdecode(result.stdout.rstrip(b"\r\n"))).resolve()
        self.ignore = self.root / ".gitignore"
        self.cache = {}
        self.blob_hashes = {}
        self.allowances = set()
        policy_path = Path(__file__).with_name("git_safety_allowlist.json")
        if policy_path.is_file():
            policy = json.loads(policy_path.read_text(encoding="utf-8"))
            prefix = "" if policy.get("repository_relative") else self.project.relative_to(self.root).as_posix()
            for entry in policy.get("allow", []):
                if entry["reason"] != "possible hardcoded password/token; review the literal" or not re.fullmatch(r"[a-f0-9]{64}", entry["sha256"]):
                    raise GuardError("Invalid allowlist: only reviewed literal warnings may be exempted by exact file hash.")
                name = (PurePosixPath(prefix) / entry["path"]).as_posix()
                self.allowances.add((name, entry["sha256"], entry["reason"]))
        self.seen = set()
        self.findings = {}
        self.discovered = set()
        self.batch = None
        self.commits_checked = 0

    def git(self, *args, input=None, ok=(0,)):
        result = subprocess.run(["git", "-C", str(self.root), *args], env=self.env, input=input,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode not in ok:
            # Do not print git stderr: it can echo credential URLs or file contents.
            raise GuardError("Git operation failed: " + args[0] + "; check the repository locally.")
        return result.stdout

    def close(self):
        if self.batch:
            self.batch.stdin.close()
            self.batch.stdout.close()
            self.batch.wait(timeout=10)
            self.batch = None

    def blob(self, oid):
        if oid in self.cache:
            return self.cache[oid]
        if not self.batch:
            self.batch = subprocess.Popen(["git", "-C", str(self.root), "cat-file", "--batch"],
                                          env=self.env, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                          stderr=subprocess.DEVNULL)
        self.batch.stdin.write(oid.encode("ascii") + b"\n")
        self.batch.stdin.flush()
        header = self.batch.stdout.readline().split()
        if len(header) != 3 or header[1] != b"blob":
            raise GuardError("Cannot inspect a Git blob; push remains blocked.")
        size = int(header[2])
        if size > MAX_BLOB:
            self.batch.kill()
            self.close()
            reasons = ["file exceeds 10 MiB inspection limit; review/store outside Git"]
        else:
            data = self.batch.stdout.read(size)
            if len(data) != size or self.batch.stdout.read(1) != b"\n":
                raise GuardError("Incomplete Git blob read; push remains blocked.")
            reasons = content_reasons(data)
            self.blob_hashes[oid] = hashlib.sha256(data).hexdigest()
        self.cache[oid] = reasons
        return reasons

    def record(self, path, reason, source):
        self.findings.setdefault((path, reason), source)

    def inspect(self, path, oid, mode, source):
        if (path, oid, mode) in self.seen:
            return
        self.seen.add((path, oid, mode))
        if mode == "160000":
            self.record(path, "submodule contents need a separate review", source)
            return
        reason = path_reason(path)
        if reason:
            self.record(path, reason, source)
            self.discovered.add(path)
            return
        reasons = [reason for reason in self.blob(oid)
                   if (path, self.blob_hashes.get(oid), reason) not in self.allowances]
        for reason in reasons:
            self.record(path, reason, source)
        if auto_ignore_content(path, reasons):
            self.discovered.add(path)

    def index(self):
        for item in self.git("ls-files", "--stage", "-z").split(b"\0"):
            if not item:
                continue
            meta, raw = item.split(b"\t", 1)
            mode, oid, stage = meta.decode("ascii").split()
            if stage != "0":
                raise GuardError("Resolve Git merge conflicts before committing/pushing.")
            self.inspect(os.fsdecode(raw), oid, mode, "staged/index")

    def tree(self, commit):
        self.commits_checked += 1
        for item in self.git("ls-tree", "-r", "-z", "--full-tree", commit).split(b"\0"):
            if not item:
                continue
            meta, raw = item.split(b"\t", 1)
            mode, kind, oid = meta.decode("ascii").split()
            self.inspect(os.fsdecode(raw), oid, mode, "commit " + commit[:12])

    def local_discovery(self):
        # Only untracked, nonignored files can need additional ignore entries.
        for raw in self.git("ls-files", "--others", "--exclude-standard", "-z").split(b"\0"):
            if not raw:
                continue
            name = os.fsdecode(raw)
            path = self.root / name
            reason = path_reason(name)
            if reason:
                self.discovered.add(name)
                continue
            # Do not follow symlinks outside the repo or consume arbitrary devices.
            if path.is_symlink() or not path.is_file():
                continue
            if path.stat().st_size <= MAX_BLOB:
                with path.open("rb") as stream:
                    data = stream.read(MAX_BLOB + 1)
                if auto_ignore_content(name, content_reasons(data)):
                    self.discovered.add(name)

    def update_ignore(self):
        candidates = []
        for path in sorted(self.discovered):
            # --no-index is essential for tracked files: ignore rules still do not untrack them.
            ignored = self.git("check-ignore", "--no-index", "-z", "--stdin",
                               input=os.fsencode(path) + b"\0", ok=(0, 1))
            if not ignored:
                candidates.append(path)
        return merge_rules(self.ignore, candidates)

    def outgoing(self, lines):
        commits = set()
        pushes = 0
        for line in lines:
            fields = line.split()
            if len(fields) != 4:
                raise GuardError("Malformed pre-push input; no unchecked push is allowed.")
            _, local, _, remote = fields
            if not all(re.fullmatch(r"(?:[0-9a-f]{40}|[0-9a-f]{64})", x) for x in (local, remote)):
                raise GuardError("Invalid pre-push object ID.")
            if set(local) == {"0"}:
                continue  # Deleting a ref transfers no file contents.
            pushes += 1
            if self.git("rev-parse", "--is-shallow-repository").strip() == b"true":
                raise GuardError("Shallow history cannot be fully checked; fetch the complete history first.")
            tip = self.git("rev-parse", "--verify", local + "^{commit}", ok=(0, 128)).strip()
            if not tip:
                raise GuardError("Only branches and tags pointing to commits are supported.")
            args = ["rev-list", tip.decode("ascii")]
            if set(remote) != {"0"}:
                old = self.git("rev-parse", "--verify", remote + "^{commit}", ok=(0, 128)).strip()
                if old:
                    args += ["--not", old.decode("ascii")]
                # Unknown destination base => inspect all reachable history, never --all remotes.
            commits.update(self.git(*args).decode("ascii").splitlines())
        for commit in sorted(commits):
            self.tree(commit)
        return pushes

    def report(self, changed):
        if changed:
            say("Updated .gitignore. Review and stage that file before retrying.")
        for (path, reason), source in list(self.findings.items())[:30]:
            say("BLOCKED " + printable(path) + " [" + source + "]: " + reason)
        if len(self.findings) > 30:
            say(str(len(self.findings) - 30) + " additional findings omitted.")
        if self.findings:
            say("No files were deleted, unstaged or removed from history. Secret values are not printed.")
            say("An ignore rule cannot remove a tracked file or a secret in an earlier commit.")
            say("Read GIT-SAFETY.md: untrack local files or fix/rewrite the affected unpublished commits, then retry.")
        if self.findings or changed:
            return 1
        say("PASS: " + str(len(self.seen)) + " file versions checked; " + str(self.commits_checked) + " commit snapshots.")
        return 0


def install(repo):
    hooks = repo.project / ".githooks"
    common = Path(os.fsdecode(repo.git("rev-parse", "--git-common-dir").rstrip(b"\r\n")))
    if not common.is_absolute():
        common = repo.root / common
    storage = common.resolve() / "wardrobe-git-safety"
    installed_hooks = storage / "hooks"
    hook_setting = installed_hooks.as_posix()
    existing = repo.git("config", "--get", "core.hooksPath", ok=(0, 1)).decode().strip()
    if existing and existing != hook_setting:
        raise GuardError("Another core.hooksPath is configured. Keep it and integrate the guard manually; see GIT-SAFETY.md.")
    if not existing:
        default = repo.git("rev-parse", "--git-path", "hooks").decode().strip()
        default_path = Path(default)
        if not default_path.is_absolute():
            default_path = repo.root / default_path
        # Switching hooksPath would disable every active hook, not just our two hooks.
        if default_path.exists() and any(p.is_file() and not p.name.endswith(".sample") for p in default_path.iterdir()):
            raise GuardError("Existing Git hooks found. They were preserved; integrate the guard manually (GIT-SAFETY.md).")
    for name in ("pre-commit", "pre-push"):
        hook = hooks / name
        if not hook.is_file() or hook.is_symlink():
            raise GuardError("Missing/unsafe versioned hook: " + name)
    marker = storage / "OWNER"
    if storage.is_symlink() or (storage.exists() and (not marker.is_file() or marker.read_text() != "Wardrobe Git Safety\n")):
        raise GuardError("Unrecognized hook storage directory; no existing files were overwritten.")
    for relative in ("tools", "hooks", "tools/git_safety.py", "tools/git_safety_allowlist.json", "hooks/pre-commit", "hooks/pre-push", "OWNER"):
        if (storage / relative).is_symlink():
            raise GuardError("Hook storage must not contain symlinks.")
    repo.local_discovery()
    repo.update_ignore()
    installed_hooks.mkdir(parents=True, exist_ok=True)
    (storage / "tools").mkdir(exist_ok=True)
    marker.write_text("Wardrobe Git Safety\n")
    shutil.copy2(repo.project / "tools/git_safety.py", storage / "tools/git_safety.py")
    policy = {"repository_relative": True, "allow": [
        {"path": path, "sha256": digest, "reason": reason}
        for path, digest, reason in sorted(repo.allowances)]}
    (storage / "tools/git_safety_allowlist.json").write_text(json.dumps(policy, indent=2) + "\n", encoding="utf-8")
    for name in ("pre-commit", "pre-push"):
        hook = installed_hooks / name
        shutil.copy2(hooks / name, hook)
        hook.chmod(hook.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    repo.git("config", "--local", "wardrobe.gitSafetyPython", Path(sys.executable).as_posix())
    repo.git("config", "--local", "core.hooksPath", hook_setting)
    say("Installed pre-commit and pre-push hooks for this clone. Repeat installation on each computer.")
    say("Review/stage .gitignore and the guard files. Run: python3 tools/git_safety.py check")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("install", "check", "audit", "pre-commit", "pre-push"))
    parser.add_argument("remote", nargs="?")
    parser.add_argument("remote_url", nargs="?")
    args = parser.parse_args()
    repo = None
    try:
        if sys.version_info < (3, 9):
            raise GuardError("Python 3.9 or newer is required.")
        project = Path.cwd() if args.mode in {"pre-commit", "pre-push"} else Path(__file__).resolve().parents[1]
        repo = Repo(project)
        if args.mode == "install":
            return install(repo)
        if args.mode == "pre-push":
            lines = sys.stdin.read().splitlines()
            if not repo.outgoing(lines):
                return 0
        elif args.mode == "audit":
            if repo.git("rev-parse", "--is-shallow-repository").strip() == b"true":
                raise GuardError("Fetch the complete history before auditing.")
            for commit in repo.git("rev-list", "--all").decode("ascii").splitlines():
                repo.tree(commit)
            repo.index()
        else:
            repo.index()
        repo.local_discovery()
        return repo.report(repo.update_ignore())
    except (GuardError, OSError, UnicodeError, ValueError) as error:
        # GuardError messages are controlled; unexpected exceptions may contain user data.
        say(str(error) if isinstance(error, GuardError) else "Inspection failed (" + type(error).__name__ + "). Nothing will be pushed.")
        return 2
    finally:
        if repo:
            repo.close()


if __name__ == "__main__":
    raise SystemExit(main())
