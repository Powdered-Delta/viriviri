# 按钮级还原规格（Button-Level Spec）

本文是**按钮/tab 级**的还原规格：每个可点元素的 id、尺寸、位置、状态、语义与**代码出处**。

比 [panel-wireframes.md](panel-wireframes.md) 更细一层：

| 层 | 文档 | 粒度 |
| --- | --- | --- |
| 面板组合 | [README.md](README.md) | 哪个状态下显示哪些面板 |
| 面板布局 | [panel-wireframes.md](panel-wireframes.md) | 面板内的区块与主要元素 |
| **按钮级** | **本文** | **每个按钮 / tab / chip / 图标按钮** |

**目的**（来自实测反馈）：**看清并修掉操作流程里别扭的地方**，例如返回控件在面板内左右漂移。
因此本文的重点不是"画得有多全"，而是**把可点元素的位置与语义钉死**，让漂移可见、可改。

单位：**dp**。位置是**相对该面板左上角**的 dp（面板自己的 dp 画布坐标系）。

---

## 0. 已发现的流程问题（本文的产出）

### 0.1 ⚠️ 返回控件在中心面板内**漂移 672 dp**（面板宽的 87.5%）

| 路由 | 返回控件 | 位置 | 语义 | 出处 |
| --- | --- | --- | --- | --- |
| `WORKBENCH_EMPTY` | **无** | — | — | `RecommendationUi.kt:272-293` |
| `RECOMMENDATIONS` | **无** | — | `onBack = null` | `:327` |
| `SEARCH_RESULTS` | 有 | **x ≈ 24（行首）** | `returnToSearchEmpty` | `VideoListFilterBar.kt:59-67`、`:327` |
| `SEARCH_EMPTY` | 有 | **x = 696（最右）** | `closeSearchWorkspace` | `RecommendationUi.kt:450-459` |

**两个问题，不止一个**：

1. **位置漂移**：同一功能的控件在两条路由间横跨 672 dp；
2. **语义不一致**：同一个返回图标，一条路由是"回到搜索空态"、另一条是"关闭整个搜索工作区"。
   位置不同已经够糟，**含义还不同**。

### 0.2 左 rail 空态有 **147 dp 留白**

主体内容实测仅 **273 dp**，可滚区 **410 dp**（464 − 54 固定底栏）→ 默认态约 1/3 高度是空的。

---

## 1. `center_content_panel` — 中心面板（768 × 615 dp）

### 1.1 通用度量

```
Column padding 16 dp
行间距 8 dp（Arrangement.spacedBy(8.dp)）
筛选栏自身 padding: horizontal 8 / vertical 4 → 内容起点 x = 24
FilterButton = contentPadding(h 8×2 = 16) + Text(11 sp) → 宽 ≈ 16 + 11 × 字数
IconButton  = 48 × 48 dp（Material 默认）
行内间距 = 4 dp
```

### 1.2 `VideoListFilterBar` 行 1 — 按钮逐个列出

| # | 元素 | id / 类型 | 宽 × 高 | 出现条件 | 选中态 | 出处 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 返回 | `IconButton` | 48 × 48 | **仅 `SEARCH_RESULTS`** | — | `VideoListFilterBar.kt:59-67` |
| 2 | 综合排序 | `FilterButton` | 60 × 36 | 总是 | `sort == COMPREHENSIVE` | `:48`, `:69-76` |
| 3 | 最新发布 | `FilterButton` | 60 × 36 | 总是 | `sort == LATEST` | 同上 |
| 4 | 最多弹幕 | `FilterButton` | 60 × 36 | 总是 | `sort == DANMAKU` | 同上 |
| 5 | 最多收藏 | `FilterButton` | 60 × 36 | 总是 | `sort == FAVORITES` | 同上 |
| 6 | 更多筛选 | `FilterButton` | 60 × 36 | 总是 | `date != ANY \|\| duration != ANY` | `:77-83` |
| 7 | 刷新 / 回顶 | `IconButton` | 48 × 48 | 总是 | — | `:84-90` |
| 8 | 布局切换 | `IconButton` | 48 × 48 | 总是 | — | `:91-97` |

