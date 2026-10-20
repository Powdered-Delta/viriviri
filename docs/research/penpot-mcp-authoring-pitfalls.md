# 用 Penpot MCP `execute_code` 批量画图：实测踩坑与方法

本文记录通过 **Penpot MCP 服务器的 `execute_code` 工具**（在 Penpot 插件上下文里执行 JS）批量创建/修改设计内容时，
**实际卡住过**的问题、根因与对策。首次实测：2026-10-10，任务为在 `handmade （复制）` 上建一份
沉浸式 UI 交互骨架。

**本文不含任何端点、token 或凭据**——那些在本地排除文件 `docs/penpot-mcp-connection.md` 里
（该文件经 `.git/info/exclude` 排除，不入提交）。本文是**可提交、可共享**的方法论记录。

> 全部结论都来自实测。其中有两条我**先判断错、后经证据纠正**（#4 与 #6），
> 保留纠错过程是因为它本身就是这类工作的主要风险来源。

---

## TL;DR — 按重要性排序的五条硬约束

1. **`shape.x` / `shape.y` 是页面绝对坐标，不是相对父级** —— 最隐蔽的坑。写局部坐标会让内容落到父级板外，被 board 裁剪，表现为"整块板空白"。**这是本次唯一花了三轮才定位的问题。**
2. **`penpot.openPage()` 不同步生效** —— 同一次 `execute_code` 内切页后 `currentPage` 仍报旧页，导致图形建到错误页面。
3. **不能修改非活动页** —— `appendChild` 报 `Cannot modify a page that is not currently active`，跨页搬运几何走不通。
4. **`penpot.createText('')` 返回 `null`** —— 空字符串会让脚本在中段炸掉，并留下半成品。
5. **SES 沙箱拒绝源码里的 `-->`** —— 即使在字符串字面量里，也会在解析期被拒。

---

## 逐条

### 1. `shape.x` / `shape.y` 是【页面绝对坐标】，不是相对父级 ⚠️ 最隐蔽

**现象**：板内有 17 个子节点、读回全部 `visible: true` / `hidden: false` / `parentIsBoard: true` / 有 fill，
**但画布上整块板是空的**。`export_shape` 导出的 PNG 也**只有板背景色**（139 色 → 1 色）。

**根因**：把**板内局部坐标**写进了 `x`/`y`。板在 `(0,0)` 时局部恰好等于绝对，所以**看起来正常**
（我第一版"能用"纯属坐标巧合）；板一旦移到 `x=4540`/`x=8000`，子节点仍留在绝对 `(64,40)` / `(1120,630)`
一带 → 落在板外 → 被 board 的 **clip 剪掉** → 表现为整块空白。

**证据**：修复后读回 `player` 为 `x=18320, parentX=1120, boardX=1120`，板范围 `17200..21040` → 落在区间内。
导出 PNG 从 **1 色 / 29,411 字节** 变成 **139 色 / 314,790 字节**，色板与设计一致。

**对策**：设坐标时**必须加父级原点**：

```js
child.appendChildToBoard(parent);        // 先 append
child.x = parent.x + localX;             // 再加父级原点
child.y = parent.y + localY;
```

**自检**：不要只读自己算出来的值（那只能证明算术没错）。要同时读 `x`（绝对）、`parentX`（父级内）、
`boardX`（所属板内）三者，并断言 `x` 落在 `board.x .. board.x + board.width`。

---

### 2. `penpot.openPage()` 同一次调用内不同步生效

**现象**：脚本里 `openPage(target)` 之后，同一调用内 `penpot.currentPage.name` **仍返回旧页**；
下一次调用才变成新页。实测输出：`{requested: 'v2-…', reportedCurrent: 'main-view'}`。

**后果**：紧随其后的 `createBoard()` 把图形建到了**旧页**上。我因此把一块板建到了用户的原稿页
`main-view` 上、与原线框重叠（**靠用户截图才发现**，我的自检完全没发现）。

**对策**：
- **切页单独占一次调用**，下次调用里**重新确认 `penpot.currentPage.name`** 之后再建图形；
- 校验/清理时**不要**依赖 `currentPage`，一律用
  `penpot.currentFile.pages.find(p => p.name === …).root.children` 逐页读写；
- 用**探针板**确认落点：先建一个 100×100 的 `ZZPROBE`，找出它出现在哪个页面的 children 里，
  落对了再正式建、落错了直接 `remove()` 并中止。

