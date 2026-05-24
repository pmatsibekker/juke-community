@echo off
:: =============================================================================
::  Juke Demo — Run the Playwright visual demo
::
::  Prerequisites:
::    1. demo-start-server.bat is running in another window
::    2. Server has printed "Started GreetingApplication"
::
::  Double-click this file, or run it from any terminal.
:: =============================================================================
setlocal

:: ── Node 22 (bundled by frontend-maven-plugin) ────────────────────────────────
:: Script lives in juke-sample-greeting/, so Node is in .node/node/ (same folder).
::
:: NOTE: do NOT use an if/else ( ) block here — cmd.exe expands %PATH% eagerly
:: when parsing the block, and any "(" in PATH (e.g. "Program Files (x86)")
:: causes a syntax error.  Use goto labels instead.
set BUNDLED_NODE=%~dp0.node\node
if not exist "%BUNDLED_NODE%\node.exe" goto :no_bundled_node
set PATH=%BUNDLED_NODE%;%PATH%
echo Using bundled Node 22
goto :check_server

:no_bundled_node
echo Bundled Node not found - using system Node
echo Build juke-sample-greeting once with Maven to download Node 22

:check_server
:: ── Check server is reachable ─────────────────────────────────────────────────
echo Checking server at http://localhost:8080 ...
curl -s -o nul -w "%%{http_code}" http://localhost:8080/service/session/status > "%TEMP%\juke_ping.txt" 2>nul
set /p HTTP_CODE=<"%TEMP%\juke_ping.txt"
del "%TEMP%\juke_ping.txt" >nul 2>&1

if "%HTTP_CODE%" NEQ "200" goto :server_down
echo Server is up.
goto :run_playwright

:server_down
echo.
echo ERROR: Server is not responding on http://localhost:8080
echo Start it first with demo-start-server.bat
echo.
pause
exit /b 1

:run_playwright
:: ── Playwright directory ───────────────────────────────────────────────────────
:: Script lives in juke-sample-greeting/, so Playwright is one level up.
set PLAYWRIGHT_DIR=%~dp0..\juke-sample-session\src\test\playwright
if not exist "%PLAYWRIGHT_DIR%" (
    echo ERROR: Playwright directory not found: %PLAYWRIGHT_DIR%
    pause
    exit /b 1
)

cd /d "%PLAYWRIGHT_DIR%"

echo.
echo ======================================================
echo   Running Juke demo -- 5 phases, ~2.5 min total
echo   Chrome will open automatically.
echo ======================================================
echo.

npx playwright test --project="Juke Demo" e2e/juke-demo.spec.ts --headed

endlocal
