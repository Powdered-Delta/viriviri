// keepalive.mjs —— 用无头浏览器把 Penpot 插件的 MCP 连接建立起来并保持
//
//   node tools/penpot-workflow/keepalive.mjs [--minutes 45]
//
// 为什么需要它：
//   插件跑在 Penpot 页面里。脚本若跑完就退出，浏览器一关插件立刻断；
//   而无头浏览器默认会把标签页当后台 → 定时器节流 → 插件心跳停 → MCP 报 frozen/no-heartbeat。
//   所以必须【常驻】+【关闭后台节流】。
//
// 凭据：从同目录（或 --dir 指定目录）的 cred.json 读取，格式 {"email":"...","password":"..."}。
//   **不要把凭据写进仓库** —— 该文件应被 gitignore 或放在仓库外。
//
// 前置：本机需有 playwright-core 与已缓存的 chromium。
//   见 README「环境准备」。

import { chromium } from 'playwright-core';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const argv = process.argv.slice(2);
const opt = (n, d) => { const i = argv.indexOf('--' + n); return i >= 0 && argv[i + 1] ? argv[i + 1] : d; };

const DIR = path.resolve(opt('dir', HERE));
const MINUTES = Number(opt('minutes', 45));
const EXE = opt('exe', process.env.PENPOT_CHROMIUM ||
  'C:\\Users\\N00b\\AppData\\Local\\ms-playwright\\chromium-1243\\chrome-win64\\chrome.exe');
const BASE = opt('base', process.env.PENPOT_BASE || '');   // 例：https://<host>
const LOG = path.join(DIR, 'keepalive.log');
const OUT = path.join(DIR, 'shots');

if (!BASE) { console.error('缺少 --base（Penpot 站点地址）或环境变量 PENPOT_BASE'); process.exit(2); }
if (!fs.existsSync(EXE)) { console.error('找不到 chromium：' + EXE + '\n（见 README「环境准备」）'); process.exit(2); }
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(LOG, '', 'utf8');
const log = (m) => fs.appendFileSync(LOG, `${new Date().toISOString()}  ${m}\n`, 'utf8');

const credFile = path.join(DIR, 'cred.json');
if (!fs.existsSync(credFile)) { console.error('缺少 ' + credFile); process.exit(2); }
const cred = JSON.parse(fs.readFileSync(credFile, 'utf8'));

const browser = await chromium.launch({
  executablePath: EXE, headless: true, timeout: 45000,
  args: [
    '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage',
    '--disable-background-timer-throttling',
    '--disable-backgrounding-occluded-windows',
    '--disable-renderer-backgrounding',
    '--disable-features=CalculateNativeWinOcclusion',
  ],
});
const ctx = await browser.newContext({ viewport: { width: 1920, height: 1200 }, ignoreHTTPSErrors: true, locale: 'zh-CN' });
const page = await ctx.newPage();
await page.bringToFront().catch(() => {});

let teamId = null;
page.on('response', async (res) => {
  try {
    if (!/get-team-recent-files/.test(res.url())) return;
    const t = (await res.text().catch(() => '')).match(/~:team-id","~u([0-9a-f-]{36})/);
    if (t) teamId = teamId || t[1];
  } catch {}
});

try {
  await page.goto(`${BASE}/#/auth/login`, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForSelector('input[type="email"]', { timeout: 20000 });
  await page.fill('input[type="email"]', cred.email);
  await page.fill('input[type="password"]', cred.password);
  await page.getByRole('button', { name: /继续|登录|Continue/i }).first().click();
  await page.waitForTimeout(9000);
  if (!teamId) teamId = (page.url().match(/team-id=([0-9a-f-]+)/) || [])[1];
  log('logged in; team=' + teamId);

  // 打开最近一个文件，让工作区挂上 MCP 按钮
  const fileId = opt('file', '');
  const url = fileId
    ? `${BASE}/#/workspace?team-id=${teamId}&file-id=${fileId}`
    : `${BASE}/#/dashboard/recent?team-id=${teamId}`;
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 40000 });
  await page.waitForTimeout(20000);

  // 点顶栏的 MCP 胶囊 →「在此连接」
  const pill = await page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find((x) => (x.textContent || '').trim() === 'MCP');
    if (!b) return null;
    const r = b.getBoundingClientRect();
    return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) };
  });
  if (!pill) throw new Error('顶栏找不到 MCP 胶囊按钮（是否已打开某个文件的工作区？）');
  await page.mouse.click(pill.x, pill.y);
  await page.waitForTimeout(2500);

  const item = await page.evaluate(() => {
    const el = [...document.querySelectorAll('*')].find(
      (e) => e.children.length === 0 && (e.textContent || '').trim() === '在此连接');
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) };
  });
  if (item) await page.mouse.click(item.x, item.y);
  else await page.mouse.click(pill.x, pill.y + 22);      // 兜底：点胶囊下方
  await page.waitForTimeout(5000);
  log('connect clicked');
  await page.screenshot({ path: path.join(OUT, 'keepalive-connected.png') });

  const keepMs = MINUTES * 60000;
  log(`KEEPALIVE start, hold ${MINUTES} min`);
  const t0 = Date.now();
  while (Date.now() - t0 < keepMs) {
    await page.waitForTimeout(30000);
    await page.bringToFront().catch(() => {});
    await page.mouse.move(960 + (Math.random() * 20 - 10), 700).catch(() => {});
    log('alive t=' + Math.round((Date.now() - t0) / 1000) + 's');
  }
  log('KEEPALIVE end');
} catch (e) {
  log('FAILED: ' + String(e).slice(0, 400));
}
try { await browser.close(); log('browser closed'); } catch (e) { log('close failed: ' + String(e).slice(0, 200)); }
process.exit(0);
