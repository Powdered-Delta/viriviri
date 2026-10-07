# 沉浸式 UI 主题运行时：现状评估与架构设计（第一阶段）

## 文档定位

| 文档 | 内容 |
| --- | --- |
| [ui-structure-and-terminology.md](ui-structure-and-terminology.md) | 当前实现的命名与结构事实（以源码为准） |
| [immersive-ui-low-code-architecture.md](immersive-ui-low-code-architecture.md) | 目标架构的原始设想 |
| **本文** | **现状 vs 目标的核实结论 + 二开主题运行时的可执行设计** |
| [spatial-coordinates.md](spatial-coordinates.md) | 坐标系与正负方向 |
| [research/workbench-dismiss-hit-layer.md](research/workbench-dismiss-hit-layer.md) | 外部收起命中层的根因与不变量 |

本文的每一条结论都附源码位置或官方文档出处。**标记为「需头显验证」的结论不得当作实测结论使用。**

> **标识符状态约定**（重要）：
> - §2 的现状矩阵中，每个反引号标识符都是**已存在于源码树**的类型/字段/常量。
> - §4–§6 的领域模型与链路图中，标有 `Spec` 后缀或标注「新建」的标识符
>   （如 `GrabGroupSpec`、`GrabMemberSpec`、`ComponentGroupSpec`、`PanelPlacementResolver`、
>   `GrabGroupRegistry`、`DanmakuGroupAllocator`、`ThemeComponentRegistry`、`SceneAnchorCatalog`、
>   `EntityCatalog`、`ThemePanelLayout` 等）是**本文提出的设计目标，尚未存在于代码中**。
>   在实现落地前，不得在任何其他文档中把它们当作既有能力引用。
> - 与 [ui-structure-and-terminology.md](ui-structure-and-terminology.md) 的 `[实现]` / `[契约]` /
>   `[未接线]` 标注体系保持一致：本文新增的第三类为 **设计目标（proposed）**。

---

## 0. 结论摘要

1. **主题契约层（`spatial-workbench-core`）比预期完整得多，但几乎没有 runtime 消费者。**
   已经存在 `SlotPlacement`、`ComponentNode`/`ComponentGroup`、`WorkbenchCanvas`、`SpatialTheme`、`ThemeValidator`，
   甚至已经有 `MediaOverlayContracts.kt`（含 `DanmakuSurfaceGroup`、`DanmakuDepthLayer`、
   `DanmakuOcclusionDomain`）+ `MediaOverlayValidator.kt` + 225 行单测。

   `SpatialTheme` 的**唯一** runtime 消费者是 `ImmersivePlaybackCanvasHost`，它只读取
   `presentationPolicies`，从不读取 `placements`。**但它的输出并不直接决定最终可见实体集合。**
   准确的事实链是：

   ```text
   SpatialTheme.presentationPolicies
     └─ PlaybackCanvasReducer.visibleSlots()          # 只消费 PERSISTENT 一项策略
          └─ Set<PanelSlot>
               └─ ImmersiveWorkbenchHost.applyCanvasSlots()   # 有损：塌缩成单个 WorkbenchEvent
                    └─ ImmersiveWorkbenchReducer.modules()     # 第二套 reducer 重新决定面板
                         └─ applyWorkbenchModules() → SpatialPanelVisibilityController
   ```

   因此正确的说法是：**主题策略参与 slot 计算，但最终面板集合由第二套 reducer 重新决定** ——
   而不是"主题策略驱动可见性"。这也正是 §2.6 记录的"两套可见性来源"冲突（D2 待收敛）。

2. **`SlotPlacement` 的全部几何字段（anchor / offset / rotation / size / shape / parent / depthRelation）零消费。**
   这不是"部分接线"，是完全未接线。最强证据：对 `app/src/main` 全仓搜索
   `offsetMeters|placement(|SpatialAnchor|Meters3|ThemeLayout|sizeMeters` → **零匹配**。

3. **真实布局来自另一套模型：`WorkbenchLayoutConfig`（app 模块里的 Kotlin 常量 data class）。**
   主题契约与真实布局是**两套并行、互不相通**的模型。这是本次重构的核心问题。

4. **`ComponentGroup` 是 UI 组合组，不是抓取组，也不是空间布局组。**
   它的 `members: List<ComponentNode>` 全是 UI 组件节点，没有任何抓取/实体语义。
   不能因为类型同名就认为有抓取能力。

5. **`GrabPilotSystem` 是死代码。** 它已被设计成通用的"一个 handle → 多个 follower"驱动器，
   但 `onSceneReady()` 的 `registerSystem` 区块没有注册它（全仓仅 1 处匹配 = 它自己的声明）。
   当前抓取只有一组：`workbenchRootEntity` 带 `IsdkBoxCollider` + `IsdkGrabbable`，
   `grab_bar_panel` 与舞台根是它的 `TransformParent` 子级。

   **必须区分两类陈述**：
   - **代码结构事实**（源码可证）：父子关系被设置、抓取锚点状态被监视、Y 高度在抓取结束时被持久化、
     app 代码里**没有**任何镜像/跟随系统在移动子级。
   - **SDK 行为**（源码不可证，**需头显验证**）：子级的世界姿态确实由 Spatial SDK 的
     `TransformParent` 传播在抓取期间跟随。代码注释如此断言，但没有任何 app 侧代码能证明它，
     也没有真机验证记录。

   → 在**头显验证通过前，不得声称"默认抓取组行为完全不变"**（见 §7 Phase 3 gate）。

6. **弹幕只有 1 个 overlay 实体、1 个全局轨道状态。** `DanmakuSurfaceGroup` 的多surface/多layer/
   occlusion domain 只有合同与验证器，`app/` 下**零引用**。

7. **场景里没有语义 anchor。** `CinemaTheme` 声明了 9 个 anchor 名（`media_stage_anchor`、
   `transport_anchor`…），而 `app/scenes/Composition/Main.scene` 只有 3 个节点
   （`MediaRoom` / `WorkbenchCenterContent` / `WorkbenchOuterDismiss`）。**主题里的 anchor 名全部解析不到东西。**

8. **`immersive-ui-low-code-architecture.md:10` 说"当前项目没有可用的 `mse-agent`"是过期的。**
   `mse-agent.exe` 存在于 `D:\Program Files\Meta Spatial Editor\v16\Resources\mse-agent.exe`，
   且 `app/build.gradle.kts` 的 `spatial.scenes.cliPath` 已把导出 CLI 指向同一版本目录
   （`preBuild` 依赖 `export`）。**已核实**：在本机执行 `mse-agent.exe -h` 返回了完整子命令表
   （`ping` / `list-objects` / `set-position` / `add-primitive` / `add-panel` / `set-property` /
   `list-components` / `get-schema` / `save` / `screenshot` …）。
   **未核实**：这些命令对**本项目当前场景**的实际读写能力（需要 Meta Spatial Editor 正在运行并
   监听 IPC）。所以正确表述是"配置指向 MSE v16 且 CLI 可用；场景读写能力待 Phase 2 开工前实测"，
   而不是"场景授权路径已经打通"。

9. **对二开最关键的好消息：Meta 官方提供了"由实体上的自定义组件驱动面板尺寸/形状"的能力。**
   `settingsCreator = { ent: Entity -> … }` 可读取实体上的自定义组件来决定
   `QuadShapeOptions` / `CylinderShapeOptions`，并且项目已经有一套**运行时 reshape**
   （`reshapeStageOverlay` + `StageOverlayReshapeSystem`）可以改已创建面板的尺寸与曲率。
   主题驱动几何因此**有官方受控通路**，不需要发明机制。

10. **最有价值的既有资产不要重写：** `MediaOverlayContracts.kt` 的三类 group 分离
    （overlay surface / danmaku group / depth layer + occlusion domain）已经符合本次设计目标，
    且 `MediaStageTargetRegistry` 已经在**合同层强制**"overlay 不是视频 Surface、
    video output 不能带 overlay surface"。应扩展它，而不是新建一套。

11. **弹幕链路里有一批"看起来已实现"的死代码。** `DanmakuLaneScheduler`（`prepareDanmaku` /
    `scheduleDanmakuLanes`）**唯一的入口调用链是 `ViriViriAppState.loadDanmaku()`，而 `loadDanmaku()`
    自身没有任何生产调用者**（只有单测）。注意精确表述：源码里**存在** `loadDanmaku() → prepareDanmaku()`
    这级调用，死的是**入口不可达**，不是"完全没有源码调用"。
    `mergeDanmakuEvents` 同样不可达；`danmakuLaneAssignments` / `danmakuEvents` /
    `danmakuRenderMetrics` 只写不读。真实轨道占用在 `DanmakuCanvasRuntime` 内 **per-surface**。
    → 文档的 `[实现]` 标注需要修正，Phase 4 也**不能**把死 scheduler 当既有轨道实现去接线。

12. **已确证的弹幕几何缺口**：overlay 面板按**整块缩放后舞台尺寸** reshape，而视频面板按
    **宽高比适配后的内容矩形** reshape；宽高比 ≠ 16:10 时泳道会跨出视频可见区。另外渲染器
    从不读取 `DanmakuEmissionDirection`，逆向弹幕方向错误。

13. **有几条"看起来存在"的 UI 分支其实完全不可达。** `WorkbenchContent` 在自身文件与测试外
    **无任何读取者**；`WorkbenchEvent.OpenFocus` / `OpenShortsControls` / `OpenPlaybackConfig` /
    `ClosePlaybackConfig` **没有 app 层生产者** → FOCUS / SHORTS / PLAYBACK_CONFIG 这条线
    连同 `PanelSlot.FOCUS`、`CinemaTheme.focusLayout()` 全部是活声明、零 writer。
    另外**弹幕开关根本不存在**（`DanmakuMode` 无消费者，overlay 硬编码 `Visible(true)`）。

14. **持久化比 UI 看上去薄得多。** 全应用只有 **6 个** SharedPreferences key，其中 5 个是空间几何；
    用户真正能持久化的空间参数只有**舞台缩放**与**舞台高度**；曲率存在**三个恒同值的冗余 key**；
    而且**空间布局完全没有设置界面**（全局导航的设置入口是 `enabled = false`）。

---

## 1. 核实方法

- **源码为准**：逐文件读取 `spatial-workbench-core`、`spatial-workbench-compose`、`app` 的关键链路，
  并对每个标识符做全仓 grep（**排除 `temp/`**，那里是 vendored 第三方样例，不属于本项目）。
- **官方文档为准**：按项目规定加载 `hz-quest-verify-first` / `hz-spatial-sdk` 技能，
  并用 `npx -y metavr docs fetch/search` 核对 Spatial SDK 行为。本文所有 SDK API 结论均来自
  当前 Meta 官方文档页（见 §9 出处清单），**不是训练记忆**。
- **静态分析 ≠ 真机结论**：所有交互、舒适度、命中、遮挡结论一律标记「需头显验证」。

---

## 2. 现状 vs 目标能力矩阵

类别含义：
- **[实现]** 已实现且有 runtime 调用链
- **[契约]** core/schema 合同已定义但未接 runtime
- **[文档]** 仅文档目标或原型
- **[缺口]** 当前实现缺口
- **[决策]** 需要产品决策
- **[头显]** 需要头显验证

### 2.1 UI 组件重组

| # | 能力 | 现状 | 证据（type） |
| --- | --- | --- | --- |
| 1 | `PanelSlot` 语义槽位 | **[实现]**（部分成员未接线） | `ThemeContracts.kt:3-13`；`ui-structure-and-terminology.md:647-659` |
| 2 | 面板注册表（10 个面板） | **[实现]** | `SpatialVideoSampleActivity.kt:477-492` |
| 3 | `ComponentNode` / `ComponentKind` 组件树 | **[契约]** 零 runtime 消费 | `ThemeContracts.kt:113-139`；`ThemeValidator.kt:188-210` |
| 4 | `ComponentGroup` UI 组合组 | **[契约]** 零 runtime 消费 | `ThemeContracts.kt:93-96` |
| 5 | `WorkbenchCanvas` 主题画布配方 | **[契约]** 零 runtime 消费 | `ThemeContracts.kt:84-91` |
| 6 | `SystemToolbarModule` / `ToolbarModule` | **[契约]** 零 runtime 消费 | `ThemeContracts.kt:103-111` |
| 7 | 主题对组件的**重排 / 排序 / slot 改归属** | **[缺口]** 无 resolver、无 renderer 映射 | — |
| 8 | 受控 action 白名单（`ThemeAction`，6 项） | **[契约]** 无 dispatcher 接到 appState | `ThemeContracts.kt:160-172` |
| 9 | Compose 组件库（原子/外壳） | **[实现]** 但非主题驱动、无注册表 | `WorkbenchPanelAtoms.kt`、`SpatialPanelShell.kt`、`SearchComponents.kt` |
| 10 | 组件语义 id → Compose 实现映射表 | **[缺口]** | — |
| 10a | `WorkbenchContent` 内容路径枚举 | **[缺口] 死影子**：在 `ImmersiveWorkbenchState.kt` 与其测试外**无任何读取者** | 核实见 `research/audit-state-ownership-and-persistence.md`（表 b） |
| 10b | `WorkbenchEvent.OpenFocus` / `OpenShortsControls` / `OpenPlaybackConfig` / `ClosePlaybackConfig` | **[缺口] 无 app 层生产者**（仅测试）→ `WorkbenchContent.FOCUS`、`WorkbenchPresentation.SHORTS`、`isPlaybackConfigVisible`、`WorkbenchModule.SHORTS_ACTIONS`、`PanelSlot.FOCUS`、`CinemaTheme.focusLayout()` 全部**不可达** | 同上；与 `ui-structure-and-terminology.md:756-764` 一致 |

### 2.2 空间布局参数

