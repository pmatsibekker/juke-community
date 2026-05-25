# =============================================================================
#  Juke Demo - Drive the ToDo CRUD journey with curl
#
#  Prerequisites:
#    1. demo-start-server.ps1 is running in another window
#    2. Server has printed "Started ToDoApplication"
#
#  Run from any directory:
#    .\juke-samples\juke-sample-todo\demo-run-curl.ps1
#
#  What it does:
#    1. RECORD baseline    -> create + read CRUD operations are captured
#    2. REPLAY same        -> the same calls return the recorded responses
#                             without ever touching the real ToDoServiceImpl
#    3. PASS-THROUGH       -> after /service/replay/end the live service answers
#                             again (a fresh GET shows the in-memory store, which
#                             was untouched by replay)
#    4. SESSION DRIFT      -> record a fresh baseline on a separate track,
#                             open a COOKIE SESSION, drive it with an INCONSISTENT
#                             title, stop the session, then GET the recording
#                             report - which marks the session COMPLETED_WITH_DEVIATIONS
#                             and prints recorded vs actual arguments.
#
#  The @Juke seam is on ToDoDAO.todoService, so every call from the
#  controller goes through Juke - captured in record mode, served from the ZIP
#  in replay mode. SessionAwareReplayHandler diffs each replayed call's
#  inputs against the recorded sidecar; the report endpoint surfaces those
#  per-call matches so drift is visible to the user, not just to log readers.
# =============================================================================

$ErrorActionPreference = "Stop"

$Base  = "http://localhost:8080"
$Track = "todo-track"
# Call curl.exe explicitly: in PowerShell, "curl" is an alias for Invoke-WebRequest.
$curl  = "curl.exe"

function Invoke-Curl([string]$Url) {
    return (& $curl -s $Url)
}

# Curl POSTs lose JSON quotes when passed as -d on Windows PowerShell. Pipe the
# JSON via stdin (--data-binary @-) so curl receives it byte-exact.
function Invoke-CurlPostJson([string]$Url, [string]$Json) {
    return ($Json | & $curl -s -X POST -H "Content-Type: application/json" --data-binary "@-" $Url)
}

# -- Verify server is reachable ----------------------------------------------
Write-Host "Checking server at $Base ..." -ForegroundColor Cyan
$code = (& $curl -s -o NUL -w "%{http_code}" "$Base/todos")
if ($code -notmatch '^[23]\d\d$') {
    Write-Error @"
Server is not responding on $Base (got HTTP '$code').
Start it first in another window:
  .\juke-samples\juke-sample-todo\demo-start-server.ps1
"@
    exit 1
}
Write-Host "Server is up." -ForegroundColor Green

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  ToDo CRUD - record + replay through @Juke" -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow

# -- 1) RECORD baseline ------------------------------------------------------
Write-Host ""
Write-Host "[1] RECORD baseline (track '$Track')" -ForegroundColor Magenta
Invoke-Curl "$Base/service/record/start?track=$Track" | Out-Null

$created = Invoke-CurlPostJson "$Base/todos" '{"title":"Write reference scripts"}'
Write-Host "    POST /todos                       -> $created"

$created2 = Invoke-CurlPostJson "$Base/todos" '{"title":"Validate the curl journey"}'
Write-Host "    POST /todos                       -> $created2"

$list = Invoke-Curl "$Base/todos"
Write-Host "    GET  /todos                       -> $list"

$pending = Invoke-Curl "$Base/todos/pending"
Write-Host "    GET  /todos/pending               -> $pending"

Invoke-Curl "$Base/service/record/end" | Out-Null
Write-Host "    [recording closed]" -ForegroundColor DarkGray

# -- 2) REPLAY same journey --------------------------------------------------
Write-Host ""
Write-Host "[2] REPLAY (no real service is invoked - responses come from the ZIP)" -ForegroundColor Magenta
Invoke-Curl "$Base/service/replay/start?track=$Track" | Out-Null

$rcreated = Invoke-CurlPostJson "$Base/todos" '{"title":"Write reference scripts"}'
Write-Host "    POST /todos                       -> $rcreated   (must match the recorded id)"

