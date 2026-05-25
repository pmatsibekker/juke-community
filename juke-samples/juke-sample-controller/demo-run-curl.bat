@echo off
:: =============================================================================
::  Juke Demo - Drive the @JukeController journey with curl
::
::  Prerequisites:
::    1. demo-start-server.bat is running in another window
::    2. Server has printed "Started ControllerApplication"
::
::  Run from any terminal (or double-click):
::    juke-samples\juke-sample-controller\demo-run-curl.bat
::
::  What it does (the same journey as ControllerCaptureTest):
::    1. RECORD a baseline    -> GET /api/greet/Alice is captured
::    2. CLEAN replay (Alice) -> matches the baseline, NO finding
::    3. POISONED replay (Bob) -> deviates from the baseline
::
::  @JukeController advice OBSERVES and REPORTS drift; it never changes the
::  response. So the poisoned call still returns "Hello, Bob!" here, and the
::  CONTROLLER_MISMATCH line is printed in the SERVER window, not here.
:: =============================================================================
setlocal enabledelayedexpansion

set BASE=http://localhost:8080
set TRACK=controller-track

:: -- Verify server is reachable ----------------------------------------------
echo Checking server at %BASE% ...
for /f "delims=" %%c in ('curl -s -o NUL -w "%%{http_code}" "%BASE%/api/greet/Ping"') do set CODE=%%c
if not "%CODE:~0,1%"=="2" if not "%CODE:~0,1%"=="3" (
    echo.
    echo ERROR: Server is not responding on %BASE% ^(got HTTP '%CODE%'^).
    echo Start it first in another window:
    echo   juke-samples\juke-sample-controller\demo-start-server.bat
    echo.
    pause
    exit /b 1
)
echo Server is up.

echo.
echo ======================================================
echo   @JukeController capture + contract-drift demo
echo   Watch the SERVER window for CONTROLLER_MISMATCH.
echo ======================================================

:: -- 1) RECORD a baseline (GET /api/greet/Alice) ------------------------------
echo.
echo [1] RECORD baseline ^(track '%TRACK%'^)
curl -s "%BASE%/service/record/start?track=%TRACK%" >NUL
for /f "delims=" %%r in ('curl -s "%BASE%/api/greet/Alice"') do set RECORDED=%%r
curl -s "%BASE%/service/record/end" >NUL
echo     recorded  GET /api/greet/Alice -^> !RECORDED!

:: -- 2) CLEAN replay (same input -> matches baseline) -------------------------
echo.
echo [2] CLEAN replay ^(same input: Alice^)
curl -s "%BASE%/service/replay/start?track=%TRACK%" >NUL
for /f "delims=" %%r in ('curl -s "%BASE%/api/greet/Alice"') do set CLEAN=%%r
echo     replayed  GET /api/greet/Alice -^> !CLEAN!
echo     =^> matches the baseline: NO CONTROLLER_MISMATCH ^(server window stays quiet^)

:: -- 3) POISONED replay (different input -> drift) ----------------------------
:: replay/start resets the per-class step counter, so this is step 1 again and is
:: diffed against the recorded Alice baseline.
echo.
echo [3] POISONED replay ^(different input: Bob^)
curl -s "%BASE%/service/replay/start?track=%TRACK%" >NUL
for /f "delims=" %%r in ('curl -s "%BASE%/api/greet/Bob"') do set POISONED=%%r
echo     replayed  GET /api/greet/Bob   -^> !POISONED!
echo     =^> live response is preserved ^(advice never rewrites it^), but it
echo        deviates from the Alice baseline, so the SERVER window logs
echo        ^(REQ uri + RESP message mismatches^):
echo        CONTROLLER_MISMATCH [com.example.controllerdemo.GreetController.greet#1]

echo.
echo Done. The contract-drift finding is in the server window's log.

endlocal