| # | 能力 | 现状 | 证据 |
| --- | --- | --- | --- |
| 11 | `SlotPlacement`（anchor/offset/rotation/size/shape/parent/depth） | **[契约]** **全部几何字段零消费**；对 `app/src/main` 搜 `offsetMeters\|placement(\|SpatialAnchor\|Meters3\|ThemeLayout\|sizeMeters` → **零匹配** | `ThemeContracts.kt:64-73`；唯一消费者 `ImmersivePlaybackCanvasHost.kt:29` 只读 `presentationPolicies`；核实见 `research/audit-spatial-scene-and-grab.md` |
| 12 | 真实布局来源 | **[实现]** 但位于 app 的 Kotlin 常量 | `WorkbenchLayoutConfig.kt:21-111` |
| 13 | scene anchor → Spatial entity 解析 | **[缺口]** 场景无 anchor 节点 | `app/scenes/Composition/Main.scene:1-75`；`CinemaTheme.kt:121-192` 声明 9 个不存在的 anchor |
| 14 | `parentSlot` / `SpatialDepthRelation` | **[缺口]** 无 adapter | `ThemeContracts.kt:22-26,71-72` |
| 15 | 尺寸 / 形状（quad vs cylinder） | **[实现]** 机制已有，**但不由主题驱动** | `SpatialVideoSampleActivity.kt:959-968`（`CylinderShapeOptions`/`QuadShapeOptions`）；官方文档 `settingsCreator` 可读实体组件 |
| 16 | 运行时改尺寸/曲率 | **[实现]** 机制已有（含一帧延迟坑） | `SpatialVideoSampleActivity.kt:2236-2239,2548-2579` |
| 17 | 旋转 pitch/yaw/roll | **[实现]** 常量式（navPitch/transportPitch/keyboardPitch/railYaw），**不由主题驱动** | `WorkbenchLayoutConfig.kt:41-68`；`ui-structure-and-terminology.md:102-130` |
| 18 | `PanelPresentationPolicy` 呈现策略 | **[部分实现]** 唯一被读取的主题字段，但**只有 `PERSISTENT` 一项被消费**（`filterValues { it == PERSISTENT }`）；`AUTO_FADE` / `ON_DEMAND` / `TRANSIENT` / `THEME_CONTROLLED` 无任何 reader | `PlaybackCanvasContracts.kt:82-86` |
| 19 | `CanvasOverflowPolicy` / `preservesHitTesting` | **[契约] / [缺口]** 无 hit-test adapter | `ThemeContracts.kt:41-44,89-90` |
| 20 | 层级顺序 / `zOrder` | **[缺口]** 无字段，靠 local Z 隐式排序 | `ui-structure-and-terminology.md:104` |
| 21 | overflow 与 hit-test 行为（隐藏同时关命中） | **[实现]** 但非声明式 | `SpatialPanelVisibilityController.kt:20-55` |
| 22 | 用户可调几何的持久化（舞台高度、曲率、画布大小、显示比例） | **[实现]** | `VideoStageGrabPersistenceSystem.kt`；`PlaybackStageCurvature.kt:112-116`；`ui-structure-and-terminology.md:698-709` |
| 23 | 曲率三字段/三 key 冗余 | **[缺口]** 已确诊待合并 | `ui-structure-and-terminology.md:708`；kanban `k26` |
| 24 | 主题几何的预览 / 回滚 | **[缺口]** | — |
| 25 | 空间布局的设置界面 | **[缺口] 完全不存在**：全局导航的设置入口是 `enabled = false` 且标注"暂不可用" | `MRPanel.kt:76-78`；`strings.xml:10` |
| 26 | 持久化规模 | **[实现] 但极薄**：全应用仅 **6 个** SharedPreferences key（无 DataStore），其中 **5 个是空间几何** | `AppPreferences.kt:92-100` |
| 27 | 显示比例 / 舞台宽高比持久化 | **[缺口] 未持久化**：每次启动回到 `AUTO` | `ViriViriAppState.kt:825-829`（只写 `mutableState`） |
| 28 | 全部 z-offset 持久化（视频/压暗/弹幕/抓手条） | **[缺口] 未持久化**（弹幕 Z 只有进程内 debug 索引） | `SpatialVideoSampleActivity.kt:175,189,192,2938,2970` |
| 29 | 完整抓取静止位姿持久化 | **[部分] 只持久化 Y**；X/Z/yaw 每次启动复位为场景默认 | `VideoStageGrabPersistenceSystem.kt:12-15` |
| 30 | 舞台缩放的两份内存表示一致性 | **[缺口] 可漂移**：`selectPlaybackCanvasSize` 同时写枚举+浮点，`setPlaybackStageScale`（摇杆/调试滑杆）**只写浮点** → 右 rail 的"画布大小"标签会显示过期值 | `ViriViriAppState.kt:831-848`；标签渲染 `SpatialVideoSampleActivity.kt:2209-2211` |

### 2.3 Grab groups

| # | 能力 | 现状 | 证据 |
| --- | --- | --- | --- |
| 25 | 通用 handle → 多 follower 抓取驱动器 | **[实现类] 但未注册 = 死代码** | `GrabPilotSystem.kt:28-39`；`onSceneReady()` 注册区块 `SpatialVideoSampleActivity.kt:526-586` 无它 |
| 26 | 当前整组抓取 | **[实现]** 单一父子层级，SDK 原生移动 | `SpatialVideoSampleActivity.kt:572-586`；`ui-structure-and-terminology.md:83-95` |
| 27 | 抓取 handle 组件 | **[实现]** | `IsdkBoxCollider`+`IsdkGrabbable`：`SpatialVideoSampleActivity.kt:68-70` |
| 28 | 多 grab group 声明 | **[缺口]** | — |
| 29 | group 间父子 / 防重复移动 | **[缺口]** | — |
| 30 | 失去跟踪后的恢复策略 | **[缺口]** | — |
| 31 | "不可抓 / 只跟随" 声明 | **[缺口]** 现为隐式（`NoCollision`、`PanelInputOptions(0)`） | `SpatialVideoSampleActivity.kt:1540,1461` |
| 32 | 单手完成核心操作 | **[实现]**（ISDK 单设备抓取） | 官方：`Grabbable` 默认仅一个设备可操纵 |
| 33 | 新 group 的手感 / 冲突 / 双模式 | **[头显]** | kanban `k30` |

### 2.4 Danmaku surface groups

| # | 能力 | 现状 | 证据 |
| --- | --- | --- | --- |
| 34 | `DanmakuSurfaceGroup` / `DanmakuDepthLayer` / `DanmakuLaneSetSpec` | **[契约]** 有 validator + 单测，**app 零引用** | `MediaOverlayContracts.kt:98-126`；`MediaOverlayValidator.kt`；`MediaOverlayContractsTest.kt`(225 行) |
| 35 | `DanmakuOcclusionDomain` + 视点投影遮挡 | **[契约]** 仅 interface 扩展点 | `MediaOverlayContracts.kt:122-126,219-234` |
| 36 | `DanmakuAllocationRequest` / 负载分配 | **[契约]** | `MediaOverlayContracts.kt:236-243` |
| 37 | overlay 不是视频 Surface 的不变量 | **[实现]（合同层强制）** | `MediaStageContracts.kt:80-92`（video 不得带 overlay surface；overlay 必须有 surface id） |
| 38 | 当前弹幕渲染 | **[实现]** 单实体 + 单 Canvas + 单 runtime | `SpatialVideoSampleActivity.kt:1437-1445,1530-1544`；`DanmakuOverlay.kt:37-97` |
| 39 | 轨道状态 | **[实现] 但归属与文档不符**：真实轨道状态在 `DanmakuCanvasRuntime` 内 per-surface（`scrollingFreeAt(12)/topFreeAt(3)/bottomFreeAt(3)`），当前仅一个实例故等同全局 | `DanmakuRuntime.kt:154-169` |
| 39a | `DanmakuLaneScheduler` / `loadDanmaku()` / `mergeDanmakuEvents` | **[缺口] 死代码**：`loadDanmaku()` 零调用点；`danmakuLaneAssignments/danmakuEvents/danmakuRenderMetrics` 只写不读 | 核实见 `research/audit-danmaku-runtime.md`；与 `ui-structure-and-terminology.md:731` 的 **[实现]** 标注冲突，需修正 |
| 39b | 弹幕 canvas 尺寸与视频可见区一致 | **[缺口]** overlay 按**整块**缩放后舞台尺寸 reshape，视频面板按**宽高比适配后的内容矩形** reshape；宽高比 ≠ 16:10 时泳道跨出可见视频 | `SpatialVideoSampleActivity.kt:2538-2541` vs `:2510-2515`；kanban `k13`/`k15`/`k17` |
| 40 | 多 surface / 多 layer 弹幕 | **[缺口]** | kanban `k34`（多 canvas runtime 已就绪，`DanmakuPlaneSpec` 零实现） |
| 41 | 发射方向可配 | **[部分]** 方向被解析并携带，但渲染器恒画右→左／居中固定，从未读取方向 → 逆向弹幕渲染方向错误 | `MediaOverlayContracts.kt:19-24`；`DanmakuOverlay.kt:81-91`；核实见 `research/audit-danmaku-runtime.md` |
| 41a | `DanmakuRenderConfig.showArea` | **[缺口]** 无消费者 | `DanmakuRenderConfig.kt`；核实见 `research/audit-danmaku-runtime.md` |
| 41b | 弹幕是否由 compositor layer 合成在视频之上 | **[头显]** 仅有设备侧经验判断，未确证 | `SpatialVideoSampleActivity.kt:2943-2960` |
| 42 | capacity / allocationWeight 生效 | **[契约]** | `MediaOverlayContracts.kt:89,115` |
| 43 | 弹幕组与 UI 面板组、grab group 分开建模 | **[契约]** 已分离，但三者都无 runtime | 本文 §4 |
| 43a | 弹幕开关（on/off） | **[缺口] 完全不存在**：`DanmakuMode.{OFF,FLAT,SPATIAL}` 无消费者；overlay 面板硬编码 `Visible(true)`，且**不在任何可见性控制器映射表中 → 永远无法被隐藏**；全仓无 danmaku 开关标识符 | `SpatialVideoSampleActivity.kt:1537`；`ThemeContracts.kt:154-158`；核实见 `research/audit-state-ownership-and-persistence.md`、`research/audit-spatial-scene-and-grab.md` |
| 44 | 多平面空间布局与遮挡实测 | **[头显]** | kanban `k34`；本文 §8 |

### 2.5 主题运行时绑定与验证

| # | 能力 | 现状 | 证据 |
| --- | --- | --- | --- |
| 45 | 主题 fixture | **[契约]** 代码内 fixture（非文件） | `CinemaTheme.kt:3-249` |
| 46 | 主题 schema parser | **[缺口]** 无 JSON/文本解析 | — |
| 47 | schema 版本 + 迁移 | **[缺口]** `SpatialTheme` 无 version 字段 | `ThemeContracts.kt:174-194` |
| 48 | 纯 Kotlin validator + 单测 | **[实现]** | `ThemeValidator.kt:39-63`；`ThemeValidatorTest.kt`(124 行) |
| 49 | validator 覆盖几何合法范围 | **[部分]** 尺寸>0、cylinder 半径>0/arc 0.1–359.9、contrast≥4.5；**无 offset/rotation 范围** | `ThemeValidator.kt:212-220` |
| 50 | 场景 anchor catalog 校验 | **[缺口]** 无 anchor 存在性检查 | — |
| 51 | 主题 preview / rollback / 默认主题 fixture 文件 | **[缺口]** | — |
| 52 | 业务 action 唯一实现复用 | **[部分]** appState 是唯一实现，但主题无法调用 | `ViriViriAppState.kt` |

### 2.6 状态所有权

| # | 能力 | 现状 | 证据 |
| --- | --- | --- | --- |
| 53 | 可见性单一 writer | **[部分]** `SpatialPanelVisibilityController` 是面板可见性 writer，但 **`PanelLayerAlpha` 有 3 个独立 owner** | `SpatialPanelVisibilityController.kt:28-47`；`GrabBarHoverSystem.kt:103-122`；`WristAttachedSystem.kt:54`（后者用 `Visible`） |
| 54 | 可见性**来源**却有两套 | **[缺口]** `WorkbenchReducer.modules()` 与 `PlaybackCanvasReducer.visibleSlots()` 并存；且 `ImmersiveWorkbenchHost` 的 `when` 会**丢掉** `MEDIA_STAGE` 与 `SYSTEM_TOOLBAR`（有损映射） | `ui-structure-and-terminology.md:752-755`；`ImmersiveWorkbenchHost.kt:27-37` |
| 54a | `PanelSlot` 同时是同一管线的输入键与输出键 | **[缺口]** `visibleSlots` 产出 `Set<PanelSlot>` → 事件 → `WorkbenchModule` → `toPanelSlot()` 又变回 `Set<PanelSlot>`（7 模块塌缩到 4 槽位） | `SpatialVideoSampleActivity.kt:2142-2148` |
| 54b | `PanelSlot.MEDIA_STAGE` 的含义 | **[缺口] 一名两物**：真实媒体实体从不传给可见性控制器；该槽位实际只控制 `stageBackdropEntity` 压暗层 | `SpatialVideoSampleActivity.kt:1522,2132-2139` |
| 55 | `PlaybackCanvas` → `WorkbenchEvent` 桥 | **[实现] 但有损**：一个 `when` 把整组槽位塌缩成**单个**事件（`BROWSE`/`CONTEXT`/`TRANSPORT` 互斥优先），`MEDIA_STAGE` 与 `SYSTEM_TOOLBAR` 被丢弃 | `ImmersiveWorkbenchHost.kt:27-37` |
| 56 | 路由归属 `SearchWorkspaceRoute` vs `WorkbenchContent` | **[决策]** 语义重叠 | `ImmersiveWorkbenchState.kt:16-22`；`ViriViriAppState.kt:55` |
| 57 | 单一 ExoPlayer / 单一活动视频 Surface | **[实现]** 全仓仅 1 处 `ExoPlayer.Builder`、1 个 `PlayerSession`、1 个 `surface` 字段（所有路径经同一个 `attachSurface` 收口） | `ViriViriAppState.kt:177,178,218-223,233`；`ui-structure-and-terminology.md:36,64,602-603` |

### 2.7 持久化现状分类（全部 6 个 key）

唯一持久化机制是 `SharedPreferences`（prefs 文件名 `viriviri_app_preferences`），**无 DataStore**。

| key | 默认 | 设置界面 | 分类 |
| --- | --- | --- | --- |
| `search_history` | 空 | 面板内历史列表 | 用户设置（内容，非空间） |
| `playback_stage_scale` | `1.0`（钳制 0.70–2.00） | **有**：右 rail「画布大小」菜单 + 调试滑杆（**两个 writer 写同一 key**） | **用户设置**（调试入口共享同一 key） |
| `playback_video_curvature` | `Flat` | 无菜单；仅摇杆 + 调试重置按钮 | **调试可达、效果持久** |
| `playback_danmaku_curvature` | `Flat` | 无 | **冗余重复**（恒与 video key 同值） |
| `playback_backdrop_curvature` | `Flat` | 无 | **冗余重复**（恒与 video key 同值） |
| `workbench_stage_y` | 缺省 → `2.14 m`（钳制 0.6–3.0） | 无菜单；仅调试重置按钮 | **用户设置（抓取手势写入）；重置仅调试可达** |

**结论**：用户今天能持久化的空间参数只有**舞台缩放**与**舞台高度**两项。
显示比例、全部 z-offset、完整抓取位姿、面板布局**均不持久化**。
`WorkbenchLayoutConfig` 的注释自称"可换成一个设置界面载入的预设"，但该绑定**不存在**
（`val layout = WorkbenchLayoutConfig.DEFAULT`，从不重新赋值）。

### 2.8 面板类型学：三种面板不能统一处理

这是 Phase 2 的**关键约束**。项目里"面板"有三类，几何能力与受控路径完全不同：