---

### 3. 不能修改非活动页：`Cannot modify a page that is not currently active`

**现象**：`<page>.root.appendChild(shape)` 抛
`[PENPOT PLUGIN] Value not valid: Cannot modify a page that is not currently active. Code: :appendChild`。

**根因**：几何修改只允许作用于**当前活动页**；而切页又是异步的（#2），所以
"把图形从 A 页搬到 B 页"这条路基本走不通。

**对策**：**换页就在新页上重建**（创建永远落在活动页），不要搬运。
先切页（单独调用）→ 探针确认落点 → 再建。要清理旧页的内容，就先把该页切成活动页再删/改。

---

### 4. `penpot.createText('')` 返回 `null`

**现象**：`Cannot set properties of null (setting 'name')`，且**失败发生在脚本中段**，
前面已建好的图形**留在页面上**（留下半成品，例如一块只有 7 个子节点的板）。

**根因**：空字符串建不出文本，返回 `null`。我的图例里用空行做视觉间隔，正好踩中。

**对策**：建文本前先判空——空行只推进 y 坐标、**不建文本**；统一入口里把 blank 替换成 `' '`：

```js
const mkText = (parent, s, …) => {
  const safe = (s == null || String(s).trim() === '') ? ' ' : String(s);
  const t = penpot.createText(safe);
  if (!t) return null;              // 防御：拿不到就跳过，不让整段脚本炸掉
  …
};
```

**附带教训**：「脚本中段抛异常 + 已建内容不回滚」意味着**每次失败都会污染画布**。
所以脚本应当**幂等可重跑**：开头先按名字前缀清理自己上一轮的产物，再重建。

---

### 5. SES 沙箱拒绝源码里的 `-->`

**现象**：`Tool execution failed: Error handling task: Possible HTML comment rejected at <unknown>:N. (SES_HTML_COMMENT_REJECTED)`
——**解析期**就拒绝，脚本一行都没执行。

**根因**：`execute_code` 跑在 SES（Secure ECMAScript）锁定环境里，`-->` 是 HTML 注释结束符，
即使在**字符串字面量**里也会被拒。

**对策**：不要把 `-->` 写进代码，用 `→`。（顺带：`->` 是安全的。）

---

### 6. 曾误判：`export_shape` "只导出板背景"

**当时的判断（错）**：导出 PNG 是纯背景色，于是推断"导出路径不渲染程序化创建的子节点，或结果被缓存"
（因为两次导出**字节数完全相同**）。

**纠正后的真相**：**导出没问题**。真正原因是 **#1 的坐标错位**——子节点全在板外被裁掉，
所以导出自然只剩板背景。修复坐标后，同一 `export_shape` 立刻返回完整画面（139 色）。

**教训**：两个症状（画布空白 + 导出空白）其实是**同一个根因**。我当时把它们当成两个独立问题，
于是在错误方向上多花了一轮。**当多个观测同时异常时，先找它们共同的解释。**

---

### 7. 骨架画法的可读性（本次最"贵"的一次返工）

**现象**（用户截图才发现）：5 块板在缩小视图下看起来是**空的黑色方块**。

**根因**：
- **配色明度差过小**：我用 `bg #0b0e13` / `stage #141c27` / `panel #222c3a` / `rail #1b2431`，
  四者几乎同明度 → 面板与背景糊在一起；
- **描边过细**：2px 描边在 3840px 宽的板上换算约 0.12px，等于不存在；
- **三个完全重合的矩形**（舞台三层）把标签叠在同一位置。

**对策**：骨架/线框类产物**以可读性优先，不要为了还原产品观感牺牲可读性**：

| 项 | 建议值 |
| --- | --- |
| 背景 / 面板明度差 | 拉开，例如 `bg #10151d` → `panel #33465d` |
| 描边 | ≥10px（板 3840 宽时） |
| 字号 | ≥44px；标题 90px+ |
| 标签位置 | **放在所属面板内左上角**（放面板外会与其它元素/板头抢位、并因宽度不足而换行压到边框） |
| 重叠元素 | 用**嵌套尺寸**（如 1.68 / 1.64 / 1.60）表现层叠，避免多个标签重合 |

---

### 8. 调用间歇性 `no heartbeat for Ns`

**现象**：`The Penpot plugin tab appears to be suspended by the browser (no heartbeat for Ns)`。

