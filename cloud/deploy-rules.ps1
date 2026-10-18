param([switch]$Deploy)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$config=Get-Content -LiteralPath (Join-Path $project 'app/google-services.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$id=[string]$config.project_info.project_id;$bucket=[string]$config.project_info.storage_bucket
if ([string]::IsNullOrWhiteSpace($id) -or $id.StartsWith('demo-') -or [string]::IsNullOrWhiteSpace($bucket)) {throw 'The existing Firebase client configuration needs a production project ID and Storage bucket.'}
Write-Host "Project from existing client configuration: $id"
Write-Host "Storage bucket: $bucket"
Write-Host 'Plan: run local emulator tests, then deploy Firestore and Storage rules to this project.'
Write-Host 'Enable email/password Auth, Firestore and Storage in Firebase Console before deployment.'
if (-not $Deploy) {Write-Host 'Read-only plan. Add -Deploy after signing into Firebase CLI.';return}
$cli=Join-Path $PSScriptRoot 'node_modules/firebase-tools/lib/bin/firebase.js'
if (-not (Test-Path -LiteralPath $cli)) {throw 'Run npm ci in the cloud folder first.'}
$loginText=(& node $cli login:list --json | Out-String)
if ($LASTEXITCODE -ne 0) {throw 'Unable to read Firebase CLI accounts.'}
$login=$loginText | ConvertFrom-Json
if (-not $login.result -or @($login.result).Count -eq 0) {throw 'No Firebase CLI account is signed in. Run npx firebase login, then rerun this helper.'}
Push-Location -LiteralPath $PSScriptRoot
try {
 & npm test
 if ($LASTEXITCODE -ne 0) {throw 'Firebase emulator tests failed; rules were not deployed.'}
 & node $cli deploy --project $id --only 'firestore:rules,storage'
 if ($LASTEXITCODE -ne 0) {throw 'Firebase rules deployment failed.'}
} finally {Pop-Location}
