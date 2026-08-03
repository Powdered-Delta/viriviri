# 沉浸式 UI 与低代码主题架构

## 状态

本文记录 ViriViri 沉浸式 UI 的当前架构结论，用于后续功能和主题开发。

- 本文描述的是目标架构和已确认的设计边界，不代表功能已经实现。
- 现有 Media3 跨 2D / 沉浸式输出交接必须保持单一 `ExoPlayer` 和单一活动视频 Surface。
- 固定的场景模型、环境、槽位锚点和主题装饰由 Meta Spatial Editor 场景负责；按用户动作出现的 Focus、Drawer、ActionSheet、弹幕实体等属于运行时动态内容。
- 当前项目没有可用的 `mse-agent`。在新增固定场景对象前必须先安装 Meta Spatial Editor；不得将新的固定空间布局硬编码为 Kotlin `Entity.create()`。

## 设计目标

1. 主视频舞台始终保持清晰，不被普通资料页或列表替换。
2. 内容浏览、当前视频导航、播放控制和账户事务在空间上职责明确。
3. 所有主题共享同一套业务状态、播放器、搜索、详情、选集和关联视频能力。
4. 主题作者主要通过场景锚点和声明式模块编排完成工作，而不是重写业务代码。
5. 低代码配置只能调用受控组件和 action，不能创建播放器、Surface、Activity 或任意可执行代码。

## 空间工作台

工作台有五个语义空间 slot。它们可以由主题映射到固定空间 panel 实体，但
**slot 存在不代表内容必须常驻可见**。正式观看可使用影院主题的淡出预设，也
可以使用驾驶舱主题的常驻 panel 预设。用户唤出时显示 `Playback Canvas`，
主动浏览时才展开 `BROWSE` 或 `CONTEXT`；常驻策略声明的 panel 不受默认
canvas 淡出强制隐藏。

每个 slot 都应有主题级呈现策略：

```kotlin
enum class PanelPresentationPolicy {
    PERSISTENT, AUTO_FADE, ON_DEMAND, TRANSIENT, THEME_CONTROLLED,
}
```

`WorkbenchCanvas` 只表示当前临时交互焦点，不是全局可见性开关。

| Slot | 位置 | 职责 | 自动淡出 |
| --- | --- | --- | --- |
| `MEDIA_STAGE` | 中间 | 唯一视频 Surface、弹幕、字幕和轻量播放覆盖层 | 否 |
| `TRANSPORT` | `MEDIA_STAGE` 前方的 Z 覆盖层；面板内部可按状态调整 2D `top` | 播放、进度、快进快退、倍速、音量、弹幕模式和位置抓手 | 由主题策略控制 |
| `SYSTEM_TOOLBAR` | 主视频上方 | 时间、电量、2D 切换、Passthrough 和未来系统工具 | 由主题策略控制 |
| `BROWSE` | 主视频左侧 | 推荐、搜索、搜索输入和内容发现 | 由主题策略控制 |
| `CONTEXT` | 主视频右侧 | 当前视频详情、选集、关联视频、UP Drawer | 由主题策略控制 |

当前既有实体的复用目标如下：

```text
spatialized_video_panel -> MEDIA_STAGE
controls_id              -> TRANSPORT
mode_panel               -> SYSTEM_TOOLBAR
video_selector_panel     -> BROWSE
mr_panel                 -> CONTEXT
```

`mr_panel` 不再只放置 Passthrough 小开关。Passthrough 应作为 `CONTEXT` 或 `SYSTEM_TOOLBAR` 内的受控模块，从而释放该实体用于右侧当前视频导航。

## 工作台位置控制

用户只通过 `TRANSPORT` 覆盖层内的显式 `GrabHandle` 调整整个工作台的位置和朝向。左、右、顶部和底部 panel 不允许各自被抓取。

`TRANSPORT` 的空间 Z 顺序固定为：

```text
用户 -> TRANSPORT overlay -> MEDIA_STAGE -> 场景
```

它不是播放器下方的空间 panel。`top = 0.60`（观看）与 `top = 0.80..0.90`
（视频列表）只是沟通视觉关系的 CSS 类比，不是最终 renderer 必须采用的二维
坐标或 theme schema。列表态父容器必须允许 overflow visible，并保留超出列表
内容矩形的命中测试。

第一期抓手只控制整个工作台的平移和 Y 轴旋转，不缩放。这样可避免子 panel 随屏幕缩放变得不可读。

正常观看时，`MEDIA_STAGE` 作为现有工作台的运行时父节点，其他 panel 使用相对 Transform 保持固定位置。若未来需要独立的可缩放工作台根节点，应在主题场景中由 Meta Spatial Editor 创建并命名为 `workspace_root`，不应新增硬编码固定实体。

## 布局模式

目标主题合同至少支持以下布局状态。当前已提交的 core 枚举尚未加入
`SHORTS`；在实现 Shorts runtime 前必须作为兼容性版本变更补齐该 API 与
`CinemaTheme` fixture，不能只依赖文档名称。

```kotlin
enum class ImmersiveLayoutMode {
    WATCH,
    SHORTS,
    FOCUS,
    EDIT,
}
```

### WATCH

`WATCH` 有两个呈现层，不是五块辅助 UI 同时争夺注意力：

