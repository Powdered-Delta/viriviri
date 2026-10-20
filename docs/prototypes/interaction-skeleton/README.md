# 沉浸式 UI 交互骨架（Penpot 原型）

本文记录 **Penpot 里的交互骨架原型**：它在哪里、怎么画的、每块面板在各状态下装什么内容，以及如何用 MCP 读写它。

与既有文档的关系：

| 文档 | 内容 |
| --- | --- |
| [ui-structure-and-terminology.md](../../ui-structure-and-terminology.md) | 当前实现的**结构与命名事实**（本文的内容清单即从它的 §5 与布局 XML 提取） |
| [immersive-ui-low-code-architecture.md](../../immersive-ui-low-code-architecture.md) | 目标架构（大量未实现项） |
| [prototypes/workbench/README.md](../workbench/README.md) | Web 原型的交互契约 |
| **本文** | **Penpot 交互骨架原型**：画法约定 + 面板内容清单 + 操作方式 |
| [research/penpot-mcp-authoring-pitfalls.md](../../research/penpot-mcp-authoring-pitfalls.md) | 用 MCP `execute_code` 写 Penpot 的实测坑与方法 |

**本目录内部分三层，按需要改的粒度选**：

| 层 | 文档 | 粒度 | 回答 |
| --- | --- | --- | --- |
| 1 · 组合 / 流程 | **本文** | 面板 | 哪个状态下**显示哪些**面板 |
| 2 · 面板布局 | [panel-wireframes.md](panel-wireframes.md) | 区块 / 行 | 每个面板**里面**长什么样 |
| 3 · 按钮级 | [button-level-spec.md](button-level-spec.md) | **按钮 / tab / chip** | 每个可点元素的**尺寸、位置、状态、语义** |

> **要改操作流程就读第 3 层。** 返回控件在路由间漂移 672 dp 这类结论只在按钮级可见，
> 第 1、2 层的粒度上看不出来。

工作流（连接 / 提取 / 对账）见 [tools/penpot-workflow/README.md](../../../tools/penpot-workflow/README.md)。
比例体系与参考物 `@ref-base` 见 [research/penpot-workflow-and-reuse.md](../../research/penpot-workflow-and-reuse.md) §2.7。

> 连接信息（端点、鉴权、token）**不在本文**，见本地排除文件 `docs/penpot-mcp-connection.md`（不入提交）。

---

## 1. 位置与产物

**文件**：Penpot 上的 `handmade （复制）`（自建实例）。**页面**：`main-view`（唯一页面）。

原型板与原稿并排放在同一页，纵向对齐（`y = -764`），x 从 8000 起：

| 板 | x | 内容 |
| --- | --- | --- |
| `V2-00 legend` | 8000 | 状态机 / 四条关闭路径 / 面板→slot→内容索引 / 画法说明 |
| `V2-01 quiet-watch` | 12600 | S1：工作台关闭 |
| `V2-02 workbench-on` | 17200 | S2：工作台展开 + 视频列表 |
| `V2-03 browse-search` | 21800 | S3：浏览 / 搜索（输入台可见） |
| `V2-04 context` | 26400 | S4：右 rail（选集 / 相关 / 详情） |

**原稿未改动**：`workbench_on`、`main scene, workbench_off` 两个 group 保持原样，位于 x = 2636 / −2028。

---

## 2. 画法约定

| 项 | 约定 | 原因 |
| --- | --- | --- |
| 尺寸 | **真实公制**，`1 m = 1000 px`，板 3840 × 2160 | 让原型同时充当**可核对的布局规格**，而不是模仿截图的观感 |
| 方向 | 绘图时 `y_screen = CY − my·PX − h/2` | 真实舞台局部系 **+y 向上**，Penpot 的 y **向下**，必须 flip |
| 坐标 | 子节点用**页面绝对坐标**（`board.x + local`） | Penpot 的 `shape.x/y` 不是相对父级；写局部坐标会让内容落到板外被裁掉 |
| 色板 | `bg #10151d` / `stage #22303f` / `panel #33465d` / `rail #2a3b4d` / `console #43395e` | **骨架以可读性优先**，不代表产品最终配色 |
| 描边 / 字号 | 描边 ≥10px（原子块 4px）、正文 44px、原子标签 26–30px | 3840px 宽的板在缩小视图下，细描边与小字号等于不存在 |
| 标签位置 | **面板内左上角** | 放面板外会与板头/相邻面板抢位，并因宽度不足换行压到边框 |
| 层叠表现 | 舞台三层用**嵌套尺寸**（1.68 / 1.64 / 1.60） | 三个等大矩形重合会让标签叠在一起 |

