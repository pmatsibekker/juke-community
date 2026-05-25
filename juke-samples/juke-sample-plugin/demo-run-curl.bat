@echo off
:: =============================================================================
::  Juke Demo - Inspect the plugin registry with curl
::
::  Prerequisites:
::    1. demo-start-server.bat is running in another window
::    2. Server has printed "Started PluginApplication"
::
::  Run from any directory:
::    .\juke-samples\juke-sample-plugin\demo-run-curl.bat
::
::  What it does:
::    1. GET /service/plugins         -> lists every registered plugin
::    2. GET /service/plugins/{id}    -> details for demo-transformer
::
::  The demo-transformer plugin lives in the same JVM and self-registers via
::  the SDK on ApplicationReadyEvent.
:: =============================================================================

setlocal enabledelayedexpansion

set BASE=http://localhost:8080
set PLUGIN_ID=demo-transformer

:: -- Verify server is reachable ----------------------------------------------
echo Checking server at %BASE% ...
curl -s -f -o NUL "%BASE%/service/plugins"
if errorlevel 1 (
    echo ERROR: Server is not responding on %BASE%.
    echo Start it first in another window:
    echo   .\juke-samples\juke-sample-plugin\demo-start-server.bat
    exit /b 1
)
echo Server is up.

echo.
echo ======================================================
echo   Plugin SDK - registry inspection
echo ======================================================

echo.
echo [1] GET /service/plugins
for /f "delims=" %%r in ('curl -s "%BASE%/service/plugins"') do set LIST=%%r
echo     -^> !LIST!

echo.
echo [2] GET /service/plugins/%PLUGIN_ID%
for /f "delims=" %%r in ('curl -s "%BASE%/service/plugins/%PLUGIN_ID%"') do set DETAIL=%%r
echo     -^> !DETAIL!

echo.
echo Done. The same view is rendered in the browser at %BASE%.

endlocal
