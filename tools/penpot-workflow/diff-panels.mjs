// diff-panels.mjs —— 设计快照（Penpot） vs 代码真值（Kotlin / XML / theme.json）对账器
//
//   node tools/penpot-workflow/diff-panels.mjs [--snapshot <path>] [--out <path>] [--repo <dir>]
//
// 判据原则：**组件库表达的是比例，不是绝对 dp**。
//   * 主判据 = 元素占本面板画布的比例（容差 ±3%）
//   * 绝对 dp 差异只作参考列 Δdp
//   * wrap_content / match_parent 标为「内容决定」，0dp+weight 标为「权重填充」
// 只读仓库文件；只写 --out 指定的报告。

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const argv = process.argv.slice(2);
const opt = (name, dflt) => {
  const i = argv.indexOf('--' + name);
  return i >= 0 && argv[i + 1] ? argv[i + 1] : dflt;
};

const REPO = path.resolve(opt('repo', path.join(HERE, '..', '..')));
const SNAP = path.resolve(opt('snapshot', path.join(REPO, 'out', 'panel-snapshot.json')));
const OUT = path.resolve(opt('out', path.join(REPO, 'out', 'design-code-diff.md')));
const KT = path.join(REPO, 'app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt');
const XMLDIR = path.join(REPO, 'app/src/main/res/layout');
const THEME = path.join(REPO, 'themes/cinema/theme.json');

const RATIO_TOL = 0.03;

if (!fs.existsSync(SNAP)) {
  console.error(`快照不存在：${SNAP}\n先跑 extract-panels.ps1`);
  process.exit(1);
}
const snap = JSON.parse(fs.readFileSync(SNAP, 'utf8'));
const kt = fs.existsSync(KT) ? fs.readFileSync(KT, 'utf8') : '';
const lines = [];
const say = (s = '') => lines.push(s);

// ---------- 代码真值：解析 *PanelSettings() ----------
const NUM = '([0-9.]+)f?';
const blockOf = (fn) => {
  const i = kt.indexOf(`private fun ${fn}(`);
  if (i < 0) return null;
  const j = kt.indexOf('\n  private fun ', i + 10);
  return kt.slice(i, j > 0 ? j : i + 1200);
};
const firstQuad = (b) => {
  const m = b && b.match(new RegExp(`QuadShapeOptions\\(\\s*width\\s*=\\s*${NUM}\\s*,\\s*height\\s*=\\s*${NUM}`));
  return m ? [+m[1], +m[2]] : null;
};
const firstDp = (b) => {
  const m = b && b.match(new RegExp(`DpDisplayOptions\\(width\\s*=\\s*${NUM}\\s*,\\s*height\\s*=\\s*${NUM}\\s*,\\s*dpi\\s*=\\s*(\\d+)`));
  return m ? [+m[1], +m[2], +m[3]] : null;
};

const CFG = { centerWidth: 1.5, centerHeight: 1.20, railWidth: 0.7, leftRailHeight: 0.9, rightRailHeight: 0.58 };
const CONST = { GRAB_BAR_WIDTH_METERS: 1.0, GRAB_BAR_HEIGHT_METERS: 0.08 };

const CODE = [
  { key: 'player_controll', fn: 'controlsPanelSettings', quad: [1.32, 0.38], dp: [460, 132, 600], board: 'W1 transport' },
  { key: 'workbench_main', fn: 'centerContentPanelSettings', quad: [CFG.centerWidth, CFG.centerHeight], dp: [768, 615, 512], board: 'W4 center' },
  { key: 'input_console', fn: 'inputMethodPanelSettings', quad: [1.62, 0.68], dp: [832, 348, 512], board: 'W5 input_console' },
  { key: 'grab_handle', fn: 'grabBarPanelSettings', quad: [CONST.GRAB_BAR_WIDTH_METERS, CONST.GRAB_BAR_HEIGHT_METERS], dp: [512, 56, 600], board: 'W7 grab_bar' },
  { key: 'right_rail', fn: 'modePanelSettings', quad: [CFG.railWidth, CFG.leftRailHeight], dp: [280, 464, 600], board: 'W3 right_rail' },
  { key: 'left_rail', fn: 'selectorPanelSettings', quad: [CFG.railWidth, CFG.leftRailHeight], dp: [361, 464, 800], board: 'W2 left_rail' },
  { key: 'global header', fn: 'mrPanelSettings', quad: [1.24, 0.30], dp: [520, 128, 600], board: 'W6 nav' },
];
for (const c of CODE) {
  const b = blockOf(c.fn);
  if (!b) continue;
  const q = firstQuad(b); const d = firstDp(b);
  if (q) c.quad = q;
  if (d) c.dp = d;
}

