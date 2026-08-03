# Quest 运行时记录

## 范围与基线

- 当前主线分支：`main/spatial-video`
- 应用名称：`ViriViri`
- 包名：`com.m0e_n00b.viriviri`
- 上游：Meta Spatial SDK Samples 的 `SpatialVideoSample`
- 上游提交：`d3cc1b7`
- Meta Spatial SDK：`0.13.2`
- 设备：Quest 2，序列号 `1WMHHB63832104`
- 系统：Horizon OS / Android 14，build `UP1A.231005.007.A1`

旧实现 `com.viriviri.app` 保留在 `master`，只作为技术储备和运行时问题的对照，不再是当前主线。

## 本机构建适配

上游样例使用 `compileSdk = 34`。本机 Android SDK 的 Platform 34 不可用，且自动下载超时，因此当前主线使用：

```text
compileSdk = 36
targetSdk = 34
```

本机未安装 Meta Spatial Editor CLI。上游场景导出任务 `:app:export` 无法执行，且导出的 `scenes/Composition.glxf` 不存在。应用在加载该可选环境场景失败时记录日志并继续启动视频样例：

```text
Unable to load optional environment scene
```

这会省略 MediaRoom 环境，不影响 Spatial video panel、视频列表、2D 路由或 OpenXR 生命周期验证。

构建命令：

```powershell
cd spatial-video
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-build-cache --no-daemon -x :app:export
```

## 已验证的应用路由

默认 Launcher 是沉浸式 Activity：

```text
SpatialVideoSampleActivity
```

沉浸空间中的 `Video Mode` panel 提供：

```text
Open 2D Window
```

点击后走 Meta `HybridSample` 使用的路由：

```text
SpatialVideoSampleActivity
-> Horizon OS Home + PendingIntent
-> PancakeActivity
```

`PancakeActivity` 标记为 `com.oculus.intent.category.2D`，但不带 `android.intent.category.LAUNCHER`，因此不会成为默认启动入口。当前 2D Activity 只显示窗口内容，不接管视频播放器、解码器或播放位置。

新包已验证可与旧包同时安装：

```text
com.m0e_n00b.viriviri
com.viriviri.app
```

## 已知问题：间歇性长黑屏

### 状态

- 类型：Horizon OS / OpenXR runtime / compositor handoff 风险
- 影响路径：重复 immersive -> 2D -> immersive 切换
- 复现性：间歇性；曾在官方样例第二次切至 2D 时出现，之后连续十余次切换未再次触发
- 当前结论：不是旧 `viriviri` 的播放器、视频接管或业务 Activity 路由独有问题

在官方样例的黑屏现场，系统观察到：

```text
XrLayerInfos (count = 0)
vr_compositor_3664x1920 haveBuffer=true
vr_compositor_protected_3664x1920 haveBuffer=false
vr_compositor_with_depth_test_3664x1920 haveBuffer=false
```

同时，沉浸任务被移入 hidden list，`PancakeActivity` 被系统激活。这与旧实现的 OpenXR compositor 卡帧现场具有相同的关键 SurfaceFlinger 状态。

旧实现中已捕获到更完整的终局日志：

```text
xrBeginFrame timed out waiting for begin frame event
XR_ERROR_RUNTIME_FAILURE
Client has lost focus
OpenXR_SessionImpl: xrEndSession
XrLayerInfos (count = 0)
```

因此当前归因为：Horizon OS 的 immersive / Home / 2D volumetric window 重复交接可能触发 runtime 时序问题。官方样例本轮未在同一时间窗捕获 `xrBeginFrame` timeout，因此不能声称两者的每条日志完全一致；但已足以排除“仅旧应用视频接管导致”的解释。

现场采集命令：

```powershell
adb logcat -c
adb logcat -d -v threadtime > temp\viriviri-spatial-video-handoff.log
adb shell dumpsys SurfaceFlinger > temp\viriviri-spatial-video-surfaceflinger.txt
adb shell dumpsys activity activities > temp\viriviri-spatial-video-activity.txt
```

旧应用的详细证据与 focus 交接问题见：

```text
.trellis/tasks/07-29-stage-b-cross-activity-handoff/research/vr-focus-handoff-input-loss.md
```

## 已知问题：返回沉浸时的 reference-space 重定位

### 用户可见现象

从 2D 窗口返回 immersive 时，用户可能先看到面板保留在离开时的位置，随后面板突然按当前注视方向重新对齐。该表现来自 Horizon OS 返回 immersive 后更新 `LOCAL_FLOOR` / viewer reference space，而非视频播放器或 2D 视频接管。

### 当前原型

当前代码已实现以下策略，尚待头显人工验收：

1. 打开 2D 窗口前，保存 viewer pose 与视频、视频列表、`Video Mode` 三个顶层 panel 的 pose。
2. 返回 immersive 且收到 `SessionState.FOCUSED` 后，等待 `120ms` 让 reference space 稳定。
3. 计算离开与返回时 viewer 的水平 yaw 差。
4. 小于 `15` 度时不做任何视觉操作。
5. 大于等于 `15` 度时，三个顶层 panel 在 `160ms` 内用 ease-out 同步移动到新的 head-relative pose。
6. 控制条与 MR 按钮是现有父 panel 的子节点，随父 panel 自动移动。

该策略不使用淡入淡出。目标是让轻微转头时保持视觉连续，明显转身时以快速连续位移替代“旧位置帧 -> 新位置”的闪跳。

### 验收步骤

1. 启动沉浸式应用并进入 2D 窗口。
2. 轻微转头，小于约 `15` 度，返回 immersive：panel 不应移动或闪烁。
3. 在 2D 窗口中水平转身超过约 `15` 度，返回 immersive：视频、视频列表和 `Video Mode` panel 应同步、快速平滑移动。
4. 检查控制条与 MR 按钮仍跟随其父 panel，且不出现独立漂移。

## 当前边界

- 2D Activity 尚未接管视频播放。
- 2D/immersive handoff 黑屏尚无应用层根治方案。
- 若系统结束 OpenXR session，应用不能强制重启 Horizon OS PhaseSync、恢复 runtime focus 或清理 stale compositor frame。
- reference-space 重定位策略仅保持面板相对于返回时用户姿态的连续性；它不是跨 session 的持久世界锚定方案。
