param([Parameter(Mandatory=$true)][ValidateSet('bank','store','delivery','email')][string]$Service)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot '.env'
if (!(Test-Path -LiteralPath $envFile)) { throw 'Copy .env.example to .env and set local credentials first.' }
foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
    if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
Push-Location $projectRoot
try {
    & .\gradlew.bat ":${Service}:bootRun"
    if ($LASTEXITCODE -ne 0) { throw "Service exited with code $LASTEXITCODE" }
} finally { Pop-Location }