| 类型 | 实例 | 构造方式 | 尺寸/形状路径 | 特殊约束 |
| --- | --- | --- | --- | --- |
| **A. 普通 UI panel** | `controls_id`、`video_selector_panel`、`mr_panel`、`mode_panel`、`input_method_panel`、`stage_backdrop_panel`、`danmaku_overlay_panel`、`grab_bar_panel`、`wrist_debug_panel` | `PanelRegistration`（ComposeView / LayoutXML / Activity / Intent） | `settingsCreator` 的 `QuadShapeOptions`/`CylinderShapeOptions`；创建后经 `PanelSceneObject` reshape | `NoCollision` / `PanelInputOptions(0)` 控制命中 |
| **B. 场景授权 panel** | `WorkbenchCenterContent`（→ `@id/center_content_panel`） | 场景里带 `Panel` + `PanelDimensions` 组件；注册表里也有同名 registration | 运行时被 `bindCenterContentPanel()` **重新挂到 MediaStage** 并改写 `PanelDimensions` | scene 授权的尺寸**不是最终尺寸**；同时有场景 `Visibility` 与运行时 `setVisible` 两个 writer |
| **C. 手工 compositor 媒体面板** | `R.id.spatialized_video_panel`（视频） | **不在 `registerPanels()`**；手工构建 `PanelSceneObject`（`MediaPanelSettings` + `PixelDisplayOptions` + `MediaPanelRenderOptions` + `stereoMode` + `zIndex`） | `videoShapeOptions()` → `QuadShapeOptions` / `CylinderShapeOptions`；曲率由**自定义 `TriangleMesh`** 生成 | 唯一视频输出 Surface；compositor layer 顺序由 SDK 掌握；命中几何特殊（`videoSurface` 显式 `NoCollision`，点击走几何判定）；不能当普通 panel reshape |

**Phase 2 的硬要求**：`PanelLayoutAdapter` 必须**按类型分流**，不能假设一个通用 adapter
能覆盖三类面板的全部几何能力。特别是：
- 类型 A 可以走「注册一次 + runtime reshape」统一路径。
- 类型 B 的尺寸/姿态在运行时被 reparent 改写，adapter 必须**接手**这两处改写而不是叠加。
- 类型 C **不套用**普通 panel contract；它的几何由专用 adapter 负责，且必须继续满足
  单一视频 Surface 与既有曲率/命中不变量。

**因此"主题可改全部面板尺寸"这个说法必须限定范围**：schema 可以统一描述目标尺寸，
但实际生效与否取决于面板类型；对类型 C 而言，可主题化的几何能力是**受限子集**。

---

## 3. 核心问题诊断

### 3.1 两套并行布局模型（最严重）

```text
                    ┌─────────────────────────────────────────┐
   主题契约层        │ SpatialTheme                            │
   (core, 未接线)    │  layouts: Map<LayoutMode, ThemeLayout>  │
                    │    └── placements: Map<PanelSlot,       │
                    │          SlotPlacement(anchor, offset,  │
                    │          rotation, size, shape, parent, │
                    │          depthRelation)>                │
                    └──────────────┬──────────────────────────┘
                                   │ 只有 presentationPolicies 被读
                                   ▼
   ┌───────────────────────────────────────────────────────────────┐
   │ ImmersivePlaybackCanvasHost  →  PlaybackCanvasReducer          │
   │   visibleSlots(state, theme) = 画布配方 ∪ PERSISTENT 槽位      │
   └───────────────────────────┬───────────────────────────────────┘
                               ▼
   ┌───────────────────────────────────────────────────────────────┐
   │ ImmersiveWorkbenchHost.applyCanvasSlots()                     │
   │   有损映射：BROWSE→OpenBrowse / CONTEXT→OpenVideoContext       │
   │             TRANSPORT→RevealTransport / else→Dismiss          │
   └───────────────────────────┬───────────────────────────────────┘
                               ▼
   ┌───────────────────────────────────────────────────────────────┐
   │ ImmersiveWorkbenchReducer.modules()  ← 第二套可见性来源        │
   │   NORMAL 恒加 DETAIL_RAIL + VIDEO_CONTEXT（与画布配方冲突）     │
   └───────────────────────────┬───────────────────────────────────┘
                               ▼
   ┌───────────────────────────────────────────────────────────────┐
   │ applyWorkbenchModules() → 硬编码 module→Entity 表              │
   │ SpatialPanelVisibilityController.setVisible(slot, entity, vis) │
   └───────────────────────────┬───────────────────────────────────┘
                               ▼
   ┌───────────────────────────────────────────────────────────────┐
   真实几何 ← WorkbenchLayoutConfig（app Kotlin 常量，第二套模型）
              navLocalY / transportLocalZ / railYawDegrees / …
```

**结论**：主题能表达的几何（`SlotPlacement`）没有任何代码读；代码实际用的几何（`WorkbenchLayoutConfig`）
没有任何主题能改。二开主题作者今天只能改"哪些 slot 可见"，改不了"面板放哪里、多大、什么形状"。

### 3.2 场景 anchor 名全部悬空

`CinemaTheme` 引用的 anchor：`media_stage_anchor`、`transport_anchor`、`system_toolbar_anchor`、
`browse_anchor`、`context_anchor`、`focus_anchor`、`action_sheet_anchor`、`pip_dock_anchor`、
`shorts_media_stage_anchor`、`shorts_transport_anchor`、`shorts_details_anchor`、`shorts_comments_anchor`、
`focus_content_top_right`。

`app/scenes/Composition/Main.scene` 实际节点：`MediaRoom`、`WorkbenchCenterContent`、`WorkbenchOuterDismiss`。
**交集为空。** 且 9 个面板里只有 `center_content_panel` 是场景授权的，其余 8 个都是 Kotlin
`Entity.createPanelEntity` 建的 —— 与 `AGENTS.md`「静态空间布置归场景授权」存在既存偏差。

**偏差规模已量化**：该 Activity 中有 **14 处**运行时 `Entity.create*` 用于静态布局：
抓取锚点、舞台根、左右 rail、transport、nav、视频表面、抓手条、压暗层、弹幕层、输入台、
手腕面板、skydome、GLXF 根。此外媒体/视频面板 `R.id.spatialized_video_panel`
**根本不在 `registerPanels()` 里** —— 它是手工构建的 `PanelSceneObject`（用 `MediaPanelSettings` +
`PixelDisplayOptions(1920×1080)`），因为"没有注册的 `Panel` 组件会崩溃"。
因此本次重构的实际起点是：**1 个场景授权面板 + 1 个手工面板 + 9 个注册面板 + 14 处运行时实体创建。**

### 3.3 三类 group 在两处被混为一谈的风险

- `ComponentGroup`（core:93）是 **UI 组合组**。
- `WorkbenchModule`（app:24-32）是 **可见性模块**，被 `toPanelSlot()` 再映射成 `PanelSlot`。
- `ImmersiveWorkbenchHost` / `SpatialPanelVisibilityController` 用 `PanelSlot` 同时承载
  "可见性分组" 与 "面板身份"（`CONTEXT` 同时被左右两条 rail 复用，`ACTION_SHEET` 实际是输入台）。

三个概念共用一个枚举，是后续加 grab group / danmaku group 时最容易产生状态冲突的地方。

---

## 4. 目标架构：三类 group 严格分离

### 4.1 领域模型分离

```text
                    ┌──────────────────────────────────────────┐
                    │ ThemeRuntime (app 装配根)                │
                    │  持有：SpatialTheme + 三个 Registry      │
                    └───┬──────────────┬───────────────┬───────┘
                        │              │               │
        ┌───────────────▼──┐  ┌────────▼────────┐  ┌───▼──────────────────┐
        │ ComponentGroup   │  │ GrabGroup       │  │ DanmakuSurfaceGroup  │
        │ (UI 组合)        │  │ (空间操控)      │  │ (弹幕渲染目标)       │
        ├──────────────────┤  ├─────────────────┤  ├──────────────────────┤
        │ 语义：组件组合、 │  │ 语义：实体成员、│  │ 语义：渲染目标、     │
        │ 重排、slot 归属  │  │ handle、跟随策略│  │ 轨道/层/容量/遮挡    │
        │ 成员：Component  │  │ 成员：EntityRef │  │ 成员：SurfaceId +    │
        │ Node (纯 Kotlin) │  │ + 跟随/锁定标志 │  │ DepthLayer           │
        │ 不含实体         │  │ 不含 Compose    │  │ 不是视频 Surface     │
        ├──────────────────┤  ├─────────────────┤  ├──────────────────────┤
        │ Owner            │  │ Owner           │  │ Owner                │
        │ ComponentComp-   │  │ GrabGroup       │  │ DanmakuGroup         │
        │ ositionResolver  │  │ Registry        │  │ Allocator            │
        │ (core, 纯函数)   │  │ (app adapter)   │  │ (core, 纯函数)       │
        ├──────────────────┤  ├─────────────────┤  ├──────────────────────┤
        │ 现有资产         │  │ 现有资产        │  │ 现有资产             │
        │ ComponentGroup   │  │ GrabPilotSystem │  │ MediaOverlay         │
        │ ComponentNode    │  │ (已通用，未注册)│  │ Contracts 全套 +     │
        │ ThemeValidator   │  │                 │  │ MediaOverlayValidator│
        └──────────────────┘  └─────────────────┘  └──────────────────────┘
```

**硬性规则**：一个实体可以同时是"某 UI 组的成员"和"某 grab 组的 follower"，但
**三种 group 的成员表不得合并为一个通用列表**。各自有独立的 id 空间、独立校验、独立 owner。

### 4.2 `ComponentGroup`（UI 组合）领域模型

保持现有 `ComponentGroup` 语义，补齐"可重组"所需的字段：

```kotlin
/** UI 组合组：主题用来重排/重组界面元素。不持有实体、不持有抓取、不持有渲染目标。 */
data class ComponentGroupSpec(
    val id: String,
    val label: String = id,
    /** 组内顺序即渲染顺序（同 slot 内后写覆盖先写）。 */
    val members: List<ComponentNode>,
    /** 该组可被引用的 slot 白名单；空 = 不限。 */
    val allowedSlots: Set<PanelSlot> = emptySet(),
    /** 可选：整组作为一个可复用配方被其他主题引用。 */
    val reusable: Boolean = true,
)
```

- **允许**：增删成员、改变成员顺序、改变成员归属的 slot、改变 `namedSlots` 嵌套、改属性/数据绑定/受控 action。
- **不允许**：在组里放非注册组件；引用未在 `ThemeComponentRegistry` 注册的实现。
- `ThemeComponentRegistry` 是新的**受控组件目录**：`componentId → (kind, 允许的 slot, 允许的 action, Compose 实现 or 语义模块 id)`。
  主题只能引用目录里的 id。

### 4.3 `GrabGroup`（空间操控）领域模型

```kotlin
enum class GrabFollowMode { TRANSLATION_ONLY, TRANSLATION_AND_ROTATION, FOLLOW_NONE }

/** 抓取组如何把 handle 的运动传给成员。 */
enum class GrabMechanism {
    /**
     * 成员以 TransformParent 挂在 handle 下，**预期**由 SDK 传播整体移动。
     *
     * 状态：**代码结构事实 + 待验证的 SDK 行为**。
     * 源码可证：父子关系被设置，且 app 侧没有任何镜像系统在移动子级。
     * 源码不可证：SDK 的 TransformParent 传播在本版本 / 全部成员类型下的实际移动结果。
     * → 启用本机制前必须有头显验证记录（见 §7 Phase 3 gate）。在验证通过前，
     *   不得把它当作"已验证的零成本默认路径"。
     */
    NATIVE_PARENT,
    /** 由 GrabPilotSystem 做增量镜像；成员可能分属不同父级，无法共享 TransformParent。 */
    DELTA_MIRROR,
}

enum class GrabLostTrackingPolicy {
    FREEZE_AT_LAST_POSE,
    RETURN_TO_REST_POSE,
    DISBAND_GROUP,
}

data class GrabMemberSpec(
    /** 语义实体引用，由 adapter 解析成 Entity；主题不得直接持有 Entity。 */
    val entityRef: EntityRef,
    val follow: GrabFollowMode = GrabFollowMode.TRANSLATION_AND_ROTATION,
    /** true = 该成员自己也可被单独抓取（不再只跟随）。 */
    val independentlyGrabbable: Boolean = false,
    val lockRotation: Boolean = false,
    val lockScale: Boolean = true,
)

data class GrabGroupSpec(
    val id: String,
    /** 抓取把手实体（scene 授权或运行时动态，但必须已注册到 EntityCatalog）。 */
    val handleRef: EntityRef,
    /** 组层次：子组不再重复应用父组 delta。 */
    val parentGroupId: String? = null,
    val mechanism: GrabMechanism = GrabMechanism.NATIVE_PARENT,
    val members: List<GrabMemberSpec>,
    val restPose: PoseSpec? = null,
    val lifecycle: GrabGroupLifecycle = GrabGroupLifecycle.ALWAYS,
    val lostTrackingPolicy: GrabLostTrackingPolicy = GrabLostTrackingPolicy.FREEZE_AT_LAST_POSE,
    /** 互斥组：同一时刻只能有一个被抓住。 */
    val exclusiveWith: Set<String> = emptySet(),
    /** 该组被抓住时，是否抑制其它组的抓取输入。 */
    val suppressOthersWhileGrabbed: Boolean = true,
)
```

**正确性规则 —— 按"由哪一层强制"分类**（原先笼统写成"validator 强制"是不准确的；
Phase 1 实现后已逐条核实实际落点）：

| # | 规则 | 强制层 | 状态 |
| --- | --- | --- | --- |
| 1 | **防重复移动**：若 `A.parentGroupId == B`，则 A 的成员不得同时出现在 B 的 `members`（否则 delta 应用两次） | core validator（`GrabMemberOverlap`） | ✅ Phase 1 已实现 + 有测试 |
| 2 | **handle 不得是自己的 member**，否则自反馈 | core validator（`GrabHandleSelfReference`） | ✅ Phase 1 已实现 + 有测试 |
| 3 | `mechanism == NATIVE_PARENT` 时，所有成员当前父级必须是 handle（或其后代） | **Meta adapter（Phase 2/3）**，**不是** core validator | ⛔ 待适配层实现。core 拿不到场景层级，无法在纯 Kotlin 层判定；原设计把它写成 validator 规则是错的 |
| 4 | `exclusiveWith` 必须对称 | core validator（`AsymmetricExclusion`） | ✅ Phase 1 已实现 + 有测试 |
| 5 | 同一 `EntityRef` 不得同时作为**两个非父子关系组**的 follower，除非该成员显式声明允许共享跟随 | core validator（`GrabMemberOverlap` 的兄弟规则） | ⚠️ **原设计引用了一个不存在的字段**。原文写"除非显式声明 `sharedFollow = true`"，但 `GrabMemberSpec` 里**没有** `sharedFollow` 字段（只有 `independentlyGrabbable`）。当前 core 因此只覆盖了规则 1。**待决策 D8**（见 §10.3） |
| 6 | `independentlyGrabbable = true` 的成员必须自己也有 handle 规格 | 未实现 | ⛔ 需要一个**不在 PRD R5 封闭清单内**的新 issue code。登记为 Phase 1 残留（见 §10.3 D8），不在 Phase 1 重开 |

