@echo off
:: =============================================================================
::  Juke Demo - Drive the ToDo "session drift" UI journey with Playwright
::
::  Prerequisites:
::    1. demo-start-server.bat is running in another window
::    2. Server has printed "Started ToDoApplication"
::
::  Double-click this file, or run it from any terminal.
::
::  What it does:
::    Opens http://localhost:8080 in a real Chrome window, records two todos,
::    starts a cookie session, deliberately sends an INCONSISTENT second title,
::    stops the session, and asserts the report panel surfaces the drift
::    (COMPLETED_WITH_DEVIATIONS + a red-highlighted call row).
:: =============================================================================
setlocal

:: -- Node 22 (bundled by frontend-maven-plugin under juke-sample-greeting) ----
:: NOTE: do NOT use an if/else ( ) block here - cmd.exe expands %PATH% eagerly
:: when parsing the block, and any "(" in PATH (e.g. "Program Files (x86)")
:: causes a syntax error. Use goto labels instead.
set BUNDLED_NODE=%~dp0..\juke-sample-greeting\.node\node
if not exist "%BUNDLED_NODE%\node.exe" goto :no_bundled_node
set PATH=%BUNDLED_NODE%;%PATH%
echo Using bundled Node 22
goto :check_server

:no_bundled_node
echo Bundled Node not found - using system Node
echo Build juke-sample-greeting once with Maven to download Node 22

:check_server
:: -- Check server is reachable -----------------------------------------------
echo Checking server at http://localhost:8080 ...
curl -s -f -o NUL http://localhost:8080/todos
if errorlevel 1 (
    echo.
    echo ERROR: Server is not responding on http://localhost:8080
    echo Start it first with demo-start-server.bat
    echo.
    pause
    exit /b 1
)
echo Server is up.

:: -- Playwright directory ----------------------------------------------------
set PLAYWRIGHT_DIR=%~dp0..\juke-sample-session\src\test\playwright
if not exist "%PLAYWRIGHT_DIR%" (
    echo ERROR: Playwright directory not found: %PLAYWRIGHT_DIR%
    pause
    exit /b 1
)

cd /d "%PLAYWRIGHT_DIR%"

echo.
echo ======================================================
echo   Running Todo drift demo -- Chrome will open visibly
echo   Watch the report panel light up red after stop-session.
echo ======================================================
echo.

npx playwright test --project="Todo Drift" e2e/todo-drift.spec.ts --headed

endlocal
