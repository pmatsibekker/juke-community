@echo off
:: =============================================================================
::  Juke Demo - Start the ToDo CRUD server
::
::  Double-click this file, or run it from any terminal.
::  Leave this window open (Ctrl+C to stop).
::  Then run demo-run-curl.bat in a SECOND window to drive a
::  RECORD -> REPLAY journey through /todos.
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

:: -- Locate the ToDo JAR ------------------------------------------------------
:: Script lives in juke-sample-todo/, so the JAR is in target/ (same folder).
set JAR=%~dp0target\juke-sample-todo-0.0.1-SNAPSHOT.jar
if not exist "%JAR%" (
    echo.
    echo ERROR: JAR not found:
    echo   %JAR%
    echo.
    echo Build it first ^(from the repo root^):
    echo   mvn -pl juke-samples/juke-sample-todo -am package -DskipTests
    echo.
    pause
    exit /b 1
)

:: -- Ensure recording directory exists ----------------------------------------
:: Matches juke.path in src/main/resources/application.yml.
if not exist "%USERPROFILE%\juke\sample-todo" (
    mkdir "%USERPROFILE%\juke\sample-todo"
    echo Created recording directory: %USERPROFILE%\juke\sample-todo
)

:: -- Launch -------------------------------------------------------------------
echo.
echo ======================================================
echo   Juke ToDo CRUD server -^> http://localhost:8080
echo   Recordings stored in: %USERPROFILE%\juke\sample-todo
echo   Drive it from another window with demo-run-curl.bat.
echo   Ctrl+C to stop.
echo ======================================================
echo.

java "-Djuke.enabled=true" -jar "%JAR%"

endlocal