**根因**：插件标签页被浏览器节流；**插件通常仍在线**（可用"同 token 连插件 WS，若返回
`Duplicate connection for given user token` 则说明在线"来判定）。

**对策**：**直接重试**，不必重新连接。实测每轮 1–6 次内必中一次；建议间隔 5–6s、上限 12–14 次。
把重试写进每个调用的包装函数里，不要手工重跑。

---

## 高性价比的自检手段（不依赖截图）

按"证据强度"从高到低：

1. **导出 + 读图**：`export_shape`（需 `shapeId`）返回内嵌 base64 PNG，
   落盘后用图像工具直接看。**这是最强的验证**，也是唯一能发现视觉问题的途径。
2. **颜色计数**：对导出的 PNG 采样统计 distinct colors。**1 色几乎一定意味着内容被裁剪或没画**——
   这是本次定位 #1 的关键信号。
3. **坐标交叉验证**：读回 `x` / `parentX` / `boardX`，断言落在板范围内（见 #1）。
4. **探针板**：先建一个可见的探针，确认它落在预期的页面与位置，再正式建（见 #2）。

```js
// 自检片段：逐板读回几何，并标出越界的子节点
const out = penpot.currentFile.pages.find((p) => p.name === 'main-view').root.children.map((b) => ({
  board: b.name, x: Math.round(b.x), w: Math.round(b.width), kids: (b.children || []).length,
  outOfBounds: (b.children || [])
    .filter((c) => c.x < b.x || c.x + c.width > b.x + b.width || c.y < b.y || c.y + c.height > b.y + b.height)
    .map((c) => c.name).slice(0, 5),
}));
return out;
```

---

## 建议的脚本骨架

```js
// 1) 探针：确认新建落在哪一页
const probe = penpot.createBoard(); probe.name = 'ZZPROBE'; probe.resize(120, 120);
const landed = penpot.currentFile.pages
  .filter((p) => (p.root.children || []).some((c) => c.id === probe.id)).map((p) => p.name);
probe.remove();
if (landed.length !== 1 || landed[0] !== 'EXPECTED_PAGE') return { aborted: true, landed };

// 2) 幂等清理：按名字前缀删掉自己上一轮的产物
page.root.children.filter((c) => /^V2-\d\d/.test(c.name)).forEach((c) => c.remove());

// 3) 建图形：先 append，坐标 = 父级原点 + 局部；文本判空
// 4) 返回自检结果（板名/子节点数/越界项），供下一步断言
```

**注意**：任何"能跑通一次"的脚本都要在**非零起点**（父级偏移 ≠ 0）下再验一次——
本次 #1 之所以能骗过三轮自检，正是因为第一版父级原点恰好是 `(0,0)`。

---

## 互动原型（Prototype）API —— **可用**

Penpot 的插件 API **能建可互动原型**。签名（查 `penpot_api_info {type:'Board', member:'addInteraction'}`）：

```ts
addInteraction(trigger: Trigger, action: Action, delay?: number): Interaction
interactions: Interaction[]        // 只读
removeInteraction(interaction: Interaction): void
```

官方示例：

```js
shape.addInteraction('click', { type: 'navigate-to', destination: anotherBoard });
```

流程（原型入口）由 `Page` 掌管：

```ts
page.flows: Flow[]
page.createFlow(name: string, board: Board): Flow     // 注意参数顺序：name 在前
page.removeFlow(flow: Flow): void
```

文档补充：若某互动**开启了一条流程**，而所在板尚未属于任何流程，
**会自动建一条以该板为起点的流程**（与编辑器行为一致）。

### 实测范例（本项目 `handmade （复制）`）

```js
const boards = penpot.currentPage.root.children.filter((c) => c.type === 'board');
const S1 = boards.find((c) => /^V2-01/.test(c.name));
const S2 = boards.find((c) => /^V2-02/.test(c.name));
S1.children.find((c) => c.name === 'player')
  .addInteraction('click', { type: 'navigate-to', destination: S2 });
penpot.currentPage.createFlow('主流程 · 工作台 toggle', S1);
```

10 条互动一次建成并读回校验通过。

### 互动相关的坑

