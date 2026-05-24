@echo off
:: =============================================================================
::  Juke Demo — Start the greeting server
::
::  Double-click this file, or run it from any terminal.
::  Leave this window open (Ctrl+C to stop).
::  Then run demo-run-playwright.bat in a SECOND window.
:: =============================================================================
setlocal

:: ── Locate JDK 25 ─────────────────────────────────────────────────────────────
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

:: ── Locate the greeting JAR ────────────────────────────────────────────────────
:: Script lives in juke-sample-greeting/, so the JAR is in target/ (same folder).
set JAR=%~dp0target\juke-sample-greeting-0.0.1-SNAPSHOT.jar
if not exist "%JAR%" (
    echo.
    echo ERROR: JAR not found:
    echo   %JAR%
    echo.
    echo Build it first ^(from the repo root^):
    echo   mvn -pl juke-samples/juke-sample-greeting -am package -DskipTests -DskipFrontend=true
    echo.
    pause
    exit /b 1
)

:: ── Ensure recording directory exists ─────────────────────────────────────────
if not exist "%USERPROFILE%\juke-demo" (
    mkdir "%USERPROFILE%\juke-demo"
    echo Created recording directory: %USERPROFILE%\juke-demo
)

:: ── Functional coverage (optional) ────────────────────────────────────────────
:: The JaCoCo agent jar is copied to target/ by the Maven build. When present we
:: attach it (output=none — coverage is read in-process via the agent API) and
:: turn on juke.coverage.enabled so /service/coverage/server works. If the jar is
:: missing the server still starts, just without coverage capture.
::
:: NOTE: goto labels, not an if/else ( ) block — cmd.exe parses parenthesised
:: blocks eagerly and a "(" in any expanded path would break the script.
set COVERAGE_ARGS=
set AGENT=%~dp0target\jacoco-agent.jar
if not exist "%AGENT%" goto :no_coverage
set COVERAGE_ARGS="-javaagent:%AGENT%=output=none" "-Djuke.coverage.enabled=true" "-Djuke.coverage.classes=%~dp0target\classes" "-Djuke.coverage.sources=%~dp0src\main\java" "-Djuke.coverage.report-dir=%USERPROFILE%\juke-demo\coverage\server" "-Djuke.coverage.bundle-name=juke-sample-greeting"
set COVERAGE_STATUS=ENABLED  -^> http://localhost:8080/service/coverage/server
goto :coverage_done

:no_coverage
set COVERAGE_STATUS=off ^(target\jacoco-agent.jar not found - rebuild with Maven^)

:coverage_done

:: ── Launch ─────────────────────────────────────────────────────────────────────
echo.
echo ======================================================
echo   Juke greeting server -^> http://localhost:8080
echo   Recordings stored in: %USERPROFILE%\juke-demo
echo   Coverage: %COVERAGE_STATUS%
echo   Ctrl+C to stop.
echo ======================================================
echo.

java %COVERAGE_ARGS% "-Djuke.enabled=true" "-Djuke.path=%USERPROFILE%\juke-demo" "-Djuke.zip=demo" -jar "%JAR%"

endlocal
