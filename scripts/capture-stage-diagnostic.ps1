#Requires -Version 7.0
[CmdletBinding()]
param(
  [Parameter(Mandatory)][string]$MetavrExe,
  [Parameter(Mandatory)][string]$Device,
  [Parameter(Mandatory)][string]$CandidateApk,
  [ValidateRange(1, 120)][int]$Seconds = 35,
  [ValidateRange(1, 64)][int]$MaxLogMiB = 16,
  [string]$PlatformToolsDirectory = "$env:LOCALAPPDATA\Android\Sdk\platform-tools",
  [string]$OutputDirectory,
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$cli = (Resolve-Path -LiteralPath $MetavrExe).Path
$apk = Get-Item -LiteralPath $CandidateApk
if ($apk.PSIsContainer) { throw 'CandidateApk must name an APK file.' }
if ([IO.Path]::GetExtension($cli) -ne '.exe') {
  throw 'Pass the actual metavr.exe, not npx/cmd; capture must own its process.'
}
$arguments = @('adb', 'logcat', '--device', $Device, '--lines', '1', '--follow',
  '--out-format', 'threadtime', '--format', 'plain', '--filter',
  'ViriViriStage:I', 'CompositorClient:V', '*:S')
if ($DryRun) {
  [pscustomobject]@{ executable = $cli; arguments = $arguments;
    seconds = $Seconds; maxLogBytes = $MaxLogMiB * 1MB;
    candidateSha256 = (Get-FileHash -LiteralPath $apk.FullName).Hash;
    deviceActions = 'none (dry run)' } | ConvertTo-Json -Depth 4
  return
}

# This helper never enables logging, launches apps, clears buffers or sends input.
# Enable debug.oculus.logLayers in a separately authorized session first.
$properties = Get-Content -LiteralPath (Join-Path $PlatformToolsDirectory 'source.properties') -Raw
$revisionMatch = [regex]::Match($properties, '(?m)^Pkg\.Revision\s*=\s*([\d.]+)\s*$')
if (-not $revisionMatch.Success) { throw 'Cannot read platform-tools revision.' }
$installedRevision = [version]$revisionMatch.Groups[1].Value
$repository = Invoke-RestMethod 'https://dl.google.com/android/repository/repository2-1.xml'
$stablePackage = $repository.SelectSingleNode("//*[local-name()='remotePackage' and @path='platform-tools' and (not(*[local-name()='channelRef']) or *[local-name()='channelRef']/@ref='channel-0')]")
if ($null -eq $stablePackage) { throw 'Cannot find stable platform-tools in official metadata.' }
$latest = $stablePackage.SelectSingleNode("*[local-name()='revision']")
if ($null -eq $latest -or $null -ne $latest.SelectSingleNode("*[local-name()='preview']")) {
  throw 'Cannot verify stable platform-tools against official Google metadata.'
}
$latestParts = foreach ($part in @('major', 'minor', 'micro')) {
  $value = $latest.SelectSingleNode("*[local-name()='$part']")
  if ($null -eq $value) { 0 } else { [int]$value.InnerText }
}
$latestRevision = [version]($latestParts -join '.')
if ($installedRevision -lt $latestRevision) {
  throw "Update platform-tools before capture: installed=$installedRevision latest=$latestRevision."
}
$doctor = & $cli doctor --json 2>&1
if ($LASTEXITCODE -ne 0) { throw "metavr doctor failed: $doctor" }
$property = & $cli shell --device $Device --format plain getprop debug.oculus.logLayers 2>&1
if ($LASTEXITCODE -ne 0) { throw "Cannot read logLayers: $property" }
if (($property -join "`n").Trim() -ne '1') {
  throw 'logLayers is not enabled. This read-only helper cannot change it.'
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
  $OutputDirectory = Join-Path $projectRoot ('temp\stage-diagnostic-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
if (Test-Path -LiteralPath $OutputDirectory) {
  throw 'OutputDirectory must be new so earlier evidence cannot be overwritten.'
}
$null = New-Item -ItemType Directory -Path $OutputDirectory
$metadataPath = Join-Path $OutputDirectory 'capture.json'
$metadata = [ordered]@{
  candidateApk = $apk.FullName
  candidateSha256 = (Get-FileHash -LiteralPath $apk.FullName).Hash
  installedApkVerification = 'not performed; candidate hash is host-side only'
  platformTools = "$installedRevision"
  device = $Device
  startedUtc = [DateTimeOffset]::UtcNow.ToString('o')
  arguments = $arguments
  requestedSeconds = $Seconds
  maxLogBytes = $MaxLogMiB * 1MB
  preflight = $doctor -join "`n"
  status = 'starting'
}
$metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $metadataPath -Encoding utf8NoBOM
$startInfo = [Diagnostics.ProcessStartInfo]::new($cli)
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
foreach ($argument in $arguments) { $startInfo.ArgumentList.Add($argument) }
$process = [Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$streams = @()
$started = $false
$byteLimitReached = $false
$bytes = 0L
$watch = [Diagnostics.Stopwatch]::StartNew()
try {
  if (-not $process.Start()) { throw 'Cannot start metavr capture.' }
  $started = $true
  foreach ($entry in @(
    @{ reader = $process.StandardOutput.BaseStream; file = 'layers-and-stage.log' },
    @{ reader = $process.StandardError.BaseStream; file = 'metavr.stderr.log' }
  )) {
    $buffer = [byte[]]::new(4096)
    $streams += @{ reader = $entry.reader; buffer = $buffer;
      pending = $entry.reader.ReadAsync($buffer, 0, $buffer.Length);
      writer = [IO.File]::Open((Join-Path $OutputDirectory $entry.file),
        [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read);
      done = $false }
  }
  $metadata.status = 'duration-limit'
  while ($watch.Elapsed.TotalSeconds -lt $Seconds -and $bytes -lt $metadata.maxLogBytes) {
    foreach ($stream in $streams) {
      if ($stream.done -or -not $stream.pending.IsCompleted) { continue }
      $count = $stream.pending.GetAwaiter().GetResult()
      if ($count -eq 0) { $stream.done = $true; continue }
      # Retain original bytes, capped across stdout and stderr together.
      $retained = [int][Math]::Min($count, $metadata.maxLogBytes - $bytes)
      $stream.writer.Write($stream.buffer, 0, $retained)
      $bytes += $retained
      if ($bytes -ge $metadata.maxLogBytes) { $byteLimitReached = $true; break }
      $stream.pending = $stream.reader.ReadAsync($stream.buffer, 0, $stream.buffer.Length)
    }
    if (@($streams | Where-Object { -not $_.done }).Count -eq 0) {
      $metadata.status = 'stream-ended'
      break
    }
    Start-Sleep -Milliseconds 20
  }
  if ($byteLimitReached) { $metadata.status = 'byte-limit' }
} catch {
  $metadata.status = 'failed'
  $metadata.error = $_.Exception.Message
  throw
} finally {
  if ($started) {
    try {
      if (-not $process.HasExited) {
        $process.Kill($true)
        if (-not $process.WaitForExit(5000)) {
          $metadata.terminationError = 'Owned process did not exit within 5 seconds.'
          $metadata.status = 'termination-failed'
        }
      } else {
        $metadata.exitCode = $process.ExitCode
        if ($process.ExitCode -ne 0) { $metadata.status = 'process-failed' }
      }
    } catch {
      $metadata.terminationError = $_.Exception.Message
      $metadata.status = 'termination-failed'
    }
  }
  foreach ($stream in $streams) { $stream.writer.Dispose() }
  $metadata.finishedUtc = [DateTimeOffset]::UtcNow.ToString('o')
  $metadata.retainedBytes = $bytes
  $metadata.coverage = 'bounded sample; initial history, OS drops and final stream tail may be absent; byte cap may cut a UTF-8 character'
  $metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $metadataPath -Encoding utf8NoBOM
  $process.Dispose()
}
if ($metadata.status -in @('process-failed', 'termination-failed')) {
  throw "Capture did not complete cleanly ($($metadata.status)); inspect $metadataPath."
}
Write-Host "Capture: $OutputDirectory ($($metadata.status), $bytes bytes)"
Write-Host 'This is evidence only; no compositor pixels or installed APK identity are certified.'
