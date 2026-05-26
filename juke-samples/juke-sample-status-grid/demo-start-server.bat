@echo off
:: =============================================================================
::  Juke Demo - Start the status-grid sample server
::
::  Double-click this file, or run it from any terminal.
::  Leave this window open (Ctrl+C to stop).
::  Then run demo-run-curl.bat in a SECOND window to drive the journey.
::
::  Records one 6-call track, then spawns two cookie sessions advancing
::  through it at different rates. GET /service/sessions reports each
::  session's lastCall + percentComplete.
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

:: -- Locate the status-grid JAR -----------------------------------------------
set JAR=%~dp0target\juke-sample-status-grid-1.0.0.jar
if not exist "%JAR%" (
    echo.
    echo ERROR: JAR not found:
    echo   %JAR%
    echo.
    echo Build it first ^(from the repo root^):
    echo   mvn -pl juke-samples/juke-sample-status-grid -am package -DskipTests
    echo.
    pause
    exit /b 1
)

:: -- Ensure recording directory exists ----------------------------------------
if not exist "%USERPROFILE%\juke\sample-status-grid" (
    mkdir "%USERPROFILE%\juke\sample-status-grid"
    echo Created recording directory: %USERPROFILE%\juke\sample-status-grid
)

:: -- Launch -------------------------------------------------------------------
echo.
echo ======================================================
echo   Juke status-grid server -^> http://localhost:8080
echo   Recordings stored in: %USERPROFILE%\juke\sample-status-grid
echo   Open http://localhost:8080 for the live grid UI.
echo   Ctrl+C to stop.
echo ======================================================
echo.

java "-Djuke.enabled=true" -jar "%JAR%"

endlocal