> **诚实说明**：规则 3/5/6 在 Phase 1 **没有被强制**。其中规则 3 的归属原本就写错了（它属于适配层），
> 规则 5 引用了不存在的字段（设计文档自身的缺陷，已在此更正），规则 6 超出 PRD 的 issue 清单。
> 这三条不得被当作"已由 validator 保证"。

**默认配置的意图是复刻现状**（用户要求"保留现有整组抓取行为作为默认配置"）：

```kotlin
val DEFAULT_WORKBENCH_GRAB_GROUP = GrabGroupSpec(
    id = "workbench",
    handleRef = EntityRef.Of("workbenchRoot"),
    mechanism = GrabMechanism.NATIVE_PARENT,
    members = listOf(
        // 现状：stage + grab_bar 是 workbenchRoot 的 TransformParent 子级
        GrabMemberSpec(EntityRef.Of("grabBar"), GrabFollowMode.TRANSLATION_AND_ROTATION),
        GrabMemberSpec(EntityRef.Of("mediaStage"), GrabFollowMode.TRANSLATION_AND_ROTATION),
        // mediaStage 的后代（rails/transport/nav/center/backdrop/danmaku/ime）随其移动，不单列
    ),
    lifecycle = GrabGroupLifecycle.ALWAYS,
)
```

> ⚠️ **这个默认组不是"已证明行为不变"，而是"结构上等价 + 待验证"。**
> 现状的移动是 SDK 父子传播，而该行为**没有头显验证记录**（见 §0.5）。
> 因此 Phase 3 的**第一个动作是头显验证**：抓取时子级是否真的跟随、跟随哪些实体、
> 哪些实体在组外不动。验证通过后，才能说默认组"等价复刻现状"；
> 验证不通过则需改用 `DELTA_MIRROR`，届时"行为不变"的承诺不成立。
> 这是把"隐式靠一棵树"变成"显式声明一组"的**前提**，不是它的结论。

### 4.4 `DanmakuSurfaceGroup`（弹幕渲染目标）领域模型

**复用并扩展现有合同**，不新建：

```kotlin
/** 现有字段保留；新增"轨道共享/隔离"、"组间负载上限"与 layer→surface 的多绑定。 */
data class DanmakuSurfaceGroup(
    val id: String,
    val allocationWeight: Float = 1f,          // 已存在：组间宏观负载权重
    val sharedCoordinateSpace: DanmakuCoordinateSpace = DanmakuCoordinateSpace(), // 已存在
    val laneSets: List<DanmakuLaneSetSpec>,    // 已存在
    val layers: List<DanmakuDepthLayer>,       // 已存在
    val occlusionPolicy: DanmakuOcclusionPolicy = DanmakuOcclusionPolicy.AVOID_PROJECTED_OVERLAP, // 已存在
    // ---- 新增 ----
    /** 共享一份轨道占用状态，还是每个 surface 独立轨道。 */
    val laneStateSharing: LaneStateSharing = LaneStateSharing.SHARED,
    /** 该组总容量上限（跨全部 layer/surface），null = 仅受各 surface capacity 约束。 */
    val maxActiveItems: Int? = null,
    /** 视点遮挡是否需要跨组检查（默认交给 DanmakuOcclusionDomain）。 */
    val occlusionDomainId: String? = null,
)

/** 现有字段 `surfaceId: String` 已确认改为多值（决策 D4=A）。 */
data class DanmakuDepthLayer(
    val id: String,
    val surfaceIds: Set<String>,      // 原：val surfaceId: String
    val depthOffsetMeters: Float,
    val maxActiveItems: Int,
    val basicStyle: OverlayStyleOverride = OverlayStyleOverride(),
)

enum class LaneStateSharing { SHARED, ISOLATED }
```

**D4=A 的连锁后果（必须一起定义，不能只改字段名）**：

| 后果 | 需要的规则 |
| --- | --- |
| allocator 输出结构 | `ScheduledDanmakuAssignment.surfaceId` 由"layer 直接给出"变为"从 `surfaceIds` 中经**唯一的 surface 选择步**选出"；选择必须确定性（按 `allocationWeight` + 当前活跃数 + 稳定 event hash），否则同一事件会落到不同 surface |
| 重复事件 | 一个 layer 绑多 surface **不等于**同一事件在多 surface 各画一份。规则：一次事件 → **恰好一个** surface（fan-out 是分配域，不是复制域）。若产品需要复制语义，必须用独立的 `duplicateTo: Set<String>` 显式声明，不能由 `surfaceIds` 隐式表达 |
| per-surface 轨道状态 | `laneStateSharing = SHARED` 时，轨道占用是 **group 级**单一状态（与绑了几个 surface 无关）；`ISOLATED` 时每个 surface 各持一份，此时"一个 layer 多 surface"意味着同一 layer 需要在每个 surface 上独立占轨 → 容量按 surface 数放大，validator 必须拒绝 `ISOLATED` + 多 surface + `maxActiveItems` 组合，除非显式给出每-surface 配额 |
| 遮挡判定 | 遮挡必须按**最终选中的 surface** 的世界投影计算，不能用 layer 级聚合投影 |

**surface 是独立渲染目标，不是视频 Surface**——该不变量已在 `MediaStageTargetRegistry.validate`
（`MediaStageContracts.kt:80-92`）强制，本设计沿用：

```text
OverlaySurfaceSpec(surfaceId) ──┐
                                ├──> DanmakuDepthLayer(surfaceIds, depthOffsetMeters, …)
DanmakuSurfaceGroup ────────────┘         │
        │                                 ▼
        │                    DanmakuGroupAllocator
        │      event → group → laneSet → layer → [唯一 surface 选择步] → surface
        ▼
   SurfaceBinding(surfaceId → 面板实体 + 独立 Compose Canvas runtime)
```

**"能分配" ≠ "遮挡已实现"**：合同层已有 `DanmakuAllocationRequest` / `DanmakuAllocationResult` 与
`ViewerProjectionResolver` / `CrossLayerOcclusionResolver` 两个 interface，但前者无 runtime 引用、
后者**无任何实现**。所以验收必须分开：
- 「能按 group/lane/layer 分配到多个 surface」→ 纯 Kotlin 单测可证。
- 「基于用户视点的跨 layer/group 遮挡正确」→ **头显才能证**，静态 JVM 测试不可替代。

- **不要为死代码建模**：`DanmakuLaneScheduler`（`prepareDanmaku` / `scheduleDanmakuLanes`）当前
  **入口不可达**（唯一入口 `ViriViriAppState.loadDanmaku()` 无生产调用者）；`mergeDanmakuEvents`
  同样死代码。真实的轨道占用在 `DanmakuCanvasRuntime` 内 per-surface。因此 `DanmakuGroupAllocator`
  的新建应**把 `DanmakuCanvasRuntime` 的轨道状态提升为 group 级**，而不是去接线一个已死的 scheduler。
- `media-overlay` 合同目前被 app 消费的只有 4 个类型（`DanmakuEvent` / `DanmakuLaneFamily` /
  `DanmakuEmissionDirection` / `OverlayStyleOverride`）；其余（含 `DanmakuSurfaceGroup`、
  `DanmakuOcclusionDomain`、`DanmakuAllocator`、`DanmakuOcclusionPolicy`）**零 runtime 读取**。
- 已有 `CrossLayerOcclusionResolver` / `ViewerProjectionResolver` 两个 interface 作为扩展点（均无实现）。
- **顺带修正**：`ui-structure-and-terminology.md:731` 把 `DanmakuLaneScheduler` 标为 **[实现]**，
  与源码不符，应改标为 **[未接线]**（精确理由：**入口不可达**，而非"无源码调用"）。

### 4.5 身份层次：五个 id 空间，不得互相代用

当前代码把"槽位""面板""组件""实体"混在少数几个枚举里，直接照搬到 schema 会导致
**无法表达既有布局**。典型反例（源码事实）：`PanelSlot.CONTEXT` 同时被左 Detail rail
与右 Context rail **两个实体**复用；`PanelSlot.MEDIA_STAGE` 在运行时的实际调用只控制
`stageBackdropEntity` 压暗层，而不是视频实体本体。

因此 schema 必须先固定五个独立 id 空间：

| id | 含义 | 是否允许 1:N | 解析者 |
| --- | --- | --- | --- |
| `PanelId` | **单一空间 panel 实例**（一个实体 / 一个注册实例） | 否（1 panel = 1 实例） | `PanelLayoutAdapter` |
| `SlotId` | **语义能力 / 内容位置**（"播放控制应该在哪"） | **是** —— 一个 slot 可映射多个 panel | `PanelPlacementResolver` |
| `ComponentId` | panel **内部**的 UI 组件（来自受控组件目录） | 是（可多实例） | `ComponentCompositionResolver` |
| `EntityRef` | 由 adapter 解析成 Spatial `Entity` 的语义引用 | 是 | `EntityCatalog` |
| `OverlaySurfaceId` | 弹幕 / 字幕的**渲染目标** | 是 | `SurfaceBinding` |

**关键修正**：`SlotId` 之间不是一对一，所以校验规则**不能**是
"同一 slot 不能被多个 panel 占用"（现有产品就违反它）。正确的规则是：

```text
规则 SlotBindingPolicy：
  同一 layout 内，一个 SlotId 可以被 N 个 PanelId 绑定，
  但必须显式声明共享策略，否则报 DuplicateSlotOwner：

    slot: "CONTEXT"
    bindings:
      - panel: "leftDetailRail"
        sharePolicy: MIRRORED        # 与同 slot 的其它 panel 语义同步（左右对称 rail）
      - panel: "rightContextRail"
        sharePolicy: MIRRORED
```

合法 `sharePolicy`：`EXCLUSIVE`（默认，仅允许单 panel，违反即报错）/
`MIRRORED`（多 panel 共享同一语义状态，需成对声明）/ `INDEPENDENT`（多 panel 各自独立状态）。
没有该声明时，`DuplicateSlotOwner` 仍然报错 —— 把"当前产品的一个特例"变成**显式声明的能力**，
而不是删掉校验规则。

**另一个必须固定的层次问题**：`OverlaySurfaceId` **不是** UI slot。弹幕 surface 不参与
`SlotId` 的可见性配方，也不受 panel presentation policy 管辖；它的生命周期由
`MediaStageReducer` 的 overlay target 管理（见 §6.2）。

---

## 5. 配置 schema 草案

### 5.1 文件与版本策略

```text
themes/
  cinema/
    theme.json          # 声明式主题（本文 schema）
    scene/Main.metaspatial  # 场景授权（anchor 节点）
  cockpit/
    theme.json
    scene/Cockpit.metaspatial
```

**版本策略**：
- `schemaVersion: Int`（必需，从 1 开始）。
- 运行时维护 `SUPPORTED_SCHEMA_VERSIONS`；`schemaVersion` 未知（未来版本）→ 拒绝加载并给出
  `UnsupportedSchemaVersion`，**不静默降级**。
- `theme.minRuntimeVersion` 可选；低于当前 runtime → 拒绝。
- 迁移链 `ThemeMigration v1→v2→…`，每步纯函数 `(JsonObject) -> JsonObject`；迁移在 validator **之前**。
- 主题来源**只允许 APK 内置资源**（`assets/themes/…`）。**不下载可执行主题**（对齐
  `immersive-ui-low-code-architecture.md:1068`）。

### 5.2 schema 草案（JSON）

