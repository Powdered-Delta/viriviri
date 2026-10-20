# PRD：播放队列来源可接管（连播「下一集」归属）

- **任务**：`.trellis/tasks/10-10-playback-queue-source-takeover/`
- **状态**：planning（**纯契约设计；未授权实现**）
- **上级任务**：`10-07-immersive-ui-theme-runtime-assessment`（主题运行时；「恰好一个状态转移 owner」原则）
- **提出**：用户 2026-10-10，原话：

> 当前连播来源应该是搜索结果页或者首页推荐列表。这个确实应该做成**可接管**的形式

---

## 1. 问题

### 1.1 修正：**当前根本没有自动连播**（2026-10-10 实测，推翻本任务最初的前提）

用户的描述是"当前连播来源应该是搜索结果页或者首页推荐列表"。
**但代码里连"连播"这个行为本身都没有。**

| 检查 | 结果 |
| --- | --- |
| `PlayerSession.setMediaSource(source, startPositionMs)` | **单数** → 替换整个队列（`ViriViriAppState.kt:189`） |
| `PlayerSession.setMediaItem(item)` | **单数** → 同上（`ViriViriAppState.kt:194-195`） |
| `setMediaItems`（复数） | **全仓库零命中** |
| `STATE_ENDED` / `onMediaItemTransition` / `seekToNext` / `hasNextMediaItem` | **全仓库零命中** |
| 3 处 `Player.Listener`（`SpatialVideoSampleActivity.kt:302-318`、`:319-345`、`RecommendationUi.kt:811-823`） | 全部只**观察**（报时 / 同步 transport / 视频尺寸），**无一推进** |
| `repeatMode` | 全仓库仅一处 `Player.REPEAT_MODE_ONE`（`SpatialVideoSampleActivity.kt:1476`）—— 循环**当前**视频 |

单条媒体项 + 无终局处理 ⇒ **播完即停**（或被 toggle 成循环同一视频）。

**因此本任务不是"把已有行为做成可接管"，而是：从零引入连播，并且一开始就把它设计成可接管的。**
（这其实是**好事**：没有历史行为需要兼容，契约可以从干净状态设计。）

### 1.2 即便如此，仍然没有 owner

| 候选 owner | 为什么它不是 |
| --- | --- |
| `PlaybackCanvas`（core） | 是**可见性**状态机（QUIET_WATCH/PLAYBACK/BROWSE/CONTEXT），管不了队列 |
| `ViriViriAppState` | spec 已声明它 own "**subsequent media-source request and player update**"（`.trellis/spec/frontend/bilibili-recommendation-playback.md:61-69`）—— 但那是"选中 → 解析 → 更新播放器"，**不含"下一个是谁"** |
| `PlayerSession` / `ExoPlayer` | 只管**唯一 Surface / 唯一播放器** |
| `selectAdjacentRecommendation(direction)` | 只是**手动**上一个/下一个（`ViriViriAppState.kt:1007`，接线于 `SpatialVideoSampleActivity.kt:1643,1646`） |

---

## 2. 已核实的事实（带证据）

### 2.1 项目里**已有**一个来源契约，但**完全没接线**

`spatial-workbench-core/.../WorkbenchContracts.kt:154-236`：

```kotlin
sealed interface PlaybackBrowseOrigin {
  data object Recommendations : PlaybackBrowseOrigin
  data class SearchResults(cacheKey, query, filters, snapshotId, pageCursor, scrollPosition) : PlaybackBrowseOrigin
}
object BrowseOriginResolver { fun continuation(origin, hasCachedSnapshot): BrowseContinuation }
object BrowseOriginValidator { fun validate(origin): List<BrowseOriginValidationIssue> }
```

- **全仓库引用只出现在** `WorkbenchContracts.kt` 与其测试 `WorkbenchContractsTest.kt:120-142`；
  **`app/` 零引用** → **纯契约、未接线**。
- 文档标注**诚实**：`docs/ui-structure-and-terminology.md:775` 标为 `[契约]`。
- **但它的用途是「浏览恢复」**（返回时恢复哪个列表），**不是**「谁拥有下一集」。
  `immersive-ui-low-code-architecture.md:456` 的原文："播放选择必须保存可恢复的浏览来源"。
- 目标文档的变体比实现**多一个** `ContinuationList(id, scrollPosition)`（`:466-469`）。

### 2.2 真实优先级（PiliPlus `b2d6a5be` 实证）

`lib/pages/video/controller.dart:1410-1423`：