| # | 现象 | 根因 | 对策 |
| --- | --- | --- | --- |
| 10 | 用 `!==` 比较两个 shape / flow，**永远不相等** | **Penpot 的 proxy 不是同一性稳定的**：每次属性访问都返回新的 proxy 对象 | **一律按 `id` 比较，绝不用 `!==`**。我因此误删了**两条** flow（含想保留的那条） |
| 11 | `flow.id` 是 `undefined` | 本版本的 `Flow` proxy 不暴露 `id`（`Object.keys` 里也没有） | 去重改用 `name` 或**数量**判断，不要依赖 `Flow.id` |
| 12 | `shape.interactions.push(x)` **静默无效**；`shape.interactions = [...]` 报 `Cannot set property ... which has only a getter` | `interactions` 是**只读 getter**，返回值上的修改不会回写 | 只能用 **`addInteraction(...)`** 增加、**`removeInteraction(...)`** 删除 |
| 13 | 走 `Object.getPrototypeOf(shape)` 枚举方法，只拿到 `constructor` | plugin proxy **不枚举方法** → 用 JS 反射**探测不到** `addInteraction` 这类 API | **以 `penpot_api_info` 文档为准**，不要用运行时反射判断"某 API 是否存在" |

### 互动自检片段

```js
return penpot.currentPage.root.children
  .filter((c) => c.type === 'board')
  .map((bd) => ({
    name: bd.name,
    own: (bd.interactions || []).length,
    kids: (bd.children || [])
      .filter((c) => (c.interactions || []).length > 0)
      .map((c) => c.name + ':' + c.interactions.length),
  }));
```

### 已知限制

- 插件 API 只能设**基础组合**（如 `'click'` + `{type:'navigate-to'}`）；
  overlay 位置、复杂触发器一类的原型属性仍需在 UI 里手工设置；
- **无法在无头环境里"播放"原型** —— 必须由人在 Penpot UI 的「原型」页签里预览。
  因此本仓库的验证边界是：**API 建的互动能读回** ≠ **人点得动**（后者必须人工确认）。

---

## 用 Playwright 做视觉复核（本机可用，**这是缺失的那一环**）

MCP 只能读回**数据**；它无法证明"画面对不对、互动点不点得动"。
本机已具备条件：**Playwright 浏览器缓存已存在**，无需下载。

### 环境

| 项 | 值 |
| --- | --- |
| 包 | `playwright-core@1.62.1`（装在 `D:\Workspace\_refs\penpot-e2e`，**仓库外**） |
| 浏览器 | `%LOCALAPPDATA%\ms-playwright\chromium-1243\chrome-win64\chrome.exe`（**已在缓存里，不要重新下载**） |
| 版本对应 | `chromium-1243` ↔ `playwright-core@1.62.1`（见 `ms-playwright\.links`） |

**务必用 `playwright-core` + 显式 `executablePath`**：`playwright` 全量包会触发浏览器下载，而 `raw.githubusercontent` / CDN 在本机常被重置。

### 可复用流程

```text
1  goto #/auth/login → 填 input[type=email] / input[type=password] → 点「继续」
2  goto #/workspace?team-id=<team>&file-id=<file>        ← 直达，不要点 dashboard 卡片
3  等 ~20s（工作台很重）
4  逐板：Shift+1 回全景 → 点左侧图层行 → Shift+2 缩放选中 → 截图
5  读图（image 输入）核对
```

**取 file-id**：监听 `get-team-recent-files` 响应，按 **transit** 格式解析 ——
先定位 `~:name","<文件名>"`，其后第一个 `~u<uuid>` 就是该文件的 id。

```js
const a = txt.indexOf(`~:name","${fileName}"`);
const id = txt.slice(a, a + 400).match(/~u([0-9a-f-]{36})/)[1];
```

### 复核环节的坑

| # | 现象 | 根因 | 对策 |
| --- | --- | --- | --- |
| 14 | 用 `storageState` 复用会话打开工作台，得到**纯空白页**（≈9 KB 纯色） | 该 SPA 在复用存储态时渲染不出来 | **每次都重新登录**（登录后 dashboard 能正常渲染） |
| 15 | 在 `#/dashboard` 上 `click()` 文件卡片**不会打开文件**（卡片只是被选中） | 卡片不是导航链接 | **不要点卡片**：从 API 响应取 file-id，按 URL 直达工作台 |
| 16 | 用 `"name":"X"` 这类 JSON 正则解析文件列表，**永远匹配不到** | 该 API 返回 **transit** 格式（`~:name","X"`），不是纯 JSON | 按 transit 模式解析（见上） |
| 17 | `getByText(name)` 同时命中**图层行**和**画布上的板名标签**，`first()` 取到的那个不可点 → 超时 | 同名元素有两个 | 用 `evaluateHandle` 按 `getBoundingClientRect().x < 320` 挑左侧图层面板里的那个；每轮先 `Shift+1` 回全景再选 |
| 18 | **插件改动会落盘，但渲染可能滞后**：刚改完去复核，浏览器仍显示旧内容 | 提交周期与渲染不同步（实测 `revn` 122→125 逐步推进） | 复核到不一致时，**先做一次无关小改动触发提交，再复核**。**不要只凭一张截图就断言"改动未生效"** |
| 19 | 「无条件按子串改写」误伤：按 `contextTabs` 子串匹配时，连带命中了图例板里的一行**索引文字**，把整行覆盖掉 | 匹配条件过宽，且没有先列出命中项 | **改写前必须先把所有命中的 (板, 形状名, 原文) 完整列出**并确认，再执行替换 |

