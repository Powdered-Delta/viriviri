# Quest 运行时记录

## 范围与基线

- 当前主线分支：`main`
- 应用名称：`ViriViri`
- 包名：`com.m0e_n00b.viriviri`
- 上游：Meta Spatial SDK Samples 的 `SpatialVideoSample`
- 上游提交：`d3cc1b7`
- Meta Spatial SDK：`0.13.2`
- 设备：Quest 2，序列号 `1WMHHB63832104`
- 系统：Horizon OS / Android 14，build `UP1A.231005.007.A1`

旧实现 `com.viriviri.app` 保留在 `archive/legacy-android`，只作为技术储备和运行时问题的对照，不再是当前主线。

## 本机构建适配

上游样例使用 `compileSdk = 34`。本机 Android SDK 的 Platform 34 不可用，且自动下载超时，因此当前主线使用：

```text
compileSdk = 36
targetSdk = 34
```

Windows Meta Spatial Editor CLI is installed at:

```text
D:\Program Files\Meta Spatial Editor\v16\Resources\CLI.exe
```

The Gradle Spatial plugin exports `app/scenes/Main.metaspatial` into
`app/src/main/assets/scenes/`. `:app:preBuild` depends on `:app:export`, so a
normal debug build packages the authored MediaRoom environment:

```text
assets/scenes/Composition.glxf
assets/scenes/MediaRoom.gltf
assets/scenes/<exported-textures>.png
```

`SpatialVideoSampleActivity` loads `apk:///scenes/Composition.glxf` and looks
up the exported `MediaRoom` node. The GLXF composition references
`MediaRoom.gltf`; the environment is visible outside system passthrough and is
hidden when MR passthrough is enabled. A Windows export/build validation
confirmed that the debug APK contains `Composition.glxf`, `MediaRoom.gltf`, and
three exported texture PNG assets under `assets/scenes/`. Quest visual
validation of the packaged environment remains a separate manual step.

构建命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-build-cache --no-daemon
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

`PancakeActivity` 标记为 `com.oculus.intent.category.2D`，但不带 `android.intent.category.LAUNCHER`，因此不会成为默认启动入口。它现在是 Compose host，header 保留 `Return to immersive`，并可显示推荐列表或所选视频。2D recommendation route 默认以可滚动列表为主；Search icon 才展开离线九宫格输入台，避免输入控件在 `800dp x 550dp` Horizon OS panel 中挤掉所有推荐内容。

## Bilibili 推荐与播放

- 默认沉浸式 selector panel 显示 Bilibili 推荐；2D 和沉浸式 UI 共用 application-scoped recommendation state，因此列表、所选条目和浏览/观看目的地在路由后保持一致。
- selector panel 使用独立的 `SearchInputPanel`，不再只依赖 Horizon OS 系统 IME。默认 `ChineseT9InputMethod` 在应用内提供多击九宫格拼音、离线热门短语排序和 Android ICU `Han-Latin` 单字候选回退；选中候选后才会写入共享查询。`SearchInputMethod`、`OfflinePinyinLexicon` 和 `SearchInputMethodRegistry` 是纯 Kotlin 扩展边界，其他开发者可注册自己的语言键盘和离线词典，不改 Compose 搜索面板、状态层或 Bilibili provider。输入面板包含 `清空输入` 与 `确定搜索`，只有后者才会请求 WBI 签名的视频搜索；系统虚拟键盘保留为图标触发的备用输入。该面板不创建 Spatial entity、播放器或 Surface。共享状态取消前一搜索并以请求序号丢弃过期结果，`Recommendations` 会重新加载推荐 feed。
- 选择推荐后，独立 `BilibiliPlaybackProvider` 依次解析 `cid`、获取 WBI 图像 key、生成签名 playurl 请求，并仅选择 AVC DASH 视频与 DASH 音频。网络、API、解析或兼容流失败显示为可恢复错误，用户仍可返回推荐列表。
- 不发送 Cookie、SESSDATA、access key、用户标识或播放心跳。该公共接口不是稳定 SDK 契约，设备验证时应准备接口变更或限流失败的回退测试。
- 一个 application-scoped Media3 `ExoPlayer` 是唯一播放会话。2D `TextureView` Surface 由应用创建并释放；Spatial SDK Surface 仅附加/分离，绝不由应用释放。切换路由会保存位置与 play state，并在目标 Surface 附加后恢复。普通同一 Spatial Surface 的重复 callback 是 no-op；但从 2D 返回 immersive 时，一次性 intent signal 会强制把已缓存的 SDK Surface 重附着到同一 player，因为离开路由已清除该 player 的当前输出。
- 2D 视频输出保留现有 `TextureView` Surface 生命周期，并从 Media3 `VideoSize` 更新 view transform。输出在黑色容器中按 contain 比例居中，产生 letterbox 或 pillarbox，不拉伸或裁切源帧。
- 尚未完成此修复的 Quest 人工验证。本次代码验证已在 Windows JDK 17 通过 `:app:testDebugUnitTest :app:assembleDebug --no-build-cache --no-daemon`；`assembleDebug` 会先执行 `:app:export` 并打包 MediaRoom。需要从头显应用库验证 2D recommendation list 默认可见、Search icon 展开九宫格、返回 immersive 后首帧恢复、切换新视频更新 spatial panel、全程仅存在一个输出 Surface，以及非 passthrough 模式下 MediaRoom 环境可见。

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

## 返回沉浸时的 reference-space 行为

### 用户可见现象

从 2D 窗口返回 immersive 时，用户可能先看到面板保留在离开时的位置，随后面板突然按当前注视方向重新对齐。该表现来自 Horizon OS 返回 immersive 后更新 `LOCAL_FLOOR` / viewer reference space，而非视频播放器或 2D 视频接管。

