param([string]$PlayerDirectory='')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
if(-not $PlayerDirectory) {$PlayerDirectory=Join-Path $root '.tmp/pc030'}
$playerRoot=(Resolve-Path -LiteralPath $PlayerDirectory).Path
foreach($relative in @('RIFT-Artillery.exe','UnityPlayer.dll','Engine/runtime/bin/java.exe','RIFT-Artillery_Data/StreamingAssets/Engine/rift-sim.jar')) {
    if(-not (Test-Path -LiteralPath (Join-Path $playerRoot $relative))) {throw "Incomplete player: $relative"}
}
$rows=Get-ChildItem -LiteralPath $playerRoot -Recurse -File | Where-Object {$_.Name -ne 'manifest-sha256.txt'} | Sort-Object FullName | ForEach-Object {
    $relative=$_.FullName.Substring($playerRoot.Length).TrimStart([char]92,[char]47).Replace([char]92,[char]47)
    '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower(),$relative
}
$rows | Set-Content (Join-Path $playerRoot 'manifest-sha256.txt')
$archive=Join-Path $root 'dist/RIFT-Artillery-Windows-0.3.0.zip'
& "$env:JAVA_HOME/bin/jar.exe" --create --file $archive --no-manifest -C $playerRoot .
if($LASTEXITCODE -ne 0) {throw 'Windows ZIP creation failed'}
Get-Item -LiteralPath $archive | Select-Object FullName,Length