// ---------- 设计真值（来自快照） ----------
// labels 兼容两种形状：字符串数组（新版提取器） 与 {name,text} 对象数组（旧版）
const labelTexts = (p) => (p.labels || []).map((l) => (typeof l === 'string' ? l : (l && l.text) || ''));
const DESIGN = {};
for (const p of snap.panels) {
  const quadLab = labelTexts(p).find((t) => /quad\s+[\d.]+\s*x\s+[\d.]+\s*m/.test(String(t)));
  const m = quadLab && String(quadLab).match(/quad\s+([\d.]+)\s*x\s+([\d.]+)\s*m/);
  DESIGN[p.board] = { dp: p.dpCanvas ? [p.dpCanvas.w, p.dpCanvas.h] : null, quad: m ? [+m[1], +m[2]] : null, elements: p.elements };
}

say('# 设计（Penpot） vs 代码 对账报告');
say();
say(`- 快照：\`${SNAP}\``);
say(`- 仓库：\`${REPO}\``);
say(`- 生成：${new Date().toISOString()}`);
say();

// ---------- Tier 1 ----------
say('## Tier 1 · 面板 quad / dp 画布 / 宽高比');
say();
say('| 面板 | quad(代码) | quad(设计) | dp 画布(代码) | dp 画布(设计) | dpi | 代码比 | quad 比 | 判定 |');
say('| --- | --- | --- | --- | --- | --- | --- | --- | --- |');
let t1bad = 0;
for (const c of CODE) {
  const d = DESIGN[c.board] || {};
  const ca = c.dp[0] / c.dp[1], qa = c.quad[0] / c.quad[1];
  const dev = (ca / qa - 1) * 100;
  const ok = Math.abs(dev) < 1.0;
  if (!ok) t1bad++;
  say(`| \`${c.key}\` | ${c.quad[0]} × ${c.quad[1]} | ${d.quad ? d.quad.join(' × ') : '—'} | ${c.dp[0]} × ${c.dp[1]} | ${d.dp ? d.dp.join(' × ') : '—'} | ${c.dp[2]} | ${ca.toFixed(4)} | ${qa.toFixed(4)} | ${ok ? '✅ 一致' : `❌ 偏差 ${dev.toFixed(1)}%`} |`);
}
say();
say(`**Tier 1：${t1bad} 个面板的 dp 画布宽高比与 quad 不一致**（dp 画布必须与 quad 同比，否则 UI 被拉伸）。`);
say();

// ---------- Tier 2（比例优先） ----------
const fixedPx = (v) => /dp$/.test(String(v || '')) && parseFloat(v) > 0;
function xmlDp(file) {
  const p = path.join(XMLDIR, file);
  if (!fs.existsSync(p)) return null;
  const t = fs.readFileSync(p, 'utf8');
  const out = [];
  const re = /android:id="@\+id\/([A-Za-z0-9_]+)"([\s\S]{0,600}?)(?=\/>|>)/g;
  let m;
  while ((m = re.exec(t))) {
    const seg = m[2];
    out.push({
      id: m[1],
      w: (seg.match(/android:layout_width="([^"]+)"/) || [])[1],
      h: (seg.match(/android:layout_height="([^"]+)"/) || [])[1],
      weight: (seg.match(/android:layout_weight="([^"]+)"/) || [])[1],
    });
  }
  return out;
}
say('## Tier 2 · XML 元素（**按比例判定**，容差 ±3%）');
say();
say('> 只有 **id 与设计稿元素名一致** 的面板才能机械对账（`W1`/`W7`）。');
say();
for (const [file, board, key] of [['controls.xml', 'W1 transport', 'player_controll'], ['grab_bar.xml', 'W7 grab_bar', 'grab_handle']]) {
  const code = xmlDp(file);
  const d = DESIGN[board] || {};
  const cp = CODE.find((c) => c.key === key);
  if (!code || !d.dp) continue;
  say(`### \`${file}\` vs \`${board}\``);
  say();
  say('| id | XML | 设计 dp | 比例(代码) | 比例(设计) | 比例偏差 | Δdp | 判定 |');
  say('| --- | --- | --- | --- | --- | --- | --- | --- |');
  for (const e of code) {
    const de = (d.elements || []).find((x) => x.name === e.id);
    const pxW = parseFloat(e.w), pxH = parseFloat(e.h);
    const wrap = /wrap_content|match_parent/.test(e.w || '') || /wrap_content|match_parent/.test(e.h || '');
    const weightW = pxW === 0 && !!e.weight;
    const codeRW = fixedPx(e.w) ? pxW / cp.dp[0] : null;
    const codeRH = fixedPx(e.h) ? pxH / cp.dp[1] : null;
    const desRW = de ? de.dp.w / d.dp[0] : null;
    const desRH = de ? de.dp.h / d.dp[1] : null;
    let verdict = '（设计中未列）', devTxt = '—';
    if (de) {
      if (wrap) verdict = '⚠️ 内容决定，不参与比例判定';
      else {
        const dw = (codeRW != null && desRW) ? codeRW / desRW - 1 : 0;
        const dh = (codeRH != null && desRH) ? codeRH / desRH - 1 : 0;
        const worst = Math.abs(dw) > Math.abs(dh) ? dw : dh;
        devTxt = `${(worst * 100).toFixed(1)}%`;
        verdict = Math.abs(worst) <= RATIO_TOL ? '✅ 比例一致' : '❌ 比例偏差超容差';
      }
    }
    const ddp = (desRW && codeRW != null) ? Math.round((codeRW - desRW) * d.dp[0]) : (weightW ? '权重填充' : '—');
    say(`| \`${e.id}\` | ${e.w} × ${e.h}${e.weight ? ` (w${e.weight})` : ''} | ${de ? de.dp.w + ' × ' + de.dp.h : '—'} | ${codeRW != null ? codeRW.toFixed(4) : (weightW ? 'weight' : '—')} | ${desRW != null ? desRW.toFixed(4) : '—'} | ${devTxt} | ${ddp} | ${verdict} |`);
  }
  say();
}

