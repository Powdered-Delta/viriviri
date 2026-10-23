# Penpot 工作流（设计 → 代码）

把 **Penpot 上的版式**变成**可对账的数据**，从而能机械地发现"设计改了哪些 dp / 哪些比例不对"。

```text
① 连接    keepalive.mjs          无头浏览器常驻，持有 Penpot 插件的 MCP 连接
② 提取    extract-panels.ps1     Penpot 板 → panel-snapshot.json（真实 dp，精确往返）
③ 对账    diff-panels.mjs        快照 + 代码真值 → design-code-diff.md（比例优先，Tier 1–4）
```

**为什么要对账**：主题作者（二开）会以契约为起点。契约与实现不一致时，
作者产出的版式会在真机上以**不同比例**出现，而**看起来像他自己的错**。
对账器把这类问题在提交前就变成红灯。

---

## 环境准备（一次性）

| 需要 | 说明 |
| --- | --- |
| Node 18+ | 跑 `*.mjs` |
| `playwright-core` | 装在工作流目录：`npm i playwright-core` |
| 已缓存的 chromium | 本机已有 `%LOCALAPPDATA%\ms-playwright\chromium-1243\`。**不要重新下载**（CDN 常被重置） |
| PowerShell 7 / Windows PowerShell | 跑 `*.ps1` |
| `cred.json` | `{"email":"…","password":"…"}`，放工作流目录。**不要提交**（已 gitignore） |
| **`mcp.json`** | **不入库**（含个人 userToken，已 gitignore）。用仓库根的模板生成：见下 |

**`mcp.json` 从模板生成**（它是唯一存放 host + token 的地方）：

```powershell
Copy-Item mcp.example.json mcp.json
# 然后把 https://penpot.example.com 换成你的 Penpot 主机
# 把 <YOUR_PENPOT_USER_TOKEN> 换成你的 userToken（Penpot 里生成）
```

`mcp.example.json` 是**入库的、无凭据的**模板，含两条 Penpot 条目：

| 条目 | 形态 | 谁用 |
| --- | --- | --- |
| `penpot` | `npx mcp-remote <url> --transport http-only` | Claude Code / DSH 等 MCP 客户端 |
| `penpot-http` | `{"type":"http","url":…}` | **本目录的脚本**（`Invoke-PenpotMcp.ps1` 直接读这个 `url`） |

> 脚本会**自动**从 `mcp.json` 解析 host + token —— **不要把 token 抄进脚本、也不要写进文档**。
> 提交范围内只允许出现占位符。

> **`playwright-core` 一定要配 `executablePath`** 指向已缓存的 chromium。
> 装全量 `playwright` 包会触发浏览器下载，而本机到 CDN 的连接不稳定。

---

## 第 1 步 · 连接

```powershell
node tools\penpot-workflow\keepalive.mjs --base https://<你的 Penpot 主机> --file <file-id> --minutes 45
```

它会：登录 → 打开该文件的工作区 → 点顶栏 **MCP 胶囊** →「**在此连接**」→ **常驻 45 分钟**。

**成功的标志**：胶囊提示变成「**MCP 已连接**」，状态灯**变白**；随后 MCP 调用能返回真实数据。

**三个必须注意的点**（都是踩过的坑）：

| 现象 | 根因 | 对策 |
| --- | --- | --- |
| 点了「在此连接」但 MCP 仍报未连接 | 脚本退出 → 浏览器关闭 → 插件随之断 | 必须**常驻**（本脚本已常驻） |
| 插件在线却报 `frozen` / `no heartbeat` | 无头浏览器把标签页当后台 → 定时器节流 → 插件心跳停 | 本脚本已带 `--disable-background-timer-throttling` 等四个开关 **+ 每 30s `bringToFront`** |
| 在全新无头上下文里看不到插件 | 「已安装的插件」**按浏览器**保存 | 不要去装插件；**直接点 MCP 胶囊** |

**代价**：连接归**这个常驻浏览器**所有；此时别人的标签页再连会撞 `Duplicate connection for given user token`。
用完停掉常驻任务即可。

---

## 第 2 步 · 提取（**两条腿，输出同构**）

| 路径 | 命令 | 依赖 | 适用 |
| --- | --- | --- | --- |
| **A · MCP 插件** | `extract-panels.ps1` | 浏览器常驻 + WebSocket（第 1 步） | 交互式；需要**写** Penpot |
| **B · ZIP 解析** | `penpot-zip-to-snapshot.mjs` | **只要一个 `.penpot` 文件** | **CI / 批量 / 需要稳定** |

两条路的产出**同构**（同样含 `dpCanvas` / `pxPerDp` / `framePx` / `elements` / `labels`），可互相替换。
**已逐项验收**：8 块板 × (dpCanvas / pxPerDp / elementCount / **每个元素的 dp 矩形**) **全部一致，0 差异**。

### A · MCP 插件

```powershell
.\tools\penpot-workflow\extract-panels.ps1
# 默认抓 ^[CW]\d （W1–W8 面板详图 + C1–C4 路由板），输出 out\panel-snapshot.json
```

### B · ZIP 解析（**不需要插件**）

```powershell
node tools\penpot-workflow\penpot-zip-to-snapshot.mjs "C:\path\file.penpot" `
     --out out\panel-snapshot.json [--pattern "^[CW]\d "]
```

