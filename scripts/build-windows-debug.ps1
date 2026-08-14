[CmdletBinding()]
param(
  [string]$JavaHome = $env:JAVA_HOME,
  [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradleWrapper = Join-Path $projectRoot 'gradlew.bat'
$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
  $JavaHome = 'D:\Program Files\Java\jdk-17'
}

$javaExe = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
  throw "JDK 17 was not found at '$JavaHome'. Pass -JavaHome with a valid Windows JDK path."
}
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
  throw "Gradle wrapper was not found at '$gradleWrapper'."
}

$env:JAVA_HOME = $JavaHome
$tasks = @('--no-daemon', '--no-configuration-cache', '--console=plain')
if (-not $SkipTests) {
  $tasks += ':app:testDebugUnitTest'
}
$tasks += ':app:assembleDebug'

Push-Location $projectRoot
try {
  Write-Host "Windows JDK: $env:JAVA_HOME"
  Write-Host "Gradle tasks: $($tasks -join ' ')"
  & $gradleWrapper @tasks
  if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE."
  }
} finally {
  Pop-Location
}

if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
  throw "Build completed without the expected APK: '$apkPath'."
}

$gitSha = (& git -C $projectRoot rev-parse --short=8 HEAD 2>$null).Trim()
if ([string]::IsNullOrWhiteSpace($gitSha)) { $gitSha = 'nogit' }
$apk = Get-Item -LiteralPath $apkPath
$sha256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash

Write-Host "Build SHA: $gitSha"
Write-Host "APK: $($apk.FullName)"
Write-Host "APK size: $($apk.Length) bytes"
Write-Host "APK SHA-256: $sha256"