标识：面板边框 `name` = 实现里的实体名（`player` / `global header` / `workbench_main` / `left_rail` / `right_rail` / `player_controll` / `input_console` / `grab_handle`）；面板内的子块以 `atom::` 前缀命名。

---

## 3. 组件目录与面板组合

> **设计约束（2026-10-10 确认）：组件必须解耦，可以自由组合。**
>
> 因此**面板不是内容的容器**——面板只是一份**组合声明**。同一个组件必须能出现在任意面板、
> 也能同时出现在多个面板（`videoSurface` 这类唯一实例除外）。下表的「当前所在面板」
> 只是**今天这一份组合**，**不是组件的属性**。

### 3.0 组件目录（可自由组合的基本单元）

`证据` 列：**实现** = 运行时已在用；**契约** = 只在 `spatial-workbench-core` / 目标文档里声明。

**媒体 / 舞台**

| 组件 id | 内容 | 数据绑定 | 状态 | 证据 | 当前所在面板 |
| --- | --- | --- | --- | --- | --- |
| `videoSurface` | 唯一视频输出 | `PlayerSession` 当前视频 | **全局唯一实例**，不可复制 | 实现 | `player` |
| `danmakuLayer` | 弹幕层（**独立渲染目标**，非视频 Surface） | 弹幕状态 | — | 实现 | `player` |
| `subtitleLayer` | 字幕 | `OverlayKind.CAPTION` | **未实现** | 契约 | `player` |
| `stageBackdrop` | 压暗层（黑 alpha 0.42） | 工作台可见性 | — | 实现 | `player` |
| `stageFeedback` | 加载 / 快进 / 状态提示 | `ImmersiveMediaStatus` | — | 实现 | `player` |

**系统 / 导航**

| 组件 id | 内容 | 状态 | 证据 | 当前所在面板 |
| --- | --- | --- | --- | --- |
| `brandHome` | 品牌按钮 → 主页 | — | 实现 | `global header` |
| `searchEntry` | 搜索入口 | — | 实现 | `global header` |
| `accountEntry` | 账户 | **disabled**（未登录） | 实现（禁用） | `global header` |
| `settingsEntry` | 设置 | **disabled** | 实现（禁用） | `global header` |
| `mrModeToggle` | MR / Passthrough 开关 | on / off | 实现 | `global header` |
| `clock` `battery` `enter2d` `passthrough` `moreActions` | 契约声明的工具栏模块 | — | 契约（**当前未被采用**） | — |

**中心工作区（`BROWSE`）**

| 组件 id | 内容 | 数据绑定 / 条件 | 证据 | 当前所在面板 |
| --- | --- | --- | --- | --- |
| `workbenchReturnChip` | 居中返回筹码 | `WorkbenchReturnTarget` = `VIDEO_LIST` / `SEARCH_RESULTS`；仅 `WORKBENCH_EMPTY` | 实现 | `workbench_main` |
| `videoListFilterBar` | 排序行 + 筛选行 | 列表查询状态；可展开/收起 | 实现 | `workbench_main` |
| `videoGrid` | 3 列卡片网格 | 列表数据；空/加载/错误/有数据 | 实现 | `workbench_main` |
| `videoRowList` | 单列列表 | 同上 | 实现 | `workbench_main` |
| `paginationStatus` | 分页尾部 | 加载更多 / 加载中 / 没有更多 | 实现 | `workbench_main` |
| `searchQueryHeader` | 查询头（图标 + 筹码 + 键盘 + 语音 + 返回） | 仅 `SEARCH_EMPTY` | 实现 | `workbench_main` |
| `searchHistory` | 搜索历史 | 空 / 有数据 / 展开收起 | 实现 | `workbench_main` |
| `searchHot` | 热搜 | 空 / 有数据 | 实现 | `workbench_main` |
| `transientMessageHost` | 瞬时消息（底部居中 FIFO） | — | 实现 | 覆盖层 |

**视频信息（Detail 语义）**

| 组件 id | 内容 | 状态 | 证据 | 当前所在面板 |
| --- | --- | --- | --- | --- |
| `videoTitleBlock` | 标题 | 未选择 / 有值 | 实现 | `left_rail` |
| `videoStatLine` | 播放量 · 时长 | 暂无数据 | 实现 | `left_rail` |
| `videoActionBar` | 点赞 / 投币 / 收藏 | **全部 disabled** | 实现（禁用） | `left_rail` |
| `creatorProfileSummary` | UP 头像 + 昵称 | 投稿入口 disabled | 实现（禁用） | `left_rail` |
| `videoDescription` | 简介 | 暂无简介 | 实现 | `left_rail` |
| `commentEntry` | 评论入口（固定底部） | — | 实现 | `left_rail` |
| `commentsUnavailableCollapse` | 评论不可用全高覆盖 | — | 实现 | `left_rail` |

