# PRD：沉浸式 UI 主题运行时（渐进式大重构）

- **任务目录**：`.trellis/tasks/10-07-immersive-ui-theme-runtime-assessment/`
- **状态**：planning（**等待用户确认产品决策 D1–D6 后才进入 `in_progress`**）
- **设计依据**：`docs/immersive-theme-runtime-design.md`（现状核实 + 可执行设计）
- **关联文档**：`docs/ui-structure-and-terminology.md`（当前实现）、
  `docs/immersive-ui-low-code-architecture.md`（目标架构原始设想）、
  `docs/spatial-coordinates.md`、
  `.trellis/spec/frontend/`（state-management / quest-spatial-panel-debugging /
  media3-surface-handoff / component-guidelines）

---

## 1. 背景

ViriViri 的沉浸式 UI 已经有一套**声明式主题契约**（`spatial-workbench-core`：
`SpatialTheme` / `SlotPlacement` / `ComponentNode` / `ComponentGroup` / `WorkbenchCanvas` /
`ThemeValidator`），以及一套**完整但未接线的弹幕 overlay 合同**
（`MediaOverlayContracts.kt`：`DanmakuSurfaceGroup` / `DanmakuDepthLayer` /
`DanmakuOcclusionDomain` + `MediaOverlayValidator.kt`）。

但核实后发现（详见设计文档 §2、§3）：

1. `SpatialTheme` 的**唯一** runtime 消费者 `ImmersivePlaybackCanvasHost` **只读 `presentationPolicies`**，
   且**只有 `PERSISTENT` 一项被消费**；`SlotPlacement` 的
   anchor / offset / rotation / size / shape / parent / depthRelation **全部零消费**。
   其输出还会经有损 bridge 被第二套 reducer 重新决定（见设计文档 §0.1）。
2. 真实布局来自 app 模块的 `WorkbenchLayoutConfig`（Kotlin 常量），与主题契约**互不相通**。
3. `CinemaTheme` 声明的 9 个场景 anchor 在 `app/scenes/Composition/Main.scene` 里**全部不存在**
   （该场景只有 3 个节点）。
4. `GrabPilotSystem` 是**死代码**（未注册）；当前抓取只有单一父子层级一种形态，
   且"SDK 父子传播"这一机制**从未头显验证**。
5. 弹幕只有 **1 个 overlay 实体 + 1 份 per-surface 轨道状态**；`DanmakuSurfaceGroup` 在 `app/` 下**零引用**。
6. `ComponentGroup` 是 **UI 组合组**，不含任何抓取或实体语义。
7. 可见性有**两套来源**（`ImmersiveWorkbenchReducer.modules()` 与
   `PlaybackCanvasReducer.visibleSlots()`），已产生行为不一致。
8. 面板几何存在**三种不同类型**（普通注册面板 / 场景授权面板 / 手工 compositor 媒体面板），
   受控路径与可主题化几何能力各不相同，不能统一处理。

即：**二开主题今天只能改"哪些 slot 可见"，改不了"面板放在哪、多大、什么形状"，也无法声明抓取组或弹幕组。**

## 2. 目标（程序级）

二开主题必须能在**不改播放器、业务状态机和 Activity 主流程**的前提下：

1. 从受控 UI 组件中**选择 / 添加 / 删除 / 替换 / 重排 / 组合**界面元素，并调整布局关系与 slot 归属。
2. **配置每个空间面板的布局参数**：anchor/parent、offset(XYZ 米)、rotation(pitch/yaw/roll)、
   宽高与形状/曲率、深度关系与层级、显示策略、overflow 与 hit-test。
3. **声明多个有独立语义的抓取组**（handle、members/followers、跟随策略、生命周期、
   组间父子、防重复移动、失去跟踪恢复）。
4. **配置独立的弹幕面板组**（一 group 多 surface/layer、共享或隔离轨道、发射方向、容量、
   深度层、组间负载与视点遮挡）。
5. **解耦**业务 action / 业务数据 / 空间布局 / UI 组合 / 实体显隐；同一语义 action 只有一个实现、
   每项交互只有一个状态转换 owner。

