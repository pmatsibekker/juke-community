# =============================================================================
#  Juke Demo - Drive the rest-client RECORD/REPLAY journey with curl
#
#  Prerequisites:
#    1. demo-start-server.ps1 is running in another window
#    2. Server has printed "Started RestClientApplication"
#
#  Run from any directory:
#    .\juke-samples\juke-sample-rest-client\demo-run-curl.ps1
#
#  What it does:
#    1. PASS-THROUGH (no Juke) -> live upstream returns random quoteIds
#    2. RECORD baseline        -> /api/quote/sku-1, /api/quote/sku-2 captured
#    3. REPLAY same SKUs       -> recorded quoteIds returned, upstream is silent
#
#  Each upstream response carries a random quoteId, so a recorded vs live call
#  is easy to spot: replay returns the exact id from step 2.
#
#  NOTE: the concrete-field RestTemplate seam binds its proxy at @PostConstruct
#  (see CLAUDE.md "Test isolation"). After /service/replay/end the cached
#  proxy keeps serving recorded responses positionally, so there's no clean
#  "pass-through restored" step in this sample - restart the JVM to reset.
# =============================================================================

$ErrorActionPreference = "Stop"

$Base  = "http://localhost:8080"
$Track = "rest-client-track"
# Call curl.exe explicitly: in PowerShell, "curl" is an alias for Invoke-WebRequest.
$curl  = "curl.exe"

function Invoke-Curl([string]$Url) { return (& $curl -s $Url) }

# -- Verify server is reachable ----------------------------------------------
Write-Host "Checking server at $Base ..." -ForegroundColor Cyan
$code = (& $curl -s -o NUL -w "%{http_code}" "$Base/upstream/health")
if ($code -notmatch '^[23]\d\d$') {
    Write-Error @"
Server is not responding on $Base (got HTTP '$code').
Start it first in another window:
  .\juke-samples\juke-sample-rest-client\demo-start-server.ps1
"@
    exit 1
}
Write-Host "Server is up." -ForegroundColor Green

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  rest-client (concrete-field @Juke on RestTemplate)" -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow

# -- 1) PASS-THROUGH ---------------------------------------------------------
Write-Host ""
Write-Host "[1] PASS-THROUGH (no Juke -> live upstream, random quoteIds)" -ForegroundColor Magenta
$pre1 = Invoke-Curl "$Base/api/quote/sku-1"
Write-Host "    GET /api/quote/sku-1 -> $pre1"
$pre2 = Invoke-Curl "$Base/api/quote/sku-2"
Write-Host "    GET /api/quote/sku-2 -> $pre2"

# -- 2) RECORD baseline ------------------------------------------------------
Write-Host ""
Write-Host "[2] RECORD baseline (track '$Track')" -ForegroundColor Magenta
Invoke-Curl "$Base/service/record/start?track=$Track" | Out-Null
$rec1 = Invoke-Curl "$Base/api/quote/sku-1"
Write-Host "    GET /api/quote/sku-1 -> $rec1"
$rec2 = Invoke-Curl "$Base/api/quote/sku-2"
Write-Host "    GET /api/quote/sku-2 -> $rec2"
Invoke-Curl "$Base/service/record/end" | Out-Null
Write-Host "    [recording closed]" -ForegroundColor DarkGray

# -- 3) REPLAY ---------------------------------------------------------------
Write-Host ""
Write-Host "[3] REPLAY (responses from ZIP - quoteIds must match step 2)" -ForegroundColor Magenta
Invoke-Curl "$Base/service/replay/start?track=$Track" | Out-Null
$rep1 = Invoke-Curl "$Base/api/quote/sku-1"
Write-Host "    GET /api/quote/sku-1 -> $rep1"
$rep2 = Invoke-Curl "$Base/api/quote/sku-2"
Write-Host "    GET /api/quote/sku-2 -> $rep2"
Invoke-Curl "$Base/service/replay/end" | Out-Null
Write-Host "    [replay closed]" -ForegroundColor DarkGray

Write-Host ""
Write-Host "Done." -ForegroundColor Cyan