### 一条方法论

**浏览器截图是 ground truth，MCP 读回只是数据。** 两者不一致时：
1. 先怀疑**渲染滞后**（#18），做一次提交再复核；
2. 再怀疑**自己读错了图** —— 33% 缩放下原子标签只有 ~13px，
   本次我就把已经改好的 `[合集]` 误读成了旧的 `[播放列表/合集]`，白追了一轮；
3. 只有排除了这两者，才是真的数据不一致。

---

## 在查看器里实测互动（可脚本化，**已跑通**）

原型预览是个**独立的查看器页面**，可以直接用 URL 打开并脚本化点击。

### 打开方式

```text
#/view?file-id=<file>&page-id=<page>&section=interactions&index=<N>
```

生成方式二选一：
- 工作区里按 **`G` `V`**（或点右上角 `预览模式`）→ 新开一个 viewer 标签页；
- **直接拼 URL**（更快，且可指定 `index`）。

### `index` 与板序的关系 ⚠️

`index` **不是** `page.root.children` 的顺序，而是**倒序**：
`index 0` = 最后一块板。实测（15 块板）：

| 板 | index |
| --- | --- |
| `W8 stage` | 0 |
| `S2 · workbench-on`（V2-02） | 10 |
| `S1 · quiet-watch`（V2-01） | 11 |
| `S0 · 组件目录`（V2-00） | 12 |

页面底部会显示 `N / 15`，`body.innerText` 的前两行就是
`N / 15` 与**板的标题**——用它判断"现在在哪一块板"，比看 URL 可靠。

### 关键：查看器默认 100% 缩放，必须按 `F` 适合

100% 时 3840×2160 的板只露出左上角，**右 rail / 底部的互动全部在屏幕外** —— 这时点击会落在空白处，看起来像"互动没生效"。

按 **`F`**（`适合 - 缩小以适应`）后，板被缩到可见。**缩放后先量出板的屏幕包围盒**，再换算点击坐标：

```js
const svgs = [...document.querySelectorAll('svg')]
  .map((s) => { const r = s.getBoundingClientRect(); return { w: r.width, h: r.height, x: r.x, y: r.y }; })
  .filter((r) => r.w > 500);
// 实测（板 3840x2160，视口 1920x1200）：{ w: 1920, h: 1080, x: 0, y: 84 }
// → scale = 1920/3840 = 0.5；点击屏幕坐标 = (boardX * 0.5, 84 + boardY * 0.5)
```

### 实测结果（本项目 `handmade （复制）` 的交互图）

| 转移 | 触发元素 | 结果 |
| --- | --- | --- |
| S1 → S2 | 点幕布（`player`） | ✅ |
| S2 → S3 | `searchEntry` | ✅ |
| S3 → S2 | 搜索头「返回」 | ✅ |
| S2 → S4 | `contextTabs` | ✅ |
| S2 → S1 | 虚空热点 | ✅ |

**5/5 通过** —— 由 MCP 写入的 `navigate-to` 互动在查看器里确实可点。

### 这一段踩的坑

| # | 现象 | 根因 | 对策 |
| --- | --- | --- | --- |
| 20 | 查看器里点击**完全没反应** | 默认 100% 缩放，目标在屏幕外 | 先按 **`F`** 适合 |
| 21 | 按了 `F` 后**第一次点击仍无效** | 缩放动画未稳定 | 按 `F` 后等 **≥2.5s** 再点 |
| 22 | 拖拽平移**没生效** | 拖动终点算到了视口外（x 为负），拖拽被截断 | 不要用拖拽平移；**用 `F` 适合**，或让起止点都落在视口内 |

