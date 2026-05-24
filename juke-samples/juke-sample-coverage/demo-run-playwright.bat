@echo off
:: =============================================================================
::  Juke Coverage Demo — Run the Playwright journey
::
::  Prerequisites:
::    1. demo-start-server.bat is running in another window
::    2. Server has printed "Started CoverageDemoApplication"
::
::  This script:
::    - Drives the demo journey end-to-end (Chrome opens automatically)
::    - Harvests window.__coverage__ from the browser
::    - Renders an nyc report into %USERPROFILE%\juke-demo\coverage\ui
::    - The dashboard at http://localhost:8080 then picks up the UI half
::      on its next 2-second poll
::
::  How to run:
::    - Double-click the file in Explorer, OR
::    - From cmd.exe:        demo-run-playwright.bat
::    - From PowerShell:     .\demo-run-playwright.bat   (the .\ is required)
::                           — or run demo-run-playwright.ps1 instead
:: =============================================================================
setlocal

:: ── Use the Node bundled by the Maven build ──────────────────────────────────
:: NOTE: goto labels, not an if/else ( ) block — cmd.exe expands %PATH%
:: eagerly inside parenthesised blocks and any "(" in PATH breaks parsing.
set BUNDLED_NODE=%~dp0.node\node
if not exist "%BUNDLED_NODE%\node.exe" goto :no_bundled_node
set PATH=%BUNDLED_NODE%;%PATH%
echo Using bundled Node 22
goto :check_server

:no_bundled_node
echo Bundled Node not found - using system Node
echo Build juke-sample-coverage once with Maven to download Node 22

:check_server
:: ── Check server is reachable ─────────────────────────────────────────────────
echo Checking server at http://localhost:8080/api/styles ...
curl -s -o nul -w "%%{http_code}" http://localhost:8080/api/styles > "%TEMP%\juke_ping.txt" 2>nul
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
:: ── Playwright directory ─────────────────────────────────────────────────────
set PLAYWRIGHT_DIR=%~dp0src\test\playwright
if not exist "%PLAYWRIGHT_DIR%" (
    echo ERROR: Playwright directory not found: %PLAYWRIGHT_DIR%
    pause
    exit /b 1
)

cd /d "%PLAYWRIGHT_DIR%"

:: First-time install (Playwright + nyc); no-op once node_modules exists.
if not exist "%PLAYWRIGHT_DIR%\node_modules" (
    echo Installing Playwright dependencies...
    call npm install
    call npx playwright install chromium
)

echo.
echo ======================================================
echo   Running the demo journey -- Chrome will open headed.
echo   Watch the coverage dashboard light up as it runs.
echo ======================================================
echo.

call npx playwright test --project="Coverage Demo"

endlocal