**不允许**：主题执行任意代码、创建 Player / Surface / Activity / Spatial Entity、
绕开场景授权制造固定布局。
**必须保持**：单一 `PlayerSession` / 单一 `ExoPlayer` / 单一活动视频 `Surface`。

## 3. 本任务范围

### 3.1 已批准开工：Phase 0 + Phase 1

| 阶段 | 内容 | 状态 | 改变 app 可见行为？ | 需头显？ |
| --- | --- | --- | --- | --- |
| Phase 0 | 诚实性与前置（修正过期文档、修正错误标注、验证 mse-agent 命令） | **✅ 已批准** | 否 | 否 |
| Phase 1 | 主题 schema + parser + 迁移 + validator + fixture（**纯 Kotlin**） | **✅ 已批准** | 否 | 否 |

**"Phase 0 + Phase 1 不改 app 行为"是明确承诺**：本阶段只改文档 + 新增 core 合同/解析/校验/fixture/单测，
**不接 adapter、不注册新 system、不改面板注册、不改可见性、不改场景**。
验收仅限纯 Kotlin 测试与文档准确性（见 §5.1）。

### 3.2 方向已确认，但执行需单独批准：Phase 2

Phase 2 的整体方向已确认（见 §8），但它**不属于本次批准的开工范围**，原因是它的每一步都触碰既有边界：

| 子步 | 触碰的边界 | 需头显 |
| --- | --- | --- |
| 2.1 场景新增 anchor | **静态布置授权源**（Meta Spatial Editor 场景） | 导出可静态检查；观感需 |
| 2.2–2.3 单面板 placement 试点 | app adapter + `settingsCreator` | 是 |
| 2.4 类型 B（场景面板） | 既有 reparent + `PanelDimensions` 改写 | 是 |
| 2.5 类型 C（视频面板） | 自定义 `TriangleMesh` 曲率、compositor layer、命中、单 Surface 不变量 | 是 |
| 2.6 其余面板迁移 | app adapter 全量 | 是 |
| 2.7 **可见性 owner 收敛** | **行为变更**（`NORMAL` 下双 rail 可能不再恒显） | 是 |

> **流程状态说明**：`task.json.status = in_progress` 是 Trellis 的流程记账
> （prd + jsonl 就绪即翻牌），**不代表 Phase 2 的 app 行为改动已被批准**。
> 执行者必须以本节为准。

**Phase 2 的进入条件见 §10。**

### 3.3 后续子任务（不在本任务内）

| 阶段 | 内容 |
| --- | --- |
| Phase 3 | 抓取组接线（`GrabGroupRegistry` + `GrabPilotSystem`）；**前置 gate：头显验证 `NATIVE_PARENT` 传播** |
| Phase 4 | 弹幕组多 surface（`DanmakuGroupAllocator`） |
| Phase 5 | UI 组件重组（`ThemeComponentRegistry` + renderer） |

### 3.4 非目标

- 不重写 UI、Activity、scene、播放器、弹幕引擎。
- 不新增 Gradle module（现有 `:app` / `:spatial-workbench-core` / `:spatial-workbench-compose`
  三个模块足够承载 Phase 0 + Phase 1）。
- 不把现有静态注册面板改为运行时高频创建（规避 Meta 已知问题：动态 Compose 面板可能失去触摸响应）。
- **Phase 0 + Phase 1 不做头显验收**（无视觉改动，无法也不需要）。
  Phase 2 的几何/命中/遮挡结论在无头显时只能标为「已接通、未验收」，不得计入"已完成"。
- 不在 Phase 0 + Phase 1 内修改 `app/scenes`、面板注册表或任何 `Spatial` 运行时行为。

## 4. 需求（Phase 0 + Phase 1）

### R1 诚实性（Phase 0）

- R1.1 修正 `docs/immersive-ui-low-code-architecture.md:10` 关于"当前项目没有可用的 `mse-agent`"
  的过期声明。**精确表述**：`mse-agent.exe` 存在且 `-h` 返回完整子命令表（已核实）；
  但它在**本项目当前场景**上的读写能力**未核实**（需编辑器运行并监听 IPC）。
  因此写成"配置指向 MSE v16 且 CLI 可用；场景读写能力待 Phase 2 开工前实测"。
