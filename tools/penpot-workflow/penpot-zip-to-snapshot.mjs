// penpot-zip-to-snapshot.mjs —— 从 .penpot 导出文件直接生成设计快照（**不需要插件**）
//
//   node tools/penpot-workflow/penpot-zip-to-snapshot.mjs <file.penpot> [--out <path>] [--pattern "^[CW]\d "]
//
// 为什么要有这条路径：
//   MCP 插件这条腿依赖浏览器常驻 + WebSocket，是整个流程最脆弱的一环（节流 / frozen /
//   孤儿浏览器占槽位）。而「仪表盘下载 .penpot」只依赖一次 HTTP，导出是**纯 ZIP + JSON**，
//   所以可以完全离线地得到同一份快照 —— 适合 CI / 批量 / 需要稳定性的场景。
//
// 与 extract-panels.ps1 的输出**同构**：同样含 dpCanvas / pxPerDp / framePx / elements / labels，
// 两个来源可以直接互相替换、互相校验。
//
// 零依赖：自带最小 ZIP 解析（EOCD + 中央目录 + inflateRaw；不支持 ZIP64）。

import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

// ---------------------------------------------------------------- 最小 ZIP 读取
const SIG_EOCD = 0x06054b50;
const SIG_CD = 0x02014b50;

function readZipEntries(buf) {
  let eocd = -1;
  const min = Math.max(0, buf.length - 65557);
  for (let i = buf.length - 22; i >= min; i--) {
    if (buf.readUInt32LE(i) === SIG_EOCD) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('不是合法 ZIP：找不到 EOCD');

  const count = buf.readUInt16LE(eocd + 10);
  const cdOffset = buf.readUInt32LE(eocd + 16);
  const entries = new Map();
  let p = cdOffset;

  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(p) !== SIG_CD) throw new Error('中央目录损坏 @' + p);
    const method = buf.readUInt16LE(p + 10);
    const compSize = buf.readUInt32LE(p + 20);
    const rawSize = buf.readUInt32LE(p + 24);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const localOffset = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nameLen);

    // 本地头里的 name/extra 长度可能与中央目录不同，必须以本地头为准
    const lNameLen = buf.readUInt16LE(localOffset + 26);
    const lExtraLen = buf.readUInt16LE(localOffset + 28);
    const dataOffset = localOffset + 30 + lNameLen + lExtraLen;
    entries.set(name, { method, compSize, rawSize, dataOffset });

    p += 46 + nameLen + extraLen + commentLen;
  }
  return { buf, entries };
}

function readText(zip, name) {
  const e = zip.entries.get(name);
  if (!e) return null;
  const slice = zip.buf.subarray(e.dataOffset, e.dataOffset + e.compSize);
  const raw = e.method === 0 ? slice : zlib.inflateRawSync(slice);
  return raw.toString('utf8');
}

function readJson(zip, name) {
  const t = readText(zip, name);
  return t == null ? null : JSON.parse(t);
}

// ---------------------------------------------------------------- 文本提取
/** Penpot 文本在 content.children → paragraph-set → paragraph → span.text，递归收集所有 text。 */
function textOf(shape) {
  const out = [];
  const walk = (n) => {
    if (!n || typeof n !== 'object') return;
    if (typeof n.text === 'string') out.push(n.text);
    (n.children || []).forEach(walk);
  };
  walk(shape.content);
  return out.join('\n') || shape.name || '';
}

// ---------------------------------------------------------------- 主流程
const argv = process.argv.slice(2);
const zipPath = argv.find((a) => !a.startsWith('--') && argv[argv.indexOf(a) - 1] !== '--out' && argv[argv.indexOf(a) - 1] !== '--pattern');
const opt = (n, d) => { const i = argv.indexOf('--' + n); return i >= 0 && argv[i + 1] ? argv[i + 1] : d; };

if (!zipPath) {
  console.error('用法: node penpot-zip-to-snapshot.mjs <file.penpot> [--out <snapshot.json>] [--pattern "^[CW]\\d "]');
  process.exit(2);
}
const OUT = path.resolve(opt('out', path.join(path.dirname(zipPath), 'panel-snapshot.zip.json')));
const PATTERN = new RegExp(opt('pattern', '^[CW]\\d '));

const zip = readZipEntries(fs.readFileSync(path.resolve(zipPath)));

// 文件元数据：files/<fileId>.json
const fileEntryName = [...zip.entries.keys()].find((n) => /^files\/[0-9a-f-]+\.json$/.test(n));
if (!fileEntryName) throw new Error('ZIP 里找不到 files/<fileId>.json');
const fileMeta = readJson(zip, fileEntryName);
const fileId = fileMeta.id;
const fileDir = `files/${fileId}`;

// 页面：files/<fileId>/pages/<pageId>.json
const pageIds = [...zip.entries.keys()]
  .map((n) => n.match(new RegExp(`^${fileDir}/pages/([0-9a-f-]+)\\.json$`)))
  .filter(Boolean)
  .map((m) => m[1]);