```text
Quiet Watch:                    Playback Canvas:
     MEDIA_STAGE               SYSTEM_TOOLBAR
                                   MEDIA_STAGE
                                    TRANSPORT
```

主视频上的 primary click/pinch/touch 打开 Playback Canvas；播放且空闲时，
仅主题策略为 `AUTO_FADE` 的辅助层淡出并禁用命中。`PERSISTENT` slot 保持
可见，`BROWSE` 与 `CONTEXT` 是用户明确进入的辅助路径，不因开始播放而自动
显示。

### SHORTS

`SHORTS` 是 Bilibili 短视频的独立沉浸布局，不是把 `WATCH` 横版画面裁成
竖屏。它保持同一个 `MEDIA_STAGE`、同一个 `ExoPlayer` 和同一个活动 Surface，
仅切换到 portrait `StageGeometry`、短视频队列与 Shorts 专属画布。

```text
Shorts Quiet:
        portrait MEDIA_STAGE
   previous | like | feedback | next

Shorts Controls:
   DETAILS | portrait MEDIA_STAGE | COMMENTS
      front TRANSPORT overlay + GrabHandle
```

- Quiet 状态只显示必要的队列和互动操作，除非主题将其他 slot 声明为
  `PERSISTENT`。
- Controls 状态显示详情、播放器和评论三栏；底部动作列仍是位于视频前方的
  `TRANSPORT` 覆盖层，不得作为视频下方的独立空间 panel。
- Bilibili 的 `feedback` 仅是“不感兴趣”受控动作或明确不可用状态，不能伪装
  为通用踩操作或已成功的服务端写入。举报仅位于 `more -> 举报` Popup。
- 短视频队列的 previous/next 只切换同一播放器的媒体项；必须等待现有 Surface
  交接完成，不创建第二播放器/Surface。
- Shorts Controls 的详情与评论是固定左/右 rail；它们没有先后关闭关系。评论
  仅使用 `SHORTS_COMMENTS` 作为交互焦点，不应与横版 `选集/相关/详情` tab 强行
  复用同一视觉层级。
- Shorts transport 的 `more` 是内容级溢出入口。hover/focus 或激活时打开
  `Popup`，第一期且唯一的选项是 `举报`；它不承载播放设置、弹幕设置或账户
  设置。举报 API/授权未实现前必须显示受限状态。

### FOCUS

用于收藏夹、合集、完整 UP 投稿等需要占据中心进行长列表浏览的内容。

```text
FOCUS 占据中间主舞台
MEDIA_STAGE 移至预留 PiP dock，持续播放
BROWSE / CONTEXT 隐藏或淡出
SYSTEM_TOOLBAR 保留
TRANSPORT 按需唤回
```

画中画是同一个 `MEDIA_STAGE` 的位置和尺寸变化，不得创建第二个播放器、第二个视频 Surface 或第二个 MediaPanel。

### EDIT

仅面向主题开发或布局编辑：显示 slot 边界、锚点和网格辅助，允许调整主题覆盖配置。编辑模式不是普通用户观看流程。

## 交互画布状态

`ImmersiveLayoutMode` 描述空间几何与大范围布局；它不应被用来表达每次
呼出控制栏、打开搜索或查看详情的瞬时 UI。正式实现需要独立、受限的交互
画布状态，避免把所有 slot 视作常驻页面：

```kotlin
enum class WorkbenchCanvas {
    QUIET_WATCH,
    PLAYBACK,
    BROWSE,
    CONTEXT,
    SHORTS_QUIET,
    SHORTS_CONTROLS,
    SHORTS_COMMENTS, // comment interaction focus, not a visibility state
    SHORTS_MORE,
}
```

- `WATCH + QUIET_WATCH`：除主题声明为 `PERSISTENT` 的 slot 外，只显示
  `MEDIA_STAGE`。
- `WATCH + PLAYBACK`：显示 `SYSTEM_TOOLBAR` 与前置 `TRANSPORT`；是播放中
  唤出操作的默认状态。Toolbar 提供时间、2D、环境、电量、Logo、用户/设置，
  以及一个可复用的内容导航主槽位。该槽位互斥显示：首页分类 tab、搜索栏、
  子页面返回或 source-aware `video list`；播放时由 `video list` 占用。Transport
  提供标题、音量、上一项、回放、播放、快进、下一项、`config` 与进度。
- `WATCH + BROWSE`：显示左侧 `BROWSE`，右侧仅在明确选择 Up Next 时显示
  紧凑上下文；搜索是 Browse 内部子状态而不是新空间 panel。
- `WATCH + CONTEXT`：显示右侧 `CONTEXT`，不自动同时展开推荐列表。
- `SHORTS + SHORTS_QUIET`：显示 portrait `MEDIA_STAGE` 及最少的
  `previous / like / feedback / next` 操作。
- `SHORTS + SHORTS_CONTROLS`：展开详情、竖屏主舞台、评论三栏和前置
  `TRANSPORT` 覆盖层；主题的常驻策略优先于 canvas 淡出。
- `SHORTS + SHORTS_COMMENTS`：保持 Shorts Controls 的固定三栏布局，仅将
  交互焦点锁定在评论 rail；不改变详情/评论的可见性。
