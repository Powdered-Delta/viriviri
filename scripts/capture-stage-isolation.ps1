#Requires -Version 7.0
[CmdletBinding()]
param(
  [switch]$Run,
  [switch]$Restore,
  [string]$SessionDirectory,
  [string]$MetavrExe = 'C:\Users\N00b\.metavr\npm\binaries\@meta-quest__metavr-win32-x64\1.8.0.17.10\metavr.exe',
  [string]$Device = '192.168.0.234:5555',
  [ValidateRange(45,1800)][int]$MaxSeconds = 600
)
$ErrorActionPreference = 'Stop'
if ($Run -and $Restore) { throw 'Choose Run or Restore.' }
if (-not $Run -and -not $Restore) {
  Write-Output 'No device actions. Start: .\scripts\capture-stage-isolation.ps1 -Run; restore: -Restore -SessionDirectory <saved directory>'
  return
}
$cli = (Resolve-Path -LiteralPath $MetavrExe).Path
$script:stopRequested = $false
# Read the explicit response terminator without waiting for CLI telemetry flush.
# All device commands are finite; killing this owned CLI never kills an app.
function Invoke-Quest([string]$Command, [int]$TimeoutSeconds = 30) {
  $info = [Diagnostics.ProcessStartInfo]::new($cli)
  $info.UseShellExecute = $false
  $info.CreateNoWindow = $true
  $info.RedirectStandardOutput = $true
  $info.RedirectStandardError = $true
  foreach ($arg in @('shell','--device',$Device,'--format','plain',($Command + "`necho __HOST_DONE__"))) { $info.ArgumentList.Add($arg) }
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $info
  $lines = [Collections.Generic.List[string]]::new()
  try {
    if (-not $process.Start()) { throw 'Cannot start metavr.' }
    $stderr = $process.StandardError.ReadToEndAsync()
    $pending = $process.StandardOutput.ReadLineAsync()
    $timer = [Diagnostics.Stopwatch]::StartNew()
    while ($timer.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
      if (-not [Console]::IsInputRedirected -and [Console]::KeyAvailable) {
        if ([Console]::ReadKey($true).Key -eq [ConsoleKey]::Enter) { $script:stopRequested = $true }
      }
      if (-not $pending.IsCompleted) { Start-Sleep -Milliseconds 50; continue }
      $line = $pending.GetAwaiter().GetResult()
      if ($null -eq $line) { break }
      if ($line.Trim() -eq '__HOST_DONE__') { return $lines -join "`n" }
      $lines.Add($line)
      $pending = $process.StandardOutput.ReadLineAsync()
    }
    throw "Incomplete metavr response (timeout/device error): $($lines -join "`n")"
  } finally {
    if (-not $process.HasExited) { $process.Kill($true); $null = $process.WaitForExit(3000) }
    $process.Dispose()
  }
}
function Save-Text([string]$Name, [string]$Text) {
  [IO.File]::WriteAllText((Join-Path $SessionDirectory $Name), $Text, [Text.UTF8Encoding]::new($false))
}
function Read-Field([string]$Text, [string]$Name) {
  $match = [regex]::Match($Text, "(?ms)^__$($Name)_BEGIN__\r?\n(.*?)^__$($Name)_END__\r?$")
  if (-not $match.Success) { throw "Missing field $Name" }
  $match.Groups[1].Value.TrimEnd("`r", "`n")
}
# One saved shell file is executed directly: no nested shell quoting of its body.
$worker = @'
#!/system/bin/sh
dir='__REMOTE__'
owner=/data/local/tmp/viriviri-stage-isolation.lock
original_debug='__DEBUG__'
original_skipped='__SKIPPED__'
package=com.m0e_n00b.viriviri
stamp() { date -u '+%Y-%m-%dT%H:%M:%SZ'; }
event() { echo "$1 utc=$(stamp) epoch=$(date +%s) $2"; }
finish() {
  [ -s "$dir/result" ] || printf '%s\n' "$1" > "$dir/result"
  printf '%s\n' "$1" > "$dir/status"
  event WORKER_RESULT "result=$(cat "$dir/result") generation=$generation segment=$segment mask=$mask projection=$projection"
}
restore() {
  mkdir "$dir/restore.lock" 2>/dev/null || return 1
  # A completed/stale session must never overwrite a newer owner's properties.
  if [ "$(cat "$owner/token" 2>/dev/null)" != "$dir" ]; then
    rmdir "$dir/restore.lock"
    return 0
  fi
  rm -f "$dir/active"
  logger_pid=$(cat "$dir/logger.pid" 2>/dev/null)
  case "$logger_pid" in ''|*[!0-9]*) ;; *)
    if tr '\000' ' ' < "/proc/$logger_pid/cmdline" 2>/dev/null | grep -F "$dir/stage-operation.log" >/dev/null; then
      kill "$logger_pid" 2>/dev/null || true
    fi ;;
  esac
  if [ -f "$dir/mutated" ]; then
    setprop debug.oculus.skippedLayers "$original_skipped"
    setprop debug.oculus.sysPropDebug "$original_debug"
    debug=$(getprop debug.oculus.sysPropDebug)
    skipped=$(getprop debug.oculus.skippedLayers)
    event RESTORATION "debug=[$debug] skipped=[$skipped]"
    restored=false
    [ "$debug" = "$original_debug" ] && [ "$skipped" = "$original_skipped" ] && restored=true
    printf '{"restored":%s,"debug":"%s","skipped":"%s","deviceUtc":"%s","epoch":%s}\n' "$restored" "$debug" "$skipped" "$(stamp)" "$(date +%s)" > "$dir/restoration.json"
    if [ "$debug" != "$original_debug" ] || [ "$skipped" != "$original_skipped" ]; then
      echo RESTORE_MISMATCH > "$dir/restore-status"
      rmdir "$dir/restore.lock"
      return 1
    fi
    rm -f "$dir/mutated"
  fi
  # Only the owner token may release the global lock.
  if [ "$(cat "$owner/token" 2>/dev/null)" = "$dir" ]; then
    rm -f "$owner/token"; rmdir "$owner"
  fi
  echo RESTORED > "$dir/restore-status"
  rmdir "$dir/restore.lock"
}
if [ "$1" = restore ]; then
  # Invalidate the lease, terminate only this saved worker, then restore.
  touch "$dir/stop"
  rm -f "$dir/active"
  worker_pid=$(cat "$dir/worker.pid" 2>/dev/null)
  case "$worker_pid" in ''|*[!0-9]*) ;; *)
    if tr '\000' ' ' < "/proc/$worker_pid/cmdline" 2>/dev/null | grep -F "$dir/worker.sh" >/dev/null; then
      kill -TERM "$worker_pid" 2>/dev/null || true
    fi ;;
  esac
  # A pending setprop child must finish before any external restoration/release.
  n=0
  while [ -n "$worker_pid" ] && [ -r "/proc/$worker_pid/cmdline" ] &&
    tr '\000' ' ' < "/proc/$worker_pid/cmdline" 2>/dev/null | grep -F "$dir/worker.sh" >/dev/null; do
    [ "$n" -lt 20 ] || { event RESTORE_WORKER_STILL_RUNNING ""; exit 1; }
    sleep 1; n=$((n+1))
  done
  # A concurrent EXIT cleanup holds the lock only during property restoration.
  n=0
  while [ -d "$dir/restore.lock" ] && [ "$n" -lt 10 ]; do sleep 1; n=$((n+1)); done
  restore
  exit $?
fi
make_plan() {
  awk 'BEGIN { n=0 } /^Layer / {
    if ($2 != (n ":") || NF != 3) bad=1
    t=$3
    if (t == "PROJECTION") { projection++; idx=n }
    else if (t == "CYLINDER") cylinder++
    else if (t != "QUAD") bad=1
    mask=mask (t == "PROJECTION" ? "1" : "0")
    stable=stable t ","
    n++
  } END {
    if (bad || n < 2 || projection != 1 || cylinder > 1) exit 3
    print idx "|" mask "|" stable
  }'
}
parse_plan() {
  # mksh treats an unescaped | in ${value#pattern} as a pattern alternative.
  # Validate the complete contract independently before using extracted values.
  printf '%s\n' "$1" | awk '
    NR != 1 { bad=1 }
    NR == 1 {
      if (split($0, fields, "[|]") != 3 || fields[1] !~ /^(0|[1-9][0-9]*)$/ || fields[2] !~ /^[01]+$/) bad=1
      count=split(fields[3], types, ",")
      if (count < 3 || types[count] != "") bad=1
      for (i=1; i<count; i++) {
        if (types[i] == "PROJECTION") { projections++; projection=i-1; expected=expected "1" }
        else if (types[i] == "QUAD") expected=expected "0"
        else if (types[i] == "CYLINDER") expected=expected "0"
        else bad=1
      }
      if (projections != 1 || fields[1] != projection || fields[2] != expected) bad=1
    }
    END { if (NR != 1 || bad) exit 3 }
  ' || return 3
  projection=${1%%\|*}
  rest=${1#*\|}
  mask=${rest%%\|*}
  signature=${rest#*\|}
}
cleanup() {
  code=$?
  if [ ! -s "$dir/result" ]; then
    if [ -n "$end" ] && [ "$(date +%s)" -ge "$end" ]; then finish DEADLINE
    elif [ -f "$dir/stop" ]; then finish MANUAL_STOP
    else finish "ABORTED_$code"
    fi
  fi
  event INTERVAL_END "result=$(cat "$dir/result")"
  restore
}
publish() {
  printf '%s\n' "$1" > "$dir/status.next"
  mv "$dir/status.next" "$dir/status"
}
live_guard() {
  [ ! -f "$dir/stop" ] || { finish MANUAL_STOP; return 1; }
  [ -f "$dir/active" ] || return 1
  [ "$(date +%s)" -lt "$end" ] || { finish DEADLINE; return 1; }
  [ "$(pidof "$package")" = "$app_pid" ] || { finish PROCESS_CHANGED; return 1; }
  [ "$(cat "$owner/token" 2>/dev/null)" = "$dir" ] || { finish OWNERSHIP_LOST; return 1; }
  [ "$(getprop debug.oculus.sysPropDebug)" = 1 ] || { finish CONFIRMATION_LOST; return 1; }
  [ "$(getprop debug.oculus.skippedLayers)" = "$mask" ] || { finish CONFIRMATION_LOST; return 1; }
  return 0
}
fresh_plan() {
  snapshot=$(timeout 5 dumpsys DumpsysProxy VrRuntime layerInfoOnly) || { finish SNAPSHOT_TIMEOUT; return 1; }
  printf '%s\n' "$snapshot" >> "$dir/snapshots.log"
  plan=$(printf '%s\n' "$snapshot" | make_plan) || { finish TOPOLOGY_UNSTABLE; return 1; }
  parse_plan "$plan" || { finish TOPOLOGY_UNSTABLE; return 1; }
  event SNAPSHOT "topology=$signature mask=$mask projection=$projection generation=$generation segment=$segment"
}
is_projection_hidden() {
  printf '%s\n' "$snapshot" | awk -v idx="$projection" '/^Layer / { p=($0 == "Layer " idx ": PROJECTION") } p && /IsVisible: Not Visible/ { print "yes" }'
}
rebind() {
  observed_plan=$1
  old_plan=$bound_plan
  old_generation=$generation
  old_mask=$mask; old_projection=$projection; old_signature=$signature
  rebind_count=$((rebind_count+1))
  event GAP_START "old_plan=[$old_plan] new_plan=[$observed_plan] old_generation=$old_generation reason=TOPOLOGY_CHANGED gap_unusable=true"
  printf 'GAP %s %s %s %s %s %s %s %s %s\n' "$(stamp)" "$(date +%s)" "$old_generation" "$segment" "$end" "$mask" "$projection" "$rebind_count" "TOPOLOGY_CHANGED" >> "$dir/gaps.log"
  publish "GAP $old_generation $segment $end $mask $projection TOPOLOGY_CHANGED"
  # Invalidate the old index before deriving or applying a replacement mask.
  [ "$(getprop debug.oculus.sysPropDebug)" = 1 ] &&
    [ "$(getprop debug.oculus.skippedLayers)" = "$mask" ] || { finish CONFIRMATION_LOST; return 1; }
  setprop debug.oculus.skippedLayers "$original_skipped" || { finish REBIND_FAILED; return 1; }
  [ "$(getprop debug.oculus.skippedLayers)" = "$original_skipped" ] || { finish REBIND_FAILED; return 1; }
  [ "$rebind_count" -le 8 ] || { finish REBIND_EXHAUSTED; return 1; }
  [ "$(date +%s)" -lt "$end" ] && [ ! -f "$dir/stop" ] && [ -f "$dir/active" ] || return 1
  fresh_plan || return 1
  [ "$plan" = "$observed_plan" ] || { finish TOPOLOGY_UNSTABLE; return 1; }
  next_plan=$plan; next_projection=$projection; next_mask=$mask; next_signature=$signature
  mask=$old_mask; projection=$old_projection; signature=$old_signature
  [ "$(pidof "$package")" = "$app_pid" ] && [ "$(cat "$owner/token" 2>/dev/null)" = "$dir" ] &&
    [ "$(getprop debug.oculus.sysPropDebug)" = 1 ] &&
    [ "$(getprop debug.oculus.skippedLayers)" = "$original_skipped" ] || { finish CONFIRMATION_LOST; return 1; }
  [ "$(date +%s)" -lt "$end" ] && [ ! -f "$dir/stop" ] && [ -f "$dir/active" ] || return 1
  event REBIND_PLAN "plan=[$next_plan] projection=$next_projection mask=$next_mask old_generation=$old_generation next_generation=$((old_generation+1))"
  [ "$next_plan" = "$observed_plan" ] || { finish TOPOLOGY_UNSTABLE; return 1; }
  [ "$(pidof "$package")" = "$app_pid" ] && [ "$(cat "$owner/token" 2>/dev/null)" = "$dir" ] &&
    [ "$(getprop debug.oculus.sysPropDebug)" = 1 ] &&
    [ "$(getprop debug.oculus.skippedLayers)" = "$original_skipped" ] &&
    [ ! -f "$dir/stop" ] && [ -f "$dir/active" ] && [ "$(date +%s)" -lt "$end" ] || { finish CONFIRMATION_LOST; return 1; }
  setprop debug.oculus.skippedLayers "$next_mask" || { finish REBIND_FAILED; return 1; }
  [ "$(getprop debug.oculus.skippedLayers)" = "$next_mask" ] || { finish REBIND_FAILED; return 1; }
  mask=$next_mask; projection=$next_projection; signature=$next_signature
  generation=$((old_generation+1)); segment="segment-$generation"; bound_plan=$plan
  event REBIND_APPLIED "topology=$signature mask=$mask projection=$projection generation=$generation segment=$segment"
  confirmations=0
  while [ "$confirmations" -lt 3 ]; do
    [ "$(date +%s)" -lt "$end" ] && [ ! -f "$dir/stop" ] && [ -f "$dir/active" ] || return 1
    fresh_plan || return 1
    [ "$plan" = "$bound_plan" ] || { finish TOPOLOGY_UNSTABLE; return 1; }
    live_guard || { [ -s "$dir/result" ] || finish CONFIRMATION_LOST; return 1; }
    runtime_mask=$(printf '%s\n' "$snapshot" | sed -n 's/^SkippedLayerMask: *//p' | tr -d '\r')
    hidden=$(is_projection_hidden)
    event PROPERTY_READBACK "expected=$mask runtime=[$runtime_mask] debug=[$(getprop debug.oculus.sysPropDebug)] skipped=[$(getprop debug.oculus.skippedLayers)] hidden=$hidden topology=$signature generation=$generation segment=$segment"
    [ "$hidden" = yes ] && [ "$runtime_mask" = "$mask" ] &&
      [ "$(getprop debug.oculus.sysPropDebug)" = 1 ] && [ "$(getprop debug.oculus.skippedLayers)" = "$mask" ] || { finish CONFIRMATION_LOST; return 1; }
    confirmations=$((confirmations+1))
    [ "$confirmations" -lt 3 ] && sleep 1
  done
  event GAP_END "generation=$generation segment=$segment mask=$mask projection=$projection deadline=$end"
  publish "READY $generation $segment $mask $projection $end $(date +%s)"
  event ISOLATION_CONFIRMED "mask=$mask projection=$projection generation=$generation segment=$segment"
  return 0
}
echo $$ > "$dir/worker.pid"
[ ! -f "$dir/stop" ] || { finish MANUAL_STOP; exit 0; }
mkdir "$owner" 2>/dev/null || { finish OWNER_BUSY; exit 3; }
printf '%s\n' "$dir" > "$owner/token"
trap cleanup EXIT
trap 'exit 130' INT TERM HUP
app_pid=$(pidof "$package")
[ "$app_pid" = '__PID__' ] || { finish PROCESS_CHANGED; exit 3; }
[ "$(getprop debug.oculus.sysPropDebug)" = "$original_debug" ] &&
  [ "$(getprop debug.oculus.skippedLayers)" = "$original_skipped" ] || {
    finish PROPERTIES_CHANGED_BEFORE_WRITE; exit 3;
  }
snapshot=$(timeout 5 dumpsys DumpsysProxy VrRuntime layerInfoOnly) || { finish SNAPSHOT_TIMEOUT; exit 4; }
plan=$(printf '%s\n' "$snapshot" | make_plan) || { finish TOPOLOGY_INVALID; exit 3; }
printf '%s\n' "$snapshot" > "$dir/before.txt"
event ACTIVATION_PLAN "raw=[$plan]"
parse_plan "$plan" || { finish PLAN_INVALID; exit 3; }
event VALIDATED_PLAN "projection=$projection mask=$mask signature=$signature"
echo "$mask" > "$dir/mask"
echo "$projection" > "$dir/projection"
start=$(date +%s); end=$((start+__MAX__))
echo "$end" > "$dir/deadline"
touch "$dir/active"
generation=1; segment=segment-1; rebind_count=0; ever_ready=0
bound_plan=$plan
publish "PENDING 1 segment-1 $mask $projection $end"
# Independent deadline cleanup survives host terminal loss and a stalled dumpsys.
( sleep __MAX__; [ -f "$dir/active" ] && sh "$dir/worker.sh" restore ) </dev/null >> "$dir/watchdog.log" 2>&1 &
event MUTATION_START "pid=$app_pid mask=$mask projection=$projection generation=$generation segment=$segment deadline=$end"
logcat -T 1 -f "$dir/stage-operation.log" -r 4096 -n 2 -v threadtime -s ViriViriStage:I ViriViriCurve:V CompositorClient:V '*:S' &
echo $! > "$dir/logger.pid"
touch "$dir/mutated"
[ ! -f "$dir/stop" ] && [ -f "$dir/active" ] && [ "$(date +%s)" -lt "$end" ] || exit 124
fresh_before_write=$(timeout 5 dumpsys DumpsysProxy VrRuntime layerInfoOnly) || { finish SNAPSHOT_TIMEOUT; exit 4; }
fresh_plan_before_write=$(printf '%s\n' "$fresh_before_write" | make_plan) || { finish TOPOLOGY_UNSTABLE; exit 3; }
[ "$fresh_plan_before_write" = "$plan" ] && [ "$(pidof "$package")" = "$app_pid" ] &&
  [ "$(cat "$owner/token" 2>/dev/null)" = "$dir" ] &&
  [ "$(getprop debug.oculus.sysPropDebug)" = "$original_debug" ] &&
  [ "$(getprop debug.oculus.skippedLayers)" = "$original_skipped" ] &&
  [ ! -f "$dir/stop" ] && [ "$(date +%s)" -lt "$end" ] || { finish VALIDATION_LOST_BEFORE_WRITE; exit 3; }
setprop debug.oculus.sysPropDebug 1 || { finish PROPERTY_WRITE_FAILED; exit 4; }
[ -f "$dir/active" ] && [ "$(date +%s)" -lt "$end" ] || exit 124
setprop debug.oculus.skippedLayers "$mask" || { finish PROPERTY_WRITE_FAILED; exit 4; }
confirmed=0
while [ -f "$dir/active" ] && [ ! -f "$dir/stop" ] && [ "$(date +%s)" -lt "$end" ]; do
  snapshot=$(timeout 5 dumpsys DumpsysProxy VrRuntime layerInfoOnly) || { finish SNAPSHOT_TIMEOUT; exit 4; }
  printf '%s\n' "$snapshot" >> "$dir/snapshots.log"
  event SNAPSHOT "topology=$signature mask=$mask projection=$projection generation=$generation segment=$segment"
  current=$(printf '%s\n' "$snapshot" | make_plan) || { finish TOPOLOGY_UNSTABLE; exit 3; }
  previous_bound=$bound_plan; previous_mask=$mask; previous_projection=$projection; previous_signature=$signature
  parse_plan "$current" || { finish TOPOLOGY_UNSTABLE; exit 3; }
  if [ "$current" != "$bound_plan" ]; then
    bound_plan=$previous_bound; mask=$previous_mask; projection=$previous_projection; signature=$previous_signature
    rebind "$current" || exit 4
    confirmed=3; ever_ready=1
    sleep 1
    continue
  fi
  live_guard || { [ -s "$dir/result" ] || finish CONFIRMATION_LOST; exit 4; }
  hidden=$(is_projection_hidden)
  runtime_mask=$(printf '%s\n' "$snapshot" | sed -n 's/^SkippedLayerMask: *//p' | tr -d '\r')
  event PROPERTY_READBACK "expected=$mask runtime=[$runtime_mask] debug=[$(getprop debug.oculus.sysPropDebug)] skipped=[$(getprop debug.oculus.skippedLayers)] hidden=[$hidden] topology=$signature generation=$generation segment=$segment"
  if [ "$hidden" = yes ] && [ "$runtime_mask" = "$mask" ] &&
     [ "$(getprop debug.oculus.sysPropDebug)" = 1 ] && [ "$(getprop debug.oculus.skippedLayers)" = "$mask" ]; then
    confirmed=$((confirmed+1))
    if [ "$confirmed" -ge 3 ]; then
      if [ "$ever_ready" = 0 ]; then event ISOLATION_CONFIRMED "mask=$mask projection=$projection generation=$generation segment=$segment"; ever_ready=1; fi
      publish "READY $generation $segment $mask $projection $end $(date +%s)"
    else publish "PENDING $generation $segment $mask $projection $end"; fi
  else
    [ "$confirmed" = 0 ] || { finish CONFIRMATION_LOST; exit 4; }
    confirmed=0
    publish "PENDING $generation $segment $mask $projection $end"
    [ "$(date +%s)" -lt "$((start+30))" ] || { finish NEVER_CONFIRMED; exit 4; }
  fi
  sleep 1
done
if [ "$(date +%s)" -ge "$end" ]; then finish DEADLINE; else finish MANUAL_STOP; fi
'@

if ($Restore) {
  if (-not $SessionDirectory) { throw 'Restore requires -SessionDirectory.' }
  $SessionDirectory = (Resolve-Path -LiteralPath $SessionDirectory).Path
  $state = Get-Content -LiteralPath (Join-Path $SessionDirectory 'session.json') -Raw | ConvertFrom-Json
  $Device = $state.device
  $remote = $state.remote
  if ($remote -notmatch '^/data/local/tmp/viriviri-isolation-[a-f0-9]{32}$') { throw 'Invalid saved remote path.' }
} else {
  if ([Console]::IsInputRedirected) { throw 'Run in an interactive PowerShell terminal (Enter stops the experiment).' }
  if (-not $SessionDirectory) { $SessionDirectory = Join-Path $PSScriptRoot ('..\temp\stage-isolation-' + (Get-Date -Format yyyyMMdd-HHmmss)) }
  if (Test-Path -LiteralPath $SessionDirectory) { throw 'SessionDirectory must be new.' }
  $SessionDirectory = (New-Item -ItemType Directory -Path $SessionDirectory).FullName
  $remote = '/data/local/tmp/viriviri-isolation-' + [Guid]::NewGuid().ToString('N')
  # Read-only tool checks. The platform-tools version must be current per project policy.
  $doctor = & $cli doctor --json 2>&1
  Save-Text 'doctor.txt' ($doctor -join "`n")
  if ($LASTEXITCODE -ne 0) { throw 'metavr doctor failed; inspect doctor.txt before retrying.' }
  $before = Invoke-Quest @'
for tool in nohup base64 sh awk timeout; do command -v "$tool" >/dev/null || exit 3; done
echo __PID_BEGIN__; pidof com.m0e_n00b.viriviri; echo __PID_END__
echo __DEBUG_BEGIN__; getprop debug.oculus.sysPropDebug; echo __DEBUG_END__
echo __SKIPPED_BEGIN__; getprop debug.oculus.skippedLayers; echo __SKIPPED_END__
echo __TOOLS_OK__
'@
  Save-Text 'preflight.txt' $before
  $appPid = Read-Field $before 'PID'
  $debug = Read-Field $before 'DEBUG'
  $skipped = Read-Field $before 'SKIPPED'
  if ($before -notmatch '__TOOLS_OK__' -or $appPid -notmatch '^\d+$' -or $debug -notmatch '^[01]?$' -or $skipped -notmatch '^0*$') { throw 'Preflight rejected (app/tool/property state); no isolation started.' }
  $state = @{device=$Device;remote=$remote;debug=$debug;skipped=$skipped;appPid=$appPid;maxSeconds=$MaxSeconds}
  Save-Text 'session.json' ($state | ConvertTo-Json)
  $worker = $worker.Replace('__REMOTE__',$remote).Replace('__DEBUG__',$debug).Replace('__SKIPPED__',$skipped).Replace('__PID__',$appPid).Replace('__MAX__',"$MaxSeconds").Replace("`r`n","`n")
  Save-Text 'worker.sh' $worker
}

$cleanupRequired = $Restore
$workerResult = $null
try {
  if ($Run) {
    if ($script:stopRequested) { throw 'Cancelled before start.' }
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($worker))
    # Set cleanup intent before submission: a lost response may still have started it.
    $cleanupRequired = $true
    $started = Invoke-Quest "mkdir '$remote' && printf '%s' '$encoded' | base64 -d > '$remote/worker.sh' || exit 3; nohup sh '$remote/worker.sh' </dev/null >'$remote/worker.log' 2>&1 & echo __WORKER_SUBMITTED__"
    if ($started -notmatch '__WORKER_SUBMITTED__') { throw 'Worker submission was not acknowledged.' }
    Write-Host "采集目录：$SessionDirectory"
    Write-Host '等待实时隐藏确认；显示「已生效」后再操作。随时按 Enter 结束并恢复。'
    $everReady = $false
    $currentReady = $false
    $currentGeneration = 0
    $hostTimer = [Diagnostics.Stopwatch]::StartNew()
    while (-not $script:stopRequested -and $hostTimer.Elapsed.TotalSeconds -lt ($MaxSeconds + 30)) {
      $status = Invoke-Quest "echo __STATUS_BEGIN__; cat '$remote/status' 2>/dev/null; echo __STATUS_END__; echo __GAPS_BEGIN__; cat '$remote/gaps.log' 2>/dev/null; echo __GAPS_END__; echo __RESULT_BEGIN__; cat '$remote/result' 2>/dev/null; echo __RESULT_END__; echo __NOW_BEGIN__; date +%s; echo __NOW_END__; echo __DEBUG_BEGIN__; getprop debug.oculus.sysPropDebug; echo __DEBUG_END__; echo __SKIPPED_BEGIN__; getprop debug.oculus.skippedLayers; echo __SKIPPED_END__"
      Save-Text 'last-status.txt' $status
      $gapText = Read-Field $status 'GAPS'
      foreach ($gapLine in ($gapText -split "`r?`n" | Where-Object { $_ })) {
        if ($gapLine -match '^GAP (\S+) (\d+) (\d+) (segment-\d+) (\d+) ([01]+) (\d+) (\d+) ([A-Z_]+)$') {
          $gapKey = "$($Matches[3]):$($Matches[8])"
          if (-not $script:seenGaps) { $script:seenGaps = [Collections.Generic.HashSet[string]]::new() }
          if ($script:seenGaps.Add($gapKey)) {
            $currentReady = $false
            Add-Content -LiteralPath (Join-Path $SessionDirectory 'intervals.jsonl') -Value (@{state='GAP';deviceUtc=$Matches[1];deviceEpoch=[long]$Matches[2];generation=[long]$Matches[3];segment=$Matches[4];deviceDeadline=[long]$Matches[5];oldMask=$Matches[6];oldProjection=[int]$Matches[7];reason=$Matches[9]} | ConvertTo-Json -Compress)
            Write-Host '隔离暂时失效：正在重新绑定；gap 期间的操作不能计入分析。' -ForegroundColor Yellow
          }
        }
      }
      $value = Read-Field $status 'STATUS'
      $workerResult = Read-Field $status 'RESULT'
      if ($workerResult) {
        if ($workerResult -in @('MANUAL_STOP','DEADLINE')) {
          Write-Host "实验结束：$workerResult；正在恢复。"
          break
        }
        throw "Device worker ended/rejected: $workerResult"
      }
      if ($value -match '^READY (\d+) (segment-\d+) ([01]+) (\d+) (\d+) (\d+)$') {
        $generation = [long]$Matches[1]; $segment = $Matches[2]; $mask = $Matches[3]
        $projection = [int]$Matches[4]; $deadline = [long]$Matches[5]; $sample = [long]$Matches[6]
        $now = [long](Read-Field $status 'NOW')
        if ($now -ge $deadline) { Write-Host '已到安全上限；正在恢复。'; break }
        $fresh = $now -ge $sample -and ($now - $sample) -le 5 -and [Math]::Abs([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - $now) -le 10
        if ($fresh -and (Read-Field $status 'DEBUG') -ceq '1' -and (Read-Field $status 'SKIPPED') -ceq $mask) {
          if (-not $currentReady -or $currentGeneration -ne $generation) {
            $kind = if ($everReady) { '重新生效' } else { '已生效' }
            Write-Host "$kind：PROJECTION 已隐藏（generation=$generation mask=$mask index=$projection）。gap 前后不可合并；现在可操作，完成后按 Enter。" -ForegroundColor Green
            Add-Content -LiteralPath (Join-Path $SessionDirectory 'intervals.jsonl') -Value (@{state='READY';generation=$generation;segment=$segment;mask=$mask;projection=$projection;deviceEpoch=$sample;deadline=$deadline} | ConvertTo-Json -Compress)
            $currentGeneration = $generation
            $everReady = $true
            $currentReady = $true
          }
        } else { $currentReady = $false; throw 'Live readiness became stale or properties changed; restoring.' }
      } elseif ($value -match '^GAP ') {
        $currentReady = $false
      } elseif ($value -match '^PENDING (\d+) (segment-\d+)') {
        $generation = [long]$Matches[1]
        if ($generation -ne $currentGeneration) { $currentReady = $false }
      } elseif ($value -and $value -ne 'PENDING') { throw "Device worker ended/rejected: $value" }
      if (-not $everReady -and $hostTimer.Elapsed.TotalSeconds -gt 75) { throw 'No live confirmation within host startup limit.' }
      if (-not $script:stopRequested) { Start-Sleep -Milliseconds 200 }
    }
    if (-not $everReady) { throw 'No live READY was observed; this run is inconclusive.' }
  }
} finally {
  if ($cleanupRequired) {
    Write-Host '正在停止并恢复原始属性…'
    try {
      $result = Invoke-Quest "mkdir -p '$remote'; touch '$remote/stop'; if [ -f '$remote/worker.sh' ]; then sh '$remote/worker.sh' restore || exit 4; fi; echo __RESTORE_COMMAND_OK__; echo __RESULT_BEGIN__; cat '$remote/result' 2>/dev/null; echo __RESULT_END__; echo __DEBUG_BEGIN__; getprop debug.oculus.sysPropDebug; echo __DEBUG_END__; echo __SKIPPED_BEGIN__; getprop debug.oculus.skippedLayers; echo __SKIPPED_END__" 45
      Save-Text 'restore-readback.txt' $result
      if ($result -notmatch '__RESTORE_COMMAND_OK__') { throw 'Remote restoration did not finish.' }
      $actualDebug = Read-Field $result 'DEBUG'; $actualSkipped = Read-Field $result 'SKIPPED'
      $workerResult = Read-Field $result 'RESULT'
      $restored = $actualDebug -ceq $state.debug -and $actualSkipped -ceq $state.skipped
      Save-Text 'restoration.json' (@{restored=$restored;debug=$actualDebug;skipped=$actualSkipped;workerResult=$workerResult;utc=[DateTimeOffset]::UtcNow.ToString('o')} | ConvertTo-Json)
      if (-not $restored) { throw 'Exact property readback mismatch.' }
      Write-Host "已恢复并读回：sysPropDebug=[$actualDebug] skippedLayers=[$actualSkipped]" -ForegroundColor Green
    } catch {
      Write-Warning "恢复未确认：$($_.Exception.Message)。恢复连接后执行：.\scripts\capture-stage-isolation.ps1 -Restore -SessionDirectory '$SessionDirectory'"
      throw
    } finally {
      foreach ($name in @('worker.log','watchdog.log','before.txt','snapshots.log','gaps.log','status','result','restore-status','restoration.json','stage-operation.log','stage-operation.log.1','stage-operation.log.2')) {
        try { Save-Text $name (Invoke-Quest "cat '$remote/$name' 2>/dev/null") } catch { Write-Warning "Cannot collect $name" }
      }
    }
  }
}
if ($Run -and $workerResult -and $workerResult -notin @('MANUAL_STOP','DEADLINE')) {
  throw "Device worker ended/rejected: $workerResult (properties restored; experiment result preserved)."
}
