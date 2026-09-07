# Workbench 整体抬升与整体 Grab 搬移

## 目标

1. **修好 grab 基础设施**：清理 toolkit/ISDK 双 grab 系统冲突，让 video stage 的
   grab handle 可见、可用，交互参考 Horizon OS 窗口（清晰边缘/底部 handle，点内容区仍可操作）。
2. **整体搬移**：grab video stage → 整个 workbench（transport/nav/中心/键盘/弹幕 +
   左右 rail）一起跟随移动。
3. **修正沉地**：幕布+workbench 初始高度不再沉到基准地面下方，整体偏高到舒适高度。
4. **高度持久化，位置/偏角启动重置**：手动调整的高度（Y）持久化；水平位置（X/Z）与
   偏角（yaw）每次启动恢复初始值。

## 背景与诊断

详见 `research/01-grab-diagnosis.md`。要点：

- 现状 video stage 同时挂了 toolkit `Grabbable(PIVOT_Y, 0.75~2.5m)` 与 ISDK
  `IsdkGrabbable` + `IsdkPanelGrabHandle`，双系统冲突 → handle 不可见/行为不确定。
- toolkit grab 仅 MR 模式 enabled（`setMrMode` L1937-1939）；VR 全沉浸下不可用。
- `spatialized_video_panel` 是代码手动构造的 PanelSceneObject（无场景实体/Panel 组件），
  ISDK 自动 handle 生成对非标准面板不可靠。
- SDK 文档 Known limitations：有 Grabbable 的实体不再收 onClick（除非有
  IsdkPanelDimensions）——当前"点幕布 reveal transport"依赖 onClick（L800-806），需验证/规避。
- 官方窗口 grab 指引：从面板边缘 grab、用清晰可见的 edge handle、grab collider 与
  可交互元素间留空间（避免 grab 误触 poke）、避免低头摆放（头倾 ≤±15°）。

## 用户约束（数据分离）

- **Y 高度**：手动调整后**持久化**（SharedPreferences，如 `workbenchStageHeightY`），
  启动时读取并应用。
- **X/Z 位置与 yaw 偏角**：**每次启动重置为初始值**（0, 1.25(+持久化Y 偏移), 2 等），
  不持久化。避免用户把幕布拖到奇怪位置后无法找回。

## 待办

### A. Grab 基础设施修正
1. 确定单一 grab 系统（倾向 ISDK：`IsdkGrabbable` + `IsdkPanelGrabHandle` +
   可选 `IsdkGrabConstraints`），移除/禁用 toolkit `Grabbable`（或验证共存可行后保留其一）。
2. 统一 MR/VR 开关：grab 在两个模式下的启用状态一致（或按需区分），不再只 MR。
3. 验证手动 PanelSceneObject 上 handle 能渲染；若不能，补必要组件/注册，或改用
   场景 panel + grab 区域方案。

### B. Grab handle 视觉与命中（参考 Horizon OS 窗口）
4. 提供清晰可读的 grab handle（面板底部/边缘区），raycast 可抓。
5. handle 区域与内容点击区分离：grab handle 只负责搬移；点视频内容仍 reveal
   transport / 交互（验证 onClick 未因 Grabbable 丢失；必要时改用 onInput 或
   IsdkPanelDimensions 修复点击）。

### C. 整体搬移
6. 让左右 rail（video_selector_panel / mode_panel）跟随 video stage 移动：
   方案 a) 运行时 reparent 到 video panel（TransformParent 保留世界 pose，已验证可用）；
   或方案 b) 监听 stage 移动事件联动 rail transform。倾向 a，保持 rails 各自
   Grabbable 或移除其独立搬移。
7. transport/nav/中心/键盘/弹幕已是 TransformParent 子级，跟随自动生效；验证
   centerContent 的局部 pose（bindCenterContentPanel L490-492）在 stage 移动后仍正确。

### D. 高度修正与持久化
8. 抬升初始高度：确认"沉地"根因（初始 y=1.25 vs LOCAL_FLOOR/MR 原点），修正默认
   高度使幕布+workbench 整体不沉地、不低头（头倾 ≤15°）。
9. 高度调整持久化：grab 结束后把最终 Y 存入 SharedPreferences；启动时
   `初始Y = 默认Y + 持久化偏移`（或直接存绝对 Y）。
10. 位置/偏角重置：启动时 X/Z/yaw 用初始值，不从持久化读。

## 实施记录（2026-09-02）

### 教训 1：reparent 必须用 stage-LOCAL 值（首版失败 → 已修正）
首版把左右 rail 设 `TransformParent(stage)` 但 Transform 仍用**世界系** authored 值
（railWorldZ≈0.95、y=1.25），reparent 后 SDK 当 local 折算 → 位置漂移。已回退。
**2026-09-02 修正**：SDK 文档确认 TransformParent 的子实体 Transform 是相对父的
local。正确做法：rails 的 local = `(∓railX, 0, railLocalZ)`，其中
`railLocalZ = railWorldZ - STAGE_WORLD_Z`（rails 在 stage 前方，stage 本地 -Z 朝用户）；
**local y=0** → rails 自动跟随 stage 高度（stage 抬起 rails 跟着抬），无需再手动联动。
已按此重新 reparent（WorkbenchLayoutConfig 加 railLocalZ；onVRReady rails 用
stage-local + TransformParent；applyStageWorldY 只动 stage，rails 随子级移动）。

