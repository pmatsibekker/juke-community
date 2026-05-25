@echo off
:: =============================================================================
::  Juke Demo - Start the Plugin-SDK sample server
::
::  Double-click this file, or run it from any terminal.
::  Leave this window open (Ctrl+C to stop).
::  Then run demo-run-curl.bat in a SECOND window to query /service/plugins.
::
::  One process = the Juke agent + a plugin that self-registers with it.
::  On ApplicationReadyEvent, the SDK POSTs DemoRecordingTransformer to
::  /service/plugins/register; GET /service/plugins then lists it.
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

:: -- Locate the plugin JAR ----------------------------------------------------
set JAR=%~dp0target\juke-sample-plugin-0.0.1-SNAPSHOT.jar
if not exist "%JAR%" (
    echo.
    echo ERROR: JAR not found:
    echo   %JAR%
    echo.
    echo Build it first ^(from the repo root^):
    echo   mvn -pl juke-samples/juke-sample-plugin -am package -DskipTests
    echo.
    pause
    exit /b 1
)

:: -- Ensure recording directory exists ----------------------------------------
if not exist "%USERPROFILE%\juke\sample-plugin" (
    mkdir "%USERPROFILE%\juke\sample-plugin"
    echo Created recording directory: %USERPROFILE%\juke\sample-plugin
)

:: -- Launch -------------------------------------------------------------------
echo.
echo ======================================================
echo   Juke plugin-SDK server -^> http://localhost:8080
echo   Recordings stored in: %USERPROFILE%\juke\sample-plugin
echo   Open http://localhost:8080 for the plugin registry UI.
echo   Ctrl+C to stop.
echo ======================================================
echo.

java "-Djuke.enabled=true" -jar "%JAR%"

endlocal
