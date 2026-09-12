# 排查 prompt：幕布（stage）启动时不加载持久化 scale

> 对应看板卡片 **k17**「幕布 scale 启动不同步（弹幕2/幕布1，需几何烘焙 scale+接曲面）」。
> 目标：定位为什么 **只有启动时** 幕布不应用持久化 scale，并给出最小修复方向。

## 现象

- 在某个 scale 下退出 app，重启后：**弹幕层 / stage_backdrop 正确加载了持久化 scale**，
  但**幕布（`spatialized_video_panel`）仍是 scale=1**（视觉尺寸不对，与弹幕层不匹配）。
- 触发**任意一次**缩放操作（摇杆 ±、canvas size 预设、debug 面板 scale 滑杆）后，
  弹幕和幕布**立刻同步**到新值（例如两者都变）。
- 即：**Scale 组件对幕布是生效的，只是"启动那一刻的持久化值"没有被应用**。

## 已确认的事实（别重复验证）

代码位置：`app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt`

1. **持久化读取正常**：`ViriViriAppState` 初始化时
   `playbackStageScale = appPreferences.loadPlaybackStageScale()`
   （`AppPreferences.kt`，clamp 范围见 `PlaybackCanvasSize`：0.70 ~ 1.50）。
2. **缩放应用链是 `applyPlaybackStageScale()`（L1720）**：给
   `spatialized_video_panel`、`danmakuOverlayEntity`、`stageBackdropEntity` 各
   `setComponent(Scale(normalizedScale))`；函数开头有 guard
   `if (appliedStageScale == normalizedScale) return`。
3. **弹幕 / backdrop 是正确的**：它们在 `onVRReady` 内创建，创建时读
   `appliedStageScale` 设 Scale（`createDanmakuOverlayPanel` / `createStageBackdropPanel`）。
4. **已尝试且无效的修复**：
   - a) `onVRReady` 里 stage 创建时带上 `Scale(appliedStageScale!!)`（L686 附近）。
   - b) `createVideoPanel()` 末尾（scene object 就绪后）**绕过 guard 直接**
     `videoPanelEntity.setComponent(Scale(appliedStageScale ?: 1f))`（L1001）。
   - c) debug 面板把 scale 读数/滑杆同步到 state（只改显示，不涉及求解）。
   两处写入都无效 → **不是"state 没读到"、也不是"guard 挡住了"**。
5. 幕布是**手动构造的 PanelSceneObject**（`createVideoPanel()` L781）：
   `MediaPanelSettings` + `sceneMeshCreator`（L796）用 `TriangleMesh` 手写顶点，
   顶点尺寸取自常量 `MR_SCREEN_WIDTH/HEIGHT`（L2169，= 1.6×0.9m，**世界尺寸，未乘 scale**）。
   它与 `danmaku/stage_backdrop` 的创建路径不同（后者走 `Entity.createPanelEntity` +
   标准 Panel）。
6. **层级已变更（重要，别按旧结构假设）**：抓取载体替换后结构是
   `workbench_root（锚：IsdkBoxCollider + IsdkGrabbable）` →
   `spatialized_video_panel（stage，TransformParent 子级，local (0,+offset,0)）` →
   rails / transport / nav / center / keyboard / danmaku / backdrop。
   stage 的 Scale 组件设在 stage 自身（父锚无 Scale）。
7. **无引用但已就位**：`CurvedStageMesh.kt`（`StageGeometry.Flat/Cylinder` +
   `CurvedStageMeshBuilder.build(width, height, geometry)`）**尚未接入**任何调用点。

## 重点怀疑方向（按优先级）

1. **手动 PanelSceneObject 忽略实体 Scale**：SDK 可能在创建/首帧渲染手动
   `PanelSceneObject` 时重置或忽略实体 `Scale`；标准 Panel（弹幕/backdrop）则正常。
   → 验证：打印 stage 实体的 Scale 组件值 + panel shape/PanelDimensions，比较弹幕 overlay。
2. **视觉尺寸其实由 mesh 顶点决定，与实体 Scale 无关**：
   `sceneMeshCreator` 顶点用 `MR_SCREEN_*` 写死世界尺寸，若渲染以顶点为准，实体 Scale
   只影响子级/命中而不缩放画面。这是"几何烘焙"方向的直接依据。
   → 验证：临时把顶点尺寸乘一个系数，看幕布是否随之变大。