$rcreated2 = Invoke-CurlPostJson "$Base/todos" '{"title":"Validate the curl journey"}'
Write-Host "    POST /todos                       -> $rcreated2  (must match the recorded id)"

$rlist = Invoke-Curl "$Base/todos"
Write-Host "    GET  /todos                       -> $rlist"

$rpending = Invoke-Curl "$Base/todos/pending"
Write-Host "    GET  /todos/pending               -> $rpending"

Invoke-Curl "$Base/service/replay/end" | Out-Null
Write-Host "    [replay closed]" -ForegroundColor DarkGray

# -- 3) PASS-THROUGH ---------------------------------------------------------
# Replay never wrote to the in-memory ToDo store, so the live list still
# contains only the two records created in step 1.
Write-Host ""
Write-Host "[3] PASS-THROUGH (live ToDoServiceImpl answers again)" -ForegroundColor Magenta
$final = Invoke-Curl "$Base/todos"
Write-Host "    GET  /todos                       -> $final"
Write-Host "    => shows the live in-memory store (unchanged by step 2)." -ForegroundColor Green

# -- 4) SESSION DRIFT --------------------------------------------------------
# Drive the controller through a cookie session with a deliberately-different
# title and let the recording report show the deviation.
$DriftTrack = "todo-ui-drift"
$Jar = Join-Path $env:TEMP "juke-todo-session.txt"
Remove-Item -Force -ErrorAction SilentlyContinue $Jar

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  SESSION DRIFT - visible @Juke deviation per-session" -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow

Write-Host ""
Write-Host "[4a] RECORD baseline on track '$DriftTrack' (titles: Buy milk, Buy bread)" -ForegroundColor Magenta
Invoke-Curl "$Base/service/record/start?track=$DriftTrack" | Out-Null
Invoke-CurlPostJson "$Base/todos" '{"title":"Buy milk"}'  | Out-Null
Invoke-CurlPostJson "$Base/todos" '{"title":"Buy bread"}' | Out-Null
Invoke-Curl "$Base/service/record/end" | Out-Null
Write-Host "    [baseline recorded: 2 calls]" -ForegroundColor DarkGray

Write-Host ""
Write-Host "[4b] Open cookie session and DRIVE WITH INCONSISTENT INPUT" -ForegroundColor Magenta
Write-Host "     (second POST sends 'Buy oats' instead of recorded 'Buy bread')"
& $curl -s -c $Jar "$Base/service/session/start?track=$DriftTrack&description=ui-drift-curl" | Out-Null
'{"title":"Buy milk"}' | & $curl -s -b $Jar -X POST -H "Content-Type: application/json" --data-binary "@-" "$Base/todos" | Out-Null
'{"title":"Buy oats"}' | & $curl -s -b $Jar -X POST -H "Content-Type: application/json" --data-binary "@-" "$Base/todos" | Out-Null
& $curl -s -b $Jar "$Base/service/session/stop" | Out-Null
Write-Host "    [session stopped]" -ForegroundColor DarkGray

Write-Host ""
Write-Host "[4c] GET /service/recording/report?track=$DriftTrack" -ForegroundColor Magenta
$reportJson = Invoke-Curl "$Base/service/recording/report?track=$DriftTrack"
$report = $reportJson | ConvertFrom-Json
foreach ($s in $report.sessions) {
    $color = if ($s.overallStatus -eq 'COMPLETED') { 'Green' } else { 'Red' }
    Write-Host "    session: $($s.description)  status: $($s.overallStatus)" -ForegroundColor $color
    foreach ($c in $s.calls) {
        $tag = if ($c.inputMatched) { 'match' } else { 'drift' }
        $clr = if ($c.inputMatched) { 'DarkGray' } else { 'Red' }
        $rec = ($c.recordedArguments | ConvertTo-Json -Compress -Depth 5)
        $act = ($c.actualArguments   | ConvertTo-Json -Compress -Depth 5)
        Write-Host ("      #{0} {1}  [{2}]  recorded={3}  actual={4}" -f $c.sequence, $c.method, $tag, $rec, $act) -ForegroundColor $clr
    }
}

Write-Host ""
Write-Host "Done. Open $Base for the same journey driven from the UI (report panel highlights the drift row)." -ForegroundColor Cyan