**上下文（Context 语义）**

| 组件 id | 内容 | 数据绑定 | 证据 | 当前所在面板 |
| --- | --- | --- | --- | --- |
| `contextTabs` | Tab 栏 | `video → currentVideo` | 契约 | `right_rail`（**目标**） |
| `playlistList`（`playlist-list`） | **视频自身的选集**：合集 `sections` + 稿件 `pages`（分P） | `currentVideo` | 契约 | `right_rail`（**目标**） |
| `relatedVideoList`（`related-list`） | 相关视频 | `currentVideo` | 契约 | `right_rail`（**目标**） |
| `video-details` | 详情 | — | 契约 | **已划归左 rail**（见 3.4 / 3.5） |

**Transport**

| 组件 id | 内容 | 状态 | 证据 | 当前所在面板 |
| --- | --- | --- | --- | --- |
| `browseButton` `prevButton` `playPauseButton` `nextButton` | 传输控制 | — | 实现 | `player_controll` |
| `volumeButton` `qualityButton` `speedButton` | 设置入口（弹菜单） | **同一时刻仅允许一个设置浮层** | 实现 | `player_controll` |
| `timelineSeek` | 当前时间 + 进度条 + 总时长 | — | 实现 | `player_controll` |

**输入**

| 组件 id | 内容 | 状态 | 证据 | 当前所在面板 |
| --- | --- | --- | --- | --- |
| `compositionArea` | 合成区 | 仅 `SEARCH_EMPTY` | 实现 | `input_console` |
| `candidateStrip` | 候选条 | 展开 / 收起 | 实现 | `input_console` |
| `keyboardBoard` | 三栏键盘（数字 / 字母 / 动作） | 字母层 / 符号层、Shift 态 | 实现 | `input_console` |
| `searchActions` | 动作行 | 动作列非空时改由动作列承担 | 实现 | `input_console` |

**抓取**

| 组件 id | 内容 | 说明 | 证据 | 当前所在面板 |
| --- | --- | --- | --- | --- |
| `grabHandle` | 抓手条 | **本身不可抓、不可命中**；抓取能力在 `workbenchRootEntity` | 实现 | `grab_handle` |

### 3.0.1 组合规则

1. **组件自带**：id、数据绑定、状态集合、实例约束（例如 `videoSurface` 全局唯一、不可复制）。
2. **面板 = 组件 id 的有序列表**（一份组合声明），面板自身不拥有内容。
3. **组合必须声明式表达**，不能硬编码在 Kotlin 布局里 —— 当前 `left_rail` / `right_rail` 的
   内容写死在 `mode_panel.xml` / Compose 里，**这正是要解耦掉的部分**。
4. **槽位级关系**（一槽两面板 `MIRRORED`、可见性策略、深度序）属于**组合层**，不属于组件。
5. 2026-10-10 的分工（左 = 视频信息 + 评论，右 = 合集 + 相关视频）**是一份组合选择**，
   不是组件属性 —— 换一份组合应当只改声明。

以下 3.1–3.8 记录**每一块面板今天的组合**，内容全部来自源码，**不是设计构思**。面板尺寸为实现的真实值。

### 3.1 `player` — MediaStage（1.60 × 0.90 m，z 0，`MEDIA_STAGE`）

| 层 | 内容 | 状态 | 证据 |
| --- | --- | --- | --- |
| 视频画面 | 唯一 `ExoPlayer` + 唯一活动视频 `Surface` | 实现 | `PlayerSession`；`SpatialVideoSampleActivity.kt` 的手工 `PanelSceneObject` |
| 弹幕层 | `danmaku_overlay_panel`，**独立渲染目标**（非视频 Surface），z −0.01 | 实现 | `DanmakuOverlay.kt`；`ui-structure-and-terminology.md` §5.7 |
| 压暗层 | `stage_backdrop_panel`，黑 alpha 0.42，工作台出现时压暗舞台，z +0.01 | 实现 | 同上 |
| 字幕层 | 契约存在（`OverlayKind.CAPTION`），**未实现** | 缺口 | `MediaOverlayContracts.kt` |
| 舞台反馈 | 加载 / 快进 / 播放状态 | 实现 | `TransientMessageHost`、`ImmersiveTransportOverlay` |

