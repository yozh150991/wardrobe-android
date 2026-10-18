param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Tasks)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME -and $env:ProgramFiles) {
 $studio=Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
 if (Test-Path -LiteralPath (Join-Path $studio 'bin/javac.exe')) {$env:JAVA_HOME=$studio}
}
$javac=if ($env:JAVA_HOME) {Join-Path $env:JAVA_HOME 'bin/javac'} else {'javac'}
if (-not (Get-Command $javac -ErrorAction SilentlyContinue)) {throw 'Install a full JDK 17 or 21 and set JAVA_HOME.'}
$version=(& $javac -version 2>&1 | Out-String)
if ($version -notmatch 'javac (17|21)\.') {throw 'Use JDK 17 or 21.'}
if (-not $env:ANDROID_HOME -and $env:LOCALAPPDATA) {
 $sdk=Join-Path $env:LOCALAPPDATA 'Android/Sdk';if (Test-Path -LiteralPath $sdk) {$env:ANDROID_HOME=$sdk}
}
$jar=Join-Path $PSScriptRoot 'gradle/wrapper/gradle-wrapper.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {throw 'Missing bundled Gradle wrapper.'}
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046') {throw 'Gradle wrapper checksum mismatch.'}
$properties=[IO.File]::ReadAllText((Join-Path $PSScriptRoot 'gradle/wrapper/gradle-wrapper.properties'))
if ($properties -notmatch '(?m)^distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6\r?$' -or $properties -notmatch '(?m)^distributionUrl=https\\://services\.gradle\.org/distributions/gradle-8\.11\.1-bin\.zip\r?$') {throw 'Unexpected Gradle distribution or checksum.'}
if (-not $Tasks -or $Tasks.Count -eq 0) {$Tasks=@('testDebugUnitTest','lintDebug','assembleDebug')}
& (Join-Path $PSScriptRoot 'gradlew.bat') --no-daemon @Tasks
if ($LASTEXITCODE -ne 0) {throw "Gradle failed with exit code $LASTEXITCODE"}
if ($Tasks -contains 'assembleDebug') {Write-Host 'APK: app/build/outputs/apk/debug/app-debug.apk'}
if ($Tasks -contains 'assembleRelease') {Write-Host 'APK: app/build/outputs/apk/release/app-release-unsigned.apk'}