```jsonc
{
  "schemaVersion": 1,
  "id": "cinema",
  "displayName": "Cinema",
  "scene": {
    "reference": "Composition",
    // 该主题要求场景必须提供的 anchor 节点；缺失即校验失败。
    // 这些节点由 Meta Spatial Editor 授权，是「静态空间布置」的唯一来源。
    "requiredAnchors": [
      "media_stage_anchor", "transport_anchor", "system_toolbar_anchor",
      "browse_anchor", "context_anchor", "focus_anchor", "action_sheet_anchor"
    ]
  },

  // ---------- 空间面板实例（PanelId → 几何） ----------
  // panelClass 决定几何受控路径（见 §2.8）：
  //   UI_PANEL      = 类型 A，普通注册面板，可 register-once + reshape
  //   SCENE_PANEL   = 类型 B，场景授权面板，运行时 reparent / 改 PanelDimensions
  //   MEDIA_SURFACE = 类型 C，手工 compositor 媒体面板，专用 adapter，不套通用 contract
  "panels": {
    "mediaStage": {
      "panelClass": "MEDIA_SURFACE",
      "component": "mediaStage",              // ThemeComponentRegistry 白名单
      "anchor": { "sceneNode": "media_stage_anchor" },
      "parent": null,                          // null = 由 anchor 定位；否则 { "panel": "<PanelId>" }
      "offsetMeters":   { "x": 0.0, "y": 0.0, "z": 0.0 },   // 相对 anchor 的局部偏移（§5.5 定义语义）
      "rotationDegrees":{ "pitch": 0.0, "yaw": 0.0, "roll": 0.0 },
      "sizeMeters":     { "width": 1.60, "height": 0.90 },
      "shape": { "kind": "quad" },             // 或 { "kind":"cylinder","radiusMeters":4.0,"arcDegrees":60 }
      "depthRelation": "ROOT",                 // ROOT | FRONT_OF_PARENT | BEHIND_PARENT
      "zOrder": 0,
      "overflow": "CLIP",                      // CLIP | VISIBLE
      "hitTest": "CUSTOM_RECT",                // NONE | PANEL | CUSTOM_RECT（媒体面板命中走几何判定）
      // 类型 C 的可主题化几何是受限子集：sizeMeters 与 shape 生效，
      // 但 compositor layer 顺序、立体模式、视频 Surface 归属不可主题化。
      "themeableGeometry": ["sizeMeters", "shape"],
      // 用户运行时是否可调（与主题静态值分开，见 §5.5）
      "userAdjustable": ["stageScale", "stageHeight"]
    },
    "leftDetailRail": {
      "panelClass": "UI_PANEL",
      "component": "detailRail",
      "anchor": { "sceneNode": "browse_anchor" },
      "parent": null,
      "offsetMeters": { "x": 0.0, "y": 0.0, "z": 0.0 },
      "rotationDegrees": { "pitch": 0.0, "yaw": -45.0, "roll": 0.0 },
      "sizeMeters": { "width": 0.70, "height": 0.90 },
      "shape": { "kind": "quad" },
      "depthRelation": "ROOT",
      "zOrder": 20,
      "overflow": "VISIBLE",
      "hitTest": "PANEL"
    },
    "rightContextRail": {
      "panelClass": "UI_PANEL",
      "component": "contextRail",
      "anchor": { "sceneNode": "context_anchor" },
      "parent": null,
      "offsetMeters": { "x": 0.0, "y": 0.0, "z": 0.0 },
      "rotationDegrees": { "pitch": 0.0, "yaw": 45.0, "roll": 0.0 },
      "sizeMeters": { "width": 0.70, "height": 0.90 },
      "shape": { "kind": "quad" },
      "depthRelation": "ROOT",
      "zOrder": 20,
      "overflow": "VISIBLE",
      "hitTest": "PANEL"
    },
    "transport": {
      "panelClass": "UI_PANEL",
      "component": "transport",
      "anchor": null,
      "parent": { "panel": "mediaStage", "attach": "bottomCenter" },
      "offsetMeters": { "x": 0.0, "y": -0.02, "z": -0.14 },
      "rotationDegrees": { "pitch": 20.0, "yaw": 0.0, "roll": 0.0 },
      "sizeMeters": { "width": 1.32, "height": 0.38 },
      "shape": { "kind": "quad" },
      "depthRelation": "FRONT_OF_PARENT",
      "zOrder": 10,
      "overflow": "VISIBLE",
      "hitTest": "PANEL"
    },
    "centerContent": {
      "panelClass": "SCENE_PANEL",
      "component": "centerContent",
      "anchor": null,
      "parent": { "panel": "mediaStage", "attach": "center" },
      "offsetMeters": { "x": 0.0, "y": 0.0, "z": -0.52 },
      "rotationDegrees": { "pitch": 0.0, "yaw": 0.0, "roll": 0.0 },
      "sizeMeters": { "width": 1.50, "height": 1.20 },
      "shape": { "kind": "quad" },
      "depthRelation": "FRONT_OF_PARENT",
      "zOrder": 5,
      "overflow": "CLIP",
      "hitTest": "PANEL",
      // 类型 B：运行时 reparent 与 PanelDimensions 改写由 adapter 接手，不与主题值叠加
      "runtimePoseOverride": "adapter"
    }
  },

  // ---------- slot → panel 绑定（SlotId 允许 1:N，必须声明共享策略） ----------
  "slotBindings": [
    { "slot": "MEDIA_STAGE",    "panel": "mediaStage",       "sharePolicy": "EXCLUSIVE" },
    { "slot": "TRANSPORT",      "panel": "transport",        "sharePolicy": "EXCLUSIVE" },
    { "slot": "BROWSE",         "panel": "centerContent",    "sharePolicy": "EXCLUSIVE" },
    // 既有布局的事实：一个 CONTEXT slot 由左右两条 rail 共享 → 必须显式 MIRRORED
    { "slot": "CONTEXT",        "panel": "leftDetailRail",   "sharePolicy": "MIRRORED", "mirrorGroup": "rails" },
    { "slot": "CONTEXT",        "panel": "rightContextRail", "sharePolicy": "MIRRORED", "mirrorGroup": "rails" },
    { "slot": "SYSTEM_TOOLBAR", "panel": "systemToolbar",    "sharePolicy": "EXCLUSIVE" },
    { "slot": "ACTION_SHEET",   "panel": "inputConsole",     "sharePolicy": "EXCLUSIVE" }
  ],

  // ---------- UI 组合组（受控组件的组合与重排） ----------
  "componentGroups": [
    {
      "id": "defaultCinemaPlaybackCanvas",
      "reusable": true,
      "members": [
        { "id": "systemStatusStrip",  "kind": "SEMANTIC_MODULE" },
        { "id": "contentNavigationSlot","kind": "SLOT" },
        { "id": "mediaStage",         "kind": "SEMANTIC_MODULE" },
        { "id": "watchTitle",         "kind": "SEMANTIC_MODULE" },
        { "id": "transportActionStrip","kind": "SEMANTIC_MODULE" },
        { "id": "seekTimeline",       "kind": "SEMANTIC_MODULE" },
        { "id": "playbackConfigPopup","kind": "POPUP" },
        { "id": "grabHandle",         "kind": "SEMANTIC_MODULE" }
      ]
    }
  ],

  // ---------- 抓取组（空间操控；与 UI 组合、弹幕组无关） ----------
  "grabGroups": [
    {
      "id": "workbench",
      "handleRef": "workbenchRoot",
      "mechanism": "NATIVE_PARENT",   // 待头显验证，见 §4.3 / §7 Phase 3 gate
      "members": [
        { "entityRef": "grabBar",    "follow": "TRANSLATION_AND_ROTATION" },
        { "entityRef": "mediaStage", "follow": "TRANSLATION_AND_ROTATION" }
      ],
      "lifecycle": "ALWAYS",
      "lostTrackingPolicy": "FREEZE_AT_LAST_POSE",
      "suppressOthersWhileGrabbed": true
    }
  ],

  // ---------- 弹幕组（渲染目标；与上面两者无关） ----------
  "danmakuGroups": [
    {
      "id": "mainDanmaku",
      "allocationWeight": 1.0,
      "laneStateSharing": "SHARED",
      "maxActiveItems": 240,
      "occlusionPolicy": "AVOID_PROJECTED_OVERLAP",
      "occlusionDomainId": "stageDomain",
      "sharedCoordinateSpace": { "widthUnits": 1.0, "heightUnits": 1.0 },
      "laneSets": [
        { "id": "scrollRTL", "laneFamily": "SCROLLING",
          "emissionDirection": "RIGHT_TO_LEFT", "laneCount": 12 },
        { "id": "topFixed",  "laneFamily": "TOP_FIXED",  "laneCount": 3 },
        { "id": "bottomFixed","laneFamily":"BOTTOM_FIXED","laneCount": 3 }
      ],
      "layers": [
        // surfaceIds 为多值（决策 D4=A）。
        // 语义是「分配域」不是「复制域」：一次事件只落到其中一个 surface。
        { "id": "danmakuL1", "surfaceIds": ["danmakuSurfaceMain"],
          "depthOffsetMeters": -0.01, "maxActiveItems": 240 }
      ]
    }
  ],
  "danmakuOcclusionDomains": [
    { "id": "stageDomain", "groupIds": ["mainDanmaku"], "requireCrossGroupChecks": true }
  ],

  // ---------- 受控 action（白名单） ----------
  "actions": { "onPrimaryStageTap": ["revealTransport"] }
}
```

### 5.3 字段默认值、合法范围与校验规则

| 字段 | 默认值 | 合法范围 / 规则 | 校验失败 |
| --- | --- | --- | --- |
| `schemaVersion` | — | ∈ `SUPPORTED_SCHEMA_VERSIONS` | `UnsupportedSchemaVersion` |
| `id` / `displayName` | — | 非空、`[a-z][a-z0-9-]*` | `InvalidThemeIdentity`（已有） |
| `scene.requiredAnchors[]` | `[]` | 必须在场景 catalog 中存在 | **`MissingSceneAnchor`（新增）** |
| `panels.<id>.panelClass` | — | ∈ `UI_PANEL` / `SCENE_PANEL` / `MEDIA_SURFACE` | **`UnknownPanelClass`（新增）** |
| `panels.<id>.component` | — | ∈ `ThemeComponentRegistry`；且 `panelClass` 与组件声明的兼容类型一致 | **`UnknownComponent`（新增）/ `ComponentClassMismatch`（新增）** |
| `panels.<id>.themeableGeometry[]` | `[]` | ⊆ 该 `panelClass` 允许的几何集合；`MEDIA_SURFACE` 只允许 `sizeMeters`/`shape` | **`GeometryNotThemeable`（新增）** |
| `panels.<id>.anchor.sceneNode` | — | ∈ `requiredAnchors` 或场景 catalog | `MissingSceneAnchor` |
| `panels.<id>.parent.panel` | `null` | 必须存在；不得成环；`anchor` 与 `parent` 不得同时给出 | **`ParentCycle`（新增）/ `AmbiguousPlacement`（新增）** |
| `offsetMeters.x/y/z` | `0,0,0` | `±3.0 m`（超界报 issue，不静默 clamp） | **`OffsetOutOfRange`（新增）** |
| `rotationDegrees.pitch/roll` | `0` | `±80°` | **`RotationOutOfRange`（新增）** |
| `rotationDegrees.yaw` | `0` | `±180°` | `RotationOutOfRange` |
| `sizeMeters.width/height` | — | `0.15–4.0 m`，有限 | `InvalidPanelSize`（已有，补上下界） |
| `shape.kind=cylinder.radiusMeters` | — | `1.5–8.0 m`（对齐 `PlaybackStageCurvature` 的合法域） | `InvalidCylinderGeometry`（已有，补范围） |
| `shape.kind=cylinder.arcDegrees` | — | `0.1–359.9°` | `InvalidCylinderGeometry`（已有） |
| `depthRelation=FRONT_OF_PARENT` / `BEHIND_PARENT` | `ROOT` | 需 `parent != null` | **`DepthRelationWithoutParent`（新增）** |
| `zOrder` | `0` | `-100..100` | `ZOrderOutOfRange` |
| `overflow=VISIBLE` | `CLIP` | 需 `hitTest != NONE` | `InvalidCanvas`（已有语义扩展） |
| `hitTest=NONE` | `PANEL` | 不可与 `userAdjustable` 非空并存 | `HitTestConflict` |
| **`slotBindings[].slot`** | — | **条件规则**：同一 slot 被 N>1 个 panel 绑定时，**每一条都必须显式给出 `sharePolicy != EXCLUSIVE`**，且 `MIRRORED` 必须成对并带同一个 `mirrorGroup`；否则报错 | **`DuplicateSlotOwner`（新增，条件化）** |
| `slotBindings[].panel` | — | 必须存在对应的 `panels.<id>` | **`UnknownPanelRef`（新增）** |
| `slotBindings[].mirrorGroup` | — | `sharePolicy == MIRRORED` 时必填，且组内成员数 ≥ 2 | **`IncompleteMirrorGroup`（新增）** |
| `grabGroups[].handleRef` / `members[].entityRef` | — | ∈ `EntityCatalog`（场景节点 ∪ 运行时注册） | **`UnknownEntityRef`（新增）** |
| `grabGroups[].parentGroupId` | `null` | 存在、无环、**成员不与父组重复** | **`GrabMemberOverlap`（新增）** |
| `grabGroups[].exclusiveWith` | `{}` | 对称 | **`AsymmetricExclusion`（新增）** |
| `grabGroups[].mechanism` | — | `NATIVE_PARENT` 需**已记录头显验证**；未验证时 validator 报 warning 而非 error，但运行时必须走 gate | **`UnverifiedGrabMechanism`（新增，warning）** |
| `danmakuGroups[].layers[].surfaceIds` | — | 非空；每个 ∈ `overlaySurfaces` 且 `kind == DANMAKU` | `InvalidTopology`（已有） |
| `danmakuGroups[].layers[].surfaceIds` + `laneStateSharing` | — | `ISOLATED` + `surfaceIds.size > 1` 时，必须给出每-surface 配额，否则拒绝 | **`AmbiguousLaneSharing`（新增）** |
| `danmakuGroups[].laneSets[].laneCount` | — | `1..64` | **`InvalidLaneCount`（新增）** |
| `danmakuOcclusionDomains[].groupIds` | — | 非空、均存在 | `EXCLUSION_OR_GROUP_UNKNOWN`（扩展已有） |
| `actions.*` | — | ∈ `ThemeAction` 白名单（6 项 + 后续注册） | `InvalidAction`（已有） |

**校验分工**：
- `ThemeValidator`（core，纯 Kotlin，可 JVM 单测）：结构 + 范围 + 引用完整性 + 跨组冲突。
- `SceneAnchorCatalog`（app adapter，运行时注入）：把"anchor/entityRef 是否存在"喂给 validator，
  使 validator 保持纯函数、可测试。**这是新增的关键抽象**——否则几何校验无法脱离设备测试。

### 5.4 受控 action 边界

**允许暴露给主题**（全部指向既有唯一实现）：

```kotlin
sealed interface ThemeAction {
    data class OpenRoute(val route: ThemeRoute) : ThemeAction        // CREATOR/COLLECTION/SERIES/CHANNEL
    data class OpenFocus(val target: FocusTarget) : ThemeAction      // CREATOR/COLLECTION/SERIES
    data class SetPlaybackSpeed(val speed: Float) : ThemeAction      // 0.25..4.0（已有校验）
    data class SetDanmakuMode(val mode: DanmakuMode) : ThemeAction   // OFF/FLAT/SPATIAL
    data object SwitchTo2D : ThemeAction
    data object TogglePassthrough : ThemeAction
    // 待新增（先复用 appState 既有方法，不新写业务逻辑）
    data class SetQuality(val quality: PlaybackQuality) : ThemeAction
    data class SetDisplayRatio(val ratio: PlaybackDisplayRatio) : ThemeAction
    data class SetStageSize(val size: PlaybackCanvasSize) : ThemeAction
    data class SetAutoplay(val enabled: Boolean) : ThemeAction
    data class SetStageGeometry(val geometry: StageGeometry) : ThemeAction
}
```

**明确不允许暴露**（硬边界）：

| 禁止项 | 原因 |
| --- | --- |
| 任意 Kotlin/JS/字节码执行 | 主题不可下载可执行代码 |
| 网络请求（HTTP/WS/下载） | 主题无网络能力；provider 归 app |
| 创建 / 持有 `ExoPlayer`、`PlayerSession` | 单一播放器不变量 |
| 创建 / 持有视频 `Surface` | 单一活动视频 Surface 不变量 |
| 创建 `Activity` / 启动路由 | 宿主归 app |
| `Entity.create()` / 任意 ECS 实体创建 | 静态布置归 Meta Spatial Editor |
| 直接写 `Transform` / `PanelSceneObject` | 必须经 placement adapter |
| 直接写 `Visible` / `PanelLayerAlpha` | 可见性单一 owner |
| 读 Cookie / 凭证 / token | 与 Bilibili provider 边界 |
| 注册新的 `SystemBase` | 系统注册归 app |

**唯一例外**：编译进应用的 `ThemePlugin`（Kotlin interface），用于主题作者确实需要的复杂行为。
它必须随 APK 编译，**不是**网络下载的可执行主题代码（对齐 `immersive-ui-low-code-architecture.md:1068`）。

### 5.5 anchor 与 offset 的语义（必须先固定，否则主题无法稳定）

选定 D1=A 之后，"anchor 由场景授权、offset 由主题给"这句话还不足以保证工作台稳定。
以下 8 项必须在 Phase 1 写入合同，否则 Phase 2 的 adapter 没有可实现的目标：

