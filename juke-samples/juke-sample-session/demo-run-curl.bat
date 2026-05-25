@echo off
:: =============================================================================
::  Juke Demo - Drive the cookie-session journey with curl
::
::  Prerequisites:
::    1. demo-start-server.bat is running in another window
::    2. Server has printed "Started SessionApplication"
::
::  Run from any directory:
::    .\juke-samples\juke-sample-session\demo-run-curl.bat
::
::  What it does (curl mirror of juke-cookie-replay.spec.ts):
::    The server runs with -Djuke=replay -Djuke.zip=juke-sample-ui.
::    Default (no cookies) replays from juke-sample-ui.zip.
::    Cookies from /service/session/start override the default ZIP for that
::    request, so each session can replay an independent track concurrently.
::
::    1. DEFAULT (no cookies) -> juke-sample-ui : "Hello, Evan!"
::    2. SESSION A on track juke-sample-ui   -> "Hello, Evan!" then "Hello, Pavel!"
::    3. SESSION B on track juke-sample-ui-2 -> "Hello, Evan!" then "Hello, Viane!"
::    4. Stop both sessions; default replay is restored.
::
::  KNOWN ISSUE: The cookie-bound replay relies on JukeBeanPostProcessor wrapping
::  SessionGreetingController.greetingsService. Because the controller is also
::  @JukeController (Spring CGLIB-proxied), the post-processor sees a null field
::  on the proxy and logs:
::    WARN ... Field greetingsService in SessionGreetingController$$SpringCGLIB$$0
::    is null, cannot wrap with Juke
::  Until that's fixed, every call falls through to the real GreetingServiceImpl,
::  so the responses below will read "Hello, any" with monotonically increasing
::  ids instead of the recorded values. The session lifecycle endpoints
::  (/service/session/{start,stop,status}) still work; only the replay routing
::  is currently a no-op for this seam.
:: =============================================================================

setlocal enabledelayedexpansion

set BASE=http://localhost:8080
set TRACK1=juke-sample-ui
set TRACK2=juke-sample-ui-2
set JAR_A=%TEMP%\juke-demo-cookies-A.txt
set JAR_B=%TEMP%\juke-demo-cookies-B.txt

:: -- 0) Verify server is reachable -------------------------------------------
echo Checking server at %BASE% ...
curl -s -f -o NUL "%BASE%/session-greeting?name=Probe"
if errorlevel 1 (
    echo ERROR: Server is not responding on %BASE%.
    echo Start it first in another window:
    echo   .\juke-samples\juke-sample-session\demo-start-server.bat
    exit /b 1
)
echo Server is up.

:: Fresh cookie jars
del /q "%JAR_A%" 2>NUL
del /q "%JAR_B%" 2>NUL

echo.
echo ======================================================
echo   Cookie-bound replay - two isolated sessions
echo ======================================================

:: -- 1) DEFAULT (no cookies, default ZIP) ------------------------------------
echo.
echo [1] DEFAULT replay (no cookies -^> juke.zip=juke-sample-ui)
for /f "delims=" %%r in ('curl -s "%BASE%/session-greeting?name=any"') do set DEF=%%r
echo     GET /session-greeting?name=any    -^> !DEF!   (expect "Hello, Evan!")

:: -- 2) SESSION A on track juke-sample-ui ------------------------------------
echo.
echo [2] SESSION A start (track '%TRACK1%')
for /f "delims=" %%r in ('curl -s -c "%JAR_A%" "%BASE%/service/session/start?track=%TRACK1%"') do set STARTA=%%r
echo     /service/session/start            -^> !STARTA!

for /f "delims=" %%r in ('curl -s -b "%JAR_A%" "%BASE%/session-greeting?name=any"') do set A1=%%r
echo     GET /session-greeting?name=any    -^> !A1!   (expect "Hello, Evan!")

for /f "delims=" %%r in ('curl -s -b "%JAR_A%" "%BASE%/session-greeting?name=any"') do set A2=%%r
echo     GET /session-greeting?name=any    -^> !A2!   (expect "Hello, Pavel!")

curl -s -b "%JAR_A%" -c "%JAR_A%" "%BASE%/service/session/stop" >NUL
echo     [session A stopped]

:: -- 3) SESSION B on track juke-sample-ui-2 ----------------------------------
echo.
echo [3] SESSION B start (track '%TRACK2%')
for /f "delims=" %%r in ('curl -s -c "%JAR_B%" "%BASE%/service/session/start?track=%TRACK2%"') do set STARTB=%%r
echo     /service/session/start            -^> !STARTB!

for /f "delims=" %%r in ('curl -s -b "%JAR_B%" "%BASE%/session-greeting?name=any"') do set B1=%%r
echo     GET /session-greeting?name=any    -^> !B1!   (expect "Hello, Evan!")

for /f "delims=" %%r in ('curl -s -b "%JAR_B%" "%BASE%/session-greeting?name=any"') do set B2=%%r
echo     GET /session-greeting?name=any    -^> !B2!   (expect "Hello, Viane!")

curl -s -b "%JAR_B%" -c "%JAR_B%" "%BASE%/service/session/stop" >NUL
echo     [session B stopped]

:: -- 4) DEFAULT restored ------------------------------------------------------
echo.
echo [4] DEFAULT restored (no cookies -^> juke.zip=juke-sample-ui)
for /f "delims=" %%r in ('curl -s "%BASE%/session-greeting?name=any"') do set POST=%%r
echo     GET /session-greeting?name=any    -^> !POST!   (expect "Hello, Evan!")

echo.
echo Done.

endlocal
