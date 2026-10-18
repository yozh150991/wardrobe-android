param([Parameter(Mandatory=$true)][string]$ProjectPath, [switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$taskProject = [IO.Path]::GetFullPath($ProjectPath)
$taskModule = Join-Path $taskProject 'app/build.gradle.kts'
$taskRootBuild = Join-Path $taskProject 'build.gradle.kts'
if (-not (Test-Path -LiteralPath $taskModule -PathType Leaf) -or -not (Test-Path -LiteralPath $taskRootBuild -PathType Leaf)) {
    throw 'Select the wardrobe-android project directory containing app/build.gradle.kts.'
}
$taskModuleText = [IO.File]::ReadAllText($taskModule)
$taskRootText = [IO.File]::ReadAllText($taskRootBuild)
if ($taskModuleText -cnotmatch 'applicationId\s*=\s*"com\.familywardrobe\.app"' -or
    $taskModuleText -cnotmatch 'com\.google\.firebase:firebase-auth' -or
    $taskRootText -cnotmatch 'com\.google\.gms\.google-services') {
    throw 'This project does not contain the Wardrobe authentication integration. Apply the 0.4.0 update first. No files changed.'
}
$taskSource = Join-Path $PSScriptRoot 'wardrobe-android/app/google-services.json'
$taskBytes = [IO.File]::ReadAllBytes($taskSource)
$taskText = [Text.Encoding]::UTF8.GetString($taskBytes)
$taskConfig = $taskText | ConvertFrom-Json
$taskClients = @($taskConfig.client | Where-Object { $_.client_info.android_client_info.package_name -ceq 'com.familywardrobe.app' })
if ($taskConfig.type -eq 'service_account' -or $taskConfig.project_info.project_id -cne 'wardrobe-75fea' -or $taskClients.Count -ne 1 -or
    -not $taskClients[0].client_info.mobilesdk_app_id -or -not @($taskClients[0].api_key | Where-Object { $_.current_key }).Count) {
    throw 'The packaged Android client configuration is invalid. No files changed.'
}
$taskDestination = Join-Path $taskProject 'app/google-services.json'
if (Test-Path -LiteralPath $taskDestination) {
    $taskExisting = [IO.File]::ReadAllText($taskDestination)
    if ([string]::Equals($taskExisting.Replace("`r`n", "`n").Trim(), $taskText.Replace("`r`n", "`n").Trim(), [StringComparison]::Ordinal)) {
        Write-Host 'Firebase config is already installed. No files changed.'
        return
    }
    throw 'An existing google-services.json differs from this package. It was not overwritten. Review the Firebase project/config before replacing it.'
}
if ($CheckOnly) { Write-Host 'Compatible. The provided Firebase client config can be added. No files changed.'; return }
$taskTemporary = Join-Path (Split-Path $taskDestination) ('.firebase-config-' + [guid]::NewGuid().ToString('N') + '.tmp')
try {
    [IO.File]::WriteAllBytes($taskTemporary, $taskBytes)
    # Two-argument Move refuses an existing destination, including one created during this operation.
    [IO.File]::Move($taskTemporary, $taskDestination)
} finally {
    if (Test-Path -LiteralPath $taskTemporary) { Remove-Item -LiteralPath $taskTemporary }
}
Write-Host 'Firebase client config installed for wardrobe-75fea / com.familywardrobe.app.'
Write-Host 'Run build-apk.ps1 to build and test the Android project. This operation did not build an APK or test sign-in.'
