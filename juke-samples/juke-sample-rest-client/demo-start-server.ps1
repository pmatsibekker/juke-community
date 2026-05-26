# =============================================================================
#  Juke Demo - Start the rest-client sample server
#
#  Run from any directory:
#    .\juke-samples\juke-sample-rest-client\demo-start-server.ps1
#
#  Or right-click the file -> "Run with PowerShell"
#
#  Leave this window open. Ctrl+C to stop.
#  Then run demo-run-curl.ps1 in a SECOND window to drive record/replay.
#
#  The sample wires concrete-field @Juke onto two RestTemplate beans
#  (ShippingClient, PricingClient). Global record/replay is driven via the
#  /service/* endpoints; cookie-session replay does not apply to concrete fields.
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
$JAR = Join-Path $ScriptDir "target\juke-sample-rest-client-1.0.0.jar"

if (-not (Test-Path $JAR)) {
    Write-Error @"
JAR not found:
  $JAR

Build it first (from the repo root):
  mvn -pl juke-samples/juke-sample-rest-client -am package -DskipTests
"@
    exit 1
}

Write-Host "JAR: $JAR" -ForegroundColor Cyan

$RecordDir = "$env:USERPROFILE\juke\sample-rest-client"
if (-not (Test-Path $RecordDir)) {
    New-Item -ItemType Directory -Force -Path $RecordDir | Out-Null
    Write-Host "Created recording directory: $RecordDir" -ForegroundColor Green
}

Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  Juke rest-client server -> http://localhost:8080" -ForegroundColor Yellow
Write-Host "  Recordings stored in: $RecordDir"                 -ForegroundColor Yellow
Write-Host "  Open http://localhost:8080 for the banner UI."    -ForegroundColor Yellow
Write-Host "  Ctrl+C to stop."                                  -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host ""

java "-Djuke.enabled=true" -jar $JAR
