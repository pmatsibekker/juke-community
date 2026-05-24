# =============================================================================
#  Juke Exception-Flow Demo — Start the server
#
#  Run from any directory:
#    .\juke-samples\juke-sample-exceptions\demo-start-server.ps1
#
#  Or right-click the file -> "Run with PowerShell". Leave this window open
#  (Ctrl+C to stop), then open http://localhost:8080 in your browser:
#    - pick a product, click Buy -> three orders are placed with the OMS
#    - each order confirmation pops up for 5 seconds
#    - click "View test coverage" for the coverage popup
#
#  In a SECOND window run demo-run-playwright.ps1 to drive the full four-run
#  demo automatically (record, replay, replay+delay, replay+exception).
# =============================================================================

$ErrorActionPreference = "Stop"

# -- Locate JDK 25 ------------------------------------------------------------
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

$JAVA_HOME      = Find-Java25
$env:JAVA_HOME  = $JAVA_HOME
$env:Path       = "$JAVA_HOME\bin;$env:Path"

Write-Host "Using Java: $((& java -version 2>&1)[0])" -ForegroundColor Cyan

# -- Locate the JAR -----------------------------------------------------------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$JAR = Join-Path $ScriptDir "target\juke-sample-exceptions-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path $JAR)) {
    Write-Error @"
JAR not found:
  $JAR

Build it first (from the repo root):
  mvn -pl juke-samples/juke-sample-exceptions -am package -Pcoverage -DskipTests

The -Pcoverage profile is REQUIRED so the React bundle is built with
vite-plugin-istanbul instrumentation (otherwise the UI half of the coverage
popup stays empty).
"@
    exit 1
}

Write-Host "JAR: $JAR" -ForegroundColor Cyan

# -- Ensure recording / coverage directories exist ----------------------------
$Home_       = $env:USERPROFILE
$RecordDir   = "$Home_\juke-demo\sample-exceptions"
$ServerCov   = "$Home_\juke-demo\coverage\server"
$UiCov       = "$Home_\juke-demo\coverage\ui"
foreach ($d in @($RecordDir, $ServerCov, $UiCov)) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Force -Path $d | Out-Null
        Write-Host "Created $d" -ForegroundColor Green
    }
}

# -- Coverage args ------------------------------------------------------------
$Agent        = Join-Path $ScriptDir "target\jacoco-agent.jar"
$CoverageArgs = @()
if (Test-Path $Agent) {
    $CoverageArgs = @(
        "-javaagent:$Agent=output=none",
        "-Djuke.coverage.enabled=true",
        "-Djuke.coverage.classes=$(Join-Path $ScriptDir 'target\classes')",
        "-Djuke.coverage.sources=$(Join-Path $ScriptDir 'src\main\java')",
        "-Djuke.coverage.report-dir=$ServerCov",
        "-Djuke.coverage.ui-report-dir=$UiCov",
        "-Djuke.coverage.bundle-name=juke-sample-exceptions"
    )
    $CoverageStatus = "ENABLED -> http://localhost:8080/service/coverage"
} else {
    $CoverageStatus = "off (target\jacoco-agent.jar not found - rebuild with Maven)"
}

# -- Launch -------------------------------------------------------------------
Write-Host ""
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host "  Juke exception-flow demo -> http://localhost:8080"     -ForegroundColor Yellow
Write-Host "  Recordings:   $RecordDir"                              -ForegroundColor Yellow
Write-Host "  Server cov:   $ServerCov"                              -ForegroundColor Yellow
Write-Host "  UI cov:       $UiCov"                                  -ForegroundColor Yellow
Write-Host "  Coverage:     $CoverageStatus"                         -ForegroundColor Yellow
Write-Host "  Ctrl+C to stop."                                       -ForegroundColor Yellow
Write-Host "======================================================" -ForegroundColor Yellow
Write-Host ""

java @CoverageArgs `
     "-Djuke.enabled=true" `
     "-Djuke.path=$RecordDir" `
     "-Djuke.zip=order-demo" `
     -jar $JAR