> 舞台**没有命中几何**（`videoSurface` 显式 `MeshCollision.NoCollision`），其点击靠几何判定
> （`AnalogMediaStageTuningSystem` + `StageRayTargeting`）。工作台展开时舞台被中心面板遮挡。

### 3.2 `global header` — `mr_panel`（1.24 × 0.30 m，pitch 8，`SYSTEM_TOOLBAR`）

Compose `GlobalNavigation`（宿主 `MRPanel`）。**路由导航不在这里**——分类 tab / 搜索栏 / 返回属于中心工作区头部。

| 元素 | 文案 / 行为 | 状态 |
| --- | --- | --- |
| 品牌按钮 | `ViriViri` → `openHomeCanvas()` | 实现 |
| 搜索 | `搜索` 图标 → `openSearchCanvas()` | 实现 |
| 账户 | `账户` 图标，`enabled = false` | **未登录，禁用** |
| 设置 | `设置` 图标，`enabled = false` | **禁用** |
| MR 开关 | `Switch` → `setMrMode()` | 实现 |

`SystemToolbarModule` 契约里另有 `CLOCK` / `BATTERY` / `ENTER_2D` / `PASSTHROUGH` / `MORE_ACTIONS`，当前 `mr_panel` **未采用**。
来源：`MRPanel.kt:41-87`、`ThemeContracts.kt:103-109`、`strings.xml`（`nav_brand` / `nav_search` / `nav_account` / `nav_settings`）。

### 3.3 `workbench_main` — `center_content_panel`（1.50 × 1.20 m，z −0.52，`BROWSE`）

中心面板**永远是列表 / 搜索，不承载视频输出**（`ImmersiveCenterContentPanel` 固定 `showViewerContent = false`）。
内容**按 `SearchWorkspaceRoute` 互斥**：

| 路由 | Header | Body |
| --- | --- | --- |
| `WORKBENCH_EMPTY` | 居中 `TextButton` **[视频列表]** 或 **[搜索结果]** | —（空白本身可点收起） |
| `RECOMMENDATIONS` / `SEARCH_RESULTS` | `VideoListFilterBar`：行 1 `综合排序 / 最新发布 / 最多弹幕 / 最多收藏 / 更多筛选` + 刷新 / 回顶 / 布局切换；行 2 `日期：不限/今天/本周/本月`、`时长：不限/短片/中等/长视频` | `VideoListPanel`：**网格态** `LazyVerticalGrid(3 列)` → `RecommendationCard`；**列表态** `LazyColumn` → `RecommendationRow`；尾部 `PaginationStatus`（加载更多 / 加载中 / 没有更多视频） |
| `SEARCH_EMPTY` | 搜索图标 + 查询筹码 + **[键盘] [语音] [返回]** | `SearchDiscoveryContent`：**搜索历史**（横向标签 + 逐条删除 + 展开）与**热搜**（横向标签 + 刷新） |

卡片构成：`RecommendationCard` = 16:9 封面（缩略图 + `ContentAccessBadge` + 时长角标）/ 标题 2 行 / UP 主 / 播放量·点赞数；
`RecommendationRow` = `MediaThumbnailFrame` + 标题 / UP 主 / 时长。

覆盖层：`TransientMessageHost`（底部居中，FIFO 瞬时消息）。
**空白点击仅在 `WORKBENCH_EMPTY` 下收起**；内容路由下保持惰性以防误触。
来源：`ui-structure-and-terminology.md` §5.3（含 §5.3 起的卡片构成段落）。

### 3.4 `left_rail` — `video_selector_panel`（0.70 × 0.90 m，yaw −45，`CONTEXT`）

> **已决策的分工（2026-10-10）**：左 rail = **视频信息 + 评论**。

`ImmersiveLeftPanel` → `ImmersiveVideoDetailPanel`（宿主 `MoviePanel`）。

| 区域 | 内容 |
| --- | --- |
| 可滚动主体 | **视频信息**：标题 / `未选择视频`；`播放量 · 时长` / `暂无播放数据`；动作条 `[点赞｜投币｜收藏]`（**全部禁用**）；UP 行（头像 + 昵称 + `作者投稿入口暂不可用`）；`暂无视频简介` |
| 固定底部 | **评论** `评论` |
| 覆盖层 | `CommentsUnavailableCollapse`：`评论服务尚未接入` + `回复、点赞和点踩需要登录与已验证的服务接口。` + `[回复(禁用)] [点踩(禁用)] [点赞(禁用)]` |

