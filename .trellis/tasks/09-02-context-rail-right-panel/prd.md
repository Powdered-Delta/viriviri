# 右面板完善为 Context rail（选集/相关/弹幕）

## 目标

把 Workbench 右面板从 mode_panel 占位（标题 + 显示比例/画布尺寸/2D 入口 + debug aspect 控件）补全为
Context / Source rail，按 `docs/prototypes/workbench/README.md` 的设计提供选集、合集/播放列表、
相关视频与弹幕状态切换。

## 现状

- 右面板现仅为 mode_panel 占位内容。
- runtime 面板已设 0.7m 宽。
- debug aspect 控件后续隐藏或迁走。

## 待办

1. 选集（parts）列表。
2. 合集 / 播放列表。
3. 相关视频。
4. 弹幕状态切换。
5. debug aspect 控件后续隐藏或迁走。

## 非目标

- 不改变左面板 / 中心面板现有结构。
- 不引入第二个播放器或 Surface。

## 参考

- `docs/prototypes/workbench/README.md` 的 Context / Source rail 设计。
- 相关源码：`app/src/main/java/com/m0e_n00b/viriviri/RecommendationUi.kt`、
  `SpatialVideoSampleActivity.kt`、`ViriViriAppState.kt`。

## 验收

- 右面板展示完整的 Context rail（选集 / 相关 / 弹幕等），交互可用。
- debug aspect 控件不再占据右面板主区域。

---

## 2026-10-10 更新（术语修正 + 分工决策 + 新增范围）

### 1. 术语修正：原描述的「合集 / 播放列表」不准确

原文写「提供选集、**合集/播放列表**、相关视频」。经源码求证（PiliPlus `b2d6a5be`），
**「合集」与「播放列表」是两个不同对象**，而且原文里的「列表」是**第三个**对象：

| 术语 | B 站对象 | identity id | 归属 | 跨 UP |
| --- | --- | --- | --- | --- |
| **合集** | `ugc_season`（含 `sections`） | `season_id` | **一个 UP** | 否 |
| **系列** | `series` | `series_id` | UP | 否 |
| **播放列表** | media list | `media_id`（`pl…`） | **用户** | **是** |

证据见 [docs/research/bilibili-list-semantics.md](../../../docs/research/bilibili-list-semantics.md)。
另有一个**命名陷阱**：B 站视频页那个字面叫「播放列表」的 tab，显示条件是 `hasSeasonOrParts`，
**里面装的其实是分P + 合集**。

**本任务范围以「合集（含分P）」为准**；真正的「播放列表」（用户自建、跨 UP）是独立 feature，
**不在本任务内**。

### 2. 已决策的分工（2026-10-10）

| 面板 | 内容 |
| --- | --- |
| **左 rail**（`video_selector_panel`） | **视频信息 + 评论**（标题 / 数据 / 动作条 / UP / 简介 + 评论固定底部） |
| **右 rail**（`mode_panel`，本任务） | **合集 + 相关视频** —— `[合集]` `[相关视频]` 两个 tab；**不含"详情"**（已划归左 rail） |

这同时修正了原验收里的「选集 / 相关 / 弹幕等」——**"选集"归并入"合集"**（分P + 合集 `sections`）。

### 3. 新增范围：修 dp 画布宽高比缺陷（**属本任务，不可留**）

`modePanelSettings()` 的 quad 与左 rail **完全相同**（`railWidth × leftRailHeight` = 0.70 × 0.90），
但 dp 画布不同：

| 面板 | quad 宽高比 | dp 画布 | dp 宽高比 | 偏差 |
| --- | --- | --- | --- | --- |
| 左 rail `selectorPanelSettings` | 0.7778 | **361 × 464** @800 | 0.7780 | ✅ |
| **右 rail `modePanelSettings`** | 0.7778 | **280 × 464** @600 | **0.6034** | ❌ **−22.4%** |

代码注释写着 "its height now matches the left rail for visual symmetry"，
但**只在 quad 上做了对称，dp 画布没同步** → UI 横向拉伸约 29%，且横向排版空间比左 rail 少 22%。

**本任务需把右 rail 的 dp 画布对齐左 rail（`361 × 464`），或至少满足 `dpW/dpH == quadW/quadH`。**

### 4. 相关任务（边界，避免重复）

- `10-10-playback-queue-source-takeover` —— 「谁拥有下一集」的**播放队列来源**契约。
  与本任务**正交**：本任务决定**面板显示什么**；它决定**连播来源**。
  两者共享同一个"进入来源"概念，实现时需对齐（见该任务 PRD §1.2）。
- 线稿参考：`docs/prototypes/interaction-skeleton/panel-wireframes.md` §3 —— 右 rail 目标线稿（真实 dp）。
  Penpot 里对应 `W3 right_rail` 板。

### 5. ⚠️ 待决策：原文第 4 项「弹幕状态切换」是否仍在右 rail？

原 `## 待办` 第 4 项列出了**弹幕状态切换**，但 2026-10-10 的分工把右 rail 定为
**合集 + 相关视频**，未包含弹幕开关。三选一待定：

- **A**：弹幕开关并入右 rail（第三个 tab，或一行开关）
- **B**：弹幕开关移到 transport 的设置菜单（与音量 / 清晰度 / 倍速同族）
- **C**：本期不做，另立任务

**实现前需确认。**
