@echo off
:: =============================================================================
::  Juke Exception-Flow Demo — Start the server
::
::  How to run:
::    - Double-click the file in Explorer, OR
::    - From cmd.exe:        demo-start-server.bat
::    - From PowerShell:     .\demo-start-server.bat   (the .\ is required)
::                           — or run demo-start-server.ps1 instead
::
::  Leave this window open (Ctrl+C to stop), then open http://localhost:8080:
::    - pick a product, click Buy -> three orders are placed with the OMS
::    - each order confirmation pops up for 5 seconds
::    - click "View test coverage" for the coverage popup
::
::  Optional: in a SECOND window run demo-run-playwright.bat to drive the full
::  four-run demo automatically (record, replay, replay+delay, replay+exception).
:: =============================================================================
setlocal

:: -- Locate JDK 25 -----------------------------------------------------------
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

:: -- Locate the JAR ----------------------------------------------------------
set JAR=%~dp0target\juke-sample-exceptions-1.0.0.jar
if not exist "%JAR%" (
    echo.
    echo ERROR: JAR not found:
    echo   %JAR%
    echo.
    echo Build it first ^(from the repo root^):
    echo   mvn -pl juke-samples/juke-sample-exceptions -am package -Pcoverage -DskipTests
    echo.
    echo The -Pcoverage profile is REQUIRED so the React bundle is built with
    echo vite-plugin-istanbul instrumentation ^(otherwise the UI half of the
    echo coverage popup stays empty^).
    echo.
    pause
    exit /b 1
)

:: -- Ensure recording / coverage directories exist ---------------------------
if not exist "%USERPROFILE%\juke-demo\sample-exceptions" mkdir "%USERPROFILE%\juke-demo\sample-exceptions"
if not exist "%USERPROFILE%\juke-demo\coverage\server"   mkdir "%USERPROFILE%\juke-demo\coverage\server"
if not exist "%USERPROFILE%\juke-demo\coverage\ui"       mkdir "%USERPROFILE%\juke-demo\coverage\ui"

:: -- Coverage args -----------------------------------------------------------
:: NOTE: goto labels, not an if/else ( ) block — cmd.exe expands paths eagerly
:: and "(" in any Program Files path would break the script.
set COVERAGE_ARGS=
set AGENT=%~dp0target\jacoco-agent.jar
if not exist "%AGENT%" goto :no_coverage
set COVERAGE_ARGS="-javaagent:%AGENT%=output=none" "-Djuke.coverage.enabled=true" "-Djuke.coverage.classes=%~dp0target\classes" "-Djuke.coverage.sources=%~dp0src\main\java" "-Djuke.coverage.report-dir=%USERPROFILE%\juke-demo\coverage\server" "-Djuke.coverage.ui-report-dir=%USERPROFILE%\juke-demo\coverage\ui" "-Djuke.coverage.bundle-name=juke-sample-exceptions"
set COVERAGE_STATUS=ENABLED  (http://localhost:8080/service/coverage)
goto :coverage_done

:no_coverage
set COVERAGE_STATUS=off ^(target\jacoco-agent.jar not found - rebuild with Maven^)

:coverage_done

:: -- Launch ------------------------------------------------------------------
echo.
echo ======================================================
echo   Juke exception-flow demo -^> http://localhost:8080
echo   Recordings:   %USERPROFILE%\juke-demo\sample-exceptions
echo   Server cov:   %USERPROFILE%\juke-demo\coverage\server
echo   UI cov:       %USERPROFILE%\juke-demo\coverage\ui
echo   Coverage:     %COVERAGE_STATUS%
echo   Ctrl+C to stop.
echo ======================================================
echo.

java %COVERAGE_ARGS% "-Djuke.enabled=true" "-Djuke.path=%USERPROFILE%\juke-demo\sample-exceptions" "-Djuke.zip=order-demo" -jar "%JAR%"

endlocal
