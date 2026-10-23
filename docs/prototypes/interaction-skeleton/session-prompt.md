> 用法：把本文件全文粘贴给一个新会话。

# 任务：继续改造 Penpot 原型（设计侧，不改代码）

## 0. 先读这四份，按顺序（读完能省掉几小时的试错）

1. `tools/penpot-workflow/README.md`
   —— 工作流全貌：连接 / 提取 / 对账 / 文件级操作 / 导出格式
2. `docs/research/penpot-mcp-authoring-pitfalls.md`
   —— 26 条实测坑。**尤其**「让 Playwright 自己持有插件连接」「在查看器里实测互动」「图层名会被规范化」
3. `docs/prototypes/interaction-skeleton/{README,panel-wireframes,button-level-spec}.md`
   —— 三层文档：组合/流程 → 面板布局 → **按钮级**。要改操作流程，读第 3 层
4. `docs/research/penpot-workflow-and-reuse.md`
   —— 比例体系与参考物 `@ref-base`；§2.7.6「模板文件 vs 共享库」

## 1. 环境事实

**`mcp.json` 不在仓库里**（被 gitignore，含个人凭据）。仓库里的模板是 **`mcp.example.json`**：

```powershell
# 首次使用：复制模板，填自己的 host 与 token
Copy-Item mcp.example.json mcp.json
# 然后把 https://penpot.example.com 换成你的 Penpot 主机
# 把 <YOUR_PENPOT_USER_TOKEN> 换成你的 userToken
```

- 目标文件：`handmade （复制）`，file-id `4bfed831-199b-81b9-8008-c43c2f7fc59b`，page `main-view`
- `tools/penpot-workflow/Invoke-PenpotMcp.ps1` 会**自动**从 `mcp.json` 里解析 host + token ——
  不要自己拼 URL，也不要把 token 抄进脚本
- 浏览器：`playwright-core` + 已缓存 chromium（`%LOCALAPPDATA%\ms-playwright\chromium-1243`）。
  **不要**跑 `playwright install`（会白下 firefox/webkit）
- 工具：`tools/penpot-workflow/`（`keepalive.mjs` / `extract-panels.ps1` / `diff-panels.mjs` /
  `penpot-zip-to-snapshot.mjs`）

## 2. 第一步永远是建立连接（这一步最容易卡住）

```powershell
node tools\penpot-workflow\keepalive.mjs `
  --base <mcp.json 里 penpot 条目的主机，如 https://your.host> `
  --file 4bfed831-199b-81b9-8008-c43c2f7fc59b --minutes 45