| # | 需要固定的事 | 建议规则 |
| --- | --- | --- |
| 1 | **anchor 初始 world pose 的所有者** | **场景**（Meta Spatial Editor 导出）。运行时**只读** anchor 的 world pose，不修改 anchor 实体本身。anchor 是参考点，不是可动对象。 |
| 2 | **坐标系与单位** | 米；右手系；沿用项目既有约定（`docs/spatial-coordinates.md`）。**anchor 局部系定义为：-Z 指向用户、-Y 向下**（与 `WorkbenchLayoutConfig` 的 MediaStage local frame 一致），避免两套方向语义。 |
| 3 | **旋转顺序** | 固定为 **Yaw → Pitch → Roll**（先绕 Y 再绕 X 再绕 Z），并写进合同；schema 的 `pitch/yaw/roll` 只是该顺序下的欧拉角，不是四元数直传。 |
| 4 | **parent 变更时的局部姿态计算** | `localPose = inverse(parentWorldPose) × anchorWorldPose × themeLocalOffset`。当 `parent` 从 `null` 变为某 panel 时，**保留世界姿态**（在父系内重算局部值），即"reparent 不跳变"。adapter 负责这次换算；主题作者永远只写"相对 anchor 或相对 parent 的局部值"。 |
| 5 | **主题静态值 vs 用户运行时调整的边界** | `panels.<id>.offsetMeters / rotationDegrees / sizeMeters / shape` = **主题静态值**（只读）。用户可调的项必须在 `userAdjustable` 显式列出，且只允许指向既有用户设置（当前仅 `stageScale`、`stageHeight`）。**主题不得把静态值伪装成用户设置。** |
| 6 | **用户调整的持久化范围** | 现状：`playback_stage_scale` 与 `workbench_stage_y` 存在**全局** prefs（不区分主题）。**这是产品决策，不是 Phase 1 能自行决定的事**（原设计写"Phase 1 必须决定"是错的标签）→ 见 §10.3 **D7**。技术建议：per-theme 命名空间（`theme.<id>.stageScale`），否则切主题后用户的调整会串味；代价是引入 prefs 迁移（见 #7）。 |
| 7 | **主题切换 / 场景导出 / anchor 改名后的兼容** | `schemaVersion` + `scene.requiredAnchors` 已经覆盖"anchor 缺失 → 拒绝加载"。另外需要：主题切换时把**已应用**的几何与 prefs 一起回滚到新主题的默认值（不能留上一个主题的残留）；anchor 改名视为**破坏性变更**，需要主题版本升级而不是静默兼容。 |
| 8 | **默认姿态的单一来源** | `WorkbenchLayoutConfig` 降级为**默认主题的 fixture 数据源**（一张"语义值 → 布局值"的搬迁表），而不是第二套运行时布局。搬迁完成后它不应再被 adapter 直接读取；`CinemaTheme` 的 `theme.json` 成为唯一默认值来源。 |

> **验收含义**：只测"某面板 offset 能改"是不够的。必须同时测 #4（reparent 不跳变）、
> #5（主题改不了用户项）、#6（切主题不串味）、#7（anchor 缺失被拒绝）。
>
> **Phase 1 实际交付边界**：上面 8 项里，**#1/#2/#3/#4 已编码为合同类型 + 有测试**；
> **#5 部分**（`userAdjustable` 与主题静态值已分离，但"主题改不了用户项"的强制尚无调用方）；
> **#6 未决定（D7）**；**#7 由 `requiredAnchors` + catalog 拒载覆盖**；
> **#8 未实现**（`WorkbenchLayoutConfig` 仍是唯一布局来源，降级属 Phase 2）。
> 因此 A6 的验收计数应剔除 #6 —— 详见 PRD §5.1。

### 5.6 尺寸与 reshape 的按类型分流

「注册一次 + runtime reshape」（D5=A）在三种面板类型上的可用性不同，
不能只写一条统一规则：

| 面板类型 | 初始尺寸来源 | 运行时改尺寸 | reshape 失败/延迟处理 |
| --- | --- | --- | --- |
| **A. UI_PANEL** | 注册时 `settingsCreator` 读实体上的 `ThemePanelLayout` | ✅ `PanelSceneObject` reshape（既有路径） | 复用 `StageOverlayReshapeSystem` 的一帧延迟 replay；pending 状态与重放**所有权归 adapter**（单一 owner，不散落） |
| **B. SCENE_PANEL** | **场景**里的 `PanelDimensions`（但**不是**最终值） | ✅ 但必须**接手** `bindCenterContentPanel()` 已有的 reparent + `PanelDimensions` 改写，避免双写 | 同 A；另外必须处理"场景 `Visibility` 与运行时 `setVisible` 两个 writer"的历史问题 |
| **C. MEDIA_SURFACE** | 手工 `MediaPanelSettings` + `videoShapeOptions()` | ⚠️ **受限**：`sizeMeters`/`shape` 生效，但曲率走**自定义 `TriangleMesh`**，且 compositor layer 顺序 / 立体模式 / 视频 Surface 归属不可主题化 | 既有 `reassertAppliedVideoStageGeometry()` / `applyPlaybackVideoCurvature()` 的守卫必须保留（它曾因 `if (applied == curvature) return` 吞掉持久化曲率） |

**必须写进合同的三件事**：
1. **初始尺寸的来源**：A/B 由主题给、C 由专用 adapter 给 —— 三者的"谁说了算"必须显式。
2. **reshape 失败/延迟的所有权**：只允许一个 owner 持 pending 状态并重放，不得每类面板各写一套。
3. **`themeableGeometry` 白名单**：主题只能声明该 panelClass 已开放的子集（见 §5.3 的
   `GeometryNotThemeable`），避免"JSON 层面可配、实际某些面板不生效"的假能力。

---

## 6. Runtime 适配链路与唯一状态 owner

### 6.1 目标链路

```text
theme.json (APK assets)
   │
   ▼ ① ThemeSource           读内置资源，拒绝网络主题
ThemeJsonParser              JsonObject -> DraftTheme
   │
   ▼ ② ThemeSchemaMigration   vN -> vN+1（纯函数链）
SpatialTheme (core)          validate 前的规范化模型
   │
   ▼ ③ ThemeValidator (core, 纯)  +  SceneAnchorCatalog + EntityCatalog (app 注入)
List<ThemeValidationIssue>   ≠空 -> 拒绝加载，回退默认主题（可回滚点）
   │
   ▼ ④ ComponentCompositionResolver (core, 纯)
ResolvedComposition          componentId -> 实现 + slot 归属 + 顺序
   │
   ├──▶ ⑤a Compose renderer (compose 模块)
   │       ResolvedComposition -> 组件树（SpatialPanelShell + 原子组件）
   │
   ▼ ⑤b PanelPlacementResolver (core, 纯)
ResolvedPanelPlacement       panelId -> (anchor, local pose, size, shape, depth, presentation, hitTest)
   │
   ▼ ⑥ PanelLayoutAdapter (app / Meta adapter)
   │    anchor 世界姿态 × 主题局部 offset/rotation -> 实体 Transform
   │    写自定义组件 ThemePanelLayout(width,height,shape) -> settingsCreator 读取
   │    运行时 reshape（复用 reshapeStageOverlay + StageOverlayReshapeSystem）
   │    PanelDimensions / Hittable / PanelInputOptions 同步
   ▼
既有 entities / panels
   ├─ workbenchRootEntity, spatialized_video_panel, videoSurfaceEntity,
   │  controls_id, mr_panel, center_content_panel, video_selector_panel,
   │  mode_panel, input_method_panel, stage_backdrop_panel, grab_bar_panel
   └─ danmaku_overlay_panel

   ▼ ⑦ GrabGroupRegistry (app adapter)
   GrabGroupSpec -> handle Entity + member Entities
     NATIVE_PARENT  -> 复用现有 TransformParent 子树（默认，行为不变）
     DELTA_MIRROR   -> 注册一个 GrabPilotSystem 实例（已有通用实现，首次接线）
   ▼
   IsdkGrabbable / IsdkBoxCollider (既有)

   ▼ ⑧ DanmakuGroupAllocator (core, 纯)
   DanmakuEvent + DanmakuAllocationRequest -> DanmakuAllocationResult
     group 宏观负载 -> laneSet -> layer -> surface
     跨组遮挡 -> DanmakuOcclusionDomain + CrossLayerOcclusionResolver
   ▼
   ⑨ DanmakuSurfaceAdapter (app)
   surfaceId -> 面板实体 + 独立 Compose Canvas runtime（复用 DanmakuCanvasRuntime）

   ▼ ⑩ ThemeActionDispatcher (app)
   ThemeAction -> 既有 ViriViriAppState / Activity 方法（唯一实现）
```

### 6.2 唯一状态 owner 表

> **本表混合了"现状"与"目标"，阅读时请按列区分。** 「唯一 owner」列中不带"新建"的项是**现状事实**；
> 带"新建"或"迁移动作"的项是**目标**，尚未实现。下表把两者显式拆开。

**现状（已核实，源码可证）**

| 关注点 | 现状 owner | 证据 |
| --- | --- | --- |
| 单一 ExoPlayer / 活动视频 Surface | `PlayerSession`（1 处 `ExoPlayer.Builder`，1 个 `surface` 字段，统一经 `attachSurface`） | `ViriViriAppState.kt:177,178,218-223,233` |
| 面板可见性**写入** | `SpatialPanelVisibilityController.setVisible` | `SpatialPanelVisibilityController.kt:20-55` |
| `PanelLayerAlpha` 写入 | **3 个 owner**（可见性控制器 / 抓手条悬停 / 手腕面板用 `Visible`） | `SpatialPanelVisibilityController.kt:28-47`；`GrabBarHoverSystem.kt:103-122`；`WristAttachedSystem.kt:54` |
| 画布可见性**来源** | **两套并存**：`ImmersiveWorkbenchReducer.modules()` 与 `PlaybackCanvasReducer.visibleSlots()`；最终由前者决定 | `ui-structure-and-terminology.md:752-755` |
| 面板几何 | `WorkbenchLayoutConfig.DEFAULT`（Kotlin 常量），经 Activity 直接写 `Transform` | `WorkbenchLayoutConfig.kt:21-111`；`SpatialVideoSampleActivity.kt:271` |
| 抓取操控 | SDK `TransformParent` 传播（**待头显验证**），无 app 侧镜像系统 | `SpatialVideoSampleActivity.kt:691-707,717,1051` |
| 弹幕轨道/分配 | `DanmakuCanvasRuntime` 内 per-surface（当前仅 1 个实例） | `DanmakuRuntime.kt:154-169` |
| 业务 action | `ViriViriAppState` / Activity 方法 | — |

**目标（尚未实现，需 Phase 1–5）**

| 关注点 | 目标 owner | 迁移动作 |
| --- | --- | --- |
| 媒体目标生命周期（video/overlay target） | `MediaStageReducer`（core）+ app adapter | 接线（已有合同） |
| 画布可见性**来源** | **收敛为单一**：`PlaybackCanvasReducer.visibleSlots()`（+ 主题 `presentationPolicies` 的 `PERSISTENT`） | `ImmersiveWorkbenchReducer.modules()` 不再产出可见性，只产出**内容路由**（D2=A，行为变更） |
| `PanelLayerAlpha` 写入 | **收敛为 `SpatialPanelVisibilityController` 单一 owner** | 抓手条悬停改为"请求"而非直接写组件 |
| 面板几何 | `PanelPlacementResolver`（core，纯）→ `PanelLayoutAdapter`（app，**按 §2.8 三类分流**） | 新建；`WorkbenchLayoutConfig` 降级为默认主题 fixture 数据源 |
| 抓取操控 | `GrabGroupRegistry` | 新建；`GrabPilotSystem` 首次接线（仅 `DELTA_MIRROR`） |
| 弹幕轨道/分配 | `DanmakuGroupAllocator`（新建，core） | 把 `DanmakuCanvasRuntime` 的 per-surface 轨道状态提升为 group 级；**不接线已死的 `DanmakuLaneScheduler`** |
| 弹幕渲染 | `DanmakuSurfaceAdapter` per surface | 新建（多 surface） |
| 业务 action | `ViriViriAppState` / Activity 方法 | **不变**；`ThemeActionDispatcher` 只做转发 |
| 内容路由（中心工作区显示什么） | `SearchWorkspaceRoute` | **未决策 D3**：见 §10 的三问拆法 |

**关键去重**：今天可见性有两个来源（`ImmersiveWorkbenchReducer.modules()` 的 `NORMAL` 恒显双 rail
vs `PlaybackCanvasReducer` 的配方，见 `ui-structure-and-terminology.md:752-755`）。
目标架构下 **canvas/presentation 是唯一可见性来源**，`ImmersiveWorkbenchState` 退化为
"内容 + 呈现形态"的状态机，不再决定哪些面板亮。

---

## 7. 分阶段迁移路线

难度：S ≤ 1 天 / M ≤ 3 天 / L ≤ 1 周 / XL > 1 周（单人、含测试）。
影响面缩写：**C**=只改 core contract / **R**=接入 app runtime / **P**=新增 theme parser/validator /
**S**=修改 Meta Spatial Editor 场景 / **H**=头显真机验证。

### 7.1 范围声明：哪些已批准，哪些还需单独批准

| 阶段 | 状态 | 是否改变 app 可见行为 | 是否需头显 |
| --- | --- | --- | --- |
| **Phase 0**（文档诚实性与前置） | **✅ 已批准** | 否 | 否 |
| **Phase 1**（schema / parser / 迁移 / validator / fixture / 单测） | **✅ 已批准** | 否 | 否 |
| **Phase 2**（场景 anchor + 几何 adapter + 可见性收敛） | **⛔ 方向已确认，执行需单独批准** | **是**（2.3–2.7 会改变面板几何与可见性） | **是**（2.3–2.7） |
| Phase 3 / 4 / 5 | ⛔ 后续子任务 | 是 | 是 |

**"Phase 0+1 不改 app 行为"是明确的承诺**，理由：Phase 0 只改文档；Phase 1 只新增 core 合同、
parser、validator、fixture 与单测，**不接 adapter、不注册新 system、不改面板注册**。
阶段验收仅限纯 Kotlin 测试与文档准确性。

**Phase 2 与本阶段必须切开**，因为它的四项动作都触碰既有边界：
2.1 改场景（静态布置授权源）、2.5 改 app adapter、2.7 改可见性 owner（行为变更）、
2.4/2.5 触碰类型 B/C 面板的特殊路径。把它们与"建合同"混在一个阶段里，
会让"本阶段不改 app 行为"变成一句无法兑现的话。

**"in_progress" 的语义**：Trellis 的 `task.json.status = in_progress` 只是流程记账
（prd + jsonl 就绪即翻牌，见 `.trellis/workflow.md` Phase 1.4）。它**不代表** Phase 2 的 app 行为改动
已被批准。执行者必须以本节为准，而不是以 status 为准。

### Phase 0：诚实性与前置（S）

