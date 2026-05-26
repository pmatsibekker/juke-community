@echo off
:: =============================================================================
::  Juke Demo - Start the cookie-session sample server
::
::  Double-click this file, or run it from any terminal.
::  Leave this window open (Ctrl+C to stop).
::  Then run demo-run-curl.bat in a SECOND window to drive the journey.
::
::  The sample wires @Juke("none") on SessionGreetingController's service field.
::  /service/session/start issues JUKE_SESSION_ID + JUKE_TRACK cookies; with
::  those cookies on /session-greeting, responses come from the per-session
::  ZIP recording. Without cookies, the live service answers.
:: =============================================================================
setlocal

:: -- Locate JDK 25 ------------------------------------------------------------
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" goto :java_found
)
set JAVA_HOME=%USERPROFILE%\.jdks\ms-25.0.3
if exist "%JAVA_HOME%\bin\java.exe" goto :java_found
set JAVA_HOME=C:\Program Files\Java\jdk-25
if exist "%JAVA_HOME%\bin\java.exe" goto :java_found

echo ERROR: Java 25 not found.
echo Set JAVA_HOME to your JDK 25 directory, or install JDK 25 from
echo https://adoptium.net
pause
exit /b 1

:java_found
set PATH=%JAVA_HOME%\bin;%PATH%
echo Using Java: && java -version

:: -- Locate the session JAR ---------------------------------------------------
set JAR=%~dp0target\juke-sample-session-1.0.0.jar
if not exist "%JAR%" (
    echo.
    echo ERROR: JAR not found:
    echo   %JAR%
    echo.
    echo Build it first ^(from the repo root^):
    echo   mvn -pl juke-samples/juke-sample-session -am package -DskipTests
    echo.
    pause
    exit /b 1
)

:: -- Stage pre-shipped session ZIPs into the juke.path -----------------------
:: The cookie demo replays from juke-sample-ui.zip and juke-sample-ui-2.zip.
:: Mirrors the launch documented in juke-cookie-replay.spec.ts.
set JUKEPATH=%~dp0src\test\playwright\test-resources

:: -- Launch -------------------------------------------------------------------
echo.
echo ======================================================
echo   Juke session-cookie server -^> http://localhost:8080
echo   Replay ZIPs from: %JUKEPATH%
echo     juke-sample-ui.zip   -^> Hello, Evan ^| Hello, Pavel
echo     juke-sample-ui-2.zip -^> Hello, Evan ^| Hello, Viane
echo   Ctrl+C to stop.
echo ======================================================
echo.

java "-Djuke=replay" "-Djuke.path=%JUKEPATH%" "-Djuke.zip=juke-sample-ui" -jar "%JAR%"

endlocal
