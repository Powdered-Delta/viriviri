[CmdletBinding()]
param(
  [switch]$Quiet
)

# Component-library boundary check.
#
# WHY THIS EXISTS
# ---------------
# This repo's portability claim is "the contract layer and the Compose components are platform-neutral;
# only :app is Meta Spatial specific". That claim is worth nothing unless something fails when it stops
# being true -- the project has already been bitten once by a gate that looked live and never ran
# (docs/research/workbench-dismiss-hit-layer.md, the "silently always-false gate").
#
# So this script turns the boundary into an executable invariant. It is meant to be run before a commit
# and to exit non-zero the moment a library module reaches for something platform-specific.
#
# TWO SEVERITIES
# --------------
#   FAIL  the layering is broken: a library module depends on the host platform, the Meta SDK, or :app.
#   DEBT  the layering is intact but Compose/Multiplatform portability would need work. Reported, not fatal.
#
# The distinction matters: failing on DEBT would block unrelated work, while ignoring it would let the
# portability slope erode. Only real boundary violations stop the build.

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# Per-module rules.
#
#   core    must stay pure Kotlin: no Android, no Compose, no Meta, no :app. This is what makes a
#           Kotlin-Multiplatform migration cheap later.
#   compose must not reach the Meta SDK or :app. androidx.compose.* is allowed (it maps onto Compose
#           Multiplatform), but Android framework and AndroidX-non-Compose packages are not.
$rules = @(
  [pscustomobject]@{
    Name = ':spatial-workbench-core'
    Path = 'spatial-workbench-core/src'
    ForbiddenImportPrefixes = @(
      'android.',
      'androidx.',
      'com.meta.spatial.',
      'com.m0e_n00b.viriviri.'
    )
    ForbiddenIdentifiers = @('ViriViriApplication')
    Rationale = 'pure Kotlin contract layer (KMP-ready)'
  },
  [pscustomobject]@{
    Name = ':spatial-workbench-compose'
    Path = 'spatial-workbench-compose/src'
    ForbiddenImportPrefixes = @(
      'android.',
      'androidx.core.',
      'androidx.appcompat.',
      'androidx.activity.',
      'com.meta.spatial.',
      'com.m0e_n00b.viriviri.'
    )
    ForbiddenIdentifiers = @('ViriViriApplication')
    Rationale = 'platform-neutral Compose component library'
  }
)

# Non-fatal: Android-only API usage inside an otherwise clean library module.
$debtPatterns = @(
  [pscustomobject]@{
    Pattern = '^import androidx\.compose\.ui\.res\.'
    Reason = 'androidx.compose.ui.res has no direct Compose Multiplatform equivalent (org.jetbrains.compose.resources uses a different mechanism)'
  },
  [pscustomobject]@{
    Pattern = '^import androidx\.compose\.ui\.platform\.LocalSoftwareKeyboardController'
    Reason = 'verify this still resolves under Compose Multiplatform before migrating; the Android soft keyboard controller has no desktop/Linux counterpart'
  }
)

function Get-TextIncludes {
  param([string]$Root, [string[]]$Filter)
  if (-not (Test-Path -LiteralPath $Root)) { return @() }
  return Get-ChildItem -LiteralPath $Root -Recurse -File -Include $Filter -ErrorAction SilentlyContinue
}

$failures = New-Object System.Collections.Generic.List[string]
$debts = New-Object System.Collections.Generic.List[string]

foreach ($rule in $rules) {
  $modulePath = Join-Path $projectRoot $rule.Path
  $files = Get-TextIncludes -Root $modulePath -Filter @('*.kt')

  if ($files.Count -eq 0) {
    $failures.Add("$($rule.Name): no sources found under '$($rule.Path)' -- has the layout changed?")
    continue
  }

  foreach ($file in $files) {
    $relative = $file.FullName.Replace("$projectRoot\", '')
    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadAllLines($file.FullName)) {
      $lineNumber++
      $trimmed = $line.Trim()

      if ($trimmed.StartsWith('import ')) {
        $imported = $trimmed.Substring(7).Trim()
        foreach ($prefix in $rule.ForbiddenImportPrefixes) {
          if ($imported.StartsWith($prefix)) {
            $failures.Add("$relative`:$lineNumber  forbidden import '$imported' (prefix '$prefix') in $($rule.Name) -- $($rule.Rationale)")
          }
        }
        foreach ($debt in $debtPatterns) {
          if ($trimmed -match $debt.Pattern) {
            $debts.Add("$relative`:$lineNumber  $($debt.Reason)")
          }
        }
      }

      foreach ($identifier in $rule.ForbiddenIdentifiers) {
        # Whole-word match, so a comment merely naming it in prose does not trip the check, but real
        # code and KDoc references to the host singleton do.
        if ($trimmed -match "\b$([regex]::Escape($identifier))\b") {
          $failures.Add("$relative`:$lineNumber  references host singleton '$identifier' inside $($rule.Name)")
        }
      }
    }
  }
}

