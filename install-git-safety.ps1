$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
foreach ($wardrobePython in @('py', 'python', 'python3')) {
    if (Get-Command $wardrobePython -ErrorAction SilentlyContinue) {
        $wardrobePrefix = @()
        if ($wardrobePython -eq 'py') { $wardrobePrefix = @('-3') }
        & $wardrobePython @wardrobePrefix -c 'import sys; sys.exit(sys.version_info < (3,9))'
        if ($LASTEXITCODE -eq 0) {
            & $wardrobePython @wardrobePrefix (Join-Path $PSScriptRoot 'tools/git_safety.py') install
            exit $LASTEXITCODE
        }
    }
}
throw 'Install Python 3.9+ from python.org, then run this installer again.'
