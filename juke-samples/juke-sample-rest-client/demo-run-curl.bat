@echo off
:: =============================================================================
::  Juke Demo - Drive the rest-client RECORD/REPLAY journey with curl
::
::  Prerequisites:
::    1. demo-start-server.bat is running in another window
::    2. Server has printed "Started RestClientApplication"
::
::  Run from any directory:
::    .\juke-samples\juke-sample-rest-client\demo-run-curl.bat
::
::  What it does:
::    1. PASS-THROUGH (no Juke) -> live upstream returns random quoteIds
::    2. RECORD baseline        -> /api/quote/sku-1, /api/quote/sku-2 captured
::    3. REPLAY same SKUs       -> recorded quoteIds returned, upstream is silent
::
::  Each upstream response carries a random quoteId, so a recorded vs live call
::  is easy to spot: replay returns the exact id from step 2.
::
::  NOTE: the concrete-field RestTemplate seam binds its proxy at @PostConstruct
::  (see CLAUDE.md "Test isolation"). After /service/replay/end the cached
::  proxy keeps serving recorded responses positionally, so there's no clean
::  "pass-through restored" step in this sample - restart the JVM to reset.
:: =============================================================================

setlocal enabledelayedexpansion

set BASE=http://localhost:8080
set TRACK=rest-client-track

:: -- Verify server is reachable ----------------------------------------------
echo Checking server at %BASE% ...
curl -s -f -o NUL "%BASE%/upstream/health"
if errorlevel 1 (
    echo ERROR: Server is not responding on %BASE%.
    echo Start it first in another window:
    echo   .\juke-samples\juke-sample-rest-client\demo-start-server.bat
    exit /b 1
)
echo Server is up.

echo.
echo ======================================================
echo   rest-client (concrete-field @Juke on RestTemplate)
echo ======================================================

:: -- 1) PASS-THROUGH ---------------------------------------------------------
echo.
echo [1] PASS-THROUGH (no Juke -^> live upstream, random quoteIds)
for /f "delims=" %%r in ('curl -s "%BASE%/api/quote/sku-1"') do set PRE1=%%r
echo     GET /api/quote/sku-1 -^> !PRE1!
for /f "delims=" %%r in ('curl -s "%BASE%/api/quote/sku-2"') do set PRE2=%%r
echo     GET /api/quote/sku-2 -^> !PRE2!

:: -- 2) RECORD baseline ------------------------------------------------------
echo.
echo [2] RECORD baseline (track '%TRACK%')
curl -s "%BASE%/service/record/start?track=%TRACK%" >NUL
for /f "delims=" %%r in ('curl -s "%BASE%/api/quote/sku-1"') do set REC1=%%r
echo     GET /api/quote/sku-1 -^> !REC1!
for /f "delims=" %%r in ('curl -s "%BASE%/api/quote/sku-2"') do set REC2=%%r
echo     GET /api/quote/sku-2 -^> !REC2!
curl -s "%BASE%/service/record/end" >NUL
echo     [recording closed]

:: -- 3) REPLAY ---------------------------------------------------------------
echo.
echo [3] REPLAY (responses from ZIP - quoteIds must match step 2)
curl -s "%BASE%/service/replay/start?track=%TRACK%" >NUL
for /f "delims=" %%r in ('curl -s "%BASE%/api/quote/sku-1"') do set REP1=%%r
echo     GET /api/quote/sku-1 -^> !REP1!
for /f "delims=" %%r in ('curl -s "%BASE%/api/quote/sku-2"') do set REP2=%%r
echo     GET /api/quote/sku-2 -^> !REP2!
curl -s "%BASE%/service/replay/end" >NUL
echo     [replay closed]

echo.
echo Done.

endlocal
