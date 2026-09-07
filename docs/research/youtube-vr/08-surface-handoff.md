# 08 — 2D↔沉浸 切换：Surface 交接与无缝过渡（SplitEngine）逆向笔记

证据：`libyoutube_vr_impress_jni.so`（IDA 13338）字符串/组件类名/proto 事件 + classes2.dex。
回答「2D 进沉浸时播放如何不中断、画面如何平滑交接」。

## 1. 结论速览

两模式切换**不是新建播放器/黑屏重载**，而是用 **SplitEngine + 外部纹理（SurfaceTexture/OES）+ 快门遮挡** 做平滑交接：
- 视频始终是**同一个播放会话**，只是输出的 Surface 从「2D 窗口的普通 Android View/Surface」迁到「VR 里的一张 XR swapchain 外部纹理 quad」。
- 切换瞬间用**黑色快门（Shutter）+ loading 占位**遮挡，等新 surface 出帧后再淡入，用户看不到撕裂/黑屏闪烁。

## 2. 外部纹理管线（2D Surface → VR quad）

native 侧用 Android NDK 直接消费 SurfaceTexture：
- `ASurfaceTexture_attachToGLContext / detachFromGLContext / updateTexImage / getTransformMatrix / getTimestamp`（`imp::android::SurfaceTexture`）。
- 着色器走 **OES 外部纹理**：`#extension GL_OES_EGL_image_external(_essl3)`、`uniform samplerExternalOES materialParams_videoTexture/video_texture`。
- `imp::PlatformAndroidExternalTextureSurface` / `DefaultPlatformAndroidExternalTextureSurface`（`android_external_texture_surface.cc`，注释：only supports a single view）。
- VR swapchain 由 Android surface 支撑：`xrCreateSwapchainAndroidSurfaceKHR`；`ANativeWindow_fromSurface` + `ANativeWindow_setProducerThrottlingEnabled`（生产者节流，避免 2D 端帧堆积）。
- proto 请求：`imp.android.CreateSurfaceTextureQuadRequest`（用一个 SurfaceTexture 创建 quad）；
  运行时事件：`vr.youtube.AndroidSurfaceReadyEvent`（Android surface 就绪）。
- 曲面网格：`split_engine/video_node/video_mesh_builder.cc`（VR 视频 quad 网格，配合 01 的投影 mesh / ToggleCurvedQuad）。

**即**：2D 面板的 SurfaceTexture 被 attach 到 GL，作为 OES 外部纹理贴到 VR 曲面 quad；`getTransformMatrix` 处理 SurfaceTexture 的纹理矩阵（和我们 ExoPlayer video SurfaceTexture 的 texture matrix 同理）。

## 3. 无缝过渡组件（切换瞬间）

切换期间由几个 imp 组件/事件协作遮挡与揭示：
| 组件 / 事件 | 作用 |
| --- | --- |
| `ShutterVideoHider`（`video_playback/shutter_video_hider.cc`）、`ShutterUpdateEvent` | **黑色快门**：切换/缓冲时盖住视频，隐藏 surface 交换瞬间的撕裂 |
| `GrabFadeComponent`（Updater） | 交接时的淡入/淡出（grab fade） |
| `LoadingSpinnerSystem` + `2d_loading_spinner.png` / `black_placeholder.png` | surface 未出帧前显示 loading/黑场占位 |
| `AndroidSurfaceReadyEvent` | 新 surface 开始出帧的就绪信号——**快门在此后才打开** |
| `nativeSetVideoContainerReadyForSecureSurface` / `onSecureSurfaceCreated` | DRM/安全 surface 的就绪时序（受保护内容要等安全 surface） |
| `ShutterUpdateEvent`、`VideoBufferingEvent`、`ShutterUpdate` 等 | 缓冲/就绪状态驱动快门开合 |

### 推断的交接时序（已由组件/事件佐证，状态名属合理重构）
1. 2D 面板模式：视频在标准 Android Surface/SurfaceTexture 播放（同一会话）。
2. 用户进沉浸：`ImmersiveVideoFsmTransitionEvent` 触发；SplitEngine 建沉浸 subspace 与 `xrCreateSwapchainAndroidSurfaceKHR` 的 XR swapchain。
3. **快门关闭**（ShutterVideoHider 黑场）+ loading spinner；播放继续（同一播放器）。
4. SurfaceTexture 绑定到 VR 外部纹理 surface（attachToGLContext / updateTexImage），`ProjectionMeshUpdatedEvent` 设曲面 mesh。
5. 收到 `AndroidSurfaceReadyEvent`（安全内容等 secure surface 就绪）。
6. **快门打开 / GrabFade 淡入**，spinner 隐藏——画面无缝出现在曲面屏。
7. 退出（`exitFromVr`）反向：快门关闭 → 内容迁回 2D 窗口 surface → 就绪后揭示。

## 4. 对 ViriViri 的可执行结论

我们 2D(Pancake)↔沉浸 共用**单 ExoPlayer + 单视频 Surface**，和 YouTube 思路一致。落地建议：
- **不重建播放器，只重绑/共享 Surface**：ExoPlayer 持续播放，切换时把输出 surface 从 2D 容器迁到 VR 面板/曲面 quad 的 SurfaceTexture；OES 外部纹理 + texture matrix 是标准做法（Spatial 的 Panel/video Surface 内部同类）。
- **加一个快门/淡入遮罩**：切换瞬间显示黑场（或淡黑）+ 可选 loading，**等 `onFirstFrameRendered`/surface-ready 回调后再淡入**——这是避免 2D→VR 黑屏闪烁/撕裂的关键，YouTube 用 ShutterVideoHider+GrabFade 正是此目的。
- **就绪门控**：揭示视频前等 `AndroidSurfaceReady` 等价信号（我们的 `SurfaceTexture` 首帧 / ExoPlayer `onRenderedFirstFrame`）；DRM 内容另等 secure surface 就绪。
- **生产节流**：2D 端 surface 在 VR 出帧后停止/节流生产者（参考 `setProducerThrottlingEnabled`），避免双端缓冲堆积浪费。
- **事件化 FSM**：把「请求进沉浸 / 快门关 / surface 就绪 / 投影 mesh 更新 / 快门开」建模成显式事件（对应 ImmersiveVideoFsm + 一串 proto event），宿主只发事件、不各自管切换，契合我们 reducer 驱动的架构。

## 5. 合规：仅记录管线/事件/组件命名等思路，独立实现。
