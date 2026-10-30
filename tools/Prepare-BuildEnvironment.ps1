param([switch]$NoTcpCompatibility)
$ErrorActionPreference='Stop'
$buildProjectRoot=Split-Path -Parent $PSScriptRoot
if(-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME/bin/javac.exe")) {throw 'Set JAVA_HOME to JDK 17.'}
if(-not $env:ANDROID_HOME) {
    $buildSdk=Join-Path $env:LOCALAPPDATA 'Android/Sdk'
    if(Test-Path -LiteralPath $buildSdk) {$env:ANDROID_HOME=$buildSdk}
}
if([Environment]::OSVersion.Platform -eq 'Win32NT' -and -not $NoTcpCompatibility) {
    $buildCompat=Join-Path $buildProjectRoot '.tmp/java-compat'
    $buildAgent=Join-Path $buildCompat 'tcp-agent.jar'
    $buildSource=Join-Path $PSScriptRoot 'java/TcpAgent.java'
    New-Item -ItemType Directory -Force $buildCompat | Out-Null
    if(-not (Test-Path -LiteralPath $buildAgent) -or (Get-Item $buildSource).LastWriteTimeUtc -gt (Get-Item $buildAgent).LastWriteTimeUtc) {
        & "$env:JAVA_HOME/bin/javac.exe" -d $buildCompat $buildSource
        if($LASTEXITCODE -ne 0) {throw 'Java compatibility helper failed to compile.'}
        $buildManifest=Join-Path $buildCompat 'manifest.txt'
        [IO.File]::WriteAllText($buildManifest,"Premain-Class: TcpAgent`n")
        & "$env:JAVA_HOME/bin/jar.exe" cfm $buildAgent $buildManifest -C $buildCompat TcpAgent.class -C $buildCompat 'TcpAgent$1.class'
        if($LASTEXITCODE -ne 0) {throw 'Java compatibility helper failed to package.'}
    }
    $buildOption='-javaagent:"'+$buildAgent+'"'
    if($env:JAVA_TOOL_OPTIONS -notlike '*java-compat*tcp-agent.jar*') {$env:JAVA_TOOL_OPTIONS=($env:JAVA_TOOL_OPTIONS+' '+$buildOption).Trim()}
}
