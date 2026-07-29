# 重复 2D/沉浸切换后 VR Focus 丢失

## 状态

- 问题类型：Horizon OS / VrRuntimeService 输入焦点交接异常
- 首次记录：2026-08-03
- 设备：Quest 2，设备序列号 `1WMHHB63832104`
- 系统：Horizon OS / Android 14，build `UP1A.231005.007.A1`
- 应用：`com.viriviri.app`
- SDK：Meta Spatial SDK `0.13.0`
- 复现状态：已在多轮测试中复现，至少有一次稳定复现
- 当前结论：不是播放器、Surface 或 Activity 路由失败；应用没有获得有效的 VR runtime focused client，导致输入和交互视觉同时失效

## 用户可见现象

在应用启动或完成若干次真实系统 2D Panel 与沉浸 Activity 切换后，可能出现：

- 看不到由手柄定位的手部模型
- 看不到控制器模型或控制器指示线
- Spatial 控制面板点击无效
- 呼出系统菜单并关闭后，手部/指示线和点击通常恢复

这里的“没有手/手柄”不是指已经确认手柄硬件断连，也不是单纯手部追踪模式关闭，而是沉浸场景中的控制器可视化和交互输入没有绑定到当前应用。

## 稳定复现路径

1. 从 Quest 正常启动 `viriviri`。
2. 在沉浸 Activity 与真实 2D Panel 之间反复切换。
3. 混合测试三种模式/路由方向，不要求固定从某一方向开始。
4. 在某一轮返回或启动沉浸 Activity 后，观察到手部、指示线和点击同时失效。
5. 呼出系统菜单，再关闭菜单，交互通常恢复。

问题不再只局限于 `2D -> 沉浸`：后续测试中已经出现冷启动即不可见、以及多种路由方向均可能触发的情况。

## 关键日志证据

证据文件：

```text
temp/viriviri-controller-laser-test.log
```

代表性时间段：`03:37:17.644` 至 `03:37:17.700`。

### 应用和 OpenXR 已进入沉浸状态

```text
OpenXR_SessionImpl: XR_SESSION_STATE_VISIBLE -> XR_SESSION_STATE_FOCUSED
SPATIAL_SDK_LOGGER: ISDK: Creating controller ...
VolumetricContentMonitor: Focused window changed to ... ImmersiveActivity
```

这说明 Activity、Spatial 场景、OpenXR session 和控制器实体都已经创建。

### Runtime focus 交接出现空窗

```text
ActivityManagerUtilsHelperImpl: topWindowUid: 10042, focusedApp: com.viriviri.app
ClientMgrFocus: focusedWindowUid_ = 10029 is changed so not looking for client with uid = 10042
ClientMgr::SetFocusedClient: No focused client
ClientMgrFocus: Clearing focused package name.
```

此时系统窗口层已经报告应用可见，但 VrRuntime 的客户端焦点为空。应用因此没有有效的 focused client。

### 输入访问被系统拒绝

```text
MemoryBroker: INPUT_TYPE_MAP: client com.viriviri.app ... does not have access (app ops or VR focus)
MemoryBroker: HAND_TRACKER: client com.viriviri.app ... does not have access (app ops or VR focus)
MemoryBroker: CONTROLLER_TRACKING: client com.viriviri.app ... does not have access (app ops or VR focus)
```

这与“看不到手部、指示线，同时点击无效”相符。问题不是单独的 Avatar 可见性。

### 系统稍后才重新绑定应用焦点

```text
ClientMgrFocus: FocusedClient changed from  to com.viriviri.app:19346
ClientMgrFocus: Updating Focus State: 1 for Client: com.viriviri.app:19346
ClientMgrFocus: SetFocusedPackageName - packageName com.viriviri.app
ServiceInputManager_Modality: SetFocusedPackageName 'com.viriviri.app'
```

这解释了为什么呼出并关闭系统菜单可以恢复：菜单关闭触发了完整的 focus 重新绑定。

## 现有应用层尝试

当前 `ImmersiveActivity` 已尝试在以下时机恢复控制器状态：