- R1.2 在 `docs/ui-structure-and-terminology.md` 记录两条已核实事实：
  (a) 主题契约（含 `SlotPlacement` 几何）零 runtime 消费；
  (b) `CinemaTheme` 的 scene anchor 名在场景中不存在。
  并修正两处标注：
  - `:731` 的 `DanmakuLaneScheduler`：`[实现]` → `[未接线]`。
    **精确理由**：**入口不可达**（`ViriViriAppState.loadDanmaku()` 无生产调用者），
    而非"完全没有源码调用"——`loadDanmaku() → prepareDanmaku()` 这级调用是存在的。
  - `:640` 的 `PanelPresentationPolicy`：`[契约]` → **`[实现]（部分）`**。
    它确实被 `PlaybackCanvasContracts.kt:82-86` 读取，但**只有 `PERSISTENT` 一项被消费**；
    `AUTO_FADE` / `ON_DEMAND` / `TRANSIENT` / `THEME_CONTROLLED` 无任何 reader，
    不得笼统标成"由主题驱动"。
- R1.3 输出评估与设计文档 `docs/immersive-theme-runtime-design.md`。

### R2 主题 schema 与版本（Phase 1）

- R2.1 `SpatialTheme` 增加 `schemaVersion: Int`；定义 `SUPPORTED_SCHEMA_VERSIONS`。
- R2.2 迁移链 `ThemeSchemaMigration`：`vN -> vN+1` 纯函数；迁移在校验**之前**执行。
- R2.3 未知（未来）版本 → 明确 issue `UnsupportedSchemaVersion`，**不静默降级**。
- R2.4 主题来源只允许 APK 内置资源；解析器不提供任何网络入口。

### R3 解析与目录注入（Phase 1）

- R3.1 `ThemeJsonParser`：JSON → 规范化模型，产出**可定位的** issue（带字段路径）。
- R3.2 `SceneAnchorCatalog`：把"场景 anchor 是否存在"抽象为可注入接口，
  使 validator 保持纯函数、可在 JVM 单测中断言（**这是让几何校验可测试的关键**）。
- R3.3 `EntityCatalog`：把 `EntityRef`（`workbenchRoot` / `mediaStage` / `grabBar` …）
  是否已注册抽象为可注入接口。
- R3.4 默认主题 fixture 文件化：`themes/cinema/theme.json`，同时保留 `CinemaTheme.create()`
  作为"无主题时的保底 fixture"。

### R4 三类 group 的独立合同（Phase 1）

- R4.1 **`ComponentGroupSpec`**（UI 组合）：成员、顺序、slot 归属白名单、`reusable`。
  明确**不含**实体、抓取、渲染目标语义。
- R4.2 **`GrabGroupSpec`**（空间操控）：`handleRef`、`parentGroupId`、`mechanism`
  （`NATIVE_PARENT` / `DELTA_MIRROR`）、`members: List<GrabMemberSpec>`、
  `restPose`、`lifecycle`、`lostTrackingPolicy`、`exclusiveWith`、
  `suppressOthersWhileGrabbed`。
- R4.3 **`DanmakuSurfaceGroup`**（弹幕渲染目标）：沿用现有合同，新增
  `laneStateSharing`（`SHARED` / `ISOLATED`）、`maxActiveItems`、`occlusionDomainId`。
- R4.4 三者**各自独立**的官方 id 空间与校验；**不得**合并为一个通用 group 列表。
- R4.5 默认抓取组必须**等价复刻现状**（单组、`NATIVE_PARENT`、行为不变）。

### R5 校验器扩展（Phase 1）

必须新增并单测覆盖以下 issue：