---

## 让 Playwright 自己持有插件连接（**已跑通**）

插件离线时（`No Penpot instance connected for user token`）可以**由脚本自己把连接建起来**，
前提是**浏览器要常驻** —— 脚本跑完就退出的话，浏览器一关，插件立刻断。

```text
1  登录 → 打开工作台
2  点顶栏那个写着 MCP 的【胶囊按钮】
   （不是左边的拼图图标！拼图打开的是通用插件列表，里面只有默认插件）
3  胶囊下方弹出「在此连接」→ 点它
4  胶囊提示变为「MCP 已连接」、状态灯变【白色】 → 连接建立
5  【保持浏览器存活】（while 循环 + 定期轻微鼠标移动，避免被当作后台标签节流）
6  MCP 调用从【另一个进程】发起（curl / PowerShell）
```

实测：常驻脚本起来后，同一 token 的 `execute_code` 立即返回真实数据（`revn: 127`）。

| # | 现象 | 根因 | 对策 |
| --- | --- | --- | --- |
| 23 | 点了 MCP 胶囊、也点了「在此连接」，连接**仍然没建立** | 脚本 `process.exit` 退出 → 浏览器关闭 → 插件随之断开 | 必须把浏览器跑成**后台常驻任务** |
| 24 | 在**全新无头上下文**里看不到 MCP 插件 | Penpot 的「已安装的插件」是**按浏览器**保存的；拼图按钮打开的是通用插件列表 | 不要试图"安装"它 —— 直接点 **MCP 胶囊**走「在此连接」 |
| 25 | 本机 `4400–4403`（插件静态站）全部关闭，公网 manifest 也 404 | 插件**早已安装进浏览器**，不需要本机插件服务 | 无需启动插件服务；依赖已安装的插件 + 胶囊连接 |

**代价**：连接归**这个常驻浏览器**所有。你自己的标签页此时再连会撞
`Duplicate connection for given user token`。用完记得停掉常驻任务。

### 提取器：把线稿变成机器可读的设计快照

`extract-panels.ps1`（仓库外 `D:\Workspace\_refs\penpot-e2e\`）读取 `W1–W8` 板，输出
`out/panel-snapshot.json`：每个面板的 `dpCanvas` / `pxPerDp` / `framePx`，以及每个元素的
**真实 dp 矩形**与互动数。

实测（8 面板 / 61 元素）dp 往返**精确**：

```text
W1  browse_button  dp=( 18,  6)  56x44      ← 与 controls.xml 的 56x44 一致
W3  contextTabs    dp=(  0,  0) 280x40
W4  card00         dp=( 16,112) 237x213
W6  searchEntry    dp=(300, 36)  56x56
W8  videoSurface   dp=(  0,  0) 1280x720
```

**错误处理已加固**（不要让它退化成 JSON 解析错）：
`frozen` / `no heartbeat` → 重试；`No Penpot instance connected` → 立刻提示"去点连接"；
`Tool execution failed: …` → 抛出原文。

---

## 图层名会被规范化：`/` 两侧被插入空格（**已实测**）

**Penpot 会改写形状名里的 `/`** —— 写进去的 `@ref/base`，读回来是 **`@ref / base`**。

逐字符实测（`@ref/base` 共 11 字符）：

```text
64 114 101 102  32  47  32  98  97 115 101
 @   r   e   f  空格  /  空格  b  a   s  e
```

**这条同时解释了之前一个"我以为是自己的错"的现象**：给原子块起名 `contextTabs [播放列表/合集][相关视频]`
之后，读回来的名字是 `contextTabs [播放列表 / 合集]`。我当时以为是自己的间距，其实是 Penpot 规范化的。

| # | 现象 | 根因 | 对策 |
| --- | --- | --- | --- |
| 26 | 按名字前缀匹配**下次就不命中**（幂等清理失效、可能产生重复） | `/` 被规范化成 ` / `，字面量匹配失败 | ① **命名避免用 `/`**（用 `-` 或 `_`）；② 匹配一律用**容错正则**：`/^@ref[\s\/_-]*base/`；③ 名字只作"人读标签"，**机器可读信息写进 `pluginData`** |

**推荐做法**：视觉标签可以写 `@ref/base`（好看、可读），
但**图层名用 `@ref-base`**，机器可读的语义放在 `pluginData`（`shape.setPluginData` 可用）。