3. **写入时序**：`onVRReady` 的 Scale 写入发生在 `PanelSceneObject` 创建之前/之中，
   可能被 native 初始化覆盖（L1001 的二次写入若在覆盖之前同样无效）。
   → 验证：延迟 1~2s 后再设一次 Scale；若延迟写入生效 ⇒ 时序问题。
4. **reshape 覆盖**：`reshapeSpatialVideoPanel()`（L1898）在 aspect 变化时 reshape panel
   （改 shape 尺寸），可能顺带重置 scale 相关状态。
   → 验证：确认 reshape 前后 stage 的 Scale 组件值。
5. **层级影响**：stage 现在是锚的 `TransformParent` 子级（父无 Scale）。确认 SDK 对
   "子级自身 Scale" 的处理不受该层级影响（SDK 已知：Scale 不向 TransformParent 子级传播）。

## 建议排查步骤

1. **先量化"幕布视觉尺寸由谁决定"**：在 `createVideoPanel()` 末尾 + 首帧后，打印
   stage 的 `tryGetComponent<Scale>()`、panel shape / `PanelDimensions`、
   mesh 顶点包围盒，和 `danmaku_overlay_panel` 的同项做对照。
2. **用两个最小实验区分"时序"与"机制"**：
   - a) `canvasHandler.postDelayed({ stage.setComponent(Scale(1.5f)) }, 2000)`：
     生效 ⇒ 时序问题；仍无效 ⇒ Scale 对手动 PanelSceneObject 不生效。
   - b) 直接把 `sceneMeshCreator` 顶点尺寸乘 scale（几何烘焙）：幕布变大 ⇒ 几何路径可行，
     可作为修复方案。
3. **确认没有第三方写 stage 的 Scale/Transform/PanelDimensions**：全仓 grep
   `spatialized_video_panel` 的 `setComponent(Scale` / `PanelDimensions` / `reshape`，
   排除 reset（`applyStageWorldY`）、MR 切换（`setMrMode`）、aspect probe 的干扰。
4. **输出结论**：给出具体 `文件:行号` + 机制结论（Scale 是否对手动 PanelSceneObject 生效）
   + 最小修复方案；不要顺手回退抓取/淡入淡出等已完成功能。

## 修复方向（倾向，待排查确认）

- 若确认 **Scale 对手动 PanelSceneObject 不生效**：放弃用实体 Scale 缩放幕布，改为
  **把 scale 烘焙进几何** —— 即让 `CurvedStageMeshBuilder.build(widthMeters × scale,
  heightMeters × scale, geometry)` 生成顶点，`sceneMeshCreator` 与
  `updateSpatialVideoContentQuad`/`reshapeSpatialVideoPanel` 共用同一尺寸来源。
  这正好与 **k10 曲面幕布**（`CurvedStageMesh.kt` 已就位未接入）合并实施：
  平面用 `StageGeometry.Flat`，曲面用 `Cylinder(radius)`。
- 弹幕 / backdrop 继续用实体 Scale（它们已生效）；但需同步改成同一套尺寸来源，
  避免两者再次分裂。
- 旧注释已预示该路径：*"a future curved canvas replaces only this presentation adapter"*。

## 边界

- 优先**只读排查**；需要改代码时最小范围，改完跑
  `.\scripts\build-windows-debug.ps1`（含单测）。**不要自动安装/启动 APK**（除非用户要求）。
- 静态场景（`app/scenes/*.metaspatial`、Composition.glxf）不要手改，走 mse-agent/导出。
- 不要回退已完成的：抓取载体替换（整条可抓）、grab bar 淡入淡出、坐标系文档结论。

## 相关文件

- `app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt`
  （`createVideoPanel` L781 / `sceneMeshCreator` L796 / `applyPlaybackStageScale` L1720 /
  `reshapeSpatialVideoPanel` L1898 / `updateSpatialVideoContentQuad` L1934 /
  `MR_SCREEN_WIDTH` L2169）
- `app/src/main/java/com/m0e_n00b/viriviri/PlaybackCanvasSize.kt`（clamp 0.70~1.50）
- `app/src/main/java/com/m0e_n00b/viriviri/AppPreferences.kt`（scale 持久化）
- `app/src/main/java/com/m0e_n00b/viriviri/CurvedStageMesh.kt`（未接入，几何生成器）
- `app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoContentQuad.kt`（内容 contain 计算）
- `docs/spatial-coordinates.md`（坐标系约定）