| issue | 规则 |
| --- | --- |
| `MissingSceneAnchor` | `requiredAnchors` / `anchor.sceneNode` 不在 catalog 中 |
| `UnknownComponent` | `panels[].component` 不在受控组件目录 |
| `DuplicateSlotOwner` | 同一 slot 被多个面板占用（除显式允许共享） |
| `ParentCycle` | `panels[].parent` 成环 |
| `DepthRelationWithoutParent` | `FRONT_OF_PARENT` / `BEHIND_PARENT` 但 `parent == null` |
| `OffsetOutOfRange` | offset 各轴超出 ±3.0 m |
| `RotationOutOfRange` | pitch/roll 超出 ±80°、yaw 超出 ±180° |
| `ZOrderOutOfRange` | `zOrder` 超出 ±100 |
| `HitTestConflict` | `hitTest == NONE` 与 `resizable.enabled == true` 并存 |
| `GrabMemberOverlap` | **父子抓取组共享同一成员（会导致 delta 应用两次）** |
| `GrabHandleSelfReference` | handle 出现在自己的 `members` 中 |
| `AsymmetricExclusion` | `exclusiveWith` 不对称 |
| `UnknownEntityRef` | `handleRef` / `entityRef` 不在 `EntityCatalog` |
| `InvalidLaneCount` | `laneCount` 不在 `1..64` |
| `UnknownOverlaySurface` | `layers[].surfaceIds` 中的 id 不在 overlay surface 注册表 |

同时把既有的 `InvalidPanelSize` / `InvalidCylinderGeometry` 补上下界
（size `0.15–4.0 m`；cylinder 半径 `1.5–8.0 m`，对齐 `PlaybackStageCurvature`）。

> **实现后修正（D9）**：本表原先把 `UnknownOverlaySurface` 写成覆盖两个子句
> （"不在注册表" **或** "kind 不是 DANMAKU"）。实际实现中后者由**既有的**
> `InvalidLayer(reason="surface-does-not-support-danmaku")` 产出，且 `MediaOverlayContractsTest` 依赖该行为。
> 为不改既有行为，改的是**本 PRD 的措辞**，不是代码。

### R6 测试（Phase 1）

纯 Kotlin JVM 测试，覆盖：解析、迁移、每条新增校验规则、三类 group 的冲突检测、
组合重排结果、placement 合成。**不触碰 app 行为**。

### R7 身份模型与校验条件化（Phase 1，**新增，必须先做**）

> 这一组是 Phase 2 的前置合同。若不在 Phase 1 固定，Phase 2 的 adapter 没有可实现的目标。

- R7.1 **五个独立 id 空间**：`PanelId`（单一空间 panel 实例）/ `SlotId`（语义能力，允许 1:N）/
  `ComponentId`（panel 内 UI 组件）/ `EntityRef`（adapter 解析的实体）/ `OverlaySurfaceId`
  （渲染目标，**不是** UI slot）。不得互相代用。
- R7.2 **`DuplicateSlotOwner` 条件化**：不再是"一槽一 panel"的绝对规则。同一 layout 内一个
  slot 可被 N 个 panel 绑定，但每条绑定必须显式给出 `sharePolicy`
  （`EXCLUSIVE`（默认）/ `MIRRORED`（需成对 + 同 `mirrorGroup`）/ `INDEPENDENT`）。
  依据：当前产品就有 `PanelSlot.CONTEXT` 一槽两 rail 的既有事实。
- R7.3 **anchor 与 offset 语义**（8 项，见设计文档 §5.5）：anchor world pose 归场景且运行时只读；
  单位/坐标系/旋转顺序（Yaw→Pitch→Roll）；parent 变更时 local pose 换算（reparent 不跳变）；
  主题静态值与用户运行时调整分开（`userAdjustable`）；用户调整的持久化是否 per-theme；
  anchor 改名视为破坏性变更；默认姿态单一来源。
- R7.4 **面板类型学**（`panelClass`：`UI_PANEL` / `SCENE_PANEL` / `MEDIA_SURFACE`）
  与 `themeableGeometry` 白名单 —— 使"主题可改全部面板尺寸"的假能力在 **schema 层就被拒绝**
  （`GeometryNotThemeable`），而不是等到 adapter 静默不生效。
- R7.5 **`DanmakuDepthLayer.surfaceIds: Set<String>`**（D4=A）连同其连锁语义一起定义：
  分配域 ≠ 复制域（一次事件落到恰好一个 surface）；`SHARED`/`ISOLATED` 与多 surface 组合的
  合法性规则（`AmbiguousLaneSharing`）。