来源：`ui-structure-and-terminology.md` §5.2、`strings.xml`（`detail_*` / `comments_*`）。

> 注意：`left_rail` 与 `right_rail` **同属 `PanelSlot.CONTEXT`**（一槽两面板）。schema 必须用
> `SlotSharePolicy.MIRRORED` 显式声明，不能假设一槽一面板。
>
> 动作条（点赞/投币/收藏）归左 rail 是**跟随"视频信息"的归属**得出的；目标架构文档曾把
> `videoActionBar` 放在 context 槽位的 footer，两者一致（左 rail 就是 context 的"信息"半边）。

### 3.5 `right_rail` — `mode_panel`（0.70 × 0.90 m，yaw +45，`CONTEXT`）

> **产品意图（2026-10-10 确认）**：`right_rail` **现阶段就是调试面板**，属于**临时状态**；
> **后续要作为真实的内容展示板块**。因此原型里该画面板的**目标内容**，并明确标注当前实现是临时的。

#### 当前实现（临时）

**用户可见的只有 6 项**，其余 12 项全是 DEBUG 遥测/开关：

| 元素 | 文案 | 状态 |
| --- | --- | --- |
| `current_media_title` | 标题（截断 42）/ `未选择视频` | 实现 |
| `current_media_detail` | 副标题：加载中 / 错误 / UP 主（截断 56）/ `浏览以选择视频` | 实现 |
| `retry_media_button` | `重试`（仅观看页失败时可见） | 条件 |
| `display_ratio_button` | `显示比例: 自动` | 实现 |
| `canvas_size_button` | `画布大小: 标准` | 实现 |
| `open_2d_button` | `打开 2D 窗口` | 实现 |

**DEBUG 区**（12 项，不应出现在产品规格里）：`stage_y_text`、`danmaku_status`、`scale_text` / `scale_bar`、
`reset_stage_y_button`、`curvature_text`、`reset_curve_button`、`danmaku_z_button`、
`backdrop_order_button` + `backdrop_order_status`、`stage_tap_guard_button`、`debug_build_label`、`debug_aspect_*`。

#### 目标内容（真实内容板块）

契约里 `PanelSlot.CONTEXT` 的目标内容有**两处独立依据**，二者一致：

**依据 A — 默认主题 fixture**（`spatial-workbench-core/.../CinemaTheme.kt`，`slot = "context"` 分支）：

```kotlin
ComponentNode(
  id = "context-tabs",
  kind = ComponentKind.TAB_BAR,
  dataBindings = mapOf("video" to "currentVideo"),
  children = listOf(
    ComponentNode("playlist-list",  ComponentKind.SEMANTIC_MODULE),
    ComponentNode("related-list",   ComponentKind.SEMANTIC_MODULE),
    ComponentNode("video-details",  ComponentKind.SEMANTIC_MODULE),
  ),
)
```

**依据 B — 目标架构文档的组合示例**（`docs/immersive-ui-low-code-architecture.md` 的 `slot: "context"`）：

```text
context 槽位
├── header  creatorProfileSummary（UP 主简介卡）
├── tabs    选集 → playlistList
│           相关 → relatedVideoList
│           详情 → videoTitleBlock / videoStatLine / videoDescription
└── footer  videoActionBar（点赞 / 投币 / 收藏 / 分享）
```

#### 已决策的分工与目标内容布局（2026-10-10）

**右 rail = 合集 + 相关视频**；**「详情」不再属于右 rail**
（视频信息归左 rail，见 3.4）。这与契约一致：`context-tabs` 声明了三个子模块，
其中 `video-details` 由左 rail 承担，右 rail 取其余两个。

| 区域 | 内容 |
| --- | --- |
| tabs | `[合集]` `[相关视频]` — 二态互斥，选中态驱动 body（`TAB_BAR`，`dataBindings: video → currentVideo`） |
| body（tab 1） | `playlistList` — **合集 `sections` + 稿件 `pages`（分P）**；两者可并存，互不替代 |
| body（tab 2） | `relatedVideoList` — 相关推荐 |
| footer | 无（`videoActionBar` 归左 rail） |

