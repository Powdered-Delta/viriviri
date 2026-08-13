[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
    [string]$Apk,
    [string]$AdbPath,
    [string]$DeviceSerial,
    [switch]$Launch
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:PackageName = 'com.m0e_n00b.viriviri'
$script:AdbExe = $null

function ConvertTo-CommandText {
    param([object[]]$Output)

    return (($Output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine).Trim()
}

function Write-CommandOutput {
    param([object[]]$Output)

    foreach ($line in $Output) {
        Write-Host $line.ToString()
    }
}

function Get-ApkSha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $getFileHash = Get-Command -Name 'Get-FileHash' -CommandType Cmdlet -ErrorAction SilentlyContinue
    if ($null -ne $getFileHash) {
        return (Get-FileHash -LiteralPath $Path -Algorithm SHA256 -ErrorAction Stop).Hash
    }

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead($Path)
        try {
            return ([System.BitConverter]::ToString($sha256.ComputeHash($stream))).Replace('-', '')
        }
        finally {
            $stream.Dispose()
        }
    }
    finally {
        $sha256.Dispose()
    }
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $output = & $script:AdbExe @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        $details = ConvertTo-CommandText -Output $output
        if ([string]::IsNullOrWhiteSpace($details)) {
            $details = 'No output was returned.'
        }

        throw "adb $($Arguments -join ' ') failed with exit code $exitCode. $details"
    }

    return $output
}

function Resolve-AdbExecutable {
    param([string]$RequestedPath)

    if (-not [string]::IsNullOrWhiteSpace($RequestedPath)) {
        if (-not (Test-Path -LiteralPath $RequestedPath -PathType Leaf)) {
            throw "-AdbPath does not point to an adb executable: $RequestedPath"
        }

        return (Get-Item -LiteralPath $RequestedPath -ErrorAction Stop).FullName
    }

    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($sdkRoot in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if (-not [string]::IsNullOrWhiteSpace($sdkRoot)) {
            $candidates.Add((Join-Path -Path $sdkRoot -ChildPath 'platform-tools\adb.exe'))
        }
    }

    $localAppData = [Environment]::GetFolderPath('LocalApplicationData')
    foreach ($candidate in @(
            (Join-Path -Path $localAppData -ChildPath 'Android\Sdk\platform-tools\adb.exe'),
            (Join-Path -Path $env:USERPROFILE -ChildPath 'AppData\Local\Android\Sdk\platform-tools\adb.exe'),
            'C:\Android\Sdk\platform-tools\adb.exe',
            'C:\Android\sdk\platform-tools\adb.exe',
            'C:\Program Files\Android\Android Studio\platform-tools\adb.exe'
        )) {
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            $candidates.Add($candidate)
        }
    }

    $pathAdb = Get-Command -Name 'adb.exe' -CommandType Application -ErrorAction SilentlyContinue
    if ($null -ne $pathAdb) {
        $candidates.Add($pathAdb.Source)
    }

    $seen = @{}
    foreach ($candidate in $candidates) {
        $key = $candidate.ToLowerInvariant()
        if ($seen.ContainsKey($key)) {
            continue
        }

        $seen[$key] = $true
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Get-Item -LiteralPath $candidate -ErrorAction Stop).FullName
        }
    }

    throw 'Could not find adb.exe. Pass -AdbPath or install Android SDK Platform-Tools and set ANDROID_HOME or ANDROID_SDK_ROOT.'
}

