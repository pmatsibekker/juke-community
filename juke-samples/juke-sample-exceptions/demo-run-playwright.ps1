# =============================================================================
#  Juke Exception-Flow Demo — Run the Playwright journey
#
#  Prerequisites:
#    1. demo-start-server.ps1 is running in another window
#    2. Server has printed "Started OrderApplication"
#
#  This script drives all four runs end-to-end (Chrome opens headed and slowed
#  down so you can watch every confirmation popup):
#    1. Record the happy path
#    2. Deterministic replay
#    3. Replay with a 10s delay on the second order  -> "queued" popup
#    4. Replay with an exception on the second order -> "technical difficulties"
#  then opens the coverage popup and holds the report on screen for >10s.
#  It also harvests window.__coverage__ into ~/juke-demo/coverage/ui.
#
#  Right-click -> "Run with PowerShell", or run it from any terminal.
# =============================================================================

$ErrorActionPreference = "Stop"

# -- Use the Node bundled by the Maven build ----------------------------------
$ScriptDir    = Split-Path -Parent $MyInvocation.MyCommand.Path
$BundledNode  = Join-Path $ScriptDir ".node\node"
if (Test-Path "$BundledNode\node.exe") {
    $env:Path = "$BundledNode;$env:Path"
    Write-Host "Using bundled Node 22" -ForegroundColor Cyan
} else {
    Write-Host "Bundled Node not found - using system Node" -ForegroundColor Yellow
    Write-Host "Build juke-sample-exceptions once with Maven to download Node 22"
}

# -- Check server is reachable ------------------------------------------------
Write-Host "Checking server at http://localhost:8080/api/products ..."
try {
    $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/products" `
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

# -- Playwright directory -----------------------------------------------------
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
Write-Host "  Running the four-run demo -- Chrome will open headed." -ForegroundColor Yellow
Write-Host "  Watch each order confirmation popup, the queued and"   -ForegroundColor Yellow
Write-Host "  technical-difficulties popups, then the coverage popup." -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host ""

npx playwright test --project="Exception Flow Demo"