**第 7 项是"一个按钮两种功能"**：`isAtTop` 为真时是 `Refresh`（刷新列表），否则是 `ArrowUpward`（回到顶部）。
图标与语义都随滚动位置变 —— **这是第二个"同一控件位置不变但含义变"的例子**，比返回键温和，但同样值得决策。

**行 1 总宽**：

| 路由 | 计算 | 合计 | 可用宽 | 是否横向滚动 |
| --- | --- | --- | --- | --- |
| `RECOMMENDATIONS` | 5×60 + 2×48 + 6×4 | **420 dp** | 720 dp | 否 |
| `SEARCH_RESULTS` | 420 + 48 + 4 | **472 dp** | 720 dp | 否 |

> 两行都包在 `horizontalScroll` 里（`:55`, `:101`），所以**窄面板下会横向滚动**而不换行 ——
> 主题若把 dp 画布改窄，这是第一个会出问题的地方。

### 1.3 `VideoListFilterBar` 行 2（条件下出现）

出现条件：`moreExpanded || date != ANY || duration != ANY`（`:99`）

| 组 | 元素 | 宽 × 高 |
| --- | --- | --- |
| 日期 | 日期不限 / 今天 / 本周 / 本月 | 各 60 × 36 |
| 时长 | 时长不限 / 短片 / 中等 / 长视频 | 各 60 × 36 |

**8 个 chip 同处一行**，合计 8×60 + 7×4 = **508 dp** < 720 → 不滚动。

### 1.4 `CenterWorkspaceHeader`（仅 `SEARCH_EMPTY`）

| # | 元素 | 尺寸 | x 区间 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | 搜索图标 | 24 × 24 | 24 – 48 | `Icons.Default.Search` |
| 2 | 查询框 | **528 × 40** | 54 – 582 | `weight(1f)`、圆角 20、可点 → `AppInputField` |
| 3 | 系统输入法 | 48 × 48 | 588 – 636 | `Icons.Default.Keyboard` |
| 4 | 语音 | 48 × 48 | 642 – 690 | `Icons.Default.Mic`（禁用占位） |
| 5 | **返回** | 48 × 48 | **696 – 744** | `Icons.AutoMirrored.Filled.ArrowBack` |

行内间距 6 dp；`Row` 自身 padding horizontal 8 / vertical 4。
查询框吃满 `weight=1` 的 528 dp，**把右侧三个按钮推到面板最右** —— 这就是返回键落到 x=696 的原因。

> 隐藏细节：查询框下还有一个 `BasicTextField`，尺寸仅 `1.dp` 且 `alpha(0f)`（`:430-437`）——
> 它是为了接管系统 IME 焦点，**不是可见控件**。

### 1.5 `WORKBENCH_EMPTY` 的唯一控件

| 元素 | 尺寸 | 位置 | 出处 |
| --- | --- | --- | --- |
| 返回筹码 `[视频列表]` / `[搜索结果]` | 约 96 × 36（内容决定） | **水平居中**（`align(CenterHorizontally)`） | `RecommendationUi.kt:273-293` |

文案由 `WorkbenchReturnTarget` 决定：`VIDEO_LIST → 视频列表`，`SEARCH_RESULTS → 搜索结果`。

> 这是"进入列表"的**唯一入口**，而它是**居中**的；返回控件则在**行首或最右**。
> 三者在同一块面板里的横向位置互不相干 —— 这是漂移问题的完整图景。

### 1.6 卡片（`VideoListPanel`）

`RecommendationCard` 237 × 213 dp（3 列网格，列间距 12，起点 x=24）：

| 区域 | dp 矩形 | 可点 |
| --- | --- | --- |
| 封面 16:9 | (0, 0, 237, 133) | 是（整卡可点 → `selectRecommendation`） |
| `ContentAccessBadge` | 左上角 | 否（非交互） |
| 时长角标 | 右下角 | 否 |
| 标题（2 行） | (8, 140, 221, 36) | 随整卡 |
| UP 主 | (8, 178, 221, 18) | 随整卡 |
| 播放量 · 点赞数 | (8, 196, 221, 18) | 随整卡 |