### R8 二开能力边界的合同化（Phase 1）

- R8.1 把 §13 的边界写进合同文档与 validator 注释：
  主题只能在**已注册 panel 集合内**重组受控组件、配置几何、绑定 slot、声明抓取/弹幕组；
  **不能**运行时动态创建/销毁 Spatial panel。
- R8.2 受控 action 白名单保持封闭；`ThemeComponentRegistry` 为唯一组件来源。
- R8.3 记录"只做一个主题不足以证明组件组合"的要求，作为 Phase 5 的验收前提。

### R9 面板几何适配（Phase 2，**方向已确认，执行需单独批准**）

> R9 的内容与设计文档 §7 Phase 2 的 2.1–2.8 子步一一对应；**不得作为单个 patch 落地**。

- R9.1 **场景授权**：用 mse-agent 新增语义 anchor 节点（至少 `media_stage_anchor`、
  `transport_anchor`、`system_toolbar_anchor`、`browse_anchor`、`context_anchor`），
  经 `:app:export` 验证导出制品含这些节点。必须用 mse-agent 命令，**不得手改 YAML**。
- R9.2 `PanelPlacementResolver`（core，纯函数）：anchor 姿态 × 主题局部 offset/rotation
  → 目标局部 pose；可 JVM 单测。
- R9.3 `ThemePanelLayout` 自定义 ECS 组件 + `settingsCreator` 读取
  → `QuadShapeOptions` / `CylinderShapeOptions`。
- R9.4 `PanelLayoutAdapter` **按 §2.8 三类分流**：
  类型 A 走注册 + reshape；类型 B 接手既有 reparent + `PanelDimensions` 改写（不叠加）；
  类型 C 走专用 adapter，只开放 `themeableGeometry`，保留曲率守卫与单 Surface 不变量。
- R9.5 reshape 的 pending/replay **单一 owner**（复用 `StageOverlayReshapeSystem` 思路，
  不得每类面板各写一套）。
- R9.6 `WorkbenchLayoutConfig` 降级为默认主题 fixture 数据源。
- R9.7 主题几何**预览 / 一键回退默认主题**，回退入口必须在**工作台之外可达**。
- R9.8 **最后**单独切换可见性 owner（D2），并显式记录 rail 行为变化。
- R9.9 **回滚是按 panel 的集合**（`PLACEMENT_ADAPTER_PANELS`），不是单一全局 bool；
  2.1–2.6（几何）与 2.7（可见性）必须分开提交。
- R9.10 头显验收；无头显时只能标为「已接通、未验收」。

## 5. 验收标准

### 5.1 Phase 0 + Phase 1（本任务开工范围，**无需头显**）

| # | 条件 | 方式 |
| --- | --- | --- |
| A1 | `docs/immersive-theme-runtime-design.md` 存在且每条结论有源码/文档出处 | 人工 |
| A2 | `docs/immersive-ui-low-code-architecture.md` 的 mse-agent 过期声明已修正（按 R1.1 的精确表述） | `git diff` |
| A3 | `docs/ui-structure-and-terminology.md` 已记录"主题契约零消费"与"anchor 悬空"，并修正两处标注（按 R1.2） | `git diff` |
| A4 | `:spatial-workbench-core:test` 全绿 | `.\gradlew.bat :spatial-workbench-core:test` |
| A5 | R5 表中每条 issue 都有对应单测断言 | 测试清单 |
| A6 | R7 的身份模型 / slotBindingPolicy / 面板类型学 / `surfaceIds` 均有合同类型 + 单测 | 测试清单 |
| A6a | **anchor/offset 语义 8 项**（设计 §5.5）：**#1–#4 已编码 + 有测试**；**#5 部分**（静态值与 `userAdjustable` 已分离，强制无调用方）；**#6 剔除**（改判为产品决策 D7，非 Phase 1 范围）；**#7 由 `requiredAnchors` + catalog 拒载覆盖**；**#8 属 Phase 2**（`WorkbenchLayoutConfig` 降级） | 测试清单 + 设计 §5.5 |
| A7 | 默认主题 fixture 可从 `themes/cinema/theme.json` 解析并通过校验 | 单测 |
| A8 | 一槽两 panel 的既有布局能用 `MIRRORED` 表达并通过校验；未声明时被拒绝 | 单测 |
| A9 | `NATIVE_PARENT` 在无头显验证记录时产生 `UnverifiedGrabMechanism` warning（**不是静默通过**）；`DELTA_MIRROR` 不报 | 单测 |
| A10 | **`app` 模块行为零变化**：无 `app/` 逻辑改动、未改面板注册、未改场景、未注册新 system | 代码审查 + `git diff --stat` |
| A11 | `git diff --check` 干净 | 命令 |
| A12 | 若触及构建配置（如新增解析依赖），`.\scripts\build-windows-debug.ps1` 通过 | 命令 |