- `SHORTS + SHORTS_MORE`：在 Shorts Controls 上方显示仅含 `举报` 的内容级
  `Popup`；关闭后返回 `SHORTS_CONTROLS`，不改变播放器、Surface 或队列。
- `FOCUS` 使用 `ImmersiveLayoutMode.FOCUS`，并让同一 `MEDIA_STAGE` 进入
  PiP dock；它不是 `WorkbenchCanvas` 的附加成员。

`PLAYBACK`、`BROWSE` 与 `CONTEXT` 在用户回退、点击明确的 canvas 空白区或
播放空闲超时后回到 `QUIET_WATCH`。`SHORTS_MORE` 关闭后返回
`SHORTS_CONTROLS`；其余情况下 `SHORTS_CONTROLS` 可回到 `SHORTS_QUIET`。
`SHORTS_COMMENTS` 只是焦点，不参与关闭栈。隐藏必须同时关闭命中，不能只做
视觉 alpha 动画。

## 内容导航

### 左侧 BROWSE

左侧承担主动内容发现，不承担被动跳转的 UP 主页。

```text
顶部 ContentNavigationSlot（同一物理区域，互斥复用）
├── HomeCategoryTabs
├── SearchQueryField: empty placeholder `搜索`, non-empty `Clear`
│   ├── RequestVoiceInput icon action
│   └── RequestSystemIme icon action (immediately right of voice)
├── BackNavigation
└── OpenVideoList

固定 Header 辅助动作
├── ViriViriHome
└── ProfileAndSettings

可滚动 MainArea
├── DefaultHotSearchList          # 查询为空时的平台默认推荐搜索/热搜
├── SearchHistoryList
├── SearchResultList
└── RecommendationList

固定 Header 下方（查询非空时）
└── QueryContentSuggestionSelect  # 内容联想下拉，最多四行，不属于输入法候选

前置独立输入控制台（不属于滚动区）
├── SearchNumberKeypad
├── 字母区上方的固定高度 Composition / `词组 | 单字` / SearchCandidateStrip
├── SearchPinyinQwertyBoard: Alphabet / `中/英` / syllable delimiter `'`
└── SearchInputActions: Delete / Voice / Enter / DismissKeyboard
```

搜索框和 tab bar 固定，不随列表滚动。视频条目使用横向媒体行：`16:9` 封面、时长、两行标题、UP 主和播放量。

### 右侧 CONTEXT

右侧是当前视频上下文和快速导航：

```text
Header: CreatorProfileSummary
Tabs: 选集 | 相关 | 详情
Footer: VideoActionBar
```

默认优先级：多 P 视频默认打开选集，单 P 视频默认打开相关；详情始终可访问但不抢占主要导航区域。

- `选集`：来自视频详情 `pages`，点击切换当前 `cid`。
- `相关`：来自 `/x/web-interface/archive/related`，点击切换当前视频。
- `详情`：标题、统计、发布日期、简介、标签和 UP 摘要。

点击右侧 UP 头像或昵称不应跳转到左侧。默认在右侧原地打开 `CreatorDrawer`：展示 UP 资料和少量最近投稿，带明确返回按钮，关闭后恢复原 tab 和滚动位置。

用户主动选择“查看全部投稿”后，才进入中央 `FOCUS`，视频进入 PiP dock。

### 中央 FOCUS

`FocusPanel` 是通用内容浏览容器，不只服务 UP：

```text
UP 全部投稿
收藏夹详情
合集 / 系列
分区 / 频道
未来的长列表内容
```

FocusPanel 必须在布局内定义 `PiPDock` 和内容避让区，不能先显示内容再任意覆盖画中画。

### TODO: 评论、回复与私信

PiliPlus 已有可参考的评论/楼中楼、评论草稿与输入禁用处理，以及私信会话、详情、
发送、黑名单和设置实现；它们属于未来账户能力，当前不接入 Bilibili 写接口。

后续模块：`CommentThread`、`CommentComposer`、`ReplyComposer`、
`DirectMessageSessionList`、`DirectMessageThread`、`DirectMessageComposer`。
它们必须由产品状态层控制登录、Cookie/session、CSRF、输入禁用、草稿、发送中、
成功与失败；主题只能渲染受控状态与 action。发送应防重复，成功后才更新局部内容，
失败时保留文本。应用内 IME 可作为输入源，但评论/私信必须使用独立 composer
session，绝不能复用 `SearchSession` 或搜索历史。

在先决条件满足前，评论可只读；回复和私信入口必须显示明确的未登录/不可用状态。

### 账户事务

投币、收藏夹选择、分享等需要确认、多选或异步请求的操作使用动态 `ActionSheet`：

| 操作 | ActionSheet 内容 |
| --- | --- |
| 投币 | 1 / 2 币、可选同时点赞、确认和取消 |
| 收藏 | 收藏夹列表、多选、创建入口、确认和取消 |
| 分享 | 分享目标、复制链接等 |
| 更多 | 低频操作菜单 |

ActionSheet 不持有播放器或 Surface。打开时不自动淡出，完成、取消或失败后关闭并恢复辅助 UI 计时。

点赞可作为 `VideoActionBar` 内的单步动作；投币、收藏当前依赖尚未实现的登录、Cookie、CSRF 和账户 API，必须显示受限状态，不能绕过平台授权。

## Panel Shell 与组件库

### Panel Shell

内容 panel 使用可组合的 `SpatialPanelShell`，而不是把所有内容做成特殊页面：

```kotlin
SpatialPanelShell(
    header = { ... },
    toolbar = { ... },
    mainArea = { ... },
    footer = { ... },
    actionBox = { ... },
)
```

```text
PanelShell
├── HeaderSlot       固定：返回、标题、关闭、工具
├── ToolbarSlot      固定：tab、筛选、状态
├── MainSlot         唯一可滚动内容区
├── FooterSlot       固定：主要操作、分页、确认
└── OverlaySlot      Popup、Toast、加载与错误覆盖层
```

系统工具组与播放控制是工具条，不强制使用 Panel Shell。

### 布局原语

Panel 内部优先使用下列布局原语，而不是将所有内容放进 Grid：

| 原语 | 用途 |
| --- | --- |
| `Column` / `Flow` | 详情、Drawer、ActionSheet、设置 |
| `Row` / `Flex` | 头像资料、工具组、操作条 |
| `Grid` | 26 键拼音输入、数字区、规则控制、数据仪表 |
| `Stack` | 封面角标、加载覆盖、PiP dock |
| `Split` | Focus 内容与 PiP dock 的分栏 |
| `ScrollArea` | 推荐、选集、关联和投稿列表 |
| `Tabs` | 推荐/搜索、选集/相关/详情 |
| `Slot` | Header、Footer、BtnBox、避让区 |
| `Overlay` | Popup、Toast、候选词条 |

空间层使用锚点和相对约束，panel 内部才使用 Grid/Flex/Stack。

### 语义预设组件

主题作者除了组合基础原语，还应可直接使用业务预设组件：

```text
CreatorAvatar
CreatorName
CreatorLevelBadge
CreatorBio
CreatorFollowerCount
CreatorFollowingCount
CreatorVideoCount
CreatorFollowButton
CreatorProfileSummary
CreatorProfileHeader
CreatorProfileCard