| 项 | 内容 | 影响面 |
| --- | --- | --- |
| 0.1 | 修正 `immersive-ui-low-code-architecture.md:10` 的 mse-agent 过期声明 | 文档 |
| 0.2 | 在 `ui-structure-and-terminology.md` 增补"主题契约零消费"与"anchor 悬空"两条事实，并修正两处标注：`:731` 的 `DanmakuLaneScheduler`（`[实现]` → `[未接线]`，**入口不可达**：`loadDanmaku()` 无生产调用者）、`:640` 的 `PanelPresentationPolicy`（`[契约]` → **`[实现]（部分）`**：仅 `PERSISTENT` 被 `PlaybackCanvasContracts.kt:82-86` 消费，其余 4 项无 reader） | 文档 |
| 0.3 | 确认 mse-agent 操作规程（`D:\Program Files\Meta Spatial Editor\v16\Resources\mse-agent.exe`） | 工具 |

**回滚点**：纯文档，无回滚风险。

### Phase 1：主题 schema + 解析 + 校验（M，纯 Kotlin，无 runtime 改动）

| 项 | 内容 | 影响面 |
| --- | --- | --- |
| 1.1 | `SpatialTheme` 加 `schemaVersion` + `ThemeSchemaMigration` 链 | **C** |
| 1.2 | `SceneAnchorCatalog` / `EntityCatalog` 抽象（app 注入，core 消费） | **C** |
| 1.3 | `ThemeJsonParser`（不引入 kotlinx.serialization 前先手写；或评估加依赖） | **P**、**C** |
| 1.4 | 扩展 `ThemeValidator`：offset/rotation/zOrder 范围、parent cycle、slot 重复、anchor 存在性 | **C** |
| 1.5 | `ComponentGroupSpec` / `GrabGroupSpec` / `DanmakuSurfaceGroup` 扩展字段 + 各自 validator | **C** |
| 1.6 | 默认主题 fixture 文件化（`CinemaTheme.create()` → `themes/cinema/theme.json` + 保底 Kotlin fixture） | **P**、**C** |
| 1.7 | 纯 Kotlin 测试：解析、迁移、范围校验、三类 group 冲突（防重复移动、exclusiveWith 对称） | 测试 |

**验收**：`:spatial-workbench-core:test` 全绿；新增 ≥40 条断言；**不触碰 app 行为**。
**回滚点**：新增文件为主，`SpatialTheme` 只加字段（有默认值）→ 可单 commit 回退。

### Phase 2：几何适配（L，首个真正改变视觉的阶段）

> ⚠️ **本阶段不包含在"当前已批准"范围内。** 见 §7.1 的范围声明与 §10 的进入条件。
> Phase 2 必须**按下面的子步骤逐个独立落地与独立回滚**，不能作为一个大 patch 提交。

| 子步 | 内容 | 影响面 | 独立回滚点 |
| --- | --- | --- | --- |
| **2.1** | 用 mse-agent 在场景新增语义 anchor 节点（`media_stage_anchor` 等），跑 `:app:export` 检查导出制品含这些节点。**不接 runtime** | **S** | 场景 git 回退；无 runtime 影响 |
| **2.2** | `PanelPlacementResolver`（core，纯）+ `ThemePanelLayout` 组件 + `settingsCreator` 读取。选**一个普通 UI panel** 试点（建议 `transport`） | **C**、**R** | 开关 `PLACEMENT_ADAPTER_PANELS = {}`（空集即全回退） |
| **2.3** | 单面板几何验证：offset / rotation / size / shape 在该 UI panel 上真实生效，且 reparent 不跳变 | **R**、**H** | 同上，逐个 panel 加入白名单 |
| **2.4** | 单独适配 **类型 B（SCENE_PANEL / `centerContent`）**：接手既有 reparent + `PanelDimensions` 改写，不叠加 | **R**、**H** | 该 panel 从白名单移除 |
| **2.5** | 单独适配 **类型 C（MEDIA_SURFACE / 视频）**：专用 adapter，只开放 `themeableGeometry`；保留曲率守卫与单 Surface 不变量 | **R**、**H** | 类型 C 走 `VideoGeometryAdapter` 旧路径 |
| **2.6** | 其余 UI panel 逐个迁入白名单；`WorkbenchLayoutConfig` 降级为 fixture 数据源 | **R** | 逐 panel 移出白名单 |
| **2.7** | **最后单独切换可见性 owner**（D2=A），并显式记录 rail 行为变化 | **R**、**H** | 开关 `VISIBILITY_SOURCE = {CANVAS, WORKBENCH_BRIDGE}`；默认切回旧桥 |
| **2.8** | 主题几何预览 / 一键回退默认主题，且回退入口**在工作台之外可达** | **R** | 保留 |

**硬性要求**：
- **回滚必须能按面板类型 / 按单个 panel 回退**，不能只有一个全局 bool。
  `PLACEMENT_ADAPTER_PANELS` 是**集合**而不是开关，就是为了让"类型 C 适配失败"不连累类型 A。
- 2.7（可见性 owner 切换）与 2.1–2.6（几何）**必须分开提交**，否则视觉回归无法归因。
- 每个子步单独跑 `.\scripts\build-windows-debug.ps1`（跨模块/场景改动），并在真机确认后再进下一步。

**已知风险**：面板 scene object 一帧后才存在（`SpatialVideoSampleActivity.kt:2548-2557`）→ 必须走
既有 reshape replay 机制，且 pending 状态**单一 owner**（见 §5.6）；否则几何静默丢失。

### Phase 3：抓取组接线（M）

> **本阶段的第一个动作是头显验证，不是写代码。** 见 §0.5 与 §4.3。

| 子步 | 内容 | 影响面 |
| --- | --- | --- |
| **3.0** | **头显 gate**：验证抓取时哪些实体真的跟随、哪些不动、`TransformParent` 传播是否成立。
      未通过则 `NATIVE_PARENT` 不可作为默认机制，且"行为不变"的承诺不成立 | **H** |
| 3.1 | `GrabGroupRegistry` + `EntityRef` → `Entity` 解析 | **R** |
| 3.2 | 默认组按 3.0 的结论选择机制（通过 → `NATIVE_PARENT`；不通过 → `DELTA_MIRROR`） | **R** |
| 3.3 | `DELTA_MIRROR` 路径首次接线 `GrabPilotSystem`（当前死代码） | **R** |
| 3.3a | 清理既有噪声：`IsdkPanelGrabHandle` 已 import 但从未使用；抓手条 KDoc 自称"不可抓取"与实际实现矛盾 | **R** |
| 3.4 | 失去跟踪策略、`exclusiveWith`、组父子防重复移动 | **R**、**C** |
| 3.5 | 头显：多组并存、单手可达性、抓取/点选冲突、MR/VR 双模式 | **H** |

**回滚点**：默认主题只声明一个 `workbench` 组；3.2 未通过 gate 前不得合并默认组切换。
**注意**：Meta 文档指出面板抓取需 `IsdkGrabbable` **与** `IsdkPanelGrabHandle` 成对；
已知问题 `IsdkPanelResize` 需 `VRFeature` 注册 —— 本项目 `registerFeatures()` 无条件注册
`VRFeature` + `ComposeFeature`（`SpatialVideoSampleActivity.kt:334-343`，`:335`），前置条件已满足。

### Phase 4：弹幕组多 surface（L）

| 项 | 内容 | 影响面 |
| --- | --- | --- |
| 4.1 | `DanmakuGroupAllocator`（core，纯）：group 负载 → laneSet → layer → surface | **C** |
| 4.2 | `OverlaySurfaceSpec` → surface 面板实体绑定表 + 多 `DanmakuCanvasRuntime` | **R** |
| 4.3 | `LaneStateSharing.SHARED/ISOLATED` 两态落地 | **C**、**R** |
| 4.4 | `DanmakuOcclusionDomain` + `CrossLayerOcclusionResolver` 实现（视点投影） | **R**、**H** |
| 4.5 | 场景新增多个弹幕 surface anchor（若不与视频共享曲面） | **S** |
| 4.6 | 头显：斜向 surface 首尾遮挡、头动容差、组间负载 | **H** |

**回滚点**：默认主题只声明 **1 个 group + 1 个 surface + SHARED**，等价当前单层弹幕。
**硬不变量**：不得创建第二个视频 Player 或第二个视频输出 Surface；
overlay surface 是独立渲染目标（已由 `MediaStageTargetRegistry.validate` 强制）。

### Phase 5：UI 组件重组（XL，最后做）

| 项 | 内容 | 影响面 |
| --- | --- | --- |
| 5.1 | `ThemeComponentRegistry` 受控组件目录 | **C** |
| 5.2 | `ComponentCompositionResolver`（core，纯） | **C** |
| 5.3 | compose 模块按 `componentId` 渲染组件树（`SpatialPanelShell` + 原子组件） | **R** |
| 5.4 | `ThemeActionDispatcher`：`ThemeAction` → 既有 appState 方法 | **R** |
| 5.5 | 驾驶舱示例主题（验证 API 能否承载第二种主题） | **R**、**S** |

**风险**：Meta 已知问题——**动态创建的 Compose 面板可能失去触摸响应**
（`spatial-sdk-known-issues.md`：`ComposeViewPanelSettings` 动态面板 + 手/手柄切换）。
因此 Phase 5 **不得**把现有静态注册面板改成运行时高频创建；应保持"注册一次 + 运行时 reshape"。

### 依赖顺序

```text
Phase 0 ──▶ Phase 1 ──┬──▶ Phase 2 (几何) ──▶ Phase 5 (组件重组)
                      ├──▶ Phase 3 (抓取组)
                      └──▶ Phase 4 (弹幕组)
```

Phase 2/3/4 相互独立，可并行；Phase 5 依赖 1 + 2。

---

## 8. 风险清单

| # | 风险 | 等级 | 缓解 |
| --- | --- | --- | --- |
| R1 | 场景无语义 anchor，Phase 2 强依赖 mse-agent 场景改动 | **高** | Phase 0 先验证 mse-agent 连通性；anchor 节点先加、面板迁移后做 |
| R2 | 面板 scene object 一帧后才存在，几何 reshape 被静默丢弃 | **高** | 复用 `StageOverlayReshapeSystem` replay；新增单测覆盖"延迟落地" |
| R3 | Meta 已知问题：动态 Compose 面板可能失去触摸响应 | **高** | 不把静态面板改为动态创建；保持注册+reshape；**需头显验证** |
| R4 | 8 个面板当前由 Kotlin 创建，与"场景授权"规则冲突 | 中 | Phase 2.1 分批迁移；迁移前先记录既存偏差 |
| R5 | 两套可见性来源（NORMAL 恒显双 rail vs 画布配方）已产生行为不一致 | 中 | Phase 2 收敛为单一来源；属**行为变更**，需产品确认 |
| R6 | `temp/` 目录含大量 vendored 样例，grep 易污染结论 | 中 | 所有检索排除 `temp/`（本文已执行） |
| R7 | 真实视频面板曲率是自定义 `TriangleMesh`，非标准 `CylinderShapeOptions` | 中 | UI overlay 面板可用标准 cylinder；视频层沿用现有自定义网格，不重写 |
| R8 | 曲率三字段/三持久化 key 冗余未合并（kanban `k26`） | 低 | 与 Phase 2 一起收敛 |
| R9 | 引入 JSON 解析依赖（kotlinx.serialization）改变构建 | 低 | 先手写最小 parser，或单独 commit 加依赖 |
| R10 | 抓取组新增后与"点选/次要输入"冲突 | 中 | `suppressOthersWhileGrabbed` + `exclusiveWith`；**需头显验证** |
| R11 | 为**死代码**建模：`DanmakuLaneScheduler` / `loadDanmaku()` / `mergeDanmakuEvents` 无 runtime 调用链，容易被误当作现有轨道实现接线 | 中 | Phase 4 明确"提升 `DanmakuCanvasRuntime` 轨道状态"，不接死 scheduler；并在 Phase 0 修正术语标注 |
| R12 | overlay 尺寸按整块舞台 reshape，视频按宽高比内容矩形 reshape，宽高比 ≠ 16:10 时弹幕泳道跨出视频可见区 | 中 | 把 overlay 的目标尺寸改为"视频内容矩形"而非舞台整块；**需头显验证**泳道与画面边界 |
| R13 | 弹幕渲染器不读发射方向，逆向弹幕方向错误 | 低 | Phase 4 显式消费 `DanmakuEmissionDirection`；补纯 Kotlin 断言 |
| R14 | `PanelLayerAlpha` 有 3 个独立 owner（可见性控制器 / 抓手条悬停 / 手腕面板用 `Visible`），新增抓取组或主题淡出会与它们打架 | 中 | 统一为单一 alpha/可见性 owner；抓手条悬停改为"请求"而非直接写组件 |
| R15 | `WorkbenchContent` / FOCUS / SHORTS 分支不可达，容易被误当既有能力去"扩展" | 中 | Phase 0 明确标注不可达；若产品需要该分支，作为独立功能任务而非本重构的一部分 |
| R16 | 舞台缩放有"枚举 + 浮点"两份内存表示，摇杆/调试滑杆只写浮点 → 标签漂移 | 低 | 收敛为单一来源（浮点为准，枚举按最近档位派生）；补单测 |
| R17 | 空间布局无设置界面，而曲率的唯一重置按钮位于"坏曲率本身可能让用户进不去的工作台内" | 中 | 主题几何预览/回退必须提供**工作台之外**的逃生通道（对齐既有 `AnalogWorkbenchSummonSystem` 的设计教训） |
| R18 | `NATIVE_PARENT` 的 SDK 传播行为**从未验证**，而它被写成默认机制与"行为不变"的依据 | **高** | Phase 3.0 设为**头显 gate**；未通过则改用 `DELTA_MIRROR`，并撤回"行为不变"表述（§0.5、§4.3） |
| R19 | 面板几何存在**三种类型**，统一 adapter 会误以为能覆盖视频面板全部几何能力 | **高** | §2.8 类型学 + §5.6 按类型分流 + `themeableGeometry` 白名单；类型 C 单独 adapter |
| R20 | 身份层次（panel / slot / component / entity / surface）混用，导致 schema 无法表达既有布局（`CONTEXT` 一槽两实体已是反例） | **高** | §4.5 五个独立 id 空间 + `SlotBindingPolicy` 条件化校验 |
| R21 | 主题"自由重组"与"注册一次"边界未写清，主题作者会按"任意动态建面板"理解 | 中 | §13 显式边界 + 两个主题 fixture 验证 |
| R22 | Phase 2 若作为一个大 patch 落地，视觉/命中回归无法归因 | **高** | §7 Phase 2 拆为 2.1–2.8，逐子步提交；回滚是**按 panel 的集合**而非单一 bool |

---

## 9. 验收标准与测试

### 9.1 纯 Kotlin（无需设备，CI 可跑）

```powershell
.\gradlew.bat :spatial-workbench-core:test
.\gradlew.bat :spatial-workbench-compose:testDebugUnitTest
```