尾部 `PaginationStatus`：高 28 dp，固定在底部。

---

## 2. `video_selector_panel` — 左 rail（361 × 464 dp）

来源：`ImmersiveLeftPanel.kt`、`WorkbenchPanelAtoms.kt`。
样式常量：`horizontalPadding = 16 dp`、`sectionSpacing = 12 dp`（`WorkbenchPanelAtoms.kt:49-50`）。

### 2.1 结构（自上而下）

| 区块 | 高 | 内部 |
| --- | --- | --- |
| `WorkbenchSection` #1 | ≈ 169 dp | Title 18sp / SecondaryText 12sp / **ActionStrip** |
| `Divider` | 1 dp | `border.copy(alpha=0.58)` |
| `WorkbenchCreatorRow` | 60 dp | 36 dp 头像 + 名字 + 副文案 |
| `Divider` | 1 dp | |
| `WorkbenchSection` #2 | ≈ 41 dp | 简介一行 |
| `Divider` | 1 dp | |
| **空态留白** | **≈ 147 dp** | 见 §0.2 |
| `WorkbenchFooterAction` | **54 dp** | 固定底部，不随主体滚动 |

### 2.2 按钮逐个列出

**`WorkbenchActionStrip`**（`WorkbenchPanelAtoms.kt:131-164`）

| # | 元素 | 尺寸 | x 区间 | 状态 | 值文案 |
| --- | --- | --- | --- | --- | --- |
| 1 | 点赞 | `IconButton` 40 × 40 + 值 10sp | 68 – 108 | **`enabled = false`** | `compactCount(likeCount)` 或 `--` |
| 2 | 投币 | 同上 | 161 – 201 | **`enabled = false`** | `--` |
| 3 | 收藏 | 同上 | 253 – 293 | **`enabled = false`** | `--` |

排布：`Arrangement.SpaceEvenly` over `361 − 2×16 = 329 dp`
→ 自由空间 `329 − 3×40 = 209`，四个间隙各 `52.25 dp`
→ 按钮左边 = `16 + 52.25 = 68.25` / `160.5` / `252.75`。

> **三个动作按钮全部禁用**（`ImmersiveLeftPanel.kt:81,87,93`）。它们的 `label` 只用于
> `contentDescription`（"点赞暂不可用"等），**可见的是 `value` 那行 10sp 小字**。

**`WorkbenchCreatorRow`**（`:176-202`）

| 元素 | 尺寸 | 可点 |
| --- | --- | --- |
| 头像方块 | 36 × 36（`style.accent` + 首字） | — |
| 名字 / 副文案 | 文本 | **整行 `clickable(enabled = false)`** |

> ⚠️ **更正**：本文档早先的线稿写了"关注"按钮 —— **代码里没有任何关注控件**，
> 整行是 `enabled = false`（`ImmersiveLeftPanel.kt:99-104`）。线稿已按此更正。

**`WorkbenchFooterAction`**（`:205-221`）

| 元素 | 尺寸 | 位置 |
| --- | --- | --- |
| `评论` 按钮 | **361 × 54** | 固定底部（y = 410 – 464） |
| └ 图标 `Comment` | 默认 24 dp | 左边距 16 |
| └ 文本 `评论` | — | 图标后 8 dp |

**`WorkbenchFullHeightCollapse`**（点"评论"后铺满整条 rail，`:224-242`）

| 元素 | 尺寸 |
| --- | --- |
| 顶部收起按钮 | **361 × 48**（左文 `评论` + 右侧 `ExpandMore`） |
| 内容：`[回复]` | `IconButton` 48，**`enabled = false`** |
| 内容：`Spacer(weight 1)` | 撑开 |
| 内容：`[点踩]` `[点赞]` | 各 `IconButton` 48，**`enabled = false`** |

---

