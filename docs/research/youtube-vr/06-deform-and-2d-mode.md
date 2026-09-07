# 06 — 屏幕变形架构通用化 & “2D 模式”设计 逆向笔记

样本：`libyoutubevrjni.so`（arm64，IDA 13337）。回答两个问题。

## 一、弹幕 canvas 能否复用这套幕布变形？—— 能，而且本来就该这么设计

逆向证据：lullaby 把「屏幕形状」做成**挂在实体上的通用变形机制**，不是视频专属：
- `lull::DeformSystem`（`third_party/lullaby/lullaby/systems/deform/deform_system.cc`）：
  `ApplyDeform(Entity, const Deformer*)` / `SetDeformationFunction(Entity)`；
  组件 `DeformerDef`（变形器）+ `DeformedDef`（被变形实体）。任何带 mesh 的实体挂了 Deformer 就会被变形。
  日志里还有 `A deformed entity ... has non deformed parent. It will not deform.` —— **变形挂在 mesh 实体（或其变形父节点）上**。
- 视频屏形状是**可替换的投影 mesh**：
  - `VideoManager_nativeSetProjectionMesh(..., eye)`：建一个 80 字节投影对象（meshType `<3` = 左/右眼/单目）赋给视频实体；
  - `VideoManager_nativeSetEquirectProjectionMesh(..., eye)`：equirectangular（360°）投影；
  - 2D 曲面屏 = 柱面 quad mesh；2D 平面屏 = 平面 mesh；360/180 = equirect/VR180 mesh。**同一视频实体，只换 mesh。**

### 对 ViriViri 的落地结论
弹幕要随屏变形，**不需要自己再算一套弯曲**，关键是「让弹幕和视频走同一层变形」：
1. **首选——弹幕渲染进视频同一个 SurfaceTexture/纹理层**：视频 + 弹幕都画到同一张纹理，这张纹理贴到曲面 mesh 上，弹幕天然跟随弯曲（YouTube 的 imp 面板就是「View → SurfaceTexture → 任意 mesh」模型，见 03 笔记）。
   → 在我们架构里：弹幕 overlay 若是一个 Spatial `Panel`（Compose 弹幕层），就把它放在与视频**同一曲面 mesh/同一 QuadEntity**上，而不是一个独立平面 Panel。
2. **次选——两套实体共用同一 Deformer/同一顶点变换**：若弹幕是独立 mesh/独立纹理，给它挂**同一个柱面变形器、相同半径/张角参数**（参数集中在 `WorkbenchLayoutConfig`），并保证两者宽高比/UV 对齐，否则曲面边缘弹幕会错位。
3. 抽象建议：做一个 `CurvedMeshBuilder(rows, cols, radius, arcAngle)`（柱面投影公式见 01 笔记），视频 quad 和弹幕层都引用它产出的 mesh；曲率只有一个数据源，视频/弹幕/将来的字幕永远同步变形。
- 反例（要避免）：弹幕用独立平面 Panel 浮在曲面视频前方——边缘会穿帮、不贴合。

## 二、YouTube VR 的「2D 模式」设计

> **更正**：经 dex/Manifest 逆向确认，YouTube VR **确实有 2D（非沉浸）模式**。本节为初步判断，
> 完整结论见 **`07-hybrid-panel-mode.md`**（hybrid app：`YouTubeVrPanel2Activity` 2D 面板 vs `YouTubeVrActivity` 沉浸，
> 由 AndroidXR **SplitEngine** + `ImmersiveVideoFsm` 驱动）。

### （初步判断，保留以备对照）

**YouTube VR 没有手机/平面 2D 模式，它永远是 VR 3D 渲染。** 逆向里没有任何「把整个 UI 切成 2D 平板布局」的开关。
所谓「2D」在它内部是三件正交的事：
1. **视频投影类型**（投影 mesh，见上）：普通 2D 视频 = 平面/柱面 quad mesh；360 = equirect；VR180 = VR180 mesh。由 `nativeSetProjectionMesh / nativeSetEquirectProjectionMesh` 切换。
2. **环境/场景（EnvironmentManager）**：`nativeUpdateEnvironmentDecor`（影院房间 vs 虚空）、`nativeSetPassthroughAppearance` / `nativeIsPassthroughAvailable`（MR 透视）、`nativeUpdateEnvironmentColor`、`nativeSetBackgroundColor`。
  还有 `theater-group` / `theater-gimbal` / `GazingAtTheaterGroup(Start|End)Event`（影院模式：注视定位屏）。
3. **浏览 UI 形态**：常规 curved 浏览面板 vs **Reels 竖屏短视频**（`AppUiManager_nativeNotifyReelsStarted/Stopped`、`reels_watch_ui should only be used with WatchUis`、`ReelVisibilityDef`、`ReelPlayerControlsOverlay`）。
  Reels 是「短视频 WatchUi」变体，不是 2D 模式。
  「switch mode 按钮」（`SwitchModeButtonTooltipManager` / `hideSwitchModeButtonTooltips`）切的是环境/视角相关的观看模式，UI 始终是空间面板。

### 对 ViriViri 的含义
- 我们的 **2D PancakeActivity（手机平面模式）和 VR 沉浸模式是两套渲染宿主、共用一个播放会话**——这点和 YouTube VR 不同（它只有 VR）。
  可借鉴的是它**把「屏幕形状」和「环境/投影类型」解耦**：Pancake 模式 = 平面投影 mesh（曲率=0）+ 无环境；沉浸模式 = 柱面/曲面 mesh + 环境。
  播放器/弹幕逻辑不关心模式，只往「当前投影 mesh」上贴；切模式 = 换 mesh + 换环境宿主。
- 即：把曲率半径做成参数（半径∞ → 平面），Pancake 与 VR 复用同一套视频/弹幕布局，只差 mesh 与宿主。

## 合规：仅记录架构思路与参数维度，独立实现。