> **A10 是本阶段的核心不变量。** 任何需要改 `app/` 行为才能通过的验收项，
> 说明实现越界到了 Phase 2，应拆分而不是放宽 A10。

### 5.2 Phase 2（未批准开工；此处仅登记其验收形态）

- 几何/命中/遮挡/可见性结论**全部需头显**；无头显时只能标「已接通、未验收」。
- 「能分配到多个 surface」（纯 Kotlin 可证）与「视点遮挡正确」（**头显才能证**）**分开计分**。
- 每个子步（2.1–2.8）单独构建 + 单独真机确认后才能进下一步。

## 6. 交付物

### 6.1 Phase 0（文档诚实性）

1. `docs/immersive-theme-runtime-design.md` — 现状 vs 目标矩阵、三类 group 领域模型、
   身份模型、面板类型学、schema 草案与校验规则、anchor/offset 语义、尺寸分流、
   runtime 链路与 owner（现状/目标分列）、分阶段路线、风险、验收、二开能力边界。
2. `docs/ui-structure-and-terminology.md` — 增补两条已核实事实，并修正两处标注（按 R1.2）。
3. `docs/immersive-ui-low-code-architecture.md` — 修正 mse-agent 过期声明（按 R1.1）。
4. 本 PRD + `research/` 下的 4 份核实记录。

### 6.2 Phase 1（纯 Kotlin）

5. `spatial-workbench-core`：
   - schema 版本 + 迁移链（R2）
   - parser + `SceneAnchorCatalog` / `EntityCatalog` 注入抽象（R3）
   - 三类 group 独立合同（`ComponentGroupSpec` / `GrabGroupSpec` / `DanmakuSurfaceGroup` 扩展）（R4）
   - 扩展 validator，含 R5 全部新 issue 与 R7.2 的条件化 `DuplicateSlotOwner`（R5、R7）
   - 五个 id 空间 + `panelClass` + `themeableGeometry` + `surfaceIds`（R7）
6. `themes/cinema/theme.json` — 默认主题 fixture（R3.4）
7. 纯 Kotlin 单测（新增/扩展），覆盖 R6 与 5.1 的 A5–A9

### 6.3 明确**不在**本任务交付

- 任何 `app/` 逻辑改动（`settingsCreator`、adapter、system 注册）
- `app/scenes` 场景改动（Stage anchor 节点）
- 可见性 owner 收敛
- 头显验收结论

## 7. 风险与回滚

| 风险 | 缓解 | 回滚点 |
| --- | --- | --- |
| `temp/` 目录的 vendored 样例污染检索结论 | 所有检索显式排除 `temp/` | — |
| 引入 JSON 解析依赖改变构建 | 优先手写最小 parser；若加 kotlinx.serialization 则单独 commit | 该 commit |
| `SpatialTheme` 加字段破坏既有构造调用 | 新字段给默认值 | 单 commit 回退 |
| 默认主题 fixture 文件化后与 Kotlin fixture 漂移 | 单测断言两者解析结果一致 | 保留 Kotlin fixture 为保底 |
| 校验规则过严导致既有 `CinemaTheme` 不通过 | Phase 1 先让既有 fixture 通过，再收紧规则 | 放宽单条规则 |
| 把 Phase 1 做成"顺手改 app"（越界） | A10 为核心不变量；越界即拆分为 Phase 2 子任务 | 拆 commit |
| 条件化 `DuplicateSlotOwner` 设计不当，导致既有双 rail 无法表达 | A8 单测：既有一槽两 panel 必须能通过 `MIRRORED` 表达 | 放宽为 `INDEPENDENT` |

