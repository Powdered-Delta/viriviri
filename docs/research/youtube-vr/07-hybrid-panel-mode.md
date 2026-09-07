# 07 — 非沉浸（2D Panel）模式设计 逆向笔记

证据来源：APK 的 Manifest（二进制 AXML）+ classes2/3.dex 字符串、proto 事件类名、Activity 类名。
**结论：YouTube VR 是一个 hybrid VR app，同时提供 2D 面板模式（非沉浸）与沉浸 VR 模式。**

## 1. 两个 Activity / 两种模式

| Activity | 模式 | intent category |
| --- | --- | --- |
| `activities.YouTubeVrPanel2Activity`（+ `InternalYouTubeVrPanel2Activity`） | **2D 面板模式**（系统主界面/家庭环境的平板窗口里跑标准 Android UI） | `com.oculus.intent.category.2D` |
| `activities.YouTubeVrActivity`（+ `InternalYouTubeVrActivity`） | **沉浸 VR 模式**（全屏 VR，lullaby 渲染） | `com.oculus.intent.category.VR`、`android.software.vr.mode` |

- Manifest 标记：`oculus.software.vr.app.hybrid`（hybrid VR app）、`uses-horizonos-sdk`、`uses-feature android.hardware.vr.headtracking/high_performance`。
- 日志证据：
  - `Panel2Activity::onCreate() - Category: ... / No Categories / Is Default Launcher Intent: ...`（2D 窗口里能以默认 launcher intent 启动）。
  - `Already in immersive mode. No action is taken.` / `Already in panel mode. No action is taken.`（两态互斥切换）。
  - `Preferred mode loaded late: Immersive. Switching.` / `Preferred mode not ready, falling back to Panel mode.` / `Restoring preferred mode: Immersive`。
  - `Missing hybrid app system feature. Aborting launch of immersive mode.` / `... Aborting launch of panel mode in home.`（系统不支持 hybrid 时回退到单一模式）。
  - 环境主题 proto：`ENVIRONMENT_THEME_PASSTHROUGH/DARK/LIGHT/AUTO/...`（沉浸模式里 MR 透视/深色环境是环境主题，不是 2D 模式）。

## 2. 切换机制：AndroidXR SplitEngine + 沉浸视频 FSM

两模式共享播放、由一套引擎驱动切换：
- `com.google.ar.imp.view.splitengine.ImpSplitEngineRenderer` / `ImpSplitEngine$SplitEngineSetupParams`（imp 层）。
- `com.google.androidxr.splitengine.SplitEngineSubspaceManager`（**AndroidXR Split Engine**，与 Meta Spatial SDK 同类技术：同一 app 可在「家庭环境 2D 窗口」与「沉浸 subspace」间拆分/迁移 Activity/内容）。
- proto 事件驱动：
  - `impress/splitengine/events/ImmersiveVideoFsmTransitionEvent`（2D↔沉浸的有限状态机迁移）。
  - `AndroidSurfaceReadyEvent`、`ProjectionMeshUpdatedEvent`、`ToggleCurvedQuadEvent`、`ShutterUpdateEvent`、`VideoBufferingEvent`、`VideoPlaybackErrorEvent`、`ContentSecurityLevelUpdateEvent`、`CompositeSourceVideoOverlay`、`ClickEvent`。
  - `fsm_*_container`：两模式复用同一套 UI 容器（player_controls / player_overlay / subtitles / watch_next / video_settings / popup_menu / skip_ad / engagement_panel / dialog_controller / environments_menu / nerd_stats / autonav / ppp / snackbar / live_stream_offline_slate / video_not_playable）。
  - 退出沉浸：`EXIT_FROM_VR_REQUEST_CODE` / `exitFromVr` / `com.google.android.apps.vr.inputmethod...action.EXIT_FROM_VR`。

**含义**：2D 面板模式里跑的是**普通 Android UI**（标准 View/Compose 浏览界面），点某个视频后经 FSM 迁移到沉浸模式，
同一播放器/同一套容器随 SplitEngine 迁移到 VR subspace；返回（exitFromVr）回到 2D 窗口。`ToggleCurvedQuadEvent`/`ProjectionMeshUpdatedEvent`
说明曲面开关、投影 mesh 更新在两模式间也是事件化、可平滑过渡的。

## 3. 与 ViriViri 架构的对照（高度一致）

| YouTube VR (hybrid) | ViriViri |
| --- | --- |
| YouTubeVrPanel2Activity（2D 窗口，标准 Android UI） | **PancakeActivity**（2D） |
| YouTubeVrActivity（沉浸 VR，lullaby 渲染） | **沉浸 Activity / SpatialVideoSampleActivity**（VR） |
| AndroidXR SplitEngine / ImpSplitEngine + SubspaceManager | Meta Spatial SDK Panel / subspace（同类技术） |
| ImmersiveVideoFsmTransitionEvent（2D↔VR FSM） | 单一播放会话 + 模式切换（我们的 ImmersiveBrowseSession） |
| fsm_*_container 两模式复用 UI 容器 | 搜索/播放/弹幕等纯逻辑 reducer 两模式复用 |
| ToggleCurvedQuadEvent / ProjectionMeshUpdatedEvent | 曲面幕布（01 笔记）：曲率=0→平面，用于 2D |
| EXIT_FROM_VR / exitFromVr | 沉浸退出回 2D |

**结论**：我们的「PancakeActivity(2D) + 沉浸模式，共用一个播放会话」方向和 YouTube VR hybrid 完全一致。
可借鉴：
1. **两模式复用同一套 UI 容器/纯逻辑**（fsm_*_container），只换宿主/渲染目标——我们的 state/reducer→host→UI 竖切片思路同理，逻辑层不要绑死在 VR 宿主。
2. **模式切换做成显式 FSM + 事件**（ImmersiveVideoFsmTransitionEvent、AndroidSurfaceReady、ProjectionMeshUpdated、ToggleCurvedQuad），而不是各宿主各自处理；
   我们可把「2D↔沉浸切换、surface ready、曲面开关」也建模成事件，保证切换时播放不中断、surface 平滑交接。
3. **默认启动/回退**：YouTube 在系统不支持 hybrid 或 preferred mode 未就绪时**回退到 Panel 模式**（更稳）；hybrid 需要 `oculus.software.vr.app.hybrid` 特性。
4. 曲面幕布在 2D 模式 = 平面（`ToggleCurvedQuadEvent`），即曲率参数 0/∞，与 06 笔记「半径∞=平面」一致。

## 4. 附带发现：VR 输入法
- APK 内带 **`com.google.android.apps.vr.inputmethod`**（VR 输入法服务，`VrInputService`、`BIND_INPUT_METHOD`），
  含拼音/注音/仓颉/日文/韩文 decoder（`decoder.pinyin.PinyinDecoder` 等）与 `com.oculus.feature.VIRTUAL_KEYBOARD`。
  即系统级 VR 键盘走标准 Android InputMethod 框架；我们自研拼音是 app 内 reducer，路线不同但可对照其事件模型（ImmersiveKeyboardInputEvent Show/Hide/Supported）。

## 合规：仅记录架构/事件命名等思路，独立实现。