VideoTitleBlock
VideoStatLine
VideoDescription
MediaThumbnail
MediaRow
PlaylistEntry
RelatedVideoRow
VideoActionBar

SearchQueryField
SearchNumberKeypad
SearchPinyinQwertyBoard
SearchCandidateStrip
SearchCandidatePopupList
SearchInputActions
SearchResultList
SearchHistoryList
DefaultHotSearchList
QueryContentSuggestionSelect

ClockModule
BatteryModule
PlaybackStatus
```

`CreatorFollowButton` 是受控业务组件，必须处理未登录、未关注、已关注、互关、请求中和失败状态，不能由主题直接修改视觉状态伪造成功。

## 搜索模块拆分

搜索状态集中在 `SearchSession`，UI 模块不得各自请求 Bilibili API 或维护独立搜索结果。

```kotlin
data class SearchSession(
    val committedQuery: String,
    val inputMethodSession: SearchInputSession, // inputMethodId is the source of truth
    val status: SearchStatus,
    val results: List<Recommendation>,
    val defaultHotSearches: List<String>,
    val queryContentSuggestions: List<String>,
    val history: List<String>,
)

// inputMethodSession.candidates are local composition-conversion candidates.
// defaultHotSearches are empty-query platform hot searches.
// queryContentSuggestions are provider suggestions derived from normalized
// committedQuery, shown as a maximum-four-row select below SearchQueryField.
// Raw Chinese Pinyin composition never becomes a provider suggestion query.
```

播放选择必须保存可恢复的浏览来源，而不仅保存当前视频：

```kotlin
sealed interface PlaybackBrowseOrigin {
    data object Recommendations : PlaybackBrowseOrigin
    data class SearchResults(
        val query: String,
        val filters: Map<String, String>,
        val scrollPosition: ListScrollPosition,
    ) : PlaybackBrowseOrigin
    data class ContinuationList(
        val id: String,
        val scrollPosition: ListScrollPosition,
    ) : PlaybackBrowseOrigin
}
```

`video list` 优先恢复此来源。当前视频来自 `SearchResults` 时，用户可见行为
是**返回之前的搜索结果页**：恢复原 query、filters、结果快照/分页状态与 scroll
position，不能重新请求搜索或用推荐列表替代。空间宿主可在 Browse Canvas 内恢复
该页面状态，无须进行 Android Activity/route 跳转。

组件通过受控 action 改变状态：

```text
UpdateQuery
PressInputKey
InsertSyllableDelimiter
SwitchInputMethod
SelectCandidate
SetCandidateMode
ClearQuery
RequestVoiceInput
RequestSystemIme
SelectSearchEntryAndSubmit // hot, discovery, content suggestion, or history
SubmitSearch
SelectResult
DismissInputMethod
OpenPlaybackConfig
SetAutoplay
SetPlaybackSpeed
SetQuality
SetStageGeometry
SetStageSize
OpenVideoList
```

`ContentNavigationSlot` 是导航层的一块共享物理区域，不是四个同时存在的
按钮。HomeCategoryTabs、SearchQueryField、BackNavigation 与 OpenVideoList
根据当前路线互斥渲染：播放中 `OpenVideoList` 若来源为搜索则返回之前的搜索
结果页，否则恢复匹配的来源列表；搜索中 SearchQueryField 占用该槽位；首页
显示分类 tab；子页面显示返回。

已实现的 `SearchInputMethod`、`OfflinePinyinLexicon` 和 `SearchInputMethodRegistry` 是输入法扩展边界。当前多击九宫格是兼容实现；目标为可以独立注册的中文 26 键 QWERTY 拼音方法，不能把拼音解析、候选排序或语言特例写进 Compose layout。目标模块为：

```text
SearchQueryField
SearchNumberKeypad
SearchPinyinQwertyBoard: Alphabet / `中/英` / syllable delimiter `'`
SearchCandidateStrip: one collapsed row + trailing expand button
SearchCandidatePopupList: topmost overlay anchored above the strip
SearchInputActions: Delete / Voice / Enter / DismissKeyboard
```

这些模块组成位于 `TRANSPORT` 前方的独立输入控制台，而不是 Browse 滚动列表的
一部分。空间层级固定为：

```text
用户 -> SearchInputConsole / CandidatePopupList -> TRANSPORT -> MEDIA_STAGE
```

查询为空时，平台默认推荐搜索/热搜位于 Browse 的内容区；它们不是
`SearchInputSession.candidates`。查询非空时，内容联想以
`QueryContentSuggestionSelect` 形式直接显示在搜索框下方，最多四行；它同样
不是输入法候选。内容联想只基于 normalized committed query 请求，不能将原始
Pinyin composition 发送给 provider。

热搜、搜索发现/推荐词、内容联想和历史搜索统一使用
`SelectSearchEntryAndSubmit`。该 action 与 PiliPlus 的 `onClickKeyword()` 行为
一致：写入选中 query、清空本地 composition/候选及 provider 联想、立即执行
`SubmitSearch`、收起应用内输入控制台和候选展开层；若 Horizon OS 系统 IME
正在显示，也请求关闭。它不会切换 ViriViri 的 Search Canvas 路由，搜索结果仍在
Browse 内容区渲染。输入法组合态、`词组 | 单字` 和候选条必须固定在 QWERTY
字母区正上方；它们与字母键构成稳定高度，候选变化不得挤压或移动键盘。候选条
默认只显示一行，末尾展开按钮在剩余候选存在时显示；展开列表锚定在候选条上方，
属于输入控制台的最上层临时 overlay。选择候选、编辑组合态、切换输入法或收起
键盘会关闭展开列表。中文拼音模式的 `'` 只用于组合态分词，例如 `xi'an`；
提交中文候选时不写入查询。每个候选必须声明它消费的原始 composition 区间：
选择单字或词组只提交并移除该区间，清理相邻多余分词符，未消费拼音保留并刷新
候选。中/英切换保留 committed query，只清除未提交中文组合态。英文模式第一期
直接写入 Latin 文本，不提供英文词汇联想或候选；数字键始终字面写入，`'` 在
英文模式写入普通 apostrophe。

