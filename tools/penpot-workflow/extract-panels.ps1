# extract-panels.ps1 —— 把 Penpot 上的面板板（默认 W1..W8）提取成机器可读的设计快照
#
#   .\tools\penpot-workflow\extract-panels.ps1
#   .\tools\penpot-workflow\extract-panels.ps1 -BoardPattern '^C\d ' -OutFile .\out\center-snapshot.json
#
# 前提：插件已连接（见 README 的「第 1 步」）。
# 产出：JSON，含每个板的 dpCanvas / pxPerDp / framePx，以及每个元素的真实 dp 矩形与互动数。

param(
  [string]$BoardPattern = '^[CW]\d ',
  [string]$OutFile = '',
  [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Invoke-PenpotMcp.ps1')

if (-not $OutFile) { $OutFile = Join-Path $RepoRoot 'out\panel-snapshot.json' }

$code = @"
const page = penpot.currentPage;
const re = new RegExp('$BoardPattern');
const boards = (page.root.children || []).filter((c) => c.type === 'board' && re.test(String(c.name)));
const out = [];
boards.forEach((bd) => {
  const kids = bd.children || [];
  const frame = kids.find((c) => c.name === 'panel')
             || kids.find((c) => c.type === 'rectangle');
  const labels = kids.filter((c) => c.type === 'text').map((c) => String(c.characters || ''));
  const dpLine = labels.find((t) => /dp\s+scale/.test(t)) || '';
  const m = dpLine.match(/(\d+)\s*x\s*(\d+)\s*dp/);
  const dpW = m ? +m[1] : null, dpH = m ? +m[2] : null;
  const s = (frame && dpW) ? frame.width / dpW : null;
  const elements = kids
    .filter((c) => c !== frame && c.type !== 'text')
    .map((c) => ({
      name: String(c.name),
      dp: (frame && s) ? {
        x: Math.round((c.x - frame.x) / s), y: Math.round((c.y - frame.y) / s),
        w: Math.round(c.width / s), h: Math.round(c.height / s),
      } : null,
      interactions: (c.interactions || []).length,
    }));
  out.push({
    board: String(bd.name),
    boardId: bd.id,
    dpCanvas: dpW ? { w: dpW, h: dpH } : null,
    pxPerDp: s ? +s.toFixed(4) : null,
    framePx: frame ? { x: Math.round(frame.x), y: Math.round(frame.y), w: Math.round(frame.width), h: Math.round(frame.height) } : null,
    elementCount: elements.length,
    elements,
    labels,
  });
});
return { file: penpot.currentFile.name, page: page.name, extractedAt: new Date().toISOString(), panels: out };
"@

$data = Invoke-PenpotMcp -Code $code -RepoRoot $RepoRoot

New-Item -ItemType Directory -Force -Path (Split-Path $OutFile -Parent) | Out-Null
$data | ConvertTo-Json -Depth 12 | Set-Content $OutFile -Encoding utf8

Write-Output "wrote: $OutFile"
Write-Output "panels: $($data.panels.Count)"
foreach ($p in $data.panels) {
  Write-Output ("  {0,-24} dpCanvas={1}x{2}  scale={3}  elements={4}" -f `
    $p.board, $p.dpCanvas.w, $p.dpCanvas.h, $p.pxPerDp, $p.elementCount)
}
