# =============================================================================
#  Juke Demo — Run the Playwright visual demo
#
#  Prerequisites:
#    1. demo-start-server.ps1 is running in another window
#    2. Server has printed "Started GreetingApplication"
#
#  Run from any directory:
#    .\juke-samples\juke-sample-greeting\demo-run-playwright.ps1
#
#  Or right-click → "Run with PowerShell"
# =============================================================================

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# ── Node 22 ───────────────────────────────────────────────────────────────────
# frontend-maven-plugin downloads Node 22 into .node/node/ inside this module
# when it is built. We prefer that over any system Node so the Playwright
# version pins are satisfied regardless of what's on PATH.
$BundledNode = Join-Path $ScriptDir ".node\node"
if (Test-Path $BundledNode) {
    $env:Path = "$BundledNode;$env:Path"
    Write-Host "Using bundled Node: $((& node --version))" -ForegroundColor Cyan
} else {
    Write-Host "Bundled Node not found — using system Node: $((& node --version))" -ForegroundColor Yellow
    Write-Host "(Build juke-sample-greeting once with Maven to download Node 22)" -ForegroundColor Yellow
}

# ── Playwright directory ───────────────────────────────────────────────────────
# Script lives in juke-sample-greeting/, so Playwright is one level up.
$PlaywrightDir = Join-Path $ScriptDir "..\juke-sample-session\src\test\playwright"

if (-not (Test-Path $PlaywrightDir)) {
    Write-Error "Playwright directory not found: $PlaywrightDir"
    exit 1
}

# ── Verify server is reachable ────────────────────────────────────────────────
Write-Host "Checking server at http://localhost:8080 ..." -ForegroundColor Cyan
try {
    $null = Invoke-WebRequest "http://localhost:8080/service/session/status" `
                -TimeoutSec 3 -ErrorAction Stop
    Write-Host "Server is up." -ForegroundColor Green
} catch {
    Write-Error @"
Server is not responding on http://localhost:8080.
Start it first:
  .\juke-samples\juke-sample-greeting\demo-start-server.ps1
"@
    exit 1
}

# ── Run the demo ──────────────────────────────────────────────────────────────
Set-Location $PlaywrightDir

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  Running Juke demo — 5 phases, ~2.5 min total"         -ForegroundColor Yellow
Write-Host "  Chrome will open automatically."                       -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host ""

npx playwright test --project="Juke Demo" e2e/juke-demo.spec.ts --headed