当前输入方法的唯一事实来源是 `inputMethodSession.inputMethodId` /
`SearchInputMethodRegistry`。语言切换应派发 `SwitchInputMethod`，而不是允许
重复的语言字段独立漂移。

### 输入控制台主题样式

应用内 IME 的语言 reducer、拼音分词、候选排序、部分候选消费、Enter 行为和
`SearchSession` 更新属于 input engine，不能由主题覆盖。主题只编排视觉模块并
提供不可执行样式 token：

```kotlin
data class InputConsoleStyle(
    val shell: SurfaceStyle,
    val numberKeyStyle: KeyStyle,
    val alphabetKeyStyle: KeyStyle,
    val actionKeyStyle: KeyStyle,
    val selectedLanguageStyle: SelectionStyle,
    val compositionStyle: TextStyleToken,
    val candidateStripStyle: CandidateStripStyle,
    val candidatePopupStyle: PopupStyle,
    val disabledStyle: DisabledStyle,
)
```

`InputConsoleStyle` 允许主题更换色板、字体、透明度、材质、边框、hover/focus/
press/disabled 状态、间距、按键分组和候选列表外观。主题可将
`SearchNumberKeypad`、`SearchPinyinQwertyBoard`、`SearchCandidateStrip`、
`SearchCandidatePopupList` 与 `SearchInputActions` 独立重排为影院控制台、
驾驶舱按键台或紧凑侧栏。它不能用主题 JSON 改写输入引擎逻辑、创建播放器/
Surface/Activity，或让候选条在输入时改变字母区稳定几何。

默认影院主题提供 `CinemaInputConsole` 组件组，使用半透明前置控制台皮肤；
该组与 `DefaultCinemaPlaybackCanvasGroup` 一样只是便利配方，成员保持可独立
导入、替换和重组。

### 默认影院主题配色

默认影院主题应提供用户可自定义的 `CinemaPalette`，供播放画布、输入控制台、
Browse、Context、Popup 与所有 focus/disabled 状态共同使用。它使用语义 token，
而不是允许每个组件自行填写颜色：

```kotlin
data class CinemaPalette(
    val background: ColorToken,
    val surface: ColorToken,
    val surfaceOverlay: ColorToken,
    val surfaceOpacity: Float,
    val border: ColorToken,
    val text: ColorToken,
    val textSecondary: ColorToken,
    val textHighlight: ColorToken,
    val button: ColorToken,
    val buttonText: ColorToken,
    val buttonSecondary: ColorToken,
    val buttonSecondaryText: ColorToken,
    val danger: ColorToken,
)
```

