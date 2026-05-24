# =============================================================================
#  Juke Demo — Start the greeting server
#
#  Run from any directory:
#    .\juke-samples\juke-sample-greeting\demo-start-server.ps1
#
#  Or right-click the file → "Run with PowerShell"
#
#  Leave this window open. Ctrl+C to stop.
#  Then run demo-run-playwright.ps1 in a SECOND window.
# =============================================================================

$ErrorActionPreference = "Stop"

# ── Locate JDK 25 ─────────────────────────────────────────────────────────────
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
https://adoptium.net or via IntelliJ (File → Project Structure → SDKs).
"@
        exit 1
    }
    return $candidates[0]
}

$JAVA_HOME = Find-Java25
$env:JAVA_HOME = $JAVA_HOME
$env:Path     = "$JAVA_HOME\bin;$env:Path"

Write-Host "Using Java: $((& java -version 2>&1)[0])" -ForegroundColor Cyan

# ── Locate the greeting JAR ────────────────────────────────────────────────────
# Script lives in juke-sample-greeting/, so the JAR is in target/ (same folder).
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$JAR = Join-Path $ScriptDir "target\juke-sample-greeting-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path $JAR)) {
    Write-Error @"
JAR not found:
  $JAR

Build it first (from the repo root):
  mvn -pl juke-samples/juke-sample-greeting -am package -DskipTests -DskipFrontend=true
"@
    exit 1
}

Write-Host "JAR: $JAR" -ForegroundColor Cyan

# ── Ensure recording directory exists ─────────────────────────────────────────
$RecordDir = "$env:USERPROFILE\juke-demo"
if (-not (Test-Path $RecordDir)) {
    New-Item -ItemType Directory -Force -Path $RecordDir | Out-Null
    Write-Host "Created recording directory: $RecordDir" -ForegroundColor Green
}

# ── Functional coverage (optional) ────────────────────────────────────────────
# The JaCoCo agent jar is copied to target/ by the Maven build. When present we
# attach it (output=none — coverage is read in-process via the agent API) and
# turn on juke.coverage.enabled so /service/coverage/server works. If the jar is
# missing the server still starts, just without coverage capture.
$Agent        = Join-Path $ScriptDir "target\jacoco-agent.jar"
$CoverageArgs = @()
if (Test-Path $Agent) {
    $CoverageArgs = @(
        "-javaagent:$Agent=output=none",
        "-Djuke.coverage.enabled=true",
        "-Djuke.coverage.classes=$(Join-Path $ScriptDir 'target\classes')",
        "-Djuke.coverage.sources=$(Join-Path $ScriptDir 'src\main\java')",
        "-Djuke.coverage.report-dir=$RecordDir\coverage\server",
        "-Djuke.coverage.bundle-name=juke-sample-greeting"
    )
    $CoverageStatus = "ENABLED -> http://localhost:8080/service/coverage/server"
} else {
    $CoverageStatus = "off (target\jacoco-agent.jar not found - rebuild with Maven)"
}

# ── Launch ─────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  Juke greeting server → http://localhost:8080"         -ForegroundColor Yellow
Write-Host "  Recordings stored in: $RecordDir"                     -ForegroundColor Yellow
Write-Host "  Coverage: $CoverageStatus"                            -ForegroundColor Yellow
Write-Host "  Ctrl+C to stop."                                      -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host ""

java @CoverageArgs `
     "-Djuke.enabled=true" `
     "-Djuke.path=$RecordDir" `
     "-Djuke.zip=demo" `
     -jar $JAR
