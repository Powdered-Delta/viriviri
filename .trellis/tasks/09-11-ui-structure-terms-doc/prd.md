# UI 结构与术语文档

## 目标

以当前源码为准，把 ViriViri 的 UI 结构（宿主、Spatial 实体树、面板内容层级、交互画布状态）
和对应术语整理成一份可长期维护的参考文档，用 ASCII 图与 Mermaid 图表达层级关系，并明确每个
术语在代码中的落点，避免"设计文档里的名字"和"代码里的名字"继续分叉。

交付物：新增 `docs/ui-structure-and-terminology.md`。

## 现状

- 已有 `docs/immersive-ui-low-code-architecture.md` 描述**目标架构**，但**当前实现**的面板
  实体、模块映射、槽位映射、状态枚举散落在 `SpatialVideoSampleActivity.kt`、
  `ImmersiveWorkbenchState.kt`、`SpatialPanelVisibilityController.kt`、
  `spatial-workbench-core` 契约中，没有一处集中说明。
- 存在同名不同义/异名同义情况，例如：core 的 `WorkbenchCanvas`（主题画布）与
  `PlaybackCanvas`（运行时交互焦点）、app 的 `WorkbenchContent`（工作台内容）；
  `PanelSlot.BROWSE` 实际承载 CenterContent、`PanelSlot.CONTEXT` 同时被左右两条 rail 复用、
  `PanelSlot.ACTION_SHEET` 实际承载输入法面板。
- 部分 core 契约（`SHORTS_*`、`FOCUS`、`ACTION_SHEET`、`SystemToolbarModule`、
  `ThemeRoute` 等）尚未接线到 runtime，需要显式标注"仅契约、未接线"，否则会被误读为已实现。

## 待办

1. 术语表：分层列出宿主 / 空间实体 / 面板与槽位 / 工作台模块 / 画布状态 / 内容路由 / 搜索输入 /
   播放控制 / 主题与视觉 / 弹幕，逐条给出源码位置。
2. 结构层级图：宿主 → Spatial 实体父子树 → 面板内容（ASCII + Mermaid）。
3. 状态机图：`ImmersiveWorkbenchState`、`PlaybackCanvas`、`SearchWorkspaceRoute`（Mermaid）。
4. 逐面板 UI 结构（ASCII）：GlobalNavigation、Left Detail rail、Center workspace、
   Right Context rail、Transport、Input console、Stage overlays、2D window。
5. "术语 → 代码落点"映射表，以及已发现的命名不一致清单（只记录，不在本次重命名）。
6. 把本文档的**更新规则**写入仓库根 `AGENTS.md`（作为项目本地章节，落在 `TRELLIS:END`
   之外）：何时必须改、改哪一节、术语校验与分层标注要求。

## 非目标

- 不修改任何 runtime 行为、实体层级、面板尺寸或命名。
- 不重写或替换 `docs/immersive-ui-low-code-architecture.md`（目标架构另文）。
- 不描述未来主题系统的完整 schema，只标注当前实现与契约的边界。

## 参考

- `docs/immersive-ui-low-code-architecture.md`（目标架构）
- `docs/prototypes/workbench/README.md`（Web 原型与交互契约）
- `spatial-workbench-core/src/main/kotlin/.../core/*.kt`（`PanelSlot`、
  `ImmersiveLayoutMode`、`PlaybackCanvas`、`MediaStage*`、`SpatialTheme`）
- `app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt`（`registerPanels`、
  `onVRReady`、`applyWorkbenchModules`）

## 验收

- `docs/ui-structure-and-terminology.md` 中每个术语都能对应到具体源文件/类型名，且与实际代码一致。
- 结构图能完整还原 `workbenchRoot → spatialized_video_panel → 各面板` 的父子关系与模块映射。
- 文档明确区分"已实现"与"仅 core 契约、尚未接线"。
- `AGENTS.md` 中存在本文档的更新规则章节，其中引用的兄弟文档路径（`immersive-ui-low-code-architecture.md`、`prototypes/workbench/README.md`、`spatial-coordinates.md`）均真实存在。

## 附注

- 编辑 `AGENTS.md` 时发现 `TRELLIS:END` 标记在 `b67a941`（"improve agent execution guidance"）
  中被误删，导致其后 270 余行项目本地内容落入托管块内、可能被 `trellis update` 覆盖。
  本次已恢复该标记，新增章节位于标记之外。
