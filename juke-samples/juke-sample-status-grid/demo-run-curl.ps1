# =============================================================================
#  Juke Demo - Drive the status-grid sessions journey with curl
#
#  Prerequisites:
#    1. demo-start-server.ps1 is running in another window
#    2. Server has printed "Started StatusGridApplication"
#
#  Run from any directory:
#    .\juke-samples\juke-sample-status-grid\demo-run-curl.ps1
#
#  What it does (matches the in-browser banner UI):
#    1. RECORD a 6-call track (Ann, Ben, Cara, Dan, Eve, Finn)
#    2. Open cookie session A, advance through 2 calls (in progress)
#    3. Open cookie session B, advance through 5 calls (in progress)
#    4. GET /service/sessions -> reports lastCall + percentComplete per session
# =============================================================================

$ErrorActionPreference = "Stop"

$Base  = "http://localhost:8080"
$Track = "curl-demo"
# Call curl.exe explicitly: in PowerShell, "curl" is an alias for Invoke-WebRequest.
$curl  = "curl.exe"

$JarA = Join-Path $env:TEMP "juke-statusgrid-A.txt"
$JarB = Join-Path $env:TEMP "juke-statusgrid-B.txt"

function Invoke-Curl([string]$Url) { return (& $curl -s $Url) }

# -- Verify server is reachable ----------------------------------------------
Write-Host "Checking server at $Base ..." -ForegroundColor Cyan
$code = (& $curl -s -o NUL -w "%{http_code}" "$Base/service/sessions")
if ($code -notmatch '^[23]\d\d$') {
    Write-Error @"
Server is not responding on $Base (got HTTP '$code').
Start it first in another window:
  .\juke-samples\juke-sample-status-grid\demo-start-server.ps1
"@
    exit 1
}
Write-Host "Server is up." -ForegroundColor Green

Remove-Item -Force -ErrorAction SilentlyContinue $JarA, $JarB

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  status-grid - per-session progress over /service/sessions" -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow

# -- 1) RECORD baseline ------------------------------------------------------
Write-Host ""
Write-Host "[1] RECORD baseline (track '$Track', 6 calls)" -ForegroundColor Magenta
Invoke-Curl "$Base/service/record/start?track=$Track" | Out-Null
foreach ($n in @("Ann","Ben","Cara","Dan","Eve","Finn")) {
    Invoke-Curl "$Base/api/greet/$n" | Out-Null
}
Invoke-Curl "$Base/service/record/end" | Out-Null
Write-Host "    [recorded 6 calls]" -ForegroundColor DarkGray

# -- 2) Session A: 2 calls (in progress) -------------------------------------
Write-Host ""
Write-Host "[2] Session A (worker-A): start + 2 of 6 calls" -ForegroundColor Magenta
& $curl -s -c $JarA "$Base/service/session/start?track=$Track&description=worker-A" | Out-Null
foreach ($n in @("Ann","Ben")) {
    & $curl -s -b $JarA "$Base/api/greet/$n" | Out-Null
}

# -- 3) Session B: 5 calls (further along) -----------------------------------
Write-Host ""
Write-Host "[3] Session B (worker-B): start + 5 of 6 calls" -ForegroundColor Magenta
& $curl -s -c $JarB "$Base/service/session/start?track=$Track&description=worker-B" | Out-Null
foreach ($n in @("Ann","Ben","Cara","Dan","Eve")) {
    & $curl -s -b $JarB "$Base/api/greet/$n" | Out-Null
}

# -- 4) Report ---------------------------------------------------------------
Write-Host ""
Write-Host "[4] GET /service/sessions (worker-A should be ~33%, worker-B ~83%)" -ForegroundColor Magenta
$ss = Invoke-Curl "$Base/service/sessions"
Write-Host "    -> $ss"

Write-Host ""
Write-Host "Done. The same view auto-refreshes at $Base." -ForegroundColor Cyan
