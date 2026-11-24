# Agent harness

The files that tell an AI agent how to work in this repository and that check its work. Decided with the owner on
23–24 November 2026 (DEV-06). Claude Code is the only agent; it runs both in cloud sessions and locally on Windows,
so hook scripts are Python, not bash.

## Layers

| Layer | Files | Loaded | Enforced by |
| --- | --- | --- | --- |
| Map and rules | `AGENTS.md`, `CLAUDE.md` (imports it) | every session | the agent reading it; `tools/check_harness.py` keeps it short and current |
| Area rules | `.claude/rules/*.md` with `paths` | when a matching file is read | the same guard checks the paths still exist |
| Procedures | `.claude/skills/`: `release`, `texts`, `ci-review`, `device-check` | on demand, or by the owner as `/name` | `tools/check_harness.py` checks their paths |
| Fast verification | `tools/verify.py`: the suites that name the changed files | after edits, by hand until the hooks exist | hooks (planned) |
| Hooks and permissions | `.claude/settings.json`, `.claude/hooks/` | always, by Claude Code itself | cannot be skipped by the agent; `tools/check_harness.py` tests the guard |
| Roles | `.claude/agents/` — planned | when delegated | — |
| Final gate | CI and the owner's merge | every PR | GitHub |

## Decisions

- Prohibitions block; checks after edits warn. Finishing with failing fast checks is blocked.
- Without asking the agent may commit, push, open a PR and rerun CI. Merging and deploying are the owner's.
- Models: the main session plans, analyses and decides (Fable when available, otherwise Opus); an `implementer`
  subagent on Opus writes code from a written specification, except edits of about 20 lines in one file, which the
  main session makes itself; a `reviewer` subagent reads every PR's diff from a clean context (Sonnet; Opus when the
  diff touches Room migrations, the backup format, `cloud/`, family sharing or account isolation); code search uses Haiku.
- History of handoffs lives in `docs/history/HANDOFFS.md`; harness files are English.
- Changes to the harness go through a PR like any other change.

## Steps

1. Map: short `AGENTS.md`, `CLAUDE.md`, area rules, history moved — done (this PR).
2. Skills: `release`, `texts`, `ci-review` (with `tools/ci_review.py`), `device-check` — done.
3. `tools/verify.py`: the suites related to the changed files, in seconds — done. The relation is a heuristic (a suite's
   script names the file), so the full run before a push stays.
4. Hooks and permissions — done:
   - `guard` (before every shell command) blocks: `--no-verify` and `commit -n`, force pushes and history rewriting,
     `firebase deploy`, `deploy-rules.ps1`, merging a PR, reading or copying keys and Firebase config, `git add -f`,
     changing the hooks path, and a push that renames the version with the same code (or the reverse) on a pushed branch.
   - `after_edit` (after Edit / Write) runs `tools/verify.py` for the edited file and shows failures to the agent.
   - `stop_gate` (before a turn ends) runs `tools/verify.py` on everything changed; failing checks keep the agent
     working, up to three times in a row, then the turn ends with a warning to the owner.
   - Permissions: reading, local Git work, `tools/*.py` and CI status run without a prompt; pushing, opening a PR,
     Gradle, adb and anything else prompt on the owner's machine; secrets cannot be read.
   - Hooks are Python started through `.claude/hooks/run.sh` (the Python of Git Safety, else `python3`, else
     `python`); on Windows Claude Code runs it through Git Bash. After pulling, check a clone with
     `python tools/check_harness.py`; personal additions go to `.claude/settings.local.json`.
5. Subagents with the models above.

Each mistake an agent makes becomes a rule, a guard or a hook here, so it is not repeated.
