param([string]$Adb = "", [string]$Serial = "")
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $Adb) {
    if ($env:ANDROID_HOME) { $Adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" }
    else { $Adb = "adb" }
}
$deviceArgs = @()
if ($Serial) { $deviceArgs = @("-s", $Serial) }
$mainApk = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $projectRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
if (-not (Test-Path -LiteralPath $mainApk) -or -not (Test-Path -LiteralPath $testApk)) {
    throw "First run: .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest"
}
& $Adb @deviceArgs install -r $mainApk
if ($LASTEXITCODE -ne 0) { throw "Game installation failed" }
& $Adb @deviceArgs install -r $testApk
if ($LASTEXITCODE -ne 0) { throw "Test installation failed" }
$output = & $Adb @deviceArgs shell am instrument -w "com.riftartillery.game.test/com.riftartillery.game.SmokeInstrumentation" 2>&1
$output | Write-Output
if ($LASTEXITCODE -ne 0 -or ($output -join "`n") -notmatch "PASS: \d+ Android integration checks") {
    throw "Android integration checks failed"
}
