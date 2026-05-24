# =============================================================================
#  Juke Coverage Demo — Run the Playwright journey
#
#  Prerequisites:
#    1. demo-start-server.ps1 is running in another window
#    2. Server has printed "Started CoverageDemoApplication"
#
#  This script:
#    - Drives the demo journey end-to-end (Chrome opens automatically)
#    - Harvests window.__coverage__ from the browser
#    - Renders an nyc report into $env:USERPROFILE\juke-demo\coverage\ui
#    - The dashboard at http://localhost:8080 then picks up the UI half
#      on its next 2-second poll
#
#  Right-click → "Run with PowerShell", or run it from any terminal.
# =============================================================================

$ErrorActionPreference = "Stop"

# ── Use the Node bundled by the Maven build ──────────────────────────────────
$ScriptDir    = Split-Path -Parent $MyInvocation.MyCommand.Path
$BundledNode  = Join-Path $ScriptDir ".node\node"
if (Test-Path "$BundledNode\node.exe") {
    $env:Path = "$BundledNode;$env:Path"
    Write-Host "Using bundled Node 22" -ForegroundColor Cyan
} else {
    Write-Host "Bundled Node not found - using system Node" -ForegroundColor Yellow
    Write-Host "Build juke-sample-coverage once with Maven to download Node 22"
}

# ── Check server is reachable ─────────────────────────────────────────────────
Write-Host "Checking server at http://localhost:8080/api/styles ..."
try {
    $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/styles" `
                              -UseBasicParsing -TimeoutSec 5
    if ($resp.StatusCode -ne 200) { throw "got HTTP $($resp.StatusCode)" }
    Write-Host "Server is up." -ForegroundColor Green
} catch {
    Write-Error @"
Server is not responding on http://localhost:8080
Start it first with demo-start-server.ps1
"@
    exit 1
}

# ── Playwright directory ─────────────────────────────────────────────────────
$PlaywrightDir = Join-Path $ScriptDir "src\test\playwright"
if (-not (Test-Path $PlaywrightDir)) {
    Write-Error "Playwright directory not found: $PlaywrightDir"
    exit 1
}

Set-Location $PlaywrightDir

# First-time install (Playwright + nyc); no-op once node_modules exists.
if (-not (Test-Path (Join-Path $PlaywrightDir "node_modules"))) {
    Write-Host "Installing Playwright dependencies..." -ForegroundColor Yellow
    npm install
    npx playwright install chromium
}

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  Running the demo journey -- Chrome will open headed."  -ForegroundColor Yellow
Write-Host "  Watch the coverage dashboard light up as it runs."     -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host ""

npx playwright test --project="Coverage Demo"
