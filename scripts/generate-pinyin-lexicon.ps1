$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$dictDir = Join-Path $root "temp\Vertick-IME\app\src\main\assets\rime\cn_dicts"
$outFile = Join-Path $root "app\src\main\assets\ime\pinyin_lexicon.txt"
$outDir = Split-Path $outFile -Parent
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$charPy = @{}
$start8 = $false
Get-Content (Join-Path $dictDir "8105.dict.yaml") -Encoding UTF8 | ForEach-Object {
  if ($_ -eq "...") { $script:start8=$true; return }
  if (-not $script:start8) { return }
  if ($_ -notmatch "`t") { return }
  $c = $_ -split "`t"
  if ($c.Count -lt 2) { return }
  $ch = $c[0]; $py = $c[1]
  if ($py -notmatch "^[a-zv]+$") { return }
  if ($ch.Length -ne 1) { return }
  $w = 0L; if ($c.Count -ge 3 -and $c[2] -match "^\d+$") { $w = [int64]$c[2] }
  if ($w -gt 0) {
    if (-not $charPy.ContainsKey($ch) -or $w -gt $charPy[$ch].w) { $charPy[$ch] = @{ py=$py; w=$w } }
  }
}
Write-Output ("8105 char-frequency entries: " + $charPy.Count)

# best (highest-weight) word per compact pinyin
$phrases = @{}
$startB = $false
Get-Content (Join-Path $dictDir "base.dict.yaml") -Encoding UTF8 | ForEach-Object {
  if ($_ -eq "...") { $script:startB=$true; return }
  if (-not $script:startB) { return }
  if ($_ -notmatch "`t") { return }
  $c = $_ -split "`t"
  if ($c.Count -lt 2) { return }
  $word = $c[0]; $py = $c[1]
  $syls = $py -split " "
  foreach ($s in $syls) { if ($s -notmatch "^[a-zv]+$") { return } }
  $n = $word.Length
  if ($n -lt 2 -or $n -gt 6) { return }
  $w = 1L; if ($c.Count -ge 3 -and $c[2] -match "^\d+$") { $w = [int64]$c[2] }
  if ($n -le 2) { $gate = 3000 } elseif ($n -le 4) { $gate = 20000 } else { $gate = 80000 }
  if ($w -lt $gate) { return }
  $key = ($syls -join "")
  if (-not $phrases.ContainsKey($key) -or $w -gt $phrases[$key].w) {
    $phrases[$key] = [pscustomobject]@{ word = $word; w = $w }
  }
}

# best (highest-frequency) char set per syllable
$syllChars = @{}
foreach ($kv in $charPy.GetEnumerator()) {
  $ch = $kv.Key; $py = $kv.Value.py; $w = $kv.Value.w
  if (-not $syllChars.ContainsKey($py)) { $syllChars[$py] = New-Object System.Collections.ArrayList }
  [void]$syllChars[$py].Add([pscustomobject]@{ ch = $ch; w = $w })
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("# ViriViri bundled pinyin lexicon")
[void]$sb.AppendLine("# Source: rime-ice https://github.com/iDvel/rime-ice GPL-3.0-or-later; char freq BLCU 2.5G corpus")
[void]$sb.AppendLine("# P <tab> compactpinyin <tab> word:weight  |  C <tab> syllable <tab> char:weight,char:weight,...")
$phraseCount = 0
foreach ($key in ($phrases.Keys | Sort-Object)) {
  $e = $phrases[$key]
  [void]$sb.AppendLine("P`t" + $key + "`t" + $e.word + ":" + $e.w)
  $phraseCount++
}
$charSyllCount = 0
foreach ($key in ($syllChars.Keys | Sort-Object)) {
  $sorted = $syllChars[$key] | Sort-Object { $_.w } -Descending | Select-Object -First 20
  $val = (($sorted | ForEach-Object { $_.ch + ":" + $_.w }) -join ",")
  [void]$sb.AppendLine("C`t" + $key + "`t" + $val)
  $charSyllCount++
}
[System.IO.File]::WriteAllText($outFile, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
$fi = Get-Item $outFile
Write-Output ("phrase pinyin keys: " + $phraseCount)
Write-Output ("char syllable keys: " + $charSyllCount)
Write-Output ("asset size KB: " + [math]::Round($fi.Length/1KB))