```dart
PlaylistSource from = PlaylistSource.UP_ARCHIVE;
if (isPlayAll) {              // ① 从某个列表「播放全部」进入
  id = args['mediaId'];  extraId = sourceType.extraId;  from = sourceType.playlistSource!;
} else if (isUgc) {           // ② 否则，UGC 且有合集
  id = ugcIntroCtr.videoDetail.value.ugcSeason?.id;
  if (id != null) { extraId = 8; from = PlaylistSource.MEDIA_LIST; }
}                             // ③ 否则 UP_ARCHIVE（默认）
```

**优先级：进入时的列表 > 视频自身的合集 > 默认。**

用户 2026-10-10 网页端实测亦确认：合集下**仍有相关推荐**（内容不互斥），
被替换的只是**连播来源**。

---

## 3. 设计提案（纯契约）

### 3.1 来源值

```kotlin
/** 谁提供「下一集」。取值与 B 站自身的来源分类对齐。 */
sealed interface PlaybackQueueSource {
  data object None : PlaybackQueueSource                       // Media3 默认 / 无连播
  data class OriginList(val origin: PlaybackBrowseOrigin) : PlaybackQueueSource  // 搜索结果 / 首页推荐 / 延续列表
  data class Season(val seasonId: Long) : PlaybackQueueSource   // 视频自身的合集
  data class Series(val seriesId: Long) : PlaybackQueueSource
  data class MediaList(val mediaId: Long) : PlaybackQueueSource // 用户播放列表 / 收藏夹 / 稍后再看
}
```

> 复用既有 `PlaybackBrowseOrigin` 而不是另造一个"来源"概念 —— 同一份事实只应有一处表示。

### 3.2 接管状态机（纯 reducer）

```kotlin
data class QueueTakeoverRequest(
    val source: PlaybackQueueSource,
    val rank: Int,          // 优先级；见 3.3
    val requestId: String,  // 谁发起的，释放时用
    val reason: TakeoverReason,
)

sealed interface TakeoverReason { object Entry  ; object UserSelection ; object SystemPolicy }

sealed interface QueueTakeoverEvent {
  data class Request(val request: QueueTakeoverRequest) : QueueTakeoverEvent
  data class Release(val requestId: String) : QueueTakeoverEvent
}

data class QueueTakeoverState(
    val owner: PlaybackQueueSource,          // 恰好一个
    val ownerRequestId: String?,
    val pending: List<QueueTakeoverRequest>, // 未被满足的请求，按 rank 降序
)

object QueueTakeover {
  fun reduce(state: QueueTakeoverState, event: QueueTakeoverEvent):
      Pair<QueueTakeoverState, List<QueueTakeoverEffect>>
}
```

### 3.3 规则（**这是本任务的核心，必须逐条有测试**）

1. **单 owner**：任何时刻恰好一个 `owner`（对应"每个交互恰好一个状态转移 owner"）。
2. **按 rank 抢占**：`rank` 更高者抢占现任 owner，被抢占者进入 `pending`。
   **同 rank 不抢占**（先到先得），避免"从列表 A 进 → 又进列表 B"时互相抖动。
3. **显式释放**：只能由 `Release(requestId)` 释放，**不得靠超时**。
4. **回落次高**：释放后 owner 变为 `pending` 中 `rank` 最高者；`pending` 空则回落 `None`。
5. **幂等**：对同一 `requestId` 重复 `Request` / 对非 owner 的 `requestId` `Release` 均不得改变状态。
6. **rank 表**（依据 2.2 实测，**待产品确认**）：

   | rank | 来源 | 何时成为 owner |
   | --- | --- | --- |
   | 2 | `OriginList`（搜索结果 / 首页推荐 / 延续列表） | 进入播放时 |
   | 2 | `MediaList` / `Fav` / `WatchLater` | 从该列表进入时 |
   | 1 | `Season` / `Series` | **仅当没有 rank 2 来源时**（与实测一致） |
   | 0 | `None` | 兜底 |

7. **用户选择即接管**：用户从某个列表面板选中一集 → 该列表以 `UserSelection` 发起 `Request`（rank 同上）。
   这对应 PiliPlus 的 `ugcIntroCtr.onChangeEpisode(...)`（`controller.dart:450,594`）。

---

## 4. 范围

**做**：

- `spatial-workbench-core` 新增纯契约：`PlaybackQueueSource` / `QueueTakeoverState` /
  `QueueTakeoverEvent` / `QueueTakeover.reduce` / `PlaybackQueueSourceValidator`；