本阶段（Phase 0 + Phase 1）**不改变任何 app 视觉/交互行为**，因此无头显回滚需求。

Phase 2 的回滚设计要求（登记备查，开工前须落实）：
- 回滚粒度是**按 panel 的集合**（`PLACEMENT_ADAPTER_PANELS`），不是单一全局 bool；
  类型 C（视频面板）适配失败不得连累类型 A。
- 几何适配（2.1–2.6）与可见性 owner 切换（2.7）**必须分开提交**，否则回归无法归因。

## 8. 产品决策

### 8.1 已确认（2026-10-07）

| # | 决策 | 选择 | 影响 |
| --- | --- | --- | --- |
| D1 | 场景 anchor 是否新增 | ✅ **A) 用 mse-agent 新增语义 anchor，面板迁到场景授权** | Phase 2（需单独批准执行） |
| D2 | 可见性来源是否收敛为单一 | ✅ **A) presentation/canvas 唯一来源，`WorkbenchReducer` 只管内容** | Phase 2.7（**行为变更**，需单独批准） |
| D4 | `DanmakuDepthLayer` 绑定单/多 surface | ✅ **A) 改为 `surfaceIds: Set<String>`** | **Phase 1** schema 字段（已并入 R7.5） |
| D5 | 是否允许运行时动态创建面板 | ✅ **A) 只允许「注册一次 + reshape」** | Phase 2 + §13 能力边界 |
| D6 | 范围 | ✅ **Phase 0 + Phase 1 已批准；Phase 2 方向确认、执行需单独批准** | 见 §3 |

### 8.2 未决策：D3 拆成三问（不阻塞 Phase 0/1）

原 D3（"`SearchWorkspaceRoute` 与 `WorkbenchContent` 是否合并"）把三个维度塞进了一个问题。
`WorkbenchContent` 当前无外部读取者是**事实**，但不能据此推出必须合并或删除。

| D3.x | 问题 | 建议归属 |
| --- | --- | --- |
| **D3.1** | 中心面板**内容路由**（搜索/推荐/列表/空态）归谁 | `SearchWorkspaceRoute`（维持现状） |
| **D3.2** | **布局/呈现模式**（WATCH/SHORTS/FOCUS）归谁 | layout/canvas 状态；不要再复制一份到内容路由 |
| **D3.3** | `WorkbenchContent` 是否还有**独立语义** | 没有 → 退役；有 → 重定义职责，不再重复命名 `BROWSE`/`VIDEO_CONTEXT` |

**阻塞范围**：只阻塞 Phase 2.7（可见性 owner 收敛）。不阻塞 Phase 0、Phase 1，也不阻塞 Phase 2.1–2.6。

### 8.3 Phase 1 实现后暴露的待决策项

由 Phase 1 的对抗性审查发现（含"把规则删掉看测试是否变红"的变异验证）。这三项是**设计文档自身的缺陷**或**超出 Phase 1 清单**的范围。

| # | 决策 | 性质 | 阻塞什么 |
| --- | --- | --- | --- |
| **D7** | 用户调整的持久化范围 | ✅ **已决定：per-theme 命名空间**（`theme.<id>.stageScale` 等）。属 Phase 2.6/2.7，须随 prefs 迁移一起做 | 仅 Phase 2.6/2.7。**不阻塞** Phase 1 已交付内容 |
| **D8** | 抓取组规则 5/6 | ✅ **已决定：可以，随 Phase 3 一起做**。规则 5 补成员级 `allowSharedFollow: Boolean = false`；规则 6 新增 issue code `GrabMemberWithoutHandle`。**不在 Phase 1 重开** | Phase 3（两者都是其前置合同） |
| **D9** | PRD R5 对 `UnknownOverlaySurface` 的第二子句与实际不符 | ✅ 已解决：由既有的 `InvalidLayer(reason="surface-does-not-support-danmaku")` 产出；**改 PRD 措辞，不改既有行为** | 已解决 |

