@echo off
:: =============================================================================
::  Juke Demo - Drive the ToDo CRUD journey with curl
::
::  Prerequisites:
::    1. demo-start-server.bat is running in another window
::    2. Server has printed "Started ToDoApplication"
::
::  Run from any terminal (or double-click):
::    juke-samples\juke-sample-todo\demo-run-curl.bat
::
::  What it does:
::    1. RECORD baseline    -> create + read CRUD operations are captured
::    2. REPLAY same        -> the same calls return the recorded responses
::                             without ever touching the real ToDoServiceImpl
::    3. PASS-THROUGH       -> after /service/replay/end the live service answers
::                             again (a fresh GET shows the in-memory store, which
::                             was untouched by replay)
::    4. SESSION DRIFT      -> record a fresh baseline on a separate track,
::                             open a COOKIE SESSION, drive it with an INCONSISTENT
::                             title, stop the session, then GET the recording
::                             report - which marks the session COMPLETED_WITH_DEVIATIONS
::                             and prints recorded vs actual arguments.
::
::  The @Juke seam is on ToDoDAO.todoService, so every call from the
::  controller goes through Juke - captured in record mode, served from the ZIP
::  in replay mode. SessionAwareReplayHandler diffs each replayed call's
::  inputs against the recorded sidecar; the report endpoint surfaces those
::  per-call matches so drift is visible to the user, not just to log readers.
:: =============================================================================
setlocal enabledelayedexpansion

set BASE=http://localhost:8080
set TRACK=todo-track

:: -- Verify server is reachable ----------------------------------------------
echo Checking server at %BASE% ...
for /f "delims=" %%c in ('curl -s -o NUL -w "%%{http_code}" "%BASE%/todos"') do set CODE=%%c
if not "%CODE:~0,1%"=="2" if not "%CODE:~0,1%"=="3" (
    echo.
    echo ERROR: Server is not responding on %BASE% ^(got HTTP '%CODE%'^).
    echo Start it first in another window:
    echo   juke-samples\juke-sample-todo\demo-start-server.bat
    echo.
    pause
    exit /b 1
)
echo Server is up.

echo.
echo ======================================================
echo   ToDo CRUD - record + replay through @Juke
echo ======================================================

:: -- 1) RECORD baseline ------------------------------------------------------
echo.
echo [1] RECORD baseline ^(track '%TRACK%'^)
curl -s "%BASE%/service/record/start?track=%TRACK%" >NUL

echo     POST  /todos          {"title":"Write reference scripts"}
for /f "delims=" %%r in ('curl -s -X POST -H "Content-Type: application/json" -d "{\"title\":\"Write reference scripts\"}" "%BASE%/todos"') do set CREATED=%%r
echo         -^> !CREATED!

echo     POST  /todos          {"title":"Validate the curl journey"}
for /f "delims=" %%r in ('curl -s -X POST -H "Content-Type: application/json" -d "{\"title\":\"Validate the curl journey\"}" "%BASE%/todos"') do set CREATED2=%%r
echo         -^> !CREATED2!

echo     GET   /todos
for /f "delims=" %%r in ('curl -s "%BASE%/todos"') do set LIST=%%r
echo         -^> !LIST!

echo     GET   /todos/pending
for /f "delims=" %%r in ('curl -s "%BASE%/todos/pending"') do set PENDING=%%r
echo         -^> !PENDING!

curl -s "%BASE%/service/record/end" >NUL
echo     [recording closed]

:: -- 2) REPLAY same journey --------------------------------------------------
echo.
echo [2] REPLAY ^(no real service is invoked - responses come from the ZIP^)
curl -s "%BASE%/service/replay/start?track=%TRACK%" >NUL

echo     POST  /todos          {"title":"Write reference scripts"}
for /f "delims=" %%r in ('curl -s -X POST -H "Content-Type: application/json" -d "{\"title\":\"Write reference scripts\"}" "%BASE%/todos"') do set RCREATED=%%r
echo         -^> !RCREATED!     ^(must match the recorded id^)

echo     POST  /todos          {"title":"Validate the curl journey"}
for /f "delims=" %%r in ('curl -s -X POST -H "Content-Type: application/json" -d "{\"title\":\"Validate the curl journey\"}" "%BASE%/todos"') do set RCREATED2=%%r
echo         -^> !RCREATED2!    ^(must match the recorded id^)

echo     GET   /todos
for /f "delims=" %%r in ('curl -s "%BASE%/todos"') do set RLIST=%%r
echo         -^> !RLIST!

echo     GET   /todos/pending
for /f "delims=" %%r in ('curl -s "%BASE%/todos/pending"') do set RPENDING=%%r
echo         -^> !RPENDING!

curl -s "%BASE%/service/replay/end" >NUL
echo     [replay closed]

:: -- 3) PASS-THROUGH ---------------------------------------------------------
:: Replay never wrote to the in-memory ToDo store, so the live list still
:: contains only the two records created in step 1.
echo.
echo [3] PASS-THROUGH ^(live ToDoServiceImpl answers again^)
for /f "delims=" %%r in ('curl -s "%BASE%/todos"') do set FINAL=%%r
echo     GET   /todos    -^> !FINAL!
echo     =^> shows the live in-memory store ^(unchanged by step 2^).

:: -- 4) SESSION DRIFT --------------------------------------------------------
set DRIFT_TRACK=todo-ui-drift
set JAR=%TEMP%\juke-todo-session.txt
del /q "%JAR%" 2>NUL

echo.
echo ======================================================
echo   SESSION DRIFT - visible @Juke deviation per-session
echo ======================================================

echo.
echo [4a] RECORD baseline on track '%DRIFT_TRACK%' (titles: Buy milk, Buy bread)
curl -s "%BASE%/service/record/start?track=%DRIFT_TRACK%" >NUL
curl -s -X POST -H "Content-Type: application/json" -d "{\"title\":\"Buy milk\"}"  "%BASE%/todos" >NUL
curl -s -X POST -H "Content-Type: application/json" -d "{\"title\":\"Buy bread\"}" "%BASE%/todos" >NUL
curl -s "%BASE%/service/record/end" >NUL
echo     [baseline recorded: 2 calls]

echo.
echo [4b] Open cookie session and DRIVE WITH INCONSISTENT INPUT
echo      (second POST sends 'Buy oats' instead of recorded 'Buy bread')
curl -s -c "%JAR%" "%BASE%/service/session/start?track=%DRIFT_TRACK%&description=ui-drift-curl" >NUL
curl -s -b "%JAR%" -X POST -H "Content-Type: application/json" -d "{\"title\":\"Buy milk\"}" "%BASE%/todos" >NUL
curl -s -b "%JAR%" -X POST -H "Content-Type: application/json" -d "{\"title\":\"Buy oats\"}" "%BASE%/todos" >NUL
curl -s -b "%JAR%" "%BASE%/service/session/stop" >NUL
echo     [session stopped]

echo.
echo [4c] GET /service/recording/report?track=%DRIFT_TRACK%
:: The report is multi-line pretty-printed JSON; for /f only captures the last
:: line so use a temp file and type it instead.
set REPORT_FILE=%TEMP%\juke-todo-report.json
curl -s "%BASE%/service/recording/report?track=%DRIFT_TRACK%" -o "%REPORT_FILE%"
type "%REPORT_FILE%"
del /q "%REPORT_FILE%" 2>NUL
echo.
echo     (look for "overallStatus":"COMPLETED_WITH_DEVIATIONS" and an "inputMatched":false entry)

echo.
echo Done. Open %BASE% for the same journey driven from the UI (report panel highlights the drift row).
endlocal