`.penpot` 的获取：仪表盘 → 文件卡片右键 → **`下载 Penpot 文件（.penpot）`**（见「文件级操作」）。

**为什么要有这条腿**：MCP 那条依赖浏览器常驻 + WebSocket，是整个流程最脆弱的一环
（节流 / `frozen` / 孤儿浏览器占槽位，实测撞过三次）。ZIP 只依赖一次 HTTP 下载，可离线、可进 CI。

**`--pattern` 也能取原型图**：用 `--pattern '^V2'` 会解析出状态板的互动边
（`links: [{ from, trigger, action, to, toId }]`），导出里**确实带互动**
（实测 10 条全在，`{ eventType, actionType, destination }`）。

### 导出文件的真实结构（实测，revn 135）

```text
file.penpot                       ← 就是 ZIP（magic 50 4b 03 04）
  files/<fileId>.json             ← 文件元数据（name / revn / id）
  files/<fileId>/pages/<pageId>.json            ← 页面元数据（name / flows / index）
  files/<fileId>/pages/<pageId>/<shapeId>.json  ← 【每个形状一个 JSON】共 651 个
  objects/<uuid>.json + .png      ← 媒体对象（图片资产）
  manifest.json
```

**读这个格式要注意的四点**（都踩过）：

| # | 事实 | 影响 |
| --- | --- | --- |
| 1 | 形状树靠 `parentId` + `shapes: [子id...]`（**`shapes` 才是绘制顺序**），页面 JSON **不含**形状 | 必须把全部形状载入成 map 再走树 |
| 2 | 文本在 `content.children[0].children[0].children[0].text` | 要**递归**收集所有 `text` 字段 |
| 3 | 互动是**平铺字段**：`{ eventType, actionType, destination }`，**不在** `action.destination` 下；`destination` 是**目标板的 frame id** | 我第一版按嵌套去取，结果 `links` 全是 0 |
| 4 | dp 标注里的乘号**可能是全角 `×`**（MCP 写 ASCII `x`、手绘常是全角） | 正则要写成 `[x×X]`，否则 dpCanvas 全为 null |

**用法假设**：不支持 ZIP64（>4 GB）；面板框取「**面积最大的非文本子形状**」，因此不依赖标签叫
`panel` 还是 `xxx_panel`。

### 产出结构（两条腿相同）

```jsonc
{
  "file": "…", "page": "main-view", "extractedAt": "…",
  "source": "mcp" | "penpot-zip",
  "panels": [{
    "board": "W1 transport",
    "dpCanvas": { "w": 460, "h": 132 },      // 面板的 dp 画布
    "pxPerDp": 3.2609,                        // 图上的换算比
    "framePx": { "x": 8200, "y": 2860, "w": 1500, "h": 430 },
    "elements": [{ "name": "browse_button", "dp": { "x": 18, "y": 6, "w": 56, "h": 44 }, "interactions": 0 }],
    "labels": ["…"]
  }]
}
```

> ZIP 路径额外带 `revn` 与 `links`（MCP 路径的 `interactions` 是计数）。

**提取的前提约定**（画板时必须遵守，否则机械对账失效）：

