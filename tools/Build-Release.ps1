param([switch]$CreateLocalKey)
$ErrorActionPreference="Stop"
$root=Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Prepare-BuildEnvironment.ps1')
$keyDir=Join-Path $root '.private'
$keyFile=Join-Path $keyDir 'rift-upload.p12'
$passwordFile=Join-Path $keyDir 'password.dpapi'
if($CreateLocalKey -and -not (Test-Path -LiteralPath $keyFile)) {
    New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
    $bytes=New-Object byte[] 36
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $password=[Convert]::ToBase64String($bytes)
    ConvertTo-SecureString $password -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $passwordFile
    $temporaryPassword=Join-Path $keyDir 'keytool-password.tmp'
    $javaOptions=$env:JAVA_TOOL_OPTIONS
    try {
        [System.IO.File]::WriteAllText($temporaryPassword,$password,[System.Text.Encoding]::ASCII)
        Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
        & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore $keyFile -storetype PKCS12 -storepass:file $temporaryPassword -keypass:file $temporaryPassword -alias rift-upload -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=RIFT Artillery'
        if($LASTEXITCODE -ne 0) {throw 'Key generation failed'}
    } finally {
        if(Test-Path -LiteralPath $temporaryPassword) {Remove-Item -LiteralPath $temporaryPassword}
        $password=$null
        $env:JAVA_TOOL_OPTIONS=$javaOptions
    }
}
if(-not (Test-Path -LiteralPath $keyFile) -or -not (Test-Path -LiteralPath $passwordFile)) {
    throw 'Use -CreateLocalKey once to create the local upload key. For an existing owner key, configure the RIFT_SIGNING_* environment variables and run Gradle directly.'
}
$secure=(Get-Content -LiteralPath $passwordFile -Raw).Trim() | ConvertTo-SecureString
$credential=[System.Net.NetworkCredential]::new('rift',$secure)
try {
    $env:RIFT_SIGNING_FILE=$keyFile
    $env:RIFT_SIGNING_PASSWORD=$credential.Password
    & (Join-Path $root 'gradlew.bat') --no-daemon :app:assembleRelease :app:bundleRelease :app:lintRelease
    if($LASTEXITCODE -ne 0) {throw 'Release build failed'}
} finally {
    Remove-Item Env:RIFT_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:RIFT_SIGNING_FILE -ErrorAction SilentlyContinue
    $credential=$null
}
