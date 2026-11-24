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
| Fast verification | `tools/verify.py` — planned | after edits | hooks |
| Hooks and permissions | `.claude/settings.json` — planned | always, by Claude Code itself | cannot be skipped by the agent |
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
3. `tools/verify.py`: the suites related to the changed files, in seconds.
4. Hooks and permissions.
5. Subagents with the models above.

Each mistake an agent makes becomes a rule, a guard or a hook here, so it is not repeated.