## 3. `mode_panel` — 右 rail（280 × 464 dp）· **目标设计**

当前实现是调试面板（见 [README.md](README.md) §3.5），本文给**目标**按钮级规格。

### 3.1 `contextTabs` — **TAB_BAR**

| # | tab | 尺寸 | x 区间 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | `合集` | **140 × 40** | 0 – 140 | selected（默认） |
| 2 | `相关视频` | **140 × 40** | 140 – 280 | 未选 |

依据：契约里 `contextTabs` 是 `ComponentKind.TAB_BAR`（`CinemaTheme.kt`），
**等宽 tab** 是 tab bar 的常规；两个 tab 各占 `280 / 2 = 140 dp`。

### 3.2 列表项（`playlistList` / `relatedVideoList`）

| 区域 | dp 矩形 |
| --- | --- |
| 列表项 | (0, 48 + n×56, 280, 56) |
| 缩略图 | (8, +4, 48, 48) |
| 标题 | (64, +10, 180, 22) |
| 时长 | 右对齐 (230, +12, 44, 18) |

**无 footer**：动作条归左 rail（2026-10-10 决策）。

### 3.3 ⚠️ 画布比例缺陷（本面板必须修）

| | 值 |
| --- | --- |
| quad | 0.70 × 0.90 m（与左 rail **完全相同**） |
| dp 画布 | **280 × 464**（左 rail 是 361 × 464） |
| 偏差 | **−22.4%** → 横向拉伸约 29% |

**结论**：右 rail 的横向排版空间比左 rail 少 **22%**。
上面 §3.1 的 `140 dp` tab 宽是在**错误的画布**上算的 —— 修成 `361 × 464` 后应为 **180.5 dp**。
**先修画布，再定 tab 宽度。**

---

## 4. 其他面板（已到按钮级）

| 面板 | 状态 | 出处 |
| --- | --- | --- |
| `player_controll` | ✅ 7 键 + seek，见 [panel-wireframes.md](panel-wireframes.md) §1 | `controls.xml` |
| `mr_panel` | ✅ 5 项（品牌 / 搜索 / 账户 / 设置 / MR 开关） | `MRPanel.kt:41-87` |
| `input_method_panel` | ⏸ **暂缓**（键数最多：数字列 16 + 字母列 40+ + 动作列 4） | `ui-structure-and-terminology.md` §5.6 |

---

## 5. Penpot 落地状态

| 板 | 状态 |
| --- | --- |
| `C1`–`C4`（中心面板四路由 · 按钮级） | ✅ 已建（含真实 SVG 图标） |
| `W1` `W6`（transport / nav · 按钮级） | ✅ 已建 |
| `@ref-base`（比例参考物） | ✅ 已建 |
| `W2` `W3`（左/右 rail · 按钮级） | ⏸ **未建** —— 插件连接被孤儿浏览器占住，MCP 报 `frozen` |
| `W5`（输入台） | ⏸ 暂缓 |

**W2 / W3 的按钮级数值已全部在本文 §2 / §3**，连接恢复后可直接落图，无需重新量。

---

## 6. 代码出处索引

| 文件 | 行 | 内容 |
| --- | --- | --- |
| `RecommendationUi.kt` | 262-345 | 中心面板四路由的 header/body 分派 |
| `RecommendationUi.kt` | 389-463 | `CenterWorkspaceHeader`（搜索头 + 返回） |
| `VideoListFilterBar.kt` | 32-123 | 筛选栏两行与全部 chip |
| `VideoListFilterBar.kt` | 126-144 | `FilterButton` 的内部度量 |
| `ImmersiveLeftPanel.kt` | 57-121 | 左 rail 结构与三个动作按钮 |
| `ImmersiveLeftPanel.kt` | 123-159 | 评论覆盖层（全禁用） |
| `WorkbenchPanelAtoms.kt` | 40-50 | `WorkbenchPanelStyle` 常量 |
| `WorkbenchPanelAtoms.kt` | 82-243 | Section / Title / ActionStrip / CreatorRow / FooterAction / Collapse |