### 当前策略

不再根据离开与返回时的 `LOCAL_FLOOR` viewer yaw 自动移动 panel。Horizon OS 的
2D 窗口布局与长按重置可能同时改变该 reference space；应用无法可靠区分这类系统
偏移和用户实际转头。此前的自动补偿会把两者叠加，导致偶发性偏航和不稳定布局。

返回 immersive 时保持现有 panel Transform。视频、推荐列表和 `Video Mode` panel
继续作为固定的顶层布局；控制条和 MR 按钮仍是父 panel 子节点。若系统层重置后布局
不适合当前视角，用户可通过现有抓取能力手动放置 panel。

### 验收步骤

1. 启动沉浸式应用并进入 2D 窗口。
2. 在 2D 中转头或长按重置视野后返回 immersive：应用不得额外旋转、移动或缩放 panel。
3. 检查视频、视频列表、`Video Mode` panel 的相对布局保持固定。
4. 检查控制条与 MR 按钮仍跟随其父 panel，且不出现独立漂移。

## 当前边界

- 无登录播放修复：`/x/web-interface/nav` 会以 `code=-101` 表示匿名状态，但仍在
  `data.wbi_img` 返回播放请求所需的公开 WBI key；provider 必须读取该字段而不是将
  该响应当作登录失败。未增加 Cookie、SESSDATA 或其它凭证。
- 返回沉浸策略：不再做自动 yaw 重定位。`LOCAL_FLOOR` 的返回 pose 受到 Horizon OS
  2D 窗口与系统长按重置共同影响，不能作为可靠的应用层布局校正基准。
- 输出切换修复：同一个 Media3 player 切换 Surface 时不能再显式 `seekTo` 已记录的
  position。DASH seek 会回退到此前关键帧或分段开头；保持播放器自然推进、只替换
  输出 Surface，避免切换后重复播放当前片段。
- MR 布局修复：`setMrMode()` 只切换 passthrough 环境、抓取能力和 locomotion，不能
  重写视频或控制 panel 的 pose / scale。三块顶层 panel 及控制条使用同一套初始
  相对布局，避免透视和非透视模式间的尺寸、位置和转向基准漂移。
- MR reference-space 修复：`setMrMode()` 不得调用 `scene.setViewOrigin()`。该调用会
  在透视模式切换时重置或叠加当前空间基准；view origin 只在 `onSceneReady()` 初始化。
- 2026-08-03：debug APK 已通过 ADB 安装到 Quest 2。ADB 启动请求已路由至
  `SpatialVideoSampleActivity`，但设备当时显示系统 reprojected OS dialog，
  Horizon OS 缓存并阻止启动；因此本次不构成应用启动或交互验收。
- Quest 设备尚未人工验收 2D 接管同一播放会话的实际 Surface 切换。
- 竖屏 aspect probe：沉浸视频 mesh 不再包含应用添加的局部 `-Z` 黑色底板。debug
  构建在每次 distinct `VideoSize` 的 retained `TriangleMesh` 更新后记录
  `ViriViriAspect`，包括 source dimensions、pixel ratio、display aspect 与目标 quad
  half-size。验证 9:16 BV 时应同时记录 `DEV <hash>`、BV ID 和该 logcat event；若
  event 显示 `1080x1920 / 0.5625 / 0.253125x0.45` 而画面仍拉伸，问题在 material
  UV 或 raw Spatial Surface compositor，不能再以第二层黑色 mesh 补偿。不要调用
  `SceneMesh.updateWithTriangleMesh(...)`：引入它的 Quest build 保留 stage hit
  testing 但失去可见视频，已知可见路径仅更新 retained `TriangleMesh`。
- reset orientation diagnostics: debug builds log `ViriViriSpatial` only when
  initializing `LOCAL_FLOOR`/view origin, reaching VR ready, and receiving a
  Spatial session state. The app does not write a panel pose/scale/yaw during
  system long-press reset, `setMrMode`, resume, or session transitions. Capture
  these events before proposing any application-level yaw correction.
- debug 构建包含一个左手 wrist tracking probe：运行时 panel 仅显示
  `DEV <hash>`，使用本地 avatar 的 head/left-hand Transform 更新；追踪 transform
  不可用时隐藏。验收其稳定性、手势/控制器输入隔离、丢失/恢复追踪与不遮挡主画面。
  它没有 Media3 player、Surface 或直播功能。
- 2026-08-13：Quest 验证观察到不定时空间/手部跟随漂移，当前不确定是 Horizon OS
  tracking、`LOCAL_FLOOR` reference space 还是应用 lifecycle。禁止据此增加 yaw
  补偿或重写 panel Transform。后续复现必须记录 `DEV <hash>`、影响范围（全部固定
  panel 或仅 wrist）、操作路径（系统长按重置、菜单、2D/沉浸、Passthrough、手部追踪
  切换）以及 `adb logcat -d -s ViriViriSpatial:I`。
- 2026-08-13：推荐中可能出现“充电专属”内容。匿名普通 UGC provider 在
  `/x/player/wbi/playurl` 未返回 `data.dash` 时会显示 `Bilibili did not provide
  DASH streams`；这表示未获得兼容普通 DASH payload，不等于 decoder 或 Spatial
  video 故障。先记录 BV ID、API result code/message、`data.dash` 是否存在和可见
  access marker；禁止记录 signed media URL、Cookie、SESSDATA、CSRF 或完整响应。
- 2D/immersive handoff 黑屏尚无应用层根治方案。
- 若系统结束 OpenXR session，应用不能强制重启 Horizon OS PhaseSync、恢复 runtime focus 或清理 stale compositor frame。
- reference-space 重定位策略仅保持面板相对于返回时用户姿态的连续性；它不是跨 session 的持久世界锚定方案。