1. **元素名 = 目标系统的标识符**（如 `@+id/browse_button`）；
2. 板上要有 `panel  <W> x <H> dp  scale …` 这行标注 —— 提取器靠它拿 dp 画布；
3. **命名避免 `/`** —— Penpot 会把名字里的 `/` 规范成 ` / `，按名字匹配会失效
   （用 `-` 或 `_`；机器可读语义放 `pluginData`）。

---

## 第 3 步 · 对账

```powershell
node tools\penpot-workflow\diff-panels.mjs
# 读 out\panel-snapshot.json，输出 out\design-code-diff.md
```

四级对账：

| Tier | 比什么 | 能否机械 |
| --- | --- | --- |
| 1 | 面板 **quad / dp 画布 / 宽高比** | ✅ |
| 2 | **XML 元素尺寸**（id 与设计稿同名时） | ✅ 比例优先 |
| 3 | Compose 面板 | ❌ 需人工（快照作目标值） |
| 4 | **`theme.json` 尺寸 vs 代码 quad** | ✅ |

### 判据原则：**比例优先，不是绝对 dp**

组件库表达的是**比例**，不是某个画布下的绝对 dp。因此：

- **主判据**：`元素dp / 面板dp画布`，容差 **±3%**；
- **参考列**：`Δdp`（绝对差异，仅供人看）；
- `wrap_content` / `match_parent` → 标为「内容决定，不参与比例判定」；
- `0dp + layout_weight` → 标为「权重填充」。

> 这条原则是被纠正后才确立的：对账器第一版用"绝对 dp 逐值相等"，
> 把 `seek_bar` 的 `0dp + weight` 误报成不一致 —— **判据选错，工具会持续产生误导性的红灯。**

---

## 比例体系与参考物

跨面板的比较**必须落到米**，而米需要一个**锚**：

```text
@ref-base   1.00 m × 1.00 m 方板（图层名不要用 "/"，Penpot 会改写）
  图内        外框 + 0.1 m 网格 + 0.5 m 中心十字
  pluginData  ref=base, meters=1.0,1.0, pxPerMeter=1000

  metersPerPx      = ref.meters / ref.px        → 0.001 m/px
  元素米数（跨面板） = 元素px × metersPerPx
  元素面板内比例    = 元素dp / 面板dp画布         ← 分母见各板标注
```

**为什么不用舞台当锚**：`mediaStage.sizeMeters` 在 `themeableGeometry` 里**可被主题改** ——
锚若随被测物变化就不是锚。**参考物必须不可主题化。**

详见 [`docs/research/penpot-workflow-and-reuse.md`](../../docs/research/penpot-workflow-and-reuse.md) §2.7。

---

## 文件级操作（**仪表盘，不是插件**）

Penpot 插件是**文件作用域**的（`penpot.currentFile` / `createPage()`），
**文件的新建 / 复制 / 改名 / 删除都不在插件能力范围内** —— 它们是仪表盘动作。
所以「批量初始化若干项目文件」只能走 UI 或 HTTP API。

以下为**实测**过的操作方法（`files-ops.mjs` / `files-ops2.mjs` / `files-ops3.mjs`）。

### 操作对照表

| 操作 | 入口 | 关键 UI 事实 |
| --- | --- | --- |
| **新建文件** | 仪表盘项目区的 `add-file-btn` | 点击后**直接进入新文件工作区**（URL 带上新 `file-id`）；默认名 `新建文件 1` |
| **新建项目** | 仪表盘右上 `+ 新项目` | 项目用于分组文件 |
| **复制文件** | 文件卡片**右键** → `复制` | 生成 `<原名> （复制）` |
| **重命名** | 文件卡片右键 → `重命名` | ⚠️ **卡片内联输入框，不是弹窗**（见下） |
| **删除** | 文件卡片右键 → `删除` | ⚠️ 有确认弹窗，按钮是 **`[取消]` / `[删除文件]`** |
| **加为共享库** | 文件卡片右键 → `添加为共享库` | 组件复用的**正解**（见下） |
| **整文件导出** | 文件卡片右键 → `下载 Penpot 文件（.penpot）` | ⚠️ 得到的是 **ZIP**（见下） |

### 重命名：内联输入，不是弹窗

点 `重命名` 后**不会弹窗**，而是把卡片名称变成 `<input>`：