> **术语修正（2026-10-10）** —— 详见 [research/bilibili-list-semantics.md](../../research/bilibili-list-semantics.md)
> （基于 PiliPlus `b2d6a5be` 的源码证据）。
>
> 我原先把 tab 标为 `[播放列表/合集]`，**这是错的**：它把三个不同的 B 站对象混成了一个。
>
> | 术语 | 对象 | identity id | 归属 | 跨 UP |
> | --- | --- | --- | --- | --- |
> | **合集** | `ugc_season`（含 `sections`） | `season_id` | **一个 UP**（`UgcSeason.mid`） | 否 |
> | **系列** | `series` | `series_id` | UP | 否 |
> | **播放列表** | media list | `media_id`（`pl…`） | **用户** | **是** |
>
> 空间卡片里的「列表」分支用的是 `series_id`，指的是**系列**，**不是播放列表**。
>
> **命名陷阱**：B 站视频页那个字面叫「播放列表」的 tab，显示条件是 `hasSeasonOrParts` ——
> **里面装的其实是分P + 合集**，不是用户 media list（`video/view.dart:1312`）。
>
> 因此本 tab 定为 **`[合集]`**（内容 = 分P + 合集），**不沿用**有歧义的「播放列表」字样。
> **真正的播放列表（用户自建、可跨 UP）是独立 feature**，是否也放右 rail 需单独决策。

#### 既存缺陷

`WorkbenchLayoutConfig.rightRailHeight = 0.58` **未被消费**——实际 `modePanelRegistration`
用的是 `leftRailHeight = 0.90`，即右 rail 的高度常量是死的。

相关既有任务：`.trellis/tasks/09-02-context-rail-right-panel/`。

### 3.6 `player_controll` — `controls_id`（1.32 × 0.38 m，pitch 20，z −0.66，`TRANSPORT`）

| 行 | 元素 |
| --- | --- |
| 行 1（高 44dp） | `浏览`（openBrowseCanvas）· `上一个视频`（`selectAdjacentRecommendation(-1)`）· `播放/暂停` · `下一个视频`（`+1`）· `音量 100%` · `清晰度 Auto` · `倍速 1x` |
| 行 2（高 32dp） | `--:--` 当前时间 · `seek_bar` 进度拖动 · `--:--` 总时长 |

**Transport 没有标题**：当前视频标题属于 Context / Detail 内容。
音量 / 清晰度 / 倍速 / 显示比例 / 画布大小（以及 debug 的调试目标、调试方案）统一经 `darkPopupMenu` 构建
（`ContextThemeWrapper` + `Theme.ViriViri.DarkPopup`，取色镜像 `CinemaPalette.DARK`）。**同一时刻只允许一个设置浮层。**

来源：`res/layout/controls.xml`、`ui-structure-and-terminology.md` §5.5。

### 3.7 `input_console` — `input_method_panel`（1.62 × 0.68 m，pitch 20，z −0.78，`ACTION_SHEET`）

**仅当 `route == SEARCH_EMPTY && isKeyboardVisible && !isKeyboardDismissed` 时可见**，且只渲染不超出候选区所需的候选项。

| 区域 | 内容 |
| --- | --- |
| header | **空**——查询框在中心工作区头部，不在键盘面板内 |
| mainArea | 合成区 `composition`（宽度对齐中列）；候选条 `SearchCandidateStrip`（自然宽度排布 + 右侧展开/收起箭头，展开态用 `LazyVerticalGrid` 覆盖字母区）；`SearchInputMethodBoard` 三栏：**数字列** `7-8-9-+ / 4-5-6-− / 1-2-3-× / 0-.-=-÷`、**字母列** `qwertyuiop / asdfghjkl / [Shift]zxcvbnm[符号] / [中/eng] , . [拼音] ! ? '`、**动作列** `⌫ / 麦克风 / ↵ / 收起` |
| footer | `⌫删除 · 语音 · 系统输入法 · 收起 · [清空输入] · [确定搜索]`（动作列非空时改由动作列承担，footer 只渲染动作列） |

输入法契约（纯 Kotlin）：`SearchInputMethod` / `SearchInputMethodRegistry`（当前仅 `ChinesePinyinQwertyInputMethod`）/
`SearchInputSession` / `SearchInputKeyboard` / `SearchInputAction` / `SearchInputCandidate` /
`SearchInputLanguage` / `SearchInputShiftState` / `SearchInputKeyboardLayer`。

来源：`ui-structure-and-terminology.md` §5.6。

### 3.8 `grab_handle` — `grab_bar_panel`（1.00 × 0.08 m）

图标 `ic_grab_move` + 文案 `握住并拖动屏幕`（`contentDescription` = `握住横条并拖动以移动屏幕`）。
**它本身不可抓、不可命中**——真正的抓取能力在 `workbenchRootEntity` 上（`IsdkBoxCollider` + `IsdkGrabbable`），
抓手条只是让用户知道在哪里抓。
来源：`res/layout/grab_bar.xml`、`ui-structure-and-terminology.md` §5.8。

