# =============================================================================
#  Juke Demo - Drive the cookie-session journey with curl
#
#  Prerequisites:
#    1. demo-start-server.ps1 is running in another window
#    2. Server has printed "Started SessionApplication"
#
#  Run from any directory:
#    .\juke-samples\juke-sample-session\demo-run-curl.ps1
#
#  What it does (curl mirror of juke-cookie-replay.spec.ts):
#    The server runs with -Djuke=replay -Djuke.zip=juke-sample-ui.
#    Default (no cookies) replays from juke-sample-ui.zip.
#    Cookies from /service/session/start override the default ZIP for that
#    request, so each session can replay an independent track concurrently.
#
#    1. DEFAULT (no cookies) -> juke-sample-ui : "Hello, Evan!"
#    2. SESSION A on track juke-sample-ui   -> "Hello, Evan!" then "Hello, Pavel!"
#    3. SESSION B on track juke-sample-ui-2 -> "Hello, Evan!" then "Hello, Viane!"
#    4. Stop both sessions; default replay is restored.
#
#  KNOWN ISSUE: The cookie-bound replay relies on JukeBeanPostProcessor wrapping
#  SessionGreetingController.greetingsService. Because the controller is also
#  @JukeController (Spring CGLIB-proxied), the post-processor sees a null field
#  on the proxy and logs:
#    WARN ... Field greetingsService in SessionGreetingController$$SpringCGLIB$$0
#    is null, cannot wrap with Juke
#  Until that's fixed, every call falls through to the real GreetingServiceImpl,
#  so the responses below will read "Hello, any" with monotonically increasing
#  ids instead of the recorded values. The session lifecycle endpoints
#  (/service/session/{start,stop,status}) still work; only the replay routing
#  is currently a no-op for this seam.
# =============================================================================

$ErrorActionPreference = "Stop"

$Base   = "http://localhost:8080"
$Track1 = "juke-sample-ui"
$Track2 = "juke-sample-ui-2"

# Call curl.exe explicitly: in PowerShell, "curl" is an alias for Invoke-WebRequest.
$curl = "curl.exe"

$JarA = Join-Path $env:TEMP "juke-demo-cookies-A.txt"
$JarB = Join-Path $env:TEMP "juke-demo-cookies-B.txt"

function Invoke-Curl([string]$Url) { return (& $curl -s $Url) }
function Invoke-CurlJar([string]$Jar, [string]$Url) {
    return (& $curl -s -b $Jar -c $Jar $Url)
}

# -- Verify server is reachable ----------------------------------------------
Write-Host "Checking server at $Base ..." -ForegroundColor Cyan
$code = (& $curl -s -o NUL -w "%{http_code}" "$Base/session-greeting?name=Probe")
if ($code -notmatch '^[23]\d\d$') {
    Write-Error @"
Server is not responding on $Base (got HTTP '$code').
Start it first in another window:
  .\juke-samples\juke-sample-session\demo-start-server.ps1
"@
    exit 1
}
Write-Host "Server is up." -ForegroundColor Green

# Fresh cookie jars
Remove-Item -Force -ErrorAction SilentlyContinue $JarA, $JarB

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  Cookie-bound replay - two isolated sessions"          -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow

# -- 1) DEFAULT (no cookies, default ZIP) ------------------------------------
Write-Host ""
Write-Host "[1] DEFAULT replay (no cookies -> juke.zip=juke-sample-ui)" -ForegroundColor Magenta
$def = Invoke-Curl "$Base/session-greeting?name=any"
Write-Host "    GET /session-greeting?name=any    -> $def   (expect ""Hello, Evan!"")"

# -- 2) SESSION A on track juke-sample-ui ------------------------------------
Write-Host ""
Write-Host "[2] SESSION A start (track '$Track1')" -ForegroundColor Magenta
$startA = & $curl -s -c $JarA "$Base/service/session/start?track=$Track1"
Write-Host "    /service/session/start            -> $startA"

$a1 = Invoke-CurlJar $JarA "$Base/session-greeting?name=any"
Write-Host "    GET /session-greeting?name=any    -> $a1   (expect ""Hello, Evan!"")"

$a2 = Invoke-CurlJar $JarA "$Base/session-greeting?name=any"
Write-Host "    GET /session-greeting?name=any    -> $a2   (expect ""Hello, Pavel!"")"

Invoke-CurlJar $JarA "$Base/service/session/stop" | Out-Null
Write-Host "    [session A stopped]" -ForegroundColor DarkGray

# -- 3) SESSION B on track juke-sample-ui-2 ----------------------------------
Write-Host ""
Write-Host "[3] SESSION B start (track '$Track2')" -ForegroundColor Magenta
$startB = & $curl -s -c $JarB "$Base/service/session/start?track=$Track2"
Write-Host "    /service/session/start            -> $startB"

$b1 = Invoke-CurlJar $JarB "$Base/session-greeting?name=any"
Write-Host "    GET /session-greeting?name=any    -> $b1   (expect ""Hello, Evan!"")"

$b2 = Invoke-CurlJar $JarB "$Base/session-greeting?name=any"
Write-Host "    GET /session-greeting?name=any    -> $b2   (expect ""Hello, Viane!"")"

Invoke-CurlJar $JarB "$Base/service/session/stop" | Out-Null
Write-Host "    [session B stopped]" -ForegroundColor DarkGray

# -- 4) DEFAULT restored ------------------------------------------------------
Write-Host ""
Write-Host "[4] DEFAULT restored (no cookies -> juke.zip=juke-sample-ui)" -ForegroundColor Magenta
$post = Invoke-Curl "$Base/session-greeting?name=any"
Write-Host "    GET /session-greeting?name=any    -> $post   (expect ""Hello, Evan!"")"

Write-Host ""
Write-Host "Done." -ForegroundColor Cyan