# Declared-but-unused Android dependencies: these inflate the Android coupling of a library module even
# when no source file imports them, and removing them is free. Reported as DEBT with the evidence.
$androidOnlyDeps = @('androidx.core.ktx', 'androidx.appcompat', 'androidx.activity')
$composeBuildFile = Join-Path $projectRoot 'spatial-workbench-compose/build.gradle.kts'
if (Test-Path -LiteralPath $composeBuildFile) {
  # Only dependency *declarations* count. Comment lines are stripped first: a comment that merely names a
  # removed dependency (which is how this project documents such removals) must not be read as a live
  # declaration, or the check would demand you delete the explanation of why you deleted the dependency.
  $declarationLines = [System.IO.File]::ReadAllLines($composeBuildFile) |
    ForEach-Object { ($_ -replace '//.*$', '').Trim() } |
    Where-Object { $_ -ne '' }
  $declarationText = $declarationLines -join "`n"
  foreach ($dep in $androidOnlyDeps) {
    if ($declarationText -match [regex]::Escape($dep)) {
      $packageRoot = ($dep -replace '\.[^.]+$', '')
      $used = Get-TextIncludes -Root (Join-Path $projectRoot 'spatial-workbench-compose/src') -Filter @('*.kt') |
        Where-Object { [System.IO.File]::ReadAllText($_.FullName) -match "(?m)^import\s+$([regex]::Escape($packageRoot))\." }
      if (-not $used) {
        $debts.Add("spatial-workbench-compose/build.gradle.kts  declares '$dep' but no source imports '$packageRoot.*' -- drop it to reduce Android coupling")
      }
    }
  }
}

# App-side UI rules: a composable must be told its state, not fetch it.
#
# This is the "encapsulation" half of the component-library boundary, and it is enforced separately from
# the module rules above because the two failure modes are different. `:app` is *allowed* to read the host
# singleton -- that is what the host is for -- so banning `ViriViriApplication` app-wide would be wrong.
# What is not allowed is a composable doing it for itself, in either of the two shapes it historically
# took:
#
#   1. a default argument, `appState: ViriViriAppState = ViriViriApplication.appState` -- the caller does
#      not have to pass anything, so the component quietly owns its own state source;
#   2. `by ViriViriApplication.appState.state.collectAsState()` inside the composable body.
#
# Both were present in this tree (four of the first, one of the second) and were removed; the rule exists
# so they cannot return one component at a time.
$appUiRules = @(
  [pscustomobject]@{
    Pattern = ':\s*\w+\s*=\s*ViriViriApplication\.appState'
    Reason = 'default-argument escape hatch: pass the state in from the host instead'
  },
  [pscustomobject]@{
    Pattern = 'by\s+ViriViriApplication\.appState'
    Reason = 'composable reads the host singleton directly; take it as a parameter'
  }
)

$appSourceRoot = Join-Path $projectRoot 'app/src/main'
foreach ($file in (Get-TextIncludes -Root $appSourceRoot -Filter @('*.kt'))) {
  $relative = $file.FullName.Replace("$projectRoot\", '')
  $lines = [System.IO.File]::ReadAllLines($file.FullName)
  $declaresComposable = ($lines | Where-Object { $_ -match '@Composable' }).Count -gt 0
  if (-not $declaresComposable) { continue }
  for ($i = 0; $i -lt $lines.Length; $i++) {
    $trimmed = $lines[$i].Trim()
    if ($trimmed.StartsWith('*') -or $trimmed.StartsWith('//')) { continue }
    foreach ($rule in $appUiRules) {
      if ($trimmed -match $rule.Pattern) {
        $failures.Add("$relative`:$($i + 1)  $($rule.Reason)")
      }
    }
  }
}

if (-not $Quiet) {
  Write-Host "Component-library boundary check"
  Write-Host "Root: $projectRoot"
  foreach ($rule in $rules) {
    Write-Host ("  {0,-30} {1}" -f $rule.Name, $rule.Rationale)
  }
  Write-Host ""
}

if ($debts.Count -gt 0) {
  Write-Host "Portability debt (non-fatal, $($debts.Count)):" -ForegroundColor Yellow
  foreach ($debt in $debts) { Write-Host "  $debt" -ForegroundColor Yellow }
  Write-Host ""
}

if ($failures.Count -gt 0) {
  Write-Host "BOUNDARY VIOLATIONS ($($failures.Count)):" -ForegroundColor Red
  foreach ($failure in $failures) { Write-Host "  $failure" -ForegroundColor Red }
  Write-Host ""
  Write-Host "Component-library boundary check FAILED." -ForegroundColor Red
  exit 1
}

Write-Host "Component-library boundary check passed ($($debts.Count) debt item(s), none fatal)." -ForegroundColor Green
exit 0