---

## 4. 状态 × 面板矩阵

`●` = 可见且有内容 · `○` = 隐藏 · `–` = 无此面板 · `◐` = 可见但内容不同（见括号）

| 面板 | S1 quiet-watch | S2 workbench-on | S3 browse/search | S4 context |
| --- | --- | --- | --- | --- |
| `player` | ● 视频 + 弹幕（+ 压暗层 ○） | ● 被中心面板遮挡 | ● 无视频输出 | ● |
| `global header` | ○ | ● 品牌/搜索/账户/设置/MR | ● 同 S2 | ● 同 S2 |
| `workbench_main` | ○ | ● **RECOMMENDATIONS**（筛选栏 + 网格） | ● **SEARCH_EMPTY**（查询头 + 历史/热搜） | ○ |
| `left_rail` | ○ | ● **视频信息 + 评论**（当前动作全部 disabled） | ● 同 S2 | ● 同 S2 |
| `right_rail` | ○ | ● 媒体状态 6 项 ⚠️ **当前实现=调试面板（临时）** | ● 同 S2 | ● **目标内容**：`[合集]` `[相关视频]` 两个 tab（**未接线**） |
| `player_controll` | ○ | ● Transport 两行 | ● 淡出 | ● Transport |
| `input_console` | ○ | ○ | ● 三栏键盘 | ○ |
| `grab_handle` | ● | ● | ● | ● |

**产品意图（须体现在原型里）**：`right_rail` 当前是**调试面板（临时）**，
后续要成为**真实的内容展示板块**——原型画面板的目标内容（见 3.5），并用角标标明其临时/目标身份。

**不可达状态（实现缺口，原型里只有占位）**：
`FOCUS` / `SHORTS` / `PLAYBACK_CONFIG` 分支在 app 层没有生产者；
`SearchWorkspaceRoute` 与 `WorkbenchContent` 语义重叠且后者无外部读取者。

---

### 4.1 缺失的第二个维度：播放列表上下文（`PlaybackListContext`）

**问题（2026-10-10）**：真正的「播放列表」（用户自建、**可跨 UP** 的 media list）与「合集」是**不同对象**
（见 [research/bilibili-list-semantics.md](../../research/bilibili-list-semantics.md)）。
那么右 rail 是否需要**额外的状态（路由）**？

#### 验证：两个界面是**正交**的，各有自己的显示条件

证据来源 PiliPlus `b2d6a5be`：

| | 合集 / 分P | 播放列表（media list） |
| --- | --- | --- |
| 呈现类 | 视频页内**常驻 tab** | **上滑面板** `CommonSlidePage` |
| 显示条件 | `_shouldShowSeasonPanel` = `hasSeasonOrParts` / `hasEpisodes` —— **只看视频自身属性，不含 `mediaId`** | `showMediaListPanel` 里 `if (mediaList.isNotEmpty)` —— **只看来源** |
| 可编辑 | 否 | 是（仅稍后再看 / 自己的收藏夹） |

- `lib/pages/video/view.dart:132-142` —— `_shouldShowSeasonPanel` 的全部条件
- `lib/pages/video/controller.dart:466-467` —— `showMediaListPanel` 的首行守卫
- `lib/pages/video/medialist/view.dart:20` —— `MediaListPanel extends CommonSlidePage`

#### 结论：**需要新状态，但不该加在 `PlaybackCanvas` 上**

- `PlaybackCanvas`（`QUIET_WATCH` / `PLAYBACK` / `BROWSE` / `CONTEXT`）是**可见性**状态机；
- 「从哪个列表进来」是**数据来源**属性，两者互相独立；
- 把后者塞进前者＝重复本项目已有的错误（`SearchWorkspaceRoute` 与 `WorkbenchContent` 语义重叠）。

#### 提议：新增正交维度 `PlaybackListContext`

取值镜像 B 站自己的 `SourceType`（`lib/models/common/video/source_type.dart`）：

| 取值 | 何时 | identity | 右 rail 组合 |
| --- | --- | --- | --- |
| `NONE` | 直接进入 / 从相关推荐点击 | — | `[合集?] [相关视频]` |
| `SEASON` | 从合集进入 | `season_id` | `[合集] [相关视频]` |
| `SERIES` | 从系列进入 | `series_id` | `[系列] [相关视频]` |
| `MEDIA_LIST` | 从用户播放列表进入 | `media_id` | `[播放列表] [相关视频]` |
| `FAV` | 从收藏夹进入 | `media_id`/`fid` | `[收藏夹] [相关视频]` |
| `WATCH_LATER` | 从稍后再看进入 | — | `[稍后再看] [相关视频]` |

