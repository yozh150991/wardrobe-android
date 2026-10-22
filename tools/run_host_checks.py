#!/usr/bin/env python3
"""One reproducible source/Java/SQLite check run. Android build is a separate required step."""
from pathlib import Path
import argparse, datetime, json, os, re, subprocess, sys
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--allow-missing-client-config', action='store_true',
                    help='Explicitly skip missing personal Firebase config for source-only CI; invalid config still fails.')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
names = ['check_project.py', 'check_refinement.py', 'check_experience.py', 'check_roadmap.py', 'check_ux.py', 'check_wardrobe.py', 'check_wishes.py',
         'check_auth.py', 'check_management.py', 'check_photo_rules.py', 'check_theme.py', 'check_auth_config.py', 'check_scenarios.py', 'check_shop_import.py', 'check_purchase_advice.py', 'check_photo_zoom.py', 'check_photo_editor.py', 'check_photo_reprocessing.py', 'check_categories.py', 'check_colors.py', 'check_family.py', 'check_family_backend.py', 'check_family_cache.py', 'check_qa_regression.py']
# Shared PID/performance-file tracking can collide in containerized parallel processes.
# This flag changes JVM diagnostics only; it does not change domain test behavior.
task_env = dict(os.environ)
task_env['JAVA_TOOL_OPTIONS'] = (task_env.get('JAVA_TOOL_OPTIONS', '') + ' -XX:-UsePerfData').strip()
results = []
for name in names:
    completed = subprocess.run([sys.executable, str(root / 'tools' / name)], cwd=root, env=task_env,
                               text=True, capture_output=True)
    output = completed.stdout + completed.stderr
    print(output.rstrip())
    skipped = (args.allow_missing_client_config and name == 'check_auth_config.py'
               and completed.returncode == 2 and not (root / 'app/google-services.json').exists())
    results.append({'script': name, 'exit_code': completed.returncode, 'output': output,
                    'status': 'skipped_not_configured' if skipped else 'pass' if completed.returncode == 0 else 'fail'})
release = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+[^"/]*)"', (root / 'app/build.gradle.kts').read_text()).group(1)
report = {'release': release, 'checked_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'source_only': True, 'checks': results,
          'android': 'Not executed by this runner. Consult the release-specific validation report; historical results do not validate the current source.',
          'updater': 'See archive package-validation.json for actual PowerShell updater validation; Windows filesystem is not exercised by Linux checks.'}
report_dir = root / 'release-evidence/host-runs'
report_dir.mkdir(parents=True, exist_ok=True)
stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
(report_dir / (release + '-' + stamp + '.json')).write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
if any(item['status'] == 'fail' for item in results): raise SystemExit(1)
