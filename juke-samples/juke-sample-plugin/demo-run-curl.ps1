# =============================================================================
#  Juke Demo - Inspect the plugin registry with curl
#
#  Prerequisites:
#    1. demo-start-server.ps1 is running in another window
#    2. Server has printed "Started PluginApplication"
#
#  Run from any directory:
#    .\juke-samples\juke-sample-plugin\demo-run-curl.ps1
#
#  What it does:
#    1. GET /service/plugins         -> lists every registered plugin
#    2. GET /service/plugins/{id}    -> details for demo-transformer
#
#  The demo-transformer plugin lives in the same JVM and self-registers via
#  the SDK on ApplicationReadyEvent.
# =============================================================================

$ErrorActionPreference = "Stop"

$Base     = "http://localhost:8080"
$PluginId = "demo-transformer"
# Call curl.exe explicitly: in PowerShell, "curl" is an alias for Invoke-WebRequest.
$curl     = "curl.exe"

function Invoke-Curl([string]$Url) { return (& $curl -s $Url) }

# -- Verify server is reachable ----------------------------------------------
Write-Host "Checking server at $Base ..." -ForegroundColor Cyan
$code = (& $curl -s -o NUL -w "%{http_code}" "$Base/service/plugins")
if ($code -notmatch '^[23]\d\d$') {
    Write-Error @"
Server is not responding on $Base (got HTTP '$code').
Start it first in another window:
  .\juke-samples\juke-sample-plugin\demo-start-server.ps1
"@
    exit 1
}
Write-Host "Server is up." -ForegroundColor Green

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  Plugin SDK - registry inspection"                     -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow

Write-Host ""
Write-Host "[1] GET /service/plugins" -ForegroundColor Magenta
$list = Invoke-Curl "$Base/service/plugins"
Write-Host "    -> $list"

Write-Host ""
Write-Host "[2] GET /service/plugins/$PluginId" -ForegroundColor Magenta
$detail = Invoke-Curl "$Base/service/plugins/$PluginId"
Write-Host "    -> $detail"

Write-Host ""
Write-Host "Done. The same view is rendered in the browser at $Base." -ForegroundColor Cyan
