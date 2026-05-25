# =============================================================================
#  Juke Demo — Drive the ToDo "session drift" UI journey with Playwright
#
#  Prerequisites:
#    1. demo-start-server.ps1 is running in another window
#    2. Server has printed "Started ToDoApplication"
#
#  Run from any directory:
#    .\juke-samples\juke-sample-todo\demo-run-playwright.ps1
#
#  What it does:
#    Opens http://localhost:8080 in a real Chrome window, records two todos,
#    starts a cookie session, deliberately sends an INCONSISTENT second title,
#    stops the session, and asserts the report panel surfaces the drift
#    (COMPLETED_WITH_DEVIATIONS + a red-highlighted call row).
#
#  Reuses the shared Playwright install under juke-sample-session/src/test/playwright
#  (same pattern as juke-sample-greeting's demo-run-playwright).
# =============================================================================

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# ── Node 22 (bundled by frontend-maven-plugin under juke-sample-greeting) ─────
$GreetingNode = Join-Path $ScriptDir "..\juke-sample-greeting\.node\node"
if (Test-Path (Join-Path $GreetingNode "node.exe")) {
    $env:Path = "$GreetingNode;$env:Path"
    Write-Host "Using bundled Node: $((& node --version))" -ForegroundColor Cyan
} else {
    Write-Host "Bundled Node not found — using system Node: $((& node --version))" -ForegroundColor Yellow
    Write-Host "(Build juke-sample-greeting once with Maven to download Node 22.)" -ForegroundColor Yellow
}

# ── Playwright directory ──────────────────────────────────────────────────────
$PlaywrightDir = Join-Path $ScriptDir "..\juke-sample-session\src\test\playwright"
if (-not (Test-Path $PlaywrightDir)) {
    Write-Error "Playwright directory not found: $PlaywrightDir"
    exit 1
}

# ── Verify server is reachable ────────────────────────────────────────────────
Write-Host "Checking server at http://localhost:8080 ..." -ForegroundColor Cyan
try {
    $null = Invoke-WebRequest "http://localhost:8080/todos" -TimeoutSec 3 -ErrorAction Stop
    Write-Host "Server is up." -ForegroundColor Green
} catch {
    Write-Error @"
Server is not responding on http://localhost:8080.
Start it first in another window:
  .\juke-samples\juke-sample-todo\demo-start-server.ps1
"@
    exit 1
}

Set-Location $PlaywrightDir

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  Running Todo drift demo — Chrome will open visibly"   -ForegroundColor Yellow
Write-Host "  Watch the report panel light up red after stop-session." -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host ""

npx playwright test --project="Todo Drift" e2e/todo-drift.spec.ts --headed