> 上表第二列到第四列是**面板组合**；`listContext` 的另一半作用是**播放队列来源**（见下文），
> 后者才是它的首要职责。**`相关视频` 恒在**（2026-10-10 用户网页端实测确认）。

右 rail 的组合 = **`f(canvas, listContext)`**，两条判据**分开**：

- **`合集` tab 由视频自身属性决定**（与来源无关）—— 这与 PiliPlus 一致；
- **`播放列表` tab 由 `listContext` 决定**。

#### 分P 始终保留

`hasParts`（`pages != null && pages.length > 1`）是**视频自身属性**，与来源无关 ——
即从播放列表进入时**分P 仍然存在**（用户的猜测正确）。

#### ✅ 互斥假设已被排除；真正被替换的是**播放队列来源**（2026-10-10 用户网页端实测）

用户实测确认：**合集下确实有相关推荐** → **相关推荐保留**，不因来源而隐藏。
被替换的是**轮播优先级**（自动连播的「下一集」来源）——从「推荐」改为「合集的下一个视频」。

PiliPlus 把这个优先级写得很清楚（`lib/pages/video/controller.dart:1410-1423`）：

```dart
PlaylistSource from = PlaylistSource.UP_ARCHIVE;
if (isPlayAll) {              // ① 从某个列表「播放全部」进入
  id = args['mediaId'];
  extraId = sourceType.extraId;
  from = sourceType.playlistSource!;
} else if (isUgc) {           // ② 否则，UGC 且有合集
  id = ugcIntroCtr.videoDetail.value.ugcSeason?.id;
  if (id != null) { extraId = 8; from = PlaylistSource.MEDIA_LIST; }
}                             // ③ 否则 UP_ARCHIVE（默认）
```

**优先级：进入时的列表 > 视频自身的合集 > UP 默认投稿。**
这解释了为什么"合集会夺取连播"——它是在**播放队列层**被注册的，与面板内容无关。
（`controller.dart:450,594` 的 `ugcIntroCtr.onChangeEpisode(...)` 即由此驱动。）

#### 由此得到两个**互相独立**的关注点

| 关注点 | 由什么决定 | 证据 |
| --- | --- | --- |
| **面板显示什么**（rail 组合） | 视频自身属性（`hasSeasonOrParts`）+ 是否从列表进入 | `_shouldShowSeasonPanel`（不含 `mediaId`）；`showMediaListPanel`（`mediaList.isNotEmpty`） |
| **谁拥有「下一集」**（播放队列来源） | **进入时的列表 > 合集 > UP 默认** | `toAudioPage` 的分支优先级 |

#### 修正后的结论

- **相关推荐始终保留** —— **不应**用来源去隐藏 `相关视频`；
- `PlaybackListContext` 的首要价值是 **own 播放队列来源（轮播优先级）**，
  其次才是决定要不要多一个 `[播放列表]` tab；
- 右 rail 的组合因此是：`[合集?]`（视频属性）`+ [播放列表?]`（来源）`+ [相关视频]`（**恒在**）。

## 5. 操作方式

### 5.1 读

原始 MCP 协议（本仓库当前没有热加载 penpot MCP 工具）：

- `execute_code` —— 在插件上下文执行 JS，读写几何；
- `export_shape`（需 `shapeId`）—— 返回**内嵌 base64 PNG**，落盘即可看，是**最强的验证手段**。

### 5.2 写

**必须遵守的硬约束**（详见 pitfalls 文档）：

1. `shape.x/y` 是**页面绝对坐标** → 设值必须加父级原点；
2. 切页**单独一次调用**，下次调用确认 `currentPage` 后再建图形；
3. **不能修改非活动页** → 换页就重建，不搬运；
4. `createText('')` 返回 `null` → 建文本前判空；
5. 源码里不要出现 `-->`（SES 拒绝）；
6. 脚本要**幂等**：开头按名字前缀清掉自己上一轮的产物；
7. 每次建完都**导出 + 读图**自检，不要只读自己算出来的坐标。

### 5.3 当前状态

**插件标签页被浏览器冻结**（`The Penpot plugin tab has been frozen by the browser and cannot run tasks`），
需要把 Penpot 标签页切到前台唤醒后才能继续写入。