- 覆盖 3.3 全部 7 条规则的 JVM 单测（`spatial-workbench-core` 是纯 `kotlin.jvm`，无 Android 依赖）。

**不做（非目标）**：

- **不接线 `app/`** —— 与既有契约一致，先落契约；
- **不改 `PlaybackBrowseOrigin` 现有取值语义**（它是浏览恢复，本任务只**复用**它）；
- 不实现 Media3 `setMediaItems` 队列；不碰 `PlayerSession` 的唯一 Surface/唯一播放器不变量；
- 不动右 rail 的面板组合（那是原型侧的事，见 `docs/prototypes/interaction-skeleton/README.md` §4.1）。

---

## 5. 验收

| # | 条件 |
| --- | --- |
| A1 | `PlaybackQueueSource` / `QueueTakeover` 为纯 Kotlin（无 Android / Media3 依赖），`spatial-workbench-core` 可编译 |
| A2 | 规则 1（单 owner）有测试，且测试能**区分**"多 owner"实现（变异测试：删除单 owner 约束必然失败） |
| A3 | 规则 2（按 rank 抢占 + 同 rank 不抢占）各有测试，含"同 rank 二次 Request 不改变 owner" |
| A4 | 规则 3（显式释放，无超时）有测试 |
| A5 | 规则 4（回落次高 / 回落 None）有测试 |
| A6 | 规则 5（幂等）有测试 |
| A7 | `PlaybackQueueSourceValidator` 能报出非法值（如 `Season(seasonId <= 0)`）并有测试 |
| A8 | 全量 `:spatial-workbench-core:test` 通过；`app/` 与 gradle 文件**零改动** |
| A9 | 文档同步：`docs/ui-structure-and-terminology.md` 相应条目标 `[契约]`（若本次新增） |

---

## 6. 待决策（实现前需确认）

| # | 问题 | 选项 |
| --- | --- | --- |
| D1 | rank 表（3.3 规则 6）是否符合产品预期？ | 按实测 / 调整 |
| D2 | 是否**提升**既有 `PlaybackBrowseOrigin`（加 `ContinuationList` 变体、对齐目标文档）？ | 是 / 否 / 另立任务 |
| D3 | `PlaybackQueueSource` 是否要承载**播放偏好**（quality / speed / autoplay 开关）？ | 本任务不含；`autoplay=false` 时 owner 应为 `None` |
| D4 | 是否同时把"连播来源"暴露到 `ThemeContracts`（让主题可读）？ | 是 / 否 |

---

## 7. 风险

| # | 风险 |
| --- | --- |
| R1 | 契约做了但不接线 → 变成**第二个** `PlaybackBrowseOrigin`（既有死契约）。**必须在 PRD 里写明后续接线任务**，否则只是增加死代码 |
| R2 | rank 表若无实测支撑，会固化成错误语义 |
| R3 | 与 `PlayerSession` 的"唯一 Surface"不变量交叉时，队列切换可能触发重复 Surface —— 接线阶段必须验证 |

---

## 8. 后续任务（**必须在实现本契约前先登记，否则本任务只会再产出一个死契约**）

因为 1.1 已证明**连播行为本身不存在**，本任务（纯契约）单独交付**不会改变任何用户可见行为**。
必须同时登记下面这个接线任务，否则重蹈 `PlaybackBrowseOrigin` 的覆辙（契约 + 测试 + 零引用）。

**`10-10-playback-auto-advance-wiring`（待建，本任务不实现）**，范围：

1. `PlayerSession`：由 `setMediaSource`（单数）改为**队列**语义（`setMediaItems`）；
2. 终局处理：`Player.Listener.onPlaybackStateChanged(STATE_ENDED)` → 向 `QueueTakeover` 询问"下一个是谁"；
3. `ViriViriAppState`：把 `PlaybackBrowseOrigin`（进入来源）作为首次 `Request` 发出；
4. 合集的接管：`ugcSeason != null` 时以 rank 1 请求；
5. **必须验证 `PlayerSession` 的唯一 Surface 不变量不被破坏**（R3）。

### 与 spec 的关系

`.trellis/spec/frontend/bilibili-recommendation-playback.md:61-69` 已声明
`ViriViriAppState` own "the subsequent media-source request and player update"。
本契约**不与它冲突**：它 own 的是"选中 → 解析 → 更新"，本契约 own 的是"**下一个是谁**"。
接线任务完成后，应把这条关系补进该 spec。
