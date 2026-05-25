# =============================================================================
#  Juke Demo - Start the cookie-session sample server
#
#  Run from any directory:
#    .\juke-samples\juke-sample-session\demo-start-server.ps1
#
#  Or right-click the file -> "Run with PowerShell"
#
#  Leave this window open. Ctrl+C to stop.
#  Then run demo-run-curl.ps1 in a SECOND window to drive the journey.
#
#  The sample wires @Juke("none") on SessionGreetingController's service field.
#  /service/session/start issues JUKE_SESSION_ID + JUKE_TRACK cookies; with
#  those cookies on /session-greeting, responses come from the per-session
#  ZIP recording. Without cookies, the live service answers.
# =============================================================================

$ErrorActionPreference = "Stop"

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

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$JAR = Join-Path $ScriptDir "target\juke-sample-session-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path $JAR)) {
    Write-Error @"
JAR not found:
  $JAR

Build it first (from the repo root):
  mvn -pl juke-samples/juke-sample-session -am package -DskipTests
"@
    exit 1
}

Write-Host "JAR: $JAR" -ForegroundColor Cyan

# Pre-shipped session ZIPs (juke-sample-ui, juke-sample-ui-2).
# Mirrors the launch documented in juke-cookie-replay.spec.ts.
$JukePath = Join-Path $ScriptDir "src\test\playwright\test-resources"

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  Juke session-cookie server -> http://localhost:8080" -ForegroundColor Yellow
Write-Host "  Replay ZIPs from: $JukePath"                         -ForegroundColor Yellow
Write-Host "    juke-sample-ui.zip   -> Hello, Evan | Hello, Pavel" -ForegroundColor Yellow
Write-Host "    juke-sample-ui-2.zip -> Hello, Evan | Hello, Viane" -ForegroundColor Yellow
Write-Host "  Ctrl+C to stop."                                     -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host ""

java "-Djuke=replay" "-Djuke.path=$JukePath" "-Djuke.zip=juke-sample-ui" -jar $JAR