// ---------- Tier 3 ----------
say('## Tier 3 · 不可机械对账（需人工）');
say();
say('| 面板 | 原因 |');
say('| --- | --- |');
say('| `W2` `W4` `W5` `W6` `W8` | Compose / 派生尺寸，布局值写在 Modifier 里，非声明式 |');
say('| `W3` | 目标是**新设计**，当前实现是 debug 面板，无对应元素 |');
say();
say('以快照的 dp 为**目标值**人工核对。按钮级数值见 `docs/prototypes/interaction-skeleton/button-level-spec.md`。');
say();

// ---------- Tier 4 ----------
say('## Tier 4 · `theme.json`（主题契约） vs 代码实现');
say();
if (!fs.existsSync(THEME)) say('（找不到 themes/cinema/theme.json）');
else {
  const theme = JSON.parse(fs.readFileSync(THEME, 'utf8'));
  const MAP = {
    mediaStage: ['player', 1.6, 0.9], transport: ['player_controll', 1.32, 0.38],
    centerContent: ['workbench_main', 1.5, 1.2], leftDetailRail: ['left_rail', 0.7, 0.9],
    rightContextRail: ['right_rail', 0.7, 0.9], systemToolbar: ['global header', 1.24, 0.30],
    inputConsole: ['input_console', 1.62, 0.68],
  };
  say('| theme.json 面板 | `sizeMeters` | 代码 quad | 偏差 | 判定 |');
  say('| --- | --- | --- | --- | --- |');
  let bad = 0;
  for (const [tk, panel] of Object.entries(theme.panels || {})) {
    const sz = panel.sizeMeters;
    const t = sz ? `${sz.width} × ${sz.height}` : '—';
    const m = MAP[tk];
    if (!m) { say(`| \`${tk}\` | ${t} | **未实现** | — | ⚠️ 契约有、代码无 |`); continue; }
    const dw = (m[1] / sz.width - 1) * 100, dh = (m[2] / sz.height - 1) * 100;
    const ok = Math.abs(dw) < 2 && Math.abs(dh) < 2;
    if (!ok) bad++;
    say(`| \`${tk}\` | ${t} | ${m[1]} × ${m[2]} | W ${dw >= 0 ? '+' : ''}${dw.toFixed(0)}% / H ${dh >= 0 ? '+' : ''}${dh.toFixed(0)}% | ${ok ? '✅ 一致' : '❌ 不一致'} |`);
  }
  say();
  say(`**Tier 4：${bad} 个面板的 \`theme.json\` 尺寸与实现不符。**`);
  say('主题作者会以契约为起点 —— 契约不实时，作者的版式会在真机上以不同比例出现，且看起来像他自己的错。');
  say();
}

// ---------- 附：常量 ----------
say('## 附 · `WorkbenchLayoutConfig` 常量');
say();
say('| 常量 | 值 | 备注 |');
say('| --- | --- | --- |');
say(`| centerWidth / centerHeight | ${CFG.centerWidth} / ${CFG.centerHeight} | 与设计稿一致 |`);
say(`| railWidth / leftRailHeight | ${CFG.railWidth} / ${CFG.leftRailHeight} | 左右 rail 共用 |`);
say(`| rightRailHeight | ${CFG.rightRailHeight} | **未被消费**（mode_panel 用的是 leftRailHeight） |`);
say();
say('---');
say();
say('本报告只对账，**不修改任何代码**。比例体系与参考物见 `docs/research/penpot-workflow-and-reuse.md` §2.7。');

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, lines.join('\n') + '\n', 'utf8');
console.log('wrote: ' + OUT);
console.log(`Tier1 mismatches: ${t1bad}`);