默认提供 `DARK`、`LIGHT`、`HIGH_CONTRAST` 三套已验证 preset。用户可在
preset 上为命名语义角色选择 background、surface、普通文本、次级文本、高亮文本、
主/次按钮、按钮文字、border、danger 与 surfaceOpacity；不能接受任意逐组件颜色。
运行时从这些基础 token 导出 hover、pressed、focus、disabled 和可读前景色。验证器
必须确保文字/背景、按钮文字/按钮背景对比度，overlay alpha 合法范围与
normal/focus/disabled 状态可区分。

`CinemaPalette` 是默认主题样式配置，不是 input engine、MediaStage 或 Danmaku
renderer 的业务配置。弹幕文字样式仍由独立的 `DanmakuStyle` 用户偏好控制。

`RequestVoiceInput` 有两个等价入口：SearchQueryField 内的语音图标，以及
键盘 action column 内的 Voice 键。两个入口必须派发同一个受控 action，并共享
可用性、请求中、partial/final result、取消和失败状态；不得创建两套录音/语音
会话。`RequestSystemIme` 位于 SearchQueryField 的语音右侧并请求 Horizon OS
系统 IME。语音或系统 IME 未可用时应显示受限状态。它们都不改变当前输入方法、
不清空 committed query、也不自动搜索。系统 IME 最终文本和语音最终文本写入
committed query；若它们到达时本地 Pinyin composition 仍未确认，先将 composition
原样追加到 query，再追加系统/语音最终文本，并清空本地 composition/candidates。
这样不丢失用户输入，也不假设系统/语音来源能安全转换本地 Pinyin。应用内中文
QWERTY 按键仅更新 composition，只有候选选择才部分提交其消费区间，应用内英文按键则直接写入 Latin
文本。中文 composition 非空时，应用内 Enter 只确认当前首选候选，不触发搜索；
composition 为空后下一次 Enter 才派发 `SubmitSearch`。英文无 composition，Enter
直接搜索。系统 IME 通过自身独立的确认按钮派发 `SubmitSearch`。三者都是同一
`SearchSession` 的输入源。系统 IME 适合系统已安装语言、
语音、手写和未来头显能力，应用内输入板则在系统中文 IME 缺失或主题需要手柄
直接输入时保证可用。

## Playback Config Popup

横版 `TRANSPORT` 的 `config` 是播放显示设置 `Popup`，与 Shorts 的内容级
`more -> 举报` 完全不同。第一期菜单：自动播放、速度、画质、弧形屏幕、屏幕尺寸。

- autoplay、speed、quality 是受控播放偏好；当前来源无法提供 quality 选项时显示
  不可用，不伪造切换成功。
- curved screen 与 screen size 只修改同一 `MEDIA_STAGE` 的 geometry、Transform
  或 Scale；不得创建第二播放器、Surface 或 MediaPanel。
- Popup 打开时不参与普通辅助 UI 淡出；Back 或明确 dismiss 区先关闭 Popup，才
  处理 Playback Canvas 的关闭。

## 系统工具与淡出

`SYSTEM_TOOLBAR` 的默认模块：

```text
ClockModule
BatteryModule
Enter2DModule
PassthroughModule
MoreActionsModule
```

- 时间默认显示本地 `HH:mm`，每分钟更新。
- 电量第一期只显示头显/系统设备电量和充电状态；控制器电量需要可靠 SDK 支持后再增加。
- 2D 切换使用熟悉的退出全屏图标按钮，外层 toolbar 背景透明，单个图标按钮背景不透明。

主题策略为 `AUTO_FADE` 的 `TRANSPORT` 保留独立播放控制淡出；策略为
`AUTO_FADE` 的 `SYSTEM_TOOLBAR`、`BROWSE` 和 `CONTEXT` 使用统一辅助 UI
计时器：播放时空闲约四秒后淡出；主视频 hover/click、手柄输入或任一辅助 panel
交互会唤回并重置计时；暂停时保持显示。`PERSISTENT`、`ON_DEMAND` 与
`TRANSIENT` 不接受该默认计时器强制隐藏。

淡出应使用空间 panel layer alpha 并在完全隐藏后禁用可见性和命中，参考 PremiumMediaSample 的 `PanelLayerAlpha` / `FadingPanel`，而不是仅改变 Android View 的 alpha。

## MediaStage、弹幕与曲面

### 舞台层级

```text
MediaStage
├── VideoLayer             唯一 Media3 Surface
├── FlatDanmakuLayer       与视频共同贴合的弹幕
├── SpatialDanmakuLayer    有深度的动态空间弹幕
├── SubtitleLayer          后续字幕
└── StageOverlayLayer      加载、快进和播放状态反馈
```

弹幕不是播放控制的附属文本。控制条只切换模式和样式；数据、轨道调度、样式解析和渲染由独立组件负责：

```text
DanmakuEngine
├── DanmakuSource
├── DanmakuScheduler
├── DanmakuStyleResolver
├── FlatDanmakuRenderer
└── SpatialDanmakuRenderer
```

### 形态

