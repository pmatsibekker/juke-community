# =============================================================================
#  Juke Demo - Drive the @JukeController journey with curl
#
#  Prerequisites:
#    1. demo-start-server.ps1 is running in another window
#    2. Server has printed "Started ControllerApplication"
#
#  Run from any directory:
#    .\juke-samples\juke-sample-controller\demo-run-curl.ps1
#
#  What it does (the same journey as ControllerCaptureTest):
#    1. RECORD a baseline   -> GET /api/greet/Alice is captured
#    2. CLEAN replay (Alice) -> matches the baseline, NO finding
#    3. POISONED replay (Bob) -> deviates from the baseline
#
#  @JukeController advice OBSERVES and REPORTS drift; it never changes the
#  response. So the poisoned call still returns "Hello, Bob!" here, and the
#  CONTROLLER_MISMATCH line is printed in the SERVER window, not in this output.
# =============================================================================

$ErrorActionPreference = "Stop"

$Base  = "http://localhost:8080"
$Track = "controller-track"
# Call curl.exe explicitly: in PowerShell, "curl" is an alias for Invoke-WebRequest.
$curl  = "curl.exe"

function Invoke-Curl([string]$url) {
    return (& $curl -s $url)
}

# -- Verify server is reachable ----------------------------------------------
Write-Host "Checking server at $Base ..." -ForegroundColor Cyan
$code = (& $curl -s -o NUL -w "%{http_code}" "$Base/api/greet/Ping")
if ($code -notmatch '^[23]\d\d$') {
    Write-Error @"
Server is not responding on $Base (got HTTP '$code').
Start it first in another window:
  .\juke-samples\juke-sample-controller\demo-start-server.ps1
"@
    exit 1
}
Write-Host "Server is up." -ForegroundColor Green

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  @JukeController capture + contract-drift demo"        -ForegroundColor Yellow
Write-Host "  Watch the SERVER window for CONTROLLER_MISMATCH."     -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow

# -- 1) RECORD a baseline (GET /api/greet/Alice) ------------------------------
Write-Host ""
Write-Host "[1] RECORD baseline (track '$Track')" -ForegroundColor Magenta
Invoke-Curl "$Base/service/record/start?track=$Track" | Out-Null
$recorded = Invoke-Curl "$Base/api/greet/Alice"
Invoke-Curl "$Base/service/record/end" | Out-Null
Write-Host "    recorded  GET /api/greet/Alice -> $recorded"

# -- 2) CLEAN replay (same input -> matches baseline) -------------------------
Write-Host ""
Write-Host "[2] CLEAN replay (same input: Alice)" -ForegroundColor Magenta
Invoke-Curl "$Base/service/replay/start?track=$Track" | Out-Null
$clean = Invoke-Curl "$Base/api/greet/Alice"
Write-Host "    replayed  GET /api/greet/Alice -> $clean"
Write-Host "    => matches the baseline: NO CONTROLLER_MISMATCH (server window stays quiet)" -ForegroundColor Green

# -- 3) POISONED replay (different input -> drift) ----------------------------
# replay/start resets the per-class step counter, so this is step 1 again and is
# diffed against the recorded Alice baseline.
Write-Host ""
Write-Host "[3] POISONED replay (different input: Bob)" -ForegroundColor Magenta
Invoke-Curl "$Base/service/replay/start?track=$Track" | Out-Null
$poisoned = Invoke-Curl "$Base/api/greet/Bob"
Write-Host "    replayed  GET /api/greet/Bob   -> $poisoned"
Write-Host "    => live response is preserved (advice never rewrites it)," -ForegroundColor Red
Write-Host "       but it deviates from the Alice baseline, so the SERVER" -ForegroundColor Red
Write-Host "       window logs (REQ uri + RESP message mismatches):"        -ForegroundColor Red
Write-Host "         CONTROLLER_MISMATCH [com.example.controllerdemo.GreetController.greet#1]" -ForegroundColor Red

Write-Host ""
Write-Host "Done. The contract-drift finding is in the server window's log." -ForegroundColor Cyan