- `onVRReady()`
- `SessionState.FOCUSED`
- `+150ms`
- `+400ms`
- `+800ms`

恢复内容包括：

```kotlin
avatarSystem.setShowControllers(true)
controller.isActive = true
controller.laserEnabled = true
```

该逻辑只能恢复已获得 runtime 输入权限后的 SDK 状态，不能绕过 `MemoryBroker` 对 focused client 的授权。因此它不是本问题的根治方案。

## 排除项

当前证据不支持以下根因：

- Media3/ExoPlayer 播放器故障
- MediaCodec 解码器故障
- 视频 Surface handoff 失败
- PanelActivity 或 ImmersiveActivity 没有创建
- 单纯的 `AvatarSystem.setShowControllers(false)` 遗留状态
- 单纯的 `Controller.laserEnabled=false`

在问题复现时，播放器、Surface、首帧和 Activity 路由仍可能正常完成；失败点是随后或同时发生的 runtime input focus 绑定。

## 相关伴随现象

同一批长时间重复切换日志中还出现过：

```text
Interstitial session took more than 30.000000s
process com.oculus.vrruntimeservice
```

以及多个 Horizon 系统服务重启或异常恢复日志。这些现象进一步支持问题属于 Horizon OS runtime/session 状态，而不是应用业务逻辑。

## 当前处理边界

暂不继续增加播放器、Surface 或路由层修复。应用层只保留有限的状态重申和诊断日志，不把它当作可靠的 focus 恢复机制。

下一步优先级：

1. 保留一次最小稳定复现日志，包含 `No focused client`、`MemoryBroker` 拒绝和后续 `SetFocusedPackageName`。
2. 检查 Meta Spatial SDK 是否提供公开的重新请求 VR focus/input session API。
3. 若没有公开 API，将菜单开关作为临时用户恢复手段，并整理为 Meta Horizon OS 问题反馈。
4. 用 YouTube VR 做冷启动和重复切换对照，确认相同设备状态下是否也会出现 `No focused client`。

## 参考命令

```powershell
adb logcat -c
adb logcat -d -v threadtime > temp\viriviri-controller-focus-failure.log
adb shell dumpsys activity activities > temp\viriviri-controller-focus-activity.txt
adb shell dumpsys input > temp\viriviri-controller-focus-input.txt
```

问题记录和后续 GitHub message 默认使用中文；系统组件名、日志原文、类名、方法名和命令保持原样。

## 已知缺陷：OpenXR compositor 首次启动卡帧

### 状态

- 问题类型：Horizon OS / OpenXR runtime / PhaseSync / compositor 帧提交失败
- 首次确认：2026-08-03，使用 Meta Spatial SDK `0.13.2` 复现
- 设备：Quest 2，设备序列号 `1WMHHB63832104`
- 系统：Horizon OS / Android 14，build `UP1A.231005.007.A1`
- 应用：`com.viriviri.app`
- 构建基线：Gradle `8.13`、AGP `8.11.1`、Kotlin `2.2.0`
- 当前结论：首次启动即可出现，与 2D/沉浸 Activity handoff 无关；播放器和 Android Surface 继续工作，但 OpenXR compositor 停止提交新的 XR frame

### 用户可见现象

首次从 Quest 应用库启动应用时，可能出现以下状态：

- 视频仍然流畅播放
- 沉浸式按钮和控制器输入仍可工作
- 画面保留一张卡住的沉浸渲染帧
- 该帧呈半透明状态，可以透过它看到视频和按钮
- 卡住帧周围的空间变黑，不再显示正常的全景/环境内容

这不是普通的全景环境加载动画，也不是应用主动绘制的半透明遮罩。它是 compositor 保留最后一张已提交的 XR 帧，而后续 XR layer 不再提交的结果。

### 关键日志证据

证据文件：

```text
temp/viriviri-sdk-0132-hung-all.log
temp/viriviri-sdk-0132-hung-surfaceflinger.txt
temp/viriviri-sdk-0132-hung-activity.txt
temp/viriviri-sdk-0132-hung-window.txt
```

