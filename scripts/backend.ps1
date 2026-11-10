param([ValidateSet('run', 'test', 'package')][string]$Action = 'run')
$ErrorActionPreference = 'Stop'
$projectBackend = Join-Path $PSScriptRoot '..\backend'
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw 'Maven 3.6.3+ is required.' }
Push-Location -LiteralPath $projectBackend
try {
    [string[]]$taskGoals = switch ($Action) {
        'run' { @('spring-boot:run') }
        'test' { @('test') }
        'package' { @('verify') }
    }
    # Keep downloaded dependencies inside this workspace; do not change the user's Maven settings.
    & mvn -B -ntp '-Dmaven.repo.local=.mvn-cache' @taskGoals
    $taskExit = $LASTEXITCODE
} finally { Pop-Location }
exit $taskExit