视频、贴合型弹幕和字幕共享 `StageGeometry`：

```kotlin
sealed interface StageGeometry {
    // Portrait Shorts uses Flat/Cylinder with width < height; no second stage type is needed.
    data class Flat(val widthMeters: Float, val heightMeters: Float) : StageGeometry
    data class Cylinder(
        val radiusMeters: Float,
        val arcDegrees: Float,
        val heightMeters: Float,
    ) : StageGeometry
}
```

```text
Flat:     视频 / 贴合弹幕 / 字幕使用平面映射
Cylinder: 视频 / 贴合弹幕 / 字幕使用同一段圆柱映射
```

弹幕模式：

```kotlin
enum class DanmakuMode {
    OFF,
    FLAT,
    SPATIAL,
}
```

- `OFF`：没有活动弹幕层。
- `FLAT`：跟随当前 `StageGeometry`；曲面视频时沿同一圆柱弧线排布。
- `SPATIAL`：在屏幕前方以动态 ECS 实体渲染，可配置深度、距离和视差，不需要严格贴合屏幕。

本地 Spatial SDK 0.13.2 的官方 AnimationsSample 已证明 Quad/Cylinder panel 形态及其动画能力存在。当前 ViriViri 使用自定义四边形 `TriangleMesh` 生成视频屏幕，不能直接假定标准 `CylinderShapeOptions` 会生效。曲面实现前必须进行一个独立验证：

1. 迁移至官方标准 media panel 曲面能力并验证现有 Surface/ISDK/2D handoff；或
2. 保留当前媒体 panel，重写为可采样的圆柱网格，让视频和贴合弹幕共享同一 UV 投影。

### 弹幕样式

```kotlin
data class DanmakuStyle(
    val fontFamily: String,
    val fontSizeSp: Float,
    val fontWeight: Int,
    val textColor: Long,
    val opacity: Float,
    val outlineColor: Long?,
    val outlineWidthDp: Float,
    val shadowEnabled: Boolean,
    val speed: Float,
    val laneDensity: Float,
    val topSafeAreaRatio: Float,
    val bottomSafeAreaRatio: Float,
    val maxVisibleCount: Int,
)
```

空间弹幕还需要距离、深度范围、曲面跟随强度和视差强度。主题默认样式与用户偏好叠加，不应把样式逻辑散落在 renderer 中。

## 低代码主题系统

主题不是颜色包，而是由场景、布局和模块编排组成：

```text
主题场景层      Meta Spatial Editor 场景、模型、材质、命名锚点
主题布局层      slot、尺寸、可见性、LayoutMode、动画
主题内容层      Panel Shell、模块组合、数据绑定和受控 action
```

推荐主题包结构：

```text
themes/
  cinema/
    scene/Main.metaspatial
    theme.json
    assets/

  cockpit/
    scene/Cockpit.metaspatial
    theme.json
    assets/
```

场景中定义语义锚点，而不是直接写业务名称：

```text
workspace_root
media_stage_anchor
browse_anchor
context_anchor
transport_anchor
system_toolbar_anchor
focus_anchor
action_sheet_anchor
pip_dock_anchor
```

主题配置将 slot 绑定到场景锚点和内容模块：

```json
{
  "id": "cockpit",
  "scene": "scene/Cockpit.metaspatial",
  "layouts": {
    "watch": {
      "mediaStage": "media_stage_anchor",
      "browse": "browse_anchor",
      "context": "context_anchor",
      "transport": "transport_anchor",
      "systemToolbar": "system_toolbar_anchor"
    },
    "shorts": {
      "mediaStage": {
        "anchor": "shorts_stage_anchor",
        "geometry": { "shape": "flat", "widthMeters": 0.72, "heightMeters": 1.28 }
      },
      "transport": {
        "anchor": "shorts_transport_anchor",
        "parentSlot": "mediaStage",
        "zOrder": "frontOverlay",
        "overflow": "visible"
      },
      "context": "shorts_context_anchor"
    },
    "focus": {
      "focus": "focus_anchor",
      "mediaStage": "pip_dock_anchor",
      "browse": "hidden",
      "context": "hidden"
    }
  },
  "toolbar": ["clock", "battery", "enter2d", "passthrough", "more"]
}
```

### 默认主题组件组与原子组件

默认影院主题可将横版播放控制 UX 引用为 `DefaultCinemaPlaybackCanvasGroup`。
它是主题层的组合配方，不是新的空间 slot，也不拥有播放器、Surface、Activity、
路由或固定场景实体。组内成员必须保持可单独导入与重新编排：

```text
SystemStatusStrip
ContentNavigationSlot
MediaStage
WatchTitle
TransportActionStrip
SeekTimeline
PlaybackConfigPopup
GrabHandle
```

主题配置可以引用组获得默认影院布局：

```json
{
  "group": "defaultCinemaPlaybackCanvas"
}
```

也可以跳过组，直接组装成员。下面是驾驶舱式拆分示例：

```json
{
  "systemToolbar": ["clock", "battery", "enter2d"],
  "persistentTransport": ["transportActionStrip", "seekTimeline", "grabHandle"],
  "leftNavigation": ["contentNavigationSlot"],
  "stage": ["mediaStage"],
  "settings": ["playbackConfigPopup"]
}
```