function Get-ConnectedDevice {
    $devicesOutput = Invoke-Adb -Arguments @('devices', '-l')
    $devicesText = ConvertTo-CommandText -Output $devicesOutput

    Write-Host 'ADB devices:'
    if ([string]::IsNullOrWhiteSpace($devicesText)) {
        Write-Host '(no output)'
    }
    else {
        Write-Host $devicesText
    }

    $devices = @()
    foreach ($line in ($devicesText -split "`r?`n")) {
        $trimmedLine = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmedLine) -or
            $trimmedLine -eq 'List of devices attached' -or
            $trimmedLine.StartsWith('*')) {
            continue
        }

        $match = [regex]::Match($trimmedLine, '^(?<Serial>\S+)\s+(?<State>\S+)(?:\s|$)')
        if ($match.Success) {
            $devices += [PSCustomObject]@{
                Serial = $match.Groups['Serial'].Value
                State = $match.Groups['State'].Value
            }
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        $matchingDevices = @($devices | Where-Object { $_.Serial -eq $DeviceSerial })
        if ($matchingDevices.Count -eq 0) {
            throw "-DeviceSerial '$DeviceSerial' was not returned by adb devices."
        }

        $selectedDevice = $matchingDevices[0]
    }
    else {
        if ($devices.Count -eq 0) {
            throw 'No ADB devices were found. Connect and authorize one Meta Quest, then retry.'
        }

        if ($devices.Count -gt 1) {
            $serials = ($devices | ForEach-Object { "$($_.Serial) [$($_.State)]" }) -join ', '
            throw "Multiple ADB devices were found: $serials. Pass -DeviceSerial to select the intended Quest."
        }

        $selectedDevice = $devices[0]
    }

    if ($selectedDevice.State -ne 'device') {
        throw "ADB device '$($selectedDevice.Serial)' is '$($selectedDevice.State)', not 'device'. Unlock and authorize the Quest, then retry."
    }

    return $selectedDevice
}

try {
    if ([string]::IsNullOrWhiteSpace($Apk)) {
        $Apk = Join-Path -Path $PSScriptRoot -ChildPath '..\app\build\outputs\apk\debug\app-debug.apk'
        Write-Host "Using repository default APK: $Apk"
    }

    if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) {
        throw "APK was not found: $Apk"
    }

    $apkFile = Get-Item -LiteralPath $Apk -ErrorAction Stop
    $script:AdbExe = Resolve-AdbExecutable -RequestedPath $AdbPath
    $adbVersion = Invoke-Adb -Arguments @('version')
    $device = Get-ConnectedDevice
    $deviceArguments = @('-s', $device.Serial)
    $apkHash = Get-ApkSha256 -Path $apkFile.FullName

    Write-Host "ADB executable: $script:AdbExe"
    Write-Host "ADB version: $((ConvertTo-CommandText -Output $adbVersion) -split "`r?`n" | Select-Object -First 1)"
    Write-Host "Selected Quest: $($device.Serial)"
    Write-Host "APK path: $($apkFile.FullName)"
    Write-Host "APK size: $($apkFile.Length) bytes"
    Write-Host "APK SHA-256: $apkHash"

    if (-not $PSCmdlet.ShouldProcess("Quest device '$($device.Serial)'", "Install '$($apkFile.Name)' with adb install -r")) {
        Write-Host 'Dry run complete. The APK was not installed.'
        exit 0
    }

    Write-Host 'Installing APK with adb install -r...'
    $installOutput = Invoke-Adb -Arguments ($deviceArguments + @('install', '-r', $apkFile.FullName))
    Write-CommandOutput -Output $installOutput

    $packagePaths = Invoke-Adb -Arguments ($deviceArguments + @('shell', 'pm', 'path', $script:PackageName))
    $pathText = ConvertTo-CommandText -Output $packagePaths
    $pathLines = @($pathText -split "`r?`n" | Where-Object { $_ -match '^package:' })
    if ($pathLines.Count -eq 0) {
        throw "Installation completed, but pm path did not find $script:PackageName."
    }

    $packageInfo = Invoke-Adb -Arguments ($deviceArguments + @('shell', 'dumpsys', 'package', $script:PackageName))
    $packageInfoText = ConvertTo-CommandText -Output $packageInfo
    $versionNameMatch = [regex]::Match($packageInfoText, '(?m)^\s*versionName=(?<Version>.+)$')
    $versionCodeMatch = [regex]::Match($packageInfoText, '(?m)^\s*versionCode=(?<Version>\S+)')
    $versionName = if ($versionNameMatch.Success) { $versionNameMatch.Groups['Version'].Value } else { 'unavailable' }
    $versionCode = if ($versionCodeMatch.Success) { $versionCodeMatch.Groups['Version'].Value } else { 'unavailable' }

    Write-Host "Installed package: $script:PackageName"
    Write-Host "Package version: $versionName (versionCode $versionCode)"
    Write-Host 'Package path(s):'
    Write-CommandOutput -Output $pathLines

    if ($Launch) {
        Write-Host 'Launching the installed package...'
        $launchOutput = Invoke-Adb -Arguments ($deviceArguments + @('shell', 'monkey', '-p', $script:PackageName, '1'))
        Write-CommandOutput -Output $launchOutput
    }

    exit 0
}
catch {
    [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)")
    exit 1
}