### 8.4 Phase 1 交付状态（已实现并独立复核）

| 项 | 结果 |
| --- | --- |
| 测试 | `:spatial-workbench-core:cleanTest :spatial-workbench-core:test` → **17 套件 / 126 测试 / 0 失败 / 0 错误 / 0 跳过** |
| 既有测试回归 | 40 个既有测试全部仍通过 |
| A10 | `app/`、场景、Gradle 构建文件**零改动**（`git status` 过滤 `app/\|.gradle\|gradle/` 无命中） |
| 依赖 | 未新增任何第三方依赖；JSON reader 手写（`ThemeJson.kt`，仅 stdlib） |
| core 平台中立 | 无 `android.*` / `com.meta.spatial.*` import |
| 行尾 | 全部 LF |
| 交叉编译 | `:spatial-workbench-compose:compileDebugKotlin` 与 `:app:compileDebugKotlin` 均 BUILD SUCCESSFUL（仅编译，未改源码） |
| 审查中修掉的真实缺陷 | **3 个**：A9 静默通过、A8 同 panel 重复声明 slot 可伪造 `MIRRORED` 配对、3 处无判别力的空断言测试 |
| 已知残留 | 设计 §4.3 规则 3（属适配层，非 core）/ 规则 5（缺字段，D8）/ 规则 6（超清单，D8）**未强制**；`WorkbenchLayoutConfig` 未降级（Phase 2）；`ThemeComponentDescriptor` 等若干面向 Phase 2–5 的声明字段暂无读取者 |

## 9. 后续子任务（Phase 2 获批后创建）

```text
10-07-immersive-ui-theme-runtime-assessment   (本任务，Phase 0 + Phase 1)
├── phase2a-scene-anchors-and-single-panel    (2.1–2.3：场景 anchor + 单普通面板试点)
├── phase2b-scene-panel-and-media-surface     (2.4–2.5：类型 B / 类型 C 专用 adapter)
├── phase2c-migrate-remaining-panels          (2.6：其余面板迁入白名单)
├── phase2d-visibility-owner-switch           (2.7：可见性收敛，行为变更，最后做)
├── phase3-grab-group-registry                (抓取组；前置头显 gate)
├── phase4-danmaku-surface-groups              (弹幕组多 surface)
└── phase5-ui-component-composition           (UI 组件重组)
```

## 10. Phase 2 进入条件（全部满足才开工）

来自 review 的 6 条 gate，逐条对应本 PRD 的落点：

| # | 进入条件 | 落点 | 状态 |
| --- | --- | --- | --- |
| G1 | 修正"唯一运行时 consumer"的表述与 `PanelPresentationPolicy` 的状态标注 | 设计文档 §0.1、§2.5 行 18；PRD R1.2 | ✅ 已在文档中修正 |
| G2 | 解决 slot/panel/entity 身份模型，以及 layer→surface 的 schema 选择 | 设计文档 §4.5、§4.4；PRD R7.1/R7.2/R7.5 | ✅ 已定为 Phase 1 交付物 |
| G3 | 普通 UI panel 与特殊视频 compositor panel 分开设计 | 设计文档 §2.8、§5.6；PRD R7.4/R9.4 | ✅ 已定为 Phase 1 合同 + Phase 2 adapter 分流 |
| G4 | 父子抓取传播改为明确的头显验证 gate | 设计文档 §0.5、§4.3、§7 Phase 3.0；PRD R7.5(`UnverifiedGrabMechanism`) | ✅ 已设为 gate |
| G5 | 拆分 Phase 2 的 scene / 单面板试点 / 全量迁移 / 显隐 owner 切换 | 设计文档 §7 Phase 2（2.1–2.8）；PRD R9、§9 | ✅ 已拆分 |
| G6 | 说明头显不可用时 Phase 2 哪些只能标"已接通、未验收" | 设计文档 §9.3；PRD §5.2、§3.4 | ✅ 已写明 |

> G1–G6 的**文档侧**已完成（本轮修订）。**执行侧**仍待：`mse-agent` 场景读写能力实测（R9.1 前置），
> 以及 D3.1–D3.3 的答案（仅 2.7 需要）。
