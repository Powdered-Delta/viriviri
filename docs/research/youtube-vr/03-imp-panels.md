# 03 — imp/Impress：2D Android View → VR 纹理面板 逆向笔记

样本：`libyoutube_vr_impress_jni.so`（arm64，IDA 13338）。包名 `com.google.ar.imp.*`（Impress / Immersive Platform）。
这是 YouTube VR 的沉浸式 UI 框架，角色等价于 **Meta Spatial SDK 的 Panel**。

## 1. 结论速览（对 ViriViri 的可执行建议）

- imp 的面板合成管线是标准的 **Android View → Surface/SurfaceTexture → OpenXR swapchain → 3D quad**：
  - `View_nCreateSwapChain(viewHandle, jobject surface, flags)`：对 Java `Surface` 调 `ANativeWindow_fromSurface()`，
    再建一个绑定到该 native window 的 XR swapchain（内部 `sub_A5387C`）。
  - `RenderViewToSurfaceTexture_nSetRenderViewSurfaceDimensions(renderer, wPx, hPx)`：
    `SurfaceTexture.setDefaultBufferSize(w,h)`（w/h 打包在对象 +108），并计算 **aspect-fit** 缩放（把宽高比拟合进 1.0，存在 +72 的 vec3，z=1）。
  - 帧循环：`View_nRenderNextFrame` / `nIsolatedPreRender` / `nIsolatedPostRender` / `nSynchronizePendingFrames` / `nCaptureVsyncTime`。
  - 配置位：`nShouldUseSrgbSwapChain` / `nShouldUseMsaaSwapChain` / `nShouldUseStencilSwapChain`（swapchain 颜色/抗锯齿/模板格式按面板类型选）。
  - 生命周期：`nCreateView/nCreateViewWithoutHost/nDestroyView/nSetup/nSetupShared/nOnResume/nOnPause/nResize/nSetDisplayRotation`。

**→ 对 Meta Spatial SDK：** Spatial 的 `Panel` 内部就是同一模型（Panel 拿一个 Surface，Compose/View 渲染进去，SDK 合成到 3D）。
  我们不需要自己造 swapchain；需要借鉴的是两点：
  1. **分辨率/宽高比**：YouTube VR 用像素尺寸 setDefaultBufferSize + aspect-fit 缩放，避免面板拉伸；我们的 PanelDimensions 已做类似的事，
     曲面/视频面板要保证 texture 宽高比与 mesh 宽高比一致（对应 01 笔记的曲面 quad）。
  2. **swapchain 格式按内容选**：视频/文字面板用 sRGB；需要抗锯齿的 UI 开 MSAA；省显存可关 stencil。可作为我们 Panel 配置的对照。

## 2. 关键 JNI（已在 IDB 重命名+注释）

| 地址 | 函数 | 作用 |
| --- | --- | --- |
| 0x850964 | `View_nCreateSwapChain` | Surface→ANativeWindow→XR swapchain |
| 0x83eb8c | `RenderViewToSurfaceTexture_nSetRenderViewSurfaceDimensions` | 默认缓冲尺寸 + aspect-fit |
| 0x850c8c | `View_nRenderNextFrame` | 渲染一帧 View 进 swapchain |
| 0x850da8/0x850e4c | `nIsolatedPreRender/PostRender` | 独立渲染阶段（隔离线程） |
| 0x8511e4/0x851208/0x85122c | `nShouldUseSrgb/Msaa/StencilSwapChain` | swapchain 格式选择 |

输入侧另有 `input.InputManager_nProcessPointerEvent`、`input.KeyboardView_nProcessKeyboardEvent`（射线→指针/键盘事件），见 04 键盘笔记（待补）。

## 3. 合规
仅记录架构思路与参数维度；ViriViri 独立实现。
