param(
    [string]$UnityEditor='C:/Program Files/Unity/Hub/Editor/6000.0.77f1/Editor/Unity.exe',
    [switch]$SkipCoreBuild,
    [switch]$Package
)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Prepare-BuildEnvironment.ps1')
$project=Join-Path $root 'unity'
$temporary=Join-Path $root '.tmp'
$output=Join-Path $temporary 'pc050'
New-Item -ItemType Directory -Force $temporary,$output | Out-Null
if(-not $env:JAVA_HOME) {throw 'Set JAVA_HOME to JDK 17.'}
if(-not (Test-Path -LiteralPath $UnityEditor)) {throw "Unity Editor missing: $UnityEditor"}
if(-not $SkipCoreBuild) {
    & (Join-Path $root 'gradlew.bat') --no-daemon :core:test :desktop-core:test :desktop-core:standaloneJar
    if($LASTEXITCODE -ne 0) {throw 'Shared simulation build failed'}
}
$jar=Join-Path $root 'desktop-core/build/libs/rift-sim.jar'
if(-not (Test-Path -LiteralPath $jar)) {throw 'Run :desktop-core:standaloneJar first.'}
$engineAssets=Join-Path $project 'Assets/StreamingAssets/Engine'
$art=Join-Path $project 'Assets/Resources/Art'
$music=Join-Path $project 'Assets/Resources/Music'
New-Item -ItemType Directory -Force $engineAssets,$art,$music | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $engineAssets 'rift-sim.jar') -Force
Copy-Item (Join-Path $root 'app/src/main/assets/*-atlas.png') -Destination $art -Force
Copy-Item (Join-Path $root 'app/src/main/assets/music/*.ogg') -Destination $music -Force
$env:RIFT_PC_OUTPUT=Join-Path $output 'RIFT-Artillery.exe'
$log=Join-Path $temporary 'unity-build-050.log'
try {
    $process=Start-Process -FilePath $UnityEditor -ArgumentList '-batchmode','-quit','-projectPath',('"'+$project+'"'),'-buildTarget','Win64','-executeMethod','Rift.Editor.BuildWindows.Build','-logFile',('"'+$log+'"') -WindowStyle Hidden -PassThru
    $process.WaitForExit()
    if($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $env:RIFT_PC_OUTPUT)) {throw "Unity build failed; see $log"}
} finally {Remove-Item Env:RIFT_PC_OUTPUT -ErrorAction SilentlyContinue}
$runtime=Join-Path $temporary 'jre030'
if(-not (Test-Path -LiteralPath (Join-Path $runtime 'bin/java.exe'))) {
    & "$env:JAVA_HOME/bin/jlink.exe" --module-path "$env:JAVA_HOME/jmods" --add-modules java.base,java.desktop --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
    if($LASTEXITCODE -ne 0) {throw 'Bundled runtime generation failed'}
}
$playerRuntime=Join-Path $output 'Engine/runtime'
New-Item -ItemType Directory -Force $playerRuntime | Out-Null
Copy-Item (Join-Path $runtime '*') -Destination $playerRuntime -Recurse -Force
@'
RIFT Artillery 0.5.0 for Windows x64
Extract this whole folder before starting RIFT-Artillery.exe.
No Java installation, account or Internet connection is required.
A/D or Left/Right: movement; Space: jump; Q: rope; W/S: rope length or flight; E: ability.
Mouse or Up/Down: aim; wheel or +/-: power; Enter: fire; R: arsenal; Tab: ally; Esc: pause.
Right drag: camera. Shift+wheel: zoom. F11 or Alt+Enter: fullscreen/windowed.
F: interact with nearby world objects. Shop: supply packs and permanent aura colors.
Supply packs are spent only on new combat matches, not on Continue or the rope race.
Music menu: 16 tracks, volume and mute. Russian keyboard layout is supported.
The Unity client runs the same simulation as Android through a bundled local engine.
'@ | Set-Content (Join-Path $output 'READ-ME.txt')
if($Package) {
    & (Join-Path $PSScriptRoot 'Package-Windows.ps1') -PlayerDirectory $output
}
Write-Output "Windows player: $output"