if (!pageIds.length) throw new Error('ZIP 里找不到页面');

// 选形状最多的页面
let best = null;
for (const pid of pageIds) {
  const prefix = `${fileDir}/pages/${pid}/`;
  const n = [...zip.entries.keys()].filter((k) => k.startsWith(prefix) && k.endsWith('.json')).length;
  if (!best || n > best.n) best = { pid, n };
}
const pageId = best.pid;
const pageMeta = readJson(zip, `${fileDir}/pages/${pageId}.json`);

// 载入全部形状
const shapes = new Map();
const shapePrefix = `${fileDir}/pages/${pageId}/`;
for (const key of zip.entries.keys()) {
  if (!key.startsWith(shapePrefix) || !key.endsWith('.json')) continue;
  const s = readJson(zip, key);
  if (s && s.id) shapes.set(s.id, s);
}

const childrenOf = (s) => (s.shapes || []).map((id) => shapes.get(id)).filter(Boolean);
const area = (s) => (s.width || 0) * (s.height || 0);

// 板：id 为 Root Frame 的顶层子节点
const ROOT_ID = '00000000-0000-0000-0000-000000000000';
const root = shapes.get(ROOT_ID);
const topLevel = root ? childrenOf(root) : [...shapes.values()].filter((s) => !s.parentId || s.parentId === ROOT_ID);

const panels = [];
for (const board of topLevel) {
  if (!PATTERN.test(String(board.name))) continue;
  const kids = childrenOf(board);

  // 面板框 = 面积最大的非文本子形状（W 系叫 panel，C 系叫 xxx_panel，取最大值最稳）
  const frame = kids
    .filter((k) => k.type !== 'text')
    .sort((a, b) => area(b) - area(a))[0];
  if (!frame) continue;

  const labels = kids.filter((k) => k.type === 'text').map(textOf);
  // 乘号既可能是 ASCII 'x' 也可能是全角 '×' —— 两种都要吃
  const dpLine = labels.find((t) => /\d+\s*[x×X]\s*\d+\s*dp/.test(t)) || '';
  const m = dpLine.match(/(\d+)\s*[x×X]\s*(\d+)\s*dp/);
  const dpW = m ? +m[1] : null;
  const dpH = m ? +m[2] : null;
  const s = dpW ? frame.width / dpW : null;

  const elements = kids
    .filter((k) => k.id !== frame.id && k.type !== 'text')
    .map((k) => ({
      name: String(k.name),
      dp: s ? {
        x: Math.round((k.x - frame.x) / s),
        y: Math.round((k.y - frame.y) / s),
        w: Math.round(k.width / s),
        h: Math.round(k.height / s),
      } : null,
      interactions: Array.isArray(k.interactions) ? k.interactions.length : 0,
      type: k.type,
    }));

  // 原型图：遍历整棵子树（互动可能挂在不只是直接子节点上）
  // 导出里的形状：{ eventType: 'click', actionType: 'navigate', destination: '<frameId>' }
  const links = [];
  const seen = new Set();
  const walkLinks = (node) => {
    if (!node || seen.has(node.id)) return;
    seen.add(node.id);
    for (const it of node.interactions || []) {
      if (!it || !it.destination) continue;
      links.push({
        from: String(node.name),
        fromId: node.id,
        trigger: it.eventType || it.trigger || '?',
        action: it.actionType || (it.action && it.action.type) || '?',
        toId: it.destination,
        to: shapes.get(it.destination) ? String(shapes.get(it.destination).name) : String(it.destination),
      });
    }
    childrenOf(node).forEach(walkLinks);
  };
  kids.forEach(walkLinks);

  panels.push({
    board: String(board.name),
    boardId: board.id,
    dpCanvas: dpW ? { w: dpW, h: dpH } : null,
    pxPerDp: s ? +s.toFixed(4) : null,
    framePx: { x: Math.round(frame.x), y: Math.round(frame.y), w: Math.round(frame.width), h: Math.round(frame.height) },
    elementCount: elements.length,
    elements,
    labels,
    linkCount: links.length,
    links,
  });
}

const snapshot = {
  file: fileMeta.name,
  page: pageMeta.name,
  extractedAt: new Date().toISOString(),
  source: 'penpot-zip',
  sourceFile: path.basename(path.resolve(zipPath)),
  revn: fileMeta.revn,
  panels,
};

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(snapshot, null, 2) + '\n', 'utf8');

console.log('wrote: ' + OUT);
console.log(`file="${snapshot.file}"  page="${snapshot.page}"  revn=${snapshot.revn}  shapes=${shapes.size}`);
console.log(`panels: ${panels.length}`);
for (const p of panels) {
  console.log(`  ${p.board.padEnd(24)} dpCanvas=${p.dpCanvas ? p.dpCanvas.w + 'x' + p.dpCanvas.h : '?'}  scale=${p.pxPerDp}  elements=${p.elementCount}  links=${p.linkCount}`);
}