代表性 OpenXR 错误：

```text
RuntimeTelemetryThread: [OpenXR] xrBeginFrame timed out waiting for begin frame event
HorizonStandalone: XR_ERROR_RUNTIME_FAILURE
HorizonStandalone: beginFrame FAILED
VrRuntimeClient: Client has lost focus.
HorizonStandalone: Session end
OpenXR_SessionImpl: xrEndSession
```

代表性 SurfaceFlinger 状态：

```text
XrLayerInfos (count = 0)
vr_compositor_3664x1920 haveBuffer=true
vr_compositor_protected_3664x1920 haveBuffer=false
vr_compositor_with_depth_test_3664x1920 haveBuffer=false
```

这表示普通 `vr_compositor` 仍保留最后一个 buffer，但 runtime 已经没有新的 `XrLayerInfos` 提交。应用窗口本身仍然可见并 ready，播放器线程和 MediaCodec 线程也仍然运行。

### 应用侧排除项

当前证据不支持以下根因：

- Media3/ExoPlayer 播放器故障
- MediaCodec 解码器故障
- TextureView 或视频 Surface handoff 失败
- `ImmersiveActivity` 主线程崩溃
- Android Activity 主题设置了半透明窗口
- 2D/沉浸 Activity 路由专属竞态

`ImmersiveActivity` 的 Android theme 只设置了 `windowNoTitle`，没有设置 `windowIsTranslucent`、`windowAlpha` 或半透明 `windowBackground`。

### 生命周期对照

在部分复现日志中，Spatial 应用生命周期已经完整推进到：

```text
nativeOnActivityReady
XR_SESSION_STATE_IDLE -> XR_SESSION_STATE_READY
XR_SESSION_STATE_READY -> XR_SESSION_STATE_SYNCHRONIZED
XR_SESSION_STATE_SYNCHRONIZED -> XR_SESSION_STATE_VISIBLE
XR_SESSION_STATE_VISIBLE -> XR_SESSION_STATE_FOCUSED
Spatial panel first rendered frame
```

但即使完成上述生命周期，仍可能随后发生 `xrBeginFrame` PhaseSync timeout，并结束 XR session。因此 `onVRReady()`、视频 Surface callback 和 Media3 首帧不能证明系统 compositor 会持续提交帧。

### 当前处理边界

应用层没有公开 API 可以：

- 重新启动 Horizon OS 的 PhaseSync
- 强制恢复已经结束的 OpenXR session
- 清除 `vr_compositor` 的 stale frame
- 强制系统 compositor 重新提交 XR frame
- 绕过系统对 XR focus 的管理

应用层可做的只是故障检测和降级：

1. 通过 session 状态、首帧超时和渲染 heartbeat 识别 XR session 失效。
2. 保留播放器和播放位置。
3. 销毁当前 `ImmersiveActivity` 和 Spatial session。
4. 退回 2D Panel。
5. 延迟后允许用户重试创建新的 `ImmersiveActivity`。
6. 连续失败时保持 2D 模式，并记录诊断信息。

这些措施只能改善用户体验，不能修复 Horizon OS runtime 本身的帧同步故障。

### 归因验证计划

保持 Horizon OS 版本不变，分别对比：

1. Meta Spatial SDK `0.13.2` 的首次启动。
2. Meta Spatial SDK `0.13.0` 的首次启动。
3. Meta 官方 `SpatialVideoSample` 或最小空 Spatial 场景的首次启动。

判断标准：

- `0.13.0` 和 `0.13.2` 都失败：优先归因 Horizon OS / OpenXR runtime。
- 只有 `0.13.2` 失败：检查 SDK `0.13.2` 兼容性或回归。
- 官方样例也失败：基本确认是 Horizon OS / OpenXR runtime。
- 只有 `viriviri` 失败：继续检查应用的 Spatial bootstrap、Manifest 或生命周期。

建议向 Meta 报告时附带：

```text
xrBeginFrame timed out waiting for begin frame event
XR_ERROR_RUNTIME_FAILURE
Client has lost focus
XrLayerInfos (count = 0)
```