```js
// 正确做法：读/写当前焦点元素，然后回车
const val = await page.evaluate(() => document.activeElement?.value);
await page.evaluate((to) => {
  const a = document.activeElement;
  a.value = to;
  a.dispatchEvent(new Event('input', { bubbles: true }));   // 必须派发，否则框架不认
}, '新名字');
await page.keyboard.press('Enter');
```

> 输入框 class 含 `inline_edition`。**用 `document.querySelector('[class*="modal"]')` 找不到它** ——
> 我第一版就是这么失败的：改名"成功"了但名字变成空串（读的是 `<input>` 的 `textContent`，恒为空）。

### 删除：确认弹窗按钮文案

右键菜单的 `删除` 会打开确认弹窗，按钮为 **`取消`** 与 **`删除文件`**（不是"确定"）。
按位置找不到时，用文案 `/删除|delete/i` 匹配更稳。

> 仪表盘右上还有 **`已删除`** 入口（回收站），删除是可恢复的。

### 下载：`.penpot` 就是 ZIP，**每个形状一个 JSON**

```text
handmade （复制）.penpot          6.38 MB, 719 entries, magic 50 4b 03 04 (PK)
  files/<fileId>.json
  files/<fileId>/pages/<pageId>.json
  files/<fileId>/pages/<pageId>/<shapeId>.json      ← 每个形状一个文件
```

**这条比它看起来重要**：既然导出是**纯 ZIP + JSON**，就可以做一条**完全绕开插件**的管线：

```text
仪表盘下载 .penpot  →  解 zip  →  读 <shapeId>.json  →  生成 theme.json
```

好处：**不依赖插件的 WebSocket 连接**（那是最脆弱的一环 —— 见「第 1 步」的三个坑），
也不需要无头浏览器；纯 HTTP + 文件解析即可，适合放进 CI。

### 库与模板：Penpot 原生就有

仪表盘底部有 **`库和模板`** 区，内置模板（`Design tokens starter kit` / `Penpot Design System` /
`Wireframe library` / `Prototype template` / `UI mockup example` / `Lucid icons`），每个带 `+` 一键加入项目；
左侧栏另有独立的 **`库`** 入口。

**载体怎么选**（组件库 vs 参考物）见
[`docs/research/penpot-workflow-and-reuse.md`](../../docs/research/penpot-workflow-and-reuse.md) §2.7.6：

- **`@ref-base` 参考物 / 基准画布表 / 命名约定** → **模板文件**（页面级产物，复制即得；共享库不共享页面）
- **按钮 / 列表项 / 图标等组件** → **共享库**（活的引用：改库即改所有引用方；复制模板做不到）

---

## 出问题先看这里

| 症状 | 去看 |
| --- | --- |
| 连接 / 节流 / 插件离线 | 本文「第 1 步」+ [`penpot-mcp-authoring-pitfalls.md`](../../docs/research/penpot-mcp-authoring-pitfalls.md) |
| `execute_code` 报错 | 同上（25 条坑，含 `createText('')` 返回 null、`shape.x/y` 是绝对坐标、`width` 只读、proxy 不同一等） |
| 提取出来的 dp 是空的 | 板缺少 `panel <W> x <H> dp` 标注行 |
| 对账报"设计中未列" | 元素名与代码 id 不一致（第 2 步约定 1） |
| 想改操作流程 | [`button-level-spec.md`](../../docs/prototypes/interaction-skeleton/button-level-spec.md)（按钮级，含返回键漂移 672 dp 的实测） |

---

## 不在本工作流范围内

- **不能**用插件 API 播放原型：预览是独立页面，需查看器 + `F` 适合 + 坐标换算（见 pitfalls 的「查看器实测互动」）；
- **不能**在无头环境"点"原型来替代人眼确认；
- **不改代码**：`diff-panels.mjs` 只读仓库、只写报告。

---

## 将来抽成独立仓库

本目录刻意做成**自包含**（无仓库内依赖，只读 `mcp.json`）。
抽出去时带上：[`docs/research/penpot-workflow-and-reuse.md`](../../docs/research/penpot-workflow-and-reuse.md) §2.7.5 列出的
「项目初始化应产出的 5 样」（参考物 / 基准画布表 / 组件 `ratio` / 比例检查器 / 命名约定）。
