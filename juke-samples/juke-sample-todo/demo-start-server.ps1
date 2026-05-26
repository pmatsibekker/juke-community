# =============================================================================
#  Juke Demo - Start the ToDo CRUD server
#
#  Run from any directory:
#    .\juke-samples\juke-sample-todo\demo-start-server.ps1
#
#  Or right-click the file -> "Run with PowerShell"
#
#  Leave this window open. Ctrl+C to stop.
#  Then run demo-run-curl.ps1 in a SECOND window to drive a
#  RECORD -> REPLAY journey through /todos.
# =============================================================================

$ErrorActionPreference = "Stop"

# -- Locate JDK 25 ------------------------------------------------------------
# Tries (in order): JAVA_HOME env var, the IntelliJ-managed JDK, common installs.
function Find-Java25 {
    $candidates = @(
        $env:JAVA_HOME,
        "$env:USERPROFILE\.jdks\ms-25.0.3",
        "$env:USERPROFILE\.jdks\openjdk-25",
        "C:\Program Files\Java\jdk-25",
        "C:\Program Files\Eclipse Adoptium\jdk-25"
    ) | Where-Object { $_ -and (Test-Path "$_\bin\java.exe") }

    if (-not $candidates) {
        Write-Error @"
Java 25 not found.
Set JAVA_HOME to your JDK 25 installation, or install JDK 25 from
https://adoptium.net or via IntelliJ (File -> Project Structure -> SDKs).
"@
        exit 1
    }
    return $candidates[0]
}

$JAVA_HOME = Find-Java25
$env:JAVA_HOME = $JAVA_HOME
$env:Path      = "$JAVA_HOME\bin;$env:Path"

Write-Host "Using Java: $((& java -version 2>&1)[0])" -ForegroundColor Cyan

# -- Locate the ToDo JAR ------------------------------------------------------
# Script lives in juke-sample-todo/, so the JAR is in target/ (same folder).
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$JAR = Join-Path $ScriptDir "target\juke-sample-todo-1.0.0.jar"

if (-not (Test-Path $JAR)) {
    Write-Error @"
JAR not found:
  $JAR

Build it first (from the repo root):
  mvn -pl juke-samples/juke-sample-todo -am package -DskipTests
"@
    exit 1
}

Write-Host "JAR: $JAR" -ForegroundColor Cyan

# -- Ensure recording directory exists ----------------------------------------
# Matches juke.path in src/main/resources/application.yml.
$RecordDir = "$env:USERPROFILE\juke\sample-todo"
if (-not (Test-Path $RecordDir)) {
    New-Item -ItemType Directory -Force -Path $RecordDir | Out-Null
    Write-Host "Created recording directory: $RecordDir" -ForegroundColor Green
}

# -- Launch -------------------------------------------------------------------
Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  Juke ToDo CRUD server -> http://localhost:8080"        -ForegroundColor Yellow
Write-Host "  Recordings stored in: $RecordDir"                      -ForegroundColor Yellow
Write-Host "  Drive it from another window with demo-run-curl.ps1." -ForegroundColor Yellow
Write-Host "  Ctrl+C to stop."                                       -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host ""

java "-Djuke.enabled=true" -jar $JAR
