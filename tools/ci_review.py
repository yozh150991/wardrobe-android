#!/usr/bin/env python3
"""CI results of a commit or pull request, and the screenshot tour's pictures, for an agent without artifact access.

Usage: ci_review.py <sha | branch | PR number> [--shots OUT_DIR] [--rerun-ksp]

Prints every check run. With --shots, takes a successful "Android instrumentation" job, rebuilds the tour's pictures
from its log (tools/ci_screenshots.py) into OUT_DIR and lists them. With --rerun-ksp, reruns a failed
"Android debug preview" job whose log says the KSP plugin was not found (a known network failure), once the
workflow run has finished. Needs the `gh` CLI. Exit code: 0 all green, 1 something failed, 2 still running.
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys

REPO = 'yozh150991/wardrobe-android'
ROOT = pathlib.Path(__file__).resolve().parents[1]


def gh(*args, check=True):
    return subprocess.run(['gh', 'api', *args], text=True, capture_output=True, check=check)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('ref')
    parser.add_argument('--shots')
    parser.add_argument('--rerun-ksp', action='store_true')
    args = parser.parse_args()
    ref = args.ref
    if ref.isdigit():
        ref = json.loads(gh(f'repos/{REPO}/pulls/{ref}').stdout)['head']['sha']
    runs = json.loads(gh(f'repos/{REPO}/commits/{ref}/check-runs?per_page=100').stdout)['check_runs']
    if not runs:
        print('No check runs yet for', ref)
        return 2
    failed = running = 0
    for run in sorted(runs, key=lambda r: (r['name'], r['id'])):
        state = run['conclusion'] or run['status']
        print(f"{state:12} {run['name']}  (job {run['id']})")
        running += run['status'] != 'completed'
        failed += run['conclusion'] not in (None, 'success', 'skipped', 'neutral')
    for run in runs:
        if not args.rerun_ksp or run['conclusion'] != 'failure' or 'debug preview' not in run['name']:
            continue
        log = gh(f"repos/{REPO}/actions/jobs/{run['id']}/logs", check=False).stdout
        if "Plugin [id: 'com.google.devtools.ksp'" in log and 'was not found' in log:
            again = gh(f"repos/{REPO}/actions/jobs/{run['id']}/rerun", '-X', 'POST', check=False)
            print('KSP plugin download failed in job', run['id'], '-', 'rerun requested' if again.returncode == 0
                  else 'rerun refused (the run is still going; try again when it ends)')
        else:
            print('Job', run['id'], 'failed for another reason: read its log')
    if args.shots:
        jobs = [r for r in runs if 'instrumentation' in r['name'] and r['conclusion'] == 'success']
        if not jobs:
            print('No successful instrumentation job to take screenshots from')
        best = 0
        for job in jobs:  # a truncated log holds fewer screens: keep the fullest
            raw = gh(f"repos/{REPO}/actions/jobs/{job['id']}/logs").stdout
            out = pathlib.Path(args.shots)
            out.mkdir(parents=True, exist_ok=True)
            log = out / f"job-{job['id']}.log"
            log.write_text(re.sub(r'(?m)^[0-9T:.Z-]+ ', '', raw), encoding='utf-8')
            target = out / f"job-{job['id']}"
            subprocess.run([sys.executable, str(ROOT / 'tools/ci_screenshots.py'), str(log), '/nonexistent', str(target), '--quiet'],
                           check=False, capture_output=True)
            count = len(list(target.glob('*.jpg')))
            print(f"{count} screens from job {job['id']} in {target}")
            best = max(best, count)
        if jobs and best == 0:
            print('The log held no screenshots (WSHOT lines)')
    return 2 if running else 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
