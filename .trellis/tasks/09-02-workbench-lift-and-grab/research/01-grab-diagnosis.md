# 诊断记录：现有 Grab 为何不可见（2026-09-02）

任务：09-02-workbench-lift-and-grab
来源：SpatialVideoSampleActivity.kt + SDK 0.13.2 反编译 + Meta 官方文档

## 现象

用户反馈：grab 不可用，看不到 grab handle；幕布+workbench 整体位置偏矮，部分沉到基准地面下。

## 代码现状（三处叠加）

1. `createVideoPanel()` L834-840（ISDK 新式，仅属性声明）:
   ```kotlin
   videoPanelEntity.setComponent(IsdkPanelDimensions())
   videoPanelEntity.setComponent(IsdkPanelGrabHandle())
   videoPanelEntity.setComponent(IsdkGrabbable())
   panelSceneObject.updateIsdkComponentProperties(videoPanelEntity)
   ```

2. `onVRReady()` L550-557（toolkit 旧式）:
   ```kotlin
   Entity(R.id.spatialized_video_panel).setComponents(
     Grabbable(type = GrabbableType.PIVOT_Y, minHeight = 0.75f, maxHeight = 2.5f),
     Transform(Pose(Vector3(0f, 1.25f, 2f), Quaternion(0f,0f,0f))),
     ...
   )
   ```

3. `setMrMode()` L1937-1939：只开关 toolkit `Grabbable.enabled = isMrMode`，
   没管 ISDK 组件。

## 根因分析

- **双系统并存**：toolkit `Grabbable`（PIVOT_Y 旧语义）与 ISDK `IsdkGrabbable` +
  `IsdkPanelGrabHandle` 同时存在。SDK 文档（spatial-sdk-isdk-grabbable.md）明确：
  "Replace the Grabbable component with an IsdkGrabbable component"。并存时 ISDK
  bridge 与 toolkit 系统互相覆盖 → handle 渲染不确定。
- **MR 门控**：toolkit grab 仅在 MR 模式 enabled；VR 全沉浸（MediaRoom）下禁用。
  ISDK 组件无对应开关。用户多数在 VR 模式测 → 看不到。
- **非标准 Panel 实体**：`spatialized_video_panel` 是代码手动构造的 PanelSceneObject
  （ids.xml 占位 id，无场景实体、无 Panel 组件）。ISDK `ensurePanelComponents`
  自动补 handle 主要面向标准 Panel 实体，手动构造面板的 handle 生成不可靠。
- **点击冲突隐患**（ISDK overview Known limitations）：实体带 Grabbable/IsdkGrabbable
  后不再收 onClick（除非有 IsdkPanelDimensions）。当前视频面板 onClick 用于
  "点幕布 reveal transport"（L800-806）——需验证是否已被破坏。

## 官方设计指引（Horizon OS window manipulation / hands-ui-best-practices）

- 窗口级移动/重定位/吸附用 grab；实际多以 raycast 触发。
- **从面板边缘 grab**，用清晰可见的 edge handle 告知可抓区。
- grab collider 与最近可交互元素之间留足空间，避免 grab 被识别为按钮 poke。
- 避免低头摆放（头倾 >±15° 疲劳）——与"整体偏高一点"诉求一致。

## 用户约束（需求，非诊断）

- 高度手动调整 → **持久化**（SharedPreferences，参考 playbackStageScale）。
- 水平位置与偏角 → **每次启动重置为初始值**（不持久化）。
- grab 形态：参考 Horizon OS 窗口——专门的高可读性 handle（底部/边缘），
  点内容区仍是操作（reveal transport 等），不冲突。
- 整体搬移：grab 幕布 → 整个 workbench（transport/nav/中心/键盘/弹幕 +
  左右 rail）一起跟随。

## 相关代码位置

- Stage 世界 pose 初始：`onVRReady()` L549-616（幕布 y=1.25, z=2; rail y=1.25）
- rail 世界坐标：`WorkbenchLayoutConfig.kt`（railCenterWorldY=1.25, railWorldZ 由弧推导）
- 摇杆缩放：`AnalogMediaStageScaleSystem.kt`（只改 Scale，不搬位置）
- stage scale 持久化：`playbackStageScale`（SharedPreferences, 0bce696/0cb06c2）
- 文档：docs/research/troubleshoot-z-offset-prompt.md（之前 z 偏移排查）
