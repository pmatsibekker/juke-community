@echo off
:: =============================================================================
::  Juke Demo - Start the rest-client sample server
::
::  Double-click this file, or run it from any terminal.
::  Leave this window open (Ctrl+C to stop).
::  Then run demo-run-curl.bat in a SECOND window to drive record/replay.
::
::  The sample wires concrete-field @Juke onto two RestTemplate beans
::  (ShippingClient, PricingClient). Global record/replay is driven via the
::  /service/* endpoints; cookie-session replay does not apply to concrete fields.
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

:: -- Locate the rest-client JAR -----------------------------------------------
set JAR=%~dp0target\juke-sample-rest-client-0.0.1-SNAPSHOT.jar
if not exist "%JAR%" (
    echo.
    echo ERROR: JAR not found:
    echo   %JAR%
    echo.
    echo Build it first ^(from the repo root^):
    echo   mvn -pl juke-samples/juke-sample-rest-client -am package -DskipTests
    echo.
    pause
    exit /b 1
)

:: -- Ensure recording directory exists ----------------------------------------
if not exist "%USERPROFILE%\juke\sample-rest-client" (
    mkdir "%USERPROFILE%\juke\sample-rest-client"
    echo Created recording directory: %USERPROFILE%\juke\sample-rest-client
)

:: -- Launch -------------------------------------------------------------------
echo.
echo ======================================================
echo   Juke rest-client server -^> http://localhost:8080
echo   Recordings stored in: %USERPROFILE%\juke\sample-rest-client
echo   Open http://localhost:8080 for the banner UI.
echo   Ctrl+C to stop.
echo ======================================================
echo.

java "-Djuke.enabled=true" -jar "%JAR%"

endlocal