### 教训 2：rails 世界 Y 需与 stage 高度联动 —— 已由 local y=0 子级化解决
不再需要手动同步 railCenterWorldY；reparent + local y=0 后 rails 天然跟随 stage。

### 现状（保留部分）
- stage 移除 toolkit `Grabbable(PIVOT_Y)`，统一 ISDK grab（IsdkGrabbable+Handle）。
- stage 默认 Y = `loadWorkbenchStageYOrDefault()`（持久化优先，默认 **1.5** 抬升后）。
- `VideoStageGrabPersistenceSystem`：grab 释放边沿持久化 Y。
- AppPreferences 增 workbench_stage_y 读写 + clamp(0.6..3.0) + 单测。
- Rails 已 stage-local 子级化（跟随 stage 高度），railCenterWorldY 同步 STAGE_DEFAULT_WORLD_Y。
- Reset Y 按钮：清持久化 + stage 回默认高度（右 rail debug 区）。
- Debug 区新增 Stage Y 实时读数。
- 坐标系文档：docs/spatial-coordinates.md（+ 架构文档交叉引用）。

## 非目标

- 不做面板各自的自由摆放（只整体搬移 + 高度调节）。
- 不引入新的播放器/Surface。
- 不改 2D PancakeActivity。
- 不动弹幕/键盘内容逻辑（只保证跟随）。

## 架构决策（2026-09-02，用户定）

### 通用 grab 驱动 + 可配置绑定
- 一套通用 grab system（`GrabPilotSystem`），导出 grab 生命周期事件
  （started / moved / ended），**任何可抓 handle 实体都可绑定**。
- 演进方向：handle → **多 follower**。一个 handle（bar）的 grab 位移镜像到一组
  follower 目标，每个目标可自定义跟随策略；导出事件供任意元素绑定。
- **主题化绑定**：
  - 当前（默认）主题：bar handle → all 绑定（stage + 全部子面板/rails 为一组）。
  - 未来其他主题：可将 grab 绑到面板/幕布的**分组**，每组指定自己的 handle/bar；
    也可不给某面板绑 grab（如配合 spatial 形成挂钟/固定效果）。
- 实现形态：`GrabPilotSystem(handle, targets[], restPoseProvider, 事件...)`，
  一个实例 = 一个 handle 的驱动；多 handle 多实例。

### 官方 API 调研（2026-09-03，verify-first 确认）
SDK 0.13.2 提供**事件驱动**的抓取/hover API（不必手写轮询）：
- `IsdkInputListenerSystem.setInputListener(InputListener)` /
  `setInputListenerForEntity(entity, listener)`：对实体注册监听。
- `InputListener.onPointerEvent(SceneObject, HitInfo, type, Entity, Vector2, semanticType)`：
  语义化事件。`type ∈ PointerEventType{Hover,Unhover,Select,Unselect,Move,Cancel}`，
  `semanticType ∈ SemanticType{Unknown,None,Select,Grab,Scroll}`。
- 官方样例 **Object3DSampleIsdk**
  （github meta-quest/Meta-Spatial-SDK-Samples）示范：
  `if (semanticType != SemanticType.Grab.id) return; when(type){ Select→grab started, Unselect→grab ended }`，
  hover 用 Hover/Unhover + 缩放（"scale slightly on hover"）。
- 文档要点："Events are emitted after all grabbed entities have been moved"（可在事件里
  读实体最新位姿）；"Grabbable entities don't receive onClick for trigger"（video stage
  的 onClick reveal transport 需留意与 grab 共存）。
- **决策**：grab bar 的 hover/grab 事件改用官方 `onPointerEvent`（替代手写轮询
  GrabBarHoverSystem/GrabPilotSystem 的轮询部分），镜像移动逻辑保留（官方无"抓A动B"先例，
  属本项目扩展）。

## 验收

- 启动后幕布+workbench 整体位于舒适高度，不沉地；头倾 ≤15°。
- grab handle 可见可用（MR 与 VR 模式一致），参考 Horizon OS 窗口形态。
- grab 幕布 → transport/nav/中心/键盘/弹幕 + 左右 rail 整体跟随，无错位撕裂。
- 点视频内容区仍 reveal transport（onClick 未破坏）。
- 手动调整高度后重启：高度保持；水平位置与偏角回到初始值。
- 单测：高度/位置状态 reducer 逻辑（如适用）；`scripts/build-windows-debug.ps1` 通过。
- Quest 实测：MR/VR 两模式 grab、跟随、持久化、重置。

## 参考

- `research/01-grab-diagnosis.md`
- SDK 文档：spatial-sdk-isdk-grabbable.md、spatial-sdk-isdk-overview.md、
  hands-ui-best-practices.md（Window manipulation 段）
- 代码：SpatialVideoSampleActivity.kt（L483-631 布置 / L800-849 面板 / L1935-1959 MR）、
  WorkbenchLayoutConfig.kt、AnalogMediaStageScaleSystem.kt
- 既有持久化范例：`playbackStageScale`（SharedPreferences）