| 测试 | 断言 |
| --- | --- |
| `ThemeJsonParserTest` | 合法样例解析通过；缺 `schemaVersion` 拒绝；字段类型错误报具体 issue |
| `ThemeSchemaMigrationTest` | v1→v2 幂等；未知版本拒绝；迁移后再校验 |
| `ThemeGeometryValidatorTest` | offset ±3.0 边界、rotation ±80/±180、size 0.15–4.0、cylinder 1.5–8.0 |
| `ThemeAnchorValidatorTest` | 缺失 anchor → `MissingSceneAnchor`（注入 catalog） |
| `PanelGraphValidatorTest` | parent 成环拒绝；slot 重复归属拒绝；`FRONT_OF_PARENT` 无 parent 拒绝 |
| `GrabGroupValidatorTest` | **父子组共享成员 → `GrabMemberOverlap`**；handle 自引用拒绝；`exclusiveWith` 非对称拒绝 |
| `DanmakuGroupValidatorTest` | 未知 surfaceId 拒绝；laneCount 越界拒绝；occlusion domain 引用未知组拒绝 |
| `ComponentCompositionResolverTest` | 成员重排改变输出顺序；未知 componentId 拒绝 |
| `PanelPlacementResolverTest` | anchor × offset × rotation 合成结果；parent 链式合成 |
| `SingleVideoSurfaceInvariantTest` | overlay target 永不成为 video target（扩展既有 `MediaStageContractsTest`） |

### 9.2 应用层（构建）

```powershell
.\scripts\build-windows-debug.ps1
```

触发条件：面板注册、场景数据、资源、跨模块改动（本次 Phase 2+ 满足）。

### 9.3 头显真机（标记 [头显]，不得用静态分析代替）

**只有 Phase 0 + Phase 1 可以在无头显条件下"完成验收"。**
Phase 2 起的任何几何、命中、遮挡、抓取结论，在无头显时**只能标为"已接通、未验收"**，
不得计入"已完成"。

| 阶段 | 头显验收项 | 无头显时可达的状态 |
| --- | --- | --- |
| Phase 2.3–2.7 | 每面板 offset/rotation/size/shape 的观感与可读性；reparent 不跳变；曲面贴合；2.7 的 rail 行为变化 | 「代码已接通、未验收」 |
| Phase 3.0 | 抓取时哪些实体真的跟随、`TransformParent` 传播是否成立 | **gate：不通过则 `NATIVE_PARENT` 不可用** |
| Phase 3.5 | 多抓取组并存、单手可达、抓取 vs 点选冲突、MR/VR 双模式、失去跟踪后的恢复 | 「代码已接通、未验收」 |
| Phase 4 | 斜向 surface 首尾遮挡、**头动容差**、组间负载；**不改动视频 Player/Surface** | 「能分配」可 JVM 证；「遮挡正确」**不可** |
| Phase 5 | 动态 Compose 面板触摸响应（R3）、组件重排后的输入所有权 | 「代码已接通、未验收」 |
| 全程 | 单 `PlayerSession` / 单活动视频 Surface 不变量在每次主题切换后仍成立 | 静态可证（代码层面）；运行时行为需验 |

**"能分配"与"遮挡已实现"必须分开计分**（对齐 §4.4）：前者是纯 Kotlin 可证的分配逻辑，
后者依赖用户视点投影，静态测试无法替代头显。

---

## 10. 决策状态

### 10.1 已确认（2026-10-07）

| # | 决策点 | 选择 |
| --- | --- | --- |
| D1 | 场景 anchor 是否新增 | **A) 用 mse-agent 新增语义 anchor，面板迁到场景授权**（Phase 2，需单独批准执行） |
| D2 | 可见性来源是否收敛为单一 | **A) presentation/canvas 唯一来源，`WorkbenchReducer` 只管内容**（Phase 2.7，行为变更） |
| D4 | `DanmakuDepthLayer` 绑定单/多 surface | **A) 改为 `surfaceIds: Set<String>`**；语义为分配域而非复制域（见 §4.4） |
| D5 | 是否允许运行时动态创建面板 | **A) 只允许「注册一次 + reshape」** |
| D6 | 范围 | **Phase 0 + Phase 1 已批准；Phase 2 方向确认，执行需单独批准**（见 §7.1） |

### 10.2 未决策：D3 不能以"合并两个 enum"的形式提问

原 D3（"`SearchWorkspaceRoute` 与 `WorkbenchContent` 是否合并"）把**三个不同维度**塞进了一个问题。
`WorkbenchContent` 在当前 runtime 确实无外部读取者，但这**不自动推出**应该合并或删除 ——
它原本想表达的是"更高层的工作台内容路径"，而 `SearchWorkspaceRoute` 表达"中心面板显示什么"，
`ImmersiveLayoutMode`/`PlaybackCanvas` 表达"布局/呈现模式"。三者是不同维度。

**建议把 D3 拆成三个独立问题**：

| D3.x | 问题 | 建议归属 |
| --- | --- | --- |
| **D3.1** | **中心面板内容路由**（搜索 / 推荐 / 列表 / 空态）由谁拥有？ | `SearchWorkspaceRoute`（维持现状） |
| **D3.2** | **布局 / 呈现模式**（WATCH / SHORTS / FOCUS）由谁拥有？ | layout / canvas 状态（即 `ImmersiveLayoutMode` 一侧）；**不要**再复制一份到内容路由 |
| **D3.3** | `WorkbenchContent` 是否还有**独立语义**？ | 若没有 → 退役；若有 → 重新定义职责，并**不再重复命名** `BROWSE` / `VIDEO_CONTEXT` |

**D3 的阻塞范围**：只阻塞 Phase 2.7（可见性 owner 收敛）的细节。
**不阻塞** Phase 0、Phase 1，也不阻塞 Phase 2.1–2.6（几何适配，与内容路由无关）。

### 10.3 Phase 1 实现后暴露的待决策项

这三项由 Phase 1 的对抗性审查（含把规则删掉看测试是否变红）发现，属于**设计文档自身的缺陷**或**超出 Phase 1 清单**的范围，
不得被当作"已由 validator 保证"。

| # | 待决策 | 性质 | 现状 |
| --- | --- | --- | --- |
| **D7** | **用户调整的持久化范围**：继续全局 prefs，还是改为 per-theme 命名空间？ | **产品决策**（原设计误标为"Phase 1 必须决定"） | ✅ **已决定：per-theme 命名空间**（`theme.<id>.stageScale` 等）。core 未编码该语义（`userAdjustable` 只声明"哪些项可调"，不声明"存哪里"）；实现属 **Phase 2.6/2.7**，须随 prefs 迁移一起做。**不阻塞** Phase 1 已交付内容 |
| **D8** | 抓取组规则 5 与 6 如何处理？<br>· 规则 5 需要一个"允许共享跟随"的成员级字段（原名 `sharedFollow` **不存在**）<br>· 规则 6（`independentlyGrabbable ⇒ 自带 handle`）需要一个 **PRD R5 封闭清单之外**的新 issue code | **设计修正 + 范围决策** | ✅ **已决定：可以，随 Phase 3 一起做**。规则 5 补成员级 `allowSharedFollow: Boolean = false`；规则 6 新增 issue code `GrabMemberWithoutHandle`。两者都是 Phase 3 的前置合同，**不在 Phase 1 重开** |
| **D9** | PRD R5 对 `UnknownOverlaySurface` 的第二子句（"kind 不是 DANMAKU"）与实际不符 | **PRD 措辞修正** | ✅ 已解决：该情形由**既有的** `InvalidLayer(reason="surface-does-not-support-danmaku")` 产出，不是 `UnknownOverlaySurface`。**不改既有行为**（会破坏既有测试）；已改 PRD 措辞 |

### 10.4 实现后确认的两项语义（记录以免回退）

| 项 | 确定语义 | 理由 |
| --- | --- | --- |
| `UnverifiedGrabMechanism` 的适用范围 | **仅 `NATIVE_PARENT`**，且"验证记录缺失"必须报 warning（不得静默通过）；`DELTA_MIRROR` 不报 | `DELTA_MIRROR` 不依赖未经头显验证的 SDK 父子传播；静默通过会违反 A9 |
| `DuplicateSlotOwner` 的判定单位 | **按 panel 计数，不按 binding 条目计数**；同一 panel 重复声明同一 slot 直接报错，且不参与 `MIRRORED` 配对 | 否则一个冗余自声明就能伪造出 `MIRRORED` 所需的"成对"条件（Phase 1 审查已证伪过一个这样的洞） |

---

## 11. 与既有工作的关系（避免重复）

| 既有任务/卡片 | 关系 |
| --- | --- |
| Trellis `09-02-workbench-lift-and-grab` | 本设计的 Phase 3 是其泛化；`GrabPilotSystem` 由它引入 |
| Trellis `09-02-context-rail-right-panel` | 右 rail 内容补齐；与本设计的 `panels.mode_panel` 位置参数相关 |
| Trellis `10-06-workbench-dismiss-scope-fix` | 外部收起命中层不变量（`Visible(false)` 退出命中测试）；Phase 2.7 会改可见性 owner，**必须与该不变量一起验证**，不是"不改语义" |
| kanban `k34` 弹幕多平面 | **与本设计 Phase 4 重叠** → 建议由本任务统一收敛为 schema 驱动 |
| kanban `k12`/`k13`/`k15`/`k16`/`k17` 弹幕批次 | Phase 4 的前置/并行项（数据源、canvas 尺寸、scale 同步） |
| kanban `k18` grab bar 重构 | Phase 3 的默认组 handle 视觉 |
| kanban `k26` 曲率冗余合并 | 并入 Phase 2 |
| kanban `k30` grab 双模式真机验收 | Phase 3 的头显验收项 |
| kanban `k82` 命中层 | 已验证完成；`Visible(false)` 退出命中测试这一事实是本设计 hit-test 字段的依据 |
| `docs/immersive-ui-low-code-architecture.md` Phase 1–6 | 本设计把其中 Phase 1（主题底座）细化为可执行 schema + 分层；Phase 3/5 对应本设计 Phase 4/2 |

---

## 12. 官方文档出处（本次核实）

| 结论 | 出处 |
| --- | --- |
| `Entity.createPanelEntity(regId, Transform)` 运行时创建面板；`entity.destroy()` 销毁；禁用 `finish()` | `documentation/spatial-sdk/spatial-sdk-2dpanel-spawn.md` |
| `UIPanelSettings(shape, display, rendering, style, input)`；`QuadShapeOptions` / `CylinderShapeOptions`；**`settingsCreator = { ent -> … }` 可读实体上的自定义组件决定尺寸** | `documentation/spatial-sdk/spatial-sdk-2dpanel-registration.md` |
| `Grabbable` + `Mesh`/Collider；`IsdkToolkitBridgeSystem` 自动加抓取；`IsdkGrabConstraints`；默认仅一个设备可抓 | `documentation/spatial-sdk/spatial-sdk-isdk-grabbable.md` |
| 面板抓取需 `IsdkGrabbable` **与** `IsdkPanelGrabHandle` 成对；`IsdkCurvedPanel`；`IsdkPanelDimensions` + `Transform` 必需 | `documentation/spatial-sdk/spatial-sdk-isdk-panels.md` |
| `IsdkPanelResize`（`ResizeMode.Simple/Relayout/None`、`min/maxDimensions`、`preserveAspectRatio`、自动加 `IsdkPanelGrabHandle`、需注册 `VRFeature`） | `documentation/spatial-sdk/spatial-sdk-resize-panel.md` |
| `childrenOf` 是**实验性** API，需 `registerLinkedEntityAttribute` | `documentation/spatial-sdk/spatial-sdk-childrenof-query.md` |
| 已知问题：动态创建的 Compose 面板可能失去触摸响应；`PanelRenderMode.Mesh()` 会错误创建 compositor layer | `documentation/spatial-sdk/spatial-sdk-known-issues.md` |
| `Visible(isVisible)` 控制渲染 | `documentation/spatial-sdk/spatial-sdk-builtin-components.md` |

```powershell
# 复现本文的文档核实
npx -y metavr docs fetch "documentation/spatial-sdk/spatial-sdk-2dpanel-registration.md"
npx -y metavr docs fetch "documentation/spatial-sdk/spatial-sdk-isdk-grabbable.md"
npx -y metavr docs fetch "documentation/spatial-sdk/spatial-sdk-isdk-panels.md"
npx -y metavr docs fetch "documentation/spatial-sdk/spatial-sdk-resize-panel.md"
npx -y metavr docs fetch "documentation/spatial-sdk/spatial-sdk-known-issues.md"
```

---

## 13. 二开能力边界（"自由重组"到底自由到什么程度）

"主题可以自由重组 UI"与"面板只注册一次"（D5=A）之间存在张力。必须把二开的实际含义写死，
否则主题作者会按"任意动态新增空间面板"去理解，而实现给不出这个能力。

### 13.1 允许

| 能力 | 说明 |
| --- | --- |
| 在**已注册 panel 内**切换 / 增删 / 重排**受控组件** | `ComponentGroupSpec` 的成员重排与 slot 归属变更；组件来自 `ThemeComponentRegistry` 白名单 |
| 按主题**启动时注册一次** panel 集合 | 不同主题可以有不同数量的 panel，但都在启动期注册完毕 |
| 重排 slot → panel 绑定 | 一个语义 slot 改绑到另一个已注册 panel；允许 `MIRRORED` 多绑（见 §4.5） |
| 配置每 panel 的几何参数 | 受 §2.8 类型学与 §5.6 `themeableGeometry` 白名单限制 |
| 声明抓取组 / 弹幕组 | 独立于 UI 组合 |
| 调用白名单 action | 复用既有唯一实现（§5.4） |

### 13.2 不允许（硬边界）

| 禁止 | 原因 |
| --- | --- |
| **运行时动态创建 / 销毁 Spatial panel** 以实现"任意布局" | Meta 已知问题：动态创建的 Compose 面板可能失去触摸响应（R3） |
| 在主题运行期间改变 panel 数量或注册表 | 与"注册一次"冲突，且触碰输入所有权 |
| 引用未注册的组件实现 | 受控目录之外无实现可解析 |
| 执行任意代码 / 网络下载主题 | §5.4 硬边界 |
| 创建 / 持有 Player、Surface、Activity、Spatial Entity | 单 Surface / 单播放器不变量 |

### 13.3 "自由重组"的准确表述

> 主题可以**在已注册的空间面板集合内**，自由选择、组合、重排受控 UI 组件，
> 自由配置每个面板的几何参数与 slot 归属，并独立声明抓取组与弹幕组。
> 主题**不能**在运行时新增或销毁空间面板，也不能执行任意代码。

### 13.4 证明该边界"够用"的最低要求

至少用**两个不同主题 fixture**（Cinema 与 Cockpit）证明：
1. 同一批**业务 action** 在两个主题中复用同一实现（不重复实现点赞/搜索/播放/质量）；
2. 组件的**组合与顺序**在两个主题中显著不同；
3. 两者的 panel 集合与几何配置由同一 schema 表达。

**只做一个主题不足以证明组件组合不是纸面 schema。** 这是 Phase 5 的验收前提。