```

**成功判据**：顶栏 MCP 胶囊提示从「在此连接」变成「**MCP 已连接**」，状态灯**变白**，
然后 `execute_code` 能返回真实数据。

**三个必知的坑**（都实测撞过）：

1. 脚本跑完就退出 → 浏览器关闭 → 插件立刻断。**必须常驻**（脚本已常驻）
2. 无头浏览器会把标签页当后台 → 定时器节流 → 插件心跳停 → MCP 报 `frozen` / `no heartbeat`。
   脚本已带四个反节流开关 + 每 30s `bringToFront`
3. 点**左边的拼图图标**只会打开通用插件列表（里面没有 MCP 插件）。要点**顶栏那个写着 MCP 的胶囊**。
   插件按浏览器保存，**不要试图"安装"它**

> 如果报 `Duplicate connection for given user token`：槽位被别的东西占了。
> 查 `Get-CimInstance Win32_Process -Filter "Name='chrome.exe'"` 里
> `ExecutablePath -like '*ms-playwright*'` 的孤儿进程并清掉。

## 3. 当前状态

文件里有 **18 块 board** + 2 个原始 group：

| 组 | 板 | 状态 |
| --- | --- | --- |
| V2 状态板 | `V2-00 legend` … `V2-04 context` | ✅ 可用；10 条互动 + 1 条流程「主流程 · 工作台 toggle」 |
| W 面板详图 | `W1` … `W8` | ⚠️ **W2 / W3 只有区块级，未到按钮级**；其余已是按钮级 |
| C 路由板 | `C1`–`C4` | ✅ 中心面板四路由的**按钮级**线稿（含真 SVG 图标） |
| 参考物 | `@ref-base` | ✅ 1 m 方板 + 0.1 m 网格 + pluginData（`ref` / `meters` / `pxPerMeter`） |

## 4. 待办（按价值排序）

1. **W2 / W3 按按钮级重建** —— 所有数值在 `button-level-spec.md` §2 / §3，**不用重新量**。
   注意：左 rail **没有"关注"按钮**（`CreatorRow` 整行 `enabled=false`）；
   右 rail 的 tab 宽度要**先修 dp 画布**（280 → 361）再定（140 → 180.5）
2. **两处 dp 画布宽高比缺陷**：`right_rail` 280×464 vs quad 0.70×0.90（**−22.4%**）、
   `grab_handle` 512×56 vs 1.00×0.08（**−26.9%**）
3. **W5 输入台** —— 用户明确说过「暂时不用管」；要做就做到**按键级**
   （数字列 16 + 字母列 40+ + 动作列 4）
4. **`theme.json` 与实现对齐** —— 3 处不符（`transport` +47%、`systemToolbar` +158%、
   `inputConsole` +179%）。**这是产品决策**：契约描述「现状」还是「目标」？先问用户
5. 若要搭「模板 + 共享库」：**组件进库、锚点进模板**（见 reuse doc §2.7.6）

## 5. 硬约束

- **本会话的职责边界：只做 Penpot 对接与设计侧产物。**
  **不编译、不跑 Gradle、不跑 `scripts\build-windows-debug.ps1`、不改任何 `*.kt` / `*.xml` / `*.json` 代码或资源。**
  验证到「快照 + 对账报告 + 浏览器截图」为止。
- **这一轮只改 Penpot，不改任何代码**（`app/`、`spatial-workbench-core/` 都不要碰）
- **元素名 = 目标系统的标识符**（如 `browse_button`）；**名字里不要用 `/`**
  （Penpot 会把 `/` 规范成 ` / `，按名匹配会失效；机器可读语义放 `shape.setPluginData`）
- **几何修正必须幂等**：Penpot 失败不回滚，会留半成品。按「目标值 − 当前值」算 delta，已达标就跳过
- **`shape.width` 是只读的**，改尺寸用 `resize(w, h)`；`shape.x/y` 是**页面绝对坐标**；
  `createText('')` 返回 `null`；源码里**不能出现 `-->`**

## 6. 改完必须验（顺序别反）

```powershell
# ① 出快照：两条腿等价；CI / 稳定性优先用 ZIP
.\tools\penpot-workflow\extract-panels.ps1
#   或：仪表盘右键 → 下载 Penpot 文件（.penpot）→
#       node tools\penpot-workflow\penpot-zip-to-snapshot.mjs "file.penpot" --out out\panel-snapshot.json

# ② 对账（比例优先，容差 ±3%）
node tools\penpot-workflow\diff-panels.mjs
```

**③ 还要看浏览器渲染** —— MCP 读回的数据 ≠ 屏幕上的样子（渲染可能滞后；
33% 缩放下 13px 的标签会看错）。用 Playwright 截图复核：
`Shift+1` 适应 → 点图层行 → `Shift+2` 缩放到选区 → 截图。

## 7. 交付前

- 跑一次 `git diff --check`
- **不要提交** `mcp.json` / `cred.json` / `out/`（都已在 `.gitignore`）
- 汇报时给**证据**：板名、dp 值、代码行号；没验证过的明确说"未验证"

## 8. 需要用户拍板的两件事（先问，别自己定）

1. `theme.json` 的定位：**契约=现状** 还是 **契约=目标**？
2. 右 rail 的「弹幕开关」放哪：并入右 rail / 移到 transport 设置菜单 / 另立任务
