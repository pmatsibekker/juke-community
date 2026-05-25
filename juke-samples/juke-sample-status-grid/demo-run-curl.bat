@echo off
:: =============================================================================
::  Juke Demo - Drive the status-grid sessions journey with curl
::
::  Prerequisites:
::    1. demo-start-server.bat is running in another window
::    2. Server has printed "Started StatusGridApplication"
::
::  Run from any directory:
::    .\juke-samples\juke-sample-status-grid\demo-run-curl.bat
::
::  What it does (matches the in-browser banner UI):
::    1. RECORD a 6-call track (Ann, Ben, Cara, Dan, Eve, Finn)
::    2. Open cookie session A, advance through 2 calls (in progress)
::    3. Open cookie session B, advance through 5 calls (in progress)
::    4. GET /service/sessions -> reports lastCall + percentComplete per session
:: =============================================================================

setlocal enabledelayedexpansion

set BASE=http://localhost:8080
set TRACK=curl-demo
set JAR_A=%TEMP%\juke-statusgrid-A.txt
set JAR_B=%TEMP%\juke-statusgrid-B.txt

:: -- Verify server is reachable ----------------------------------------------
echo Checking server at %BASE% ...
curl -s -f -o NUL "%BASE%/service/sessions"
if errorlevel 1 (
    echo ERROR: Server is not responding on %BASE%.
    echo Start it first in another window:
    echo   .\juke-samples\juke-sample-status-grid\demo-start-server.bat
    exit /b 1
)
echo Server is up.

del /q "%JAR_A%" 2>NUL
del /q "%JAR_B%" 2>NUL

echo.
echo ======================================================
echo   status-grid - per-session progress over /service/sessions
echo ======================================================

:: -- 1) RECORD baseline ------------------------------------------------------
echo.
echo [1] RECORD baseline (track '%TRACK%', 6 calls)
curl -s "%BASE%/service/record/start?track=%TRACK%" >NUL
for %%n in (Ann Ben Cara Dan Eve Finn) do (
    curl -s "%BASE%/api/greet/%%n" >NUL
)
curl -s "%BASE%/service/record/end" >NUL
echo     [recorded 6 calls]

:: -- 2) Session A: 2 calls (in progress) -------------------------------------
echo.
echo [2] Session A (worker-A): start + 2 of 6 calls
curl -s -c "%JAR_A%" "%BASE%/service/session/start?track=%TRACK%&description=worker-A" >NUL
for %%n in (Ann Ben) do (
    curl -s -b "%JAR_A%" "%BASE%/api/greet/%%n" >NUL
)

:: -- 3) Session B: 5 calls (further along) -----------------------------------
echo.
echo [3] Session B (worker-B): start + 5 of 6 calls
curl -s -c "%JAR_B%" "%BASE%/service/session/start?track=%TRACK%&description=worker-B" >NUL
for %%n in (Ann Ben Cara Dan Eve) do (
    curl -s -b "%JAR_B%" "%BASE%/api/greet/%%n" >NUL
)

:: -- 4) Report ---------------------------------------------------------------
echo.
echo [4] GET /service/sessions (worker-A should be ~33%%, worker-B ~83%%)
for /f "delims=" %%r in ('curl -s "%BASE%/service/sessions"') do set SS=%%r
echo     -^> !SS!

echo.
echo Done. The same view auto-refreshes at %BASE%.

endlocal