组的状态只是对原子组件状态/受控 action 的便利映射。例如默认 `config` 打开
`PlaybackConfigPopup`；二开主题可改用 `ActionSheet` 或常驻设置模块，但必须继续
调用相同 `SetAutoplay`、`SetPlaybackSpeed`、`SetQuality`、`SetStageGeometry` 和
`SetStageSize` action。`CinemaInputConsole` 同样只绑定 `InputConsoleStyle` 和
语义输入模块，不拥有 input engine。`GrabHandle` 可以在 transport 内重新定位，
仍必须是唯一的整套工作台操控入口。

主题内容通过受控模块组合：

```json
{
  "slot": "context",
  "shell": "panel",
  "header": ["creatorProfileSummary"],
  "tabs": {
    "选集": ["playlistList"],
    "相关": ["relatedVideoList"],
    "详情": ["videoTitleBlock", "videoStatLine", "videoDescription"]
  },
  "footer": ["videoActionBar"]
}
```

主题 action 只能调用白名单行为：

```text
openRoute("creator")
openFocus("collection")
setPlaybackSpeed(1.5)
setDanmakuMode("spatial")
switchTo2D()
togglePassthrough()
```

不允许主题 JSON 执行任意 Kotlin、网络请求或创建播放器/Surface。自定义复杂行为通过编译进应用的 `ThemePlugin` 提供，不能作为网络下载的可执行主题代码。

## 组件库演进与示例应用

沉浸式组件库先作为当前仓库内的独立 Gradle 模块演进，不立即创建独立仓库。这样 ViriViri 可以作为第一个真实使用方，先验证 API 是否能承载影院和驾驶舱两种主题。

第一阶段建议模块边界：

```text
:spatial-workbench-core
  PanelSlot、LayoutMode、Theme schema、受控 action 和数据绑定接口

:spatial-workbench-compose
  PanelShell、Tab、Drawer、Popup、ActionSheet、FocusPanel 和模块 renderer

:spatial-workbench-meta
  Spatial SDK slot binding、工作台抓手、panel fade、MediaStage 和 PiP dock

:spatial-workbench-media
  单播放器 Surface 契约、StageGeometry、弹幕层接口

:demo-cinema
  Mock 视频、Mock 作者、Mock 选集/关联数据和默认影院主题

:demo-cockpit
  Mock 数据、驾驶舱主题和对应 Meta Spatial Editor 场景
```

ViriViri 应保留 Bilibili provider、WBI、登录与授权、真实推荐/搜索/详情数据适配及产品业务路由。组件库不得依赖 Bilibili endpoint、DTO、Cookie 或凭证。

Demo 必须使用本地测试视频和 Mock 数据，不能请求 Bilibili 接口或携带 Cookie、账号、第三方视频资源。它的职责是验证主题、slot、Focus/PiP、曲面舞台、弹幕层、淡出和输入组件。

当以下条件满足时再提取独立仓库：

1. 默认影院和驾驶舱两个主题均可运行。
2. ViriViri 与至少一个 demo 都在消费同一组件 API。
3. 模块 API 已覆盖 slot、theme、layout、fade、MediaStage 和输入组件的核心契约。
4. 组件库可以独立构建、测试和在 Quest 上安装验证。

## 实施阶段

### Phase 1: 主题底座

1. 定义 `PanelSlot`、`ImmersiveLayoutMode`、`SpatialTheme` 和模块注册表。
2. 将现有 panel 的硬编码职责迁移到默认 `CinemaTheme`，视觉布局暂不大改。
3. 使用空间 panel layer alpha 实现辅助 UI 淡出。
4. 将输入、列表和详情拆为可组合模块。

### Phase 2: 视频上下文与工作台

1. 接入视频详情、选集和关联视频数据。
2. 实现左侧 Browse 和右侧 Context 的模块化布局。
3. 重构播放控制、顶部工具组和整体抓手。
4. 实现 UP Drawer、ActionSheet 和 Focus/PiP 状态机。

### Phase 3: 媒体舞台

1. 抽取 `MediaStage` 与 `StageGeometry`。
2. 验证 Quad/Cylinder 视频舞台，确保唯一 Surface 和 2D handoff 不回归。
3. 实现平面弹幕，再实现空间弹幕。
4. 将弹幕模式、样式和曲率接入主题配置。

### Phase 4: 主题创作

1. 安装 Meta Spatial Editor，并在场景中创建命名 slot anchor。
2. 制作默认影院主题场景。
3. 制作驾驶舱主题原型。
4. 增加 EDIT 布局模式、主题覆盖持久化和主题导入导出。

## 验收原则

- 任何主题和布局状态下都只有一个 `ExoPlayer` 与一个活动视频输出 Surface。
- 2D / 沉浸式 handoff、MR 切换和主题切换不能主动 seek 或创建第二个播放器。
- Focus/PiP 仅变化同一个媒体舞台的几何、Transform、Scale 和可见性。
- 主题模块没有 Bilibili endpoint、WBI、Cookie、Surface 或 Activity 路由依赖。
- 所有临时内容页、Drawer、ActionSheet 和弹幕实体都有明确关闭、取消和资源清理路径。
- 曲面视频、曲面贴合弹幕、空间弹幕、淡出和输入必须在 Quest 真机验证后才能标记完成。
