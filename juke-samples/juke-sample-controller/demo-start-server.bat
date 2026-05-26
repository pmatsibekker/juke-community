@echo off
:: =============================================================================
::  Juke Demo - Start the @JukeController server
::
::  Double-click this file, or run it from any terminal.
::  Leave this window open (Ctrl+C to stop).
::  Then run demo-run-curl.bat in a SECOND window and WATCH THIS WINDOW:
::  the poisoned replay logs a CONTROLLER_MISMATCH line here (the advice
::  reports drift server-side and never changes the HTTP response).
:: =============================================================================
setlocal

:: -- Locate JDK 25 ------------------------------------------------------------
:: Tries JAVA_HOME first, then the IntelliJ-managed location.
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

:: -- Locate the controller JAR ------------------------------------------------
:: Script lives in juke-sample-controller/, so the JAR is in target/ (same folder).
set JAR=%~dp0target\juke-sample-controller-1.0.0.jar
if not exist "%JAR%" (
    echo.
    echo ERROR: JAR not found:
    echo   %JAR%
    echo.
    echo Build it first ^(from the repo root^):
    echo   mvn -pl juke-samples/juke-sample-controller -am package -DskipTests
    echo.
    pause
    exit /b 1
)

:: -- Ensure recording directory exists ----------------------------------------
:: Matches juke.path in src/main/resources/application.yml.
if not exist "%USERPROFILE%\juke\sample-controller" (
    mkdir "%USERPROFILE%\juke\sample-controller"
    echo Created recording directory: %USERPROFILE%\juke\sample-controller
)

:: -- Launch -------------------------------------------------------------------
echo.
echo ======================================================
echo   Juke @JukeController server -^> http://localhost:8080
echo   Recordings stored in: %USERPROFILE%\juke\sample-controller
echo   WATCH THIS WINDOW for CONTROLLER_MISMATCH on the
echo   poisoned replay driven by demo-run-curl.bat.
echo   Ctrl+C to stop.
echo ======================================================
echo.

java "-Djuke.enabled=true" -jar "%JAR%"

endlocal
