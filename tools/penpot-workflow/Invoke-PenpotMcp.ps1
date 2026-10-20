# Invoke-PenpotMcp.ps1 —— Penpot MCP 调用的共享助手
#
# 用法（dot-source 后调用）：
#   . "$PSScriptRoot\Invoke-PenpotMcp.ps1"
#   $r = Invoke-PenpotMcp -Code 'return { ok: true };'
#
# 设计要点（都是踩过坑才加的）：
#   * 三类失败分开处理：可重试的节流 / 立刻失败的未连接 / 其它工具错误
#   * 不把 token 写进本文件 —— 从仓库根的 mcp.json 读取（那里是唯一的明文位置）

$ErrorActionPreference = 'Stop'

function Get-PenpotContext {
  <#
    解析 mcp.json 里的 Penpot URL（含 userToken），必要时做一次 initialize 拿 session id。
    结果缓存在 $script:PenpotCtx。
  #>
  param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
  )
  if ($script:PenpotCtx) { return $script:PenpotCtx }

  $cfgPath = Join-Path $RepoRoot 'mcp.json'
  if (-not (Test-Path $cfgPath)) { throw "找不到 $cfgPath" }
  $cfg = Get-Content $cfgPath -Raw | ConvertFrom-Json

  $url = $null
  foreach ($k in $cfg.mcpServers.PSObject.Properties.Name) {
    $e = $cfg.mcpServers.PSObject.Properties[$k].Value
    if ($e.url -and $e.url -match 'userToken=') { $url = $e.url; break }
    if ($e.args) {
      $u = $e.args | Where-Object { $_ -match 'userToken=' } | Select-Object -First 1
      if ($u) { $url = $u; break }
    }
  }
  if (-not $url) { throw "mcp.json 里没有带 userToken 的 Penpot 条目" }

  $sidFile = Join-Path $env:TEMP 'penpot-sid.txt'
  $sid = if (Test-Path $sidFile) { (Get-Content $sidFile -Raw).Trim() } else { '' }

  if (-not $sid) {
    $init = '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"penpot-workflow","version":"1.0"}}}'
    $fi = Join-Path $env:TEMP 'penpot-init.json'
    $hdr = Join-Path $env:TEMP 'penpot-hdr.txt'
    [System.IO.File]::WriteAllText($fi, $init, [System.Text.UTF8Encoding]::new($false))
    curl.exe -sS -D $hdr -o NUL --max-time 25 -X POST $url `
      -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' `
      --data-binary "@$fi"
    $sid = ((Get-Content $hdr | Where-Object { $_ -match '^mcp-session-id:' }) -split ':', 2)[1].Trim()
    if (-not $sid) { throw 'initialize 未返回 mcp-session-id' }
    [System.IO.File]::WriteAllText($sidFile, $sid, [System.Text.UTF8Encoding]::new($false))
  }

  $script:PenpotCtx = @{ Url = $url; Sid = $sid; Token = ($url -split 'userToken=')[1] }
  return $script:PenpotCtx
}

function Invoke-PenpotMcp {
  <#
    调用 execute_code。返回内层 result 对象（已解析）。
    失败分类：
      frozen / no heartbeat  -> 重试（标签页被节流）
      No Penpot instance...  -> 立刻抛出，提示去点连接
      Tool execution failed  -> 抛出原文
  #>
  param(
    [Parameter(Mandatory = $true)][string]$Code,
    [int]$MaxAttempts = 25,
    [int]$DelaySeconds = 5,
    [string]$RepoRoot
  )
  $ctx = Get-PenpotContext -RepoRoot $RepoRoot
  $payload = @{
    jsonrpc = '2.0'; id = 9001; method = 'tools/call'
    params  = @{ name = 'execute_code'; arguments = @{ code = $Code } }
  } | ConvertTo-Json -Depth 10 -Compress

  $pf = Join-Path $env:TEMP 'penpot-call.json'
  $of = Join-Path $env:TEMP 'penpot-call.out'
  [System.IO.File]::WriteAllText($pf, $payload, [System.Text.UTF8Encoding]::new($false))

  for ($i = 1; $i -le $MaxAttempts; $i++) {
    Start-Sleep -Seconds $DelaySeconds
    curl.exe -sS -o $of --max-time 300 -X POST $ctx.Url `
      -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' `
      -H "mcp-session-id: $($ctx.Sid)" --data-binary "@$pf"
    $raw = Get-Content $of -Raw -ErrorAction SilentlyContinue
    if (-not $raw) { continue }
    $body = ($raw -split "`n" | Where-Object { $_ -like 'data: *' }) -replace '^data: ', ''
    if (-not $body) { continue }

    if ($body -match 'frozen' -or $body -match 'no heartbeat') { continue }
    if ($body -match 'No Penpot instance connected') {
      throw '插件未连接。请在 Penpot 页面点顶栏 MCP 胶囊 →「在此连接」（状态灯变白），或先跑 keepalive.mjs。'
    }
    if ($body -match 'Tool execution failed') {
      throw ('execute_code 失败: ' + ($body | ConvertFrom-Json).result.content[0].text)
    }

    $inner = ($body | ConvertFrom-Json).result.content[0].text
    try { return ($inner | ConvertFrom-Json).result }
    catch { throw "返回不是合法 JSON：`n$($inner.Substring(0, [Math]::Min(400, $inner.Length)))" }
  }
  throw "MCP 在 $MaxAttempts 次尝试内一直节流（frozen / no heartbeat）"
}
