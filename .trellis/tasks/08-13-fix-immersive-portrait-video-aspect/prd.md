# 修复沉浸式竖屏视频比例

## Problem

Quest 上 9:16 Bilibili 视频仍被横向拉伸。当前 implementation 以
`Player.Listener.onVideoSizeChanged` 计算 contain-sized foreground quad，并调用
`SceneMesh.updateWithTriangleMesh(...)`，但设备现象表明最终 Spatial texture /
material / geometry path 仍存在 16:9 强制采样或未提交问题。

此前加入的 full-stage black backdrop 位于 video foreground 后方的局部 `-Z`，其
视觉语义错误，必须删除。它不得作为 aspect-ratio 修复或诊断手段。

## Scope

1. 移除 custom `SceneMesh` 中完整的 black backdrop: material、vertices、UV、
   primitives、depth/alpha constants 和 imports。
2. 保持一个共享 `ExoPlayer`、一个 SDK-owned active video Surface、一个现有
   `spatialized_video_panel`；不得增加 player、Surface、Entity、panel 或静态场景
   对象。
3. 为 Media3 `VideoSize` -> target quad 计算提取可测试的 diagnostic snapshot，
   包含 source width/height/pixel ratio、display ratio、target content width/height。
4. 在 debug build 对每次有效 VideoSize 仅记录一次 geometry commit event，供
   Quest logcat 确认实际收到的 source metadata 与目标 physical quad。不得逐帧记录。
5. 构建一个 geometry-commit diagnostic artifact：portrait source 使用既有 contain
   quad，并以一次性 `ViriViriAspect` event 记录 `VideoSize` 与已提交的目标 geometry。
   该 diagnostic 不修改 Player scaling、Surface buffer、UV 或 Entity Transform。
6. 根据设备 diagnostic 结果再选择并单独实现最终方案：
   - geometry commit 生效但视频仍拉伸：研究支持 Surface texture 的 material/UV
     contract，在同一 plane 内做 transparent/letterbox sampling；不创建后方 black
     plane。
   - geometry commit 未生效：使用 SDK 文档确认 SceneMesh 更新 API 或稳定的
     runtime geometry mechanism；Entity Transform 只能作为验证手段，不能未经
     input/shadow/layout 验证直接成为最终方案。
7. 同一 Quest APK 附加手部跟随测试项：调查 SDK 0.13.2 可用 API，选择左手手腕或
   手背的稳定 tracking pose，显示仅含 `DEV <hash>` 的非视频 debug panel。该 panel
   不属于直播功能，不得拥有 Player/Surface；手追踪不可用时必须隐藏，恢复后再显示。

## Non-Goals

- 不实现直播间、直播列表、直播弹幕、直播 API 或 WebSocket。
- 不加入第二层黑色视频底板、第二玩家、第二 Surface、第二视频 panel 或 shader
  资产，除非单独的最终 UV/material task 已经获得 SDK API 证据。
- 不改 Bilibili provider、Media3 source resolution、2D output routing 或静态
  `.metaspatial` scene layout。

## Device Acceptance

1. 进入 APK 后，在 `mode_panel` 确认 `DEV <hash>` 与 artifact metadata 相同。
2. 播放已知 9:16 BV，采集 `ViriViriAspect` logcat 事件，并确认无后方 full-stage
   black backdrop。
3. 确认 probe event 记录 `meshCommit=true`，并记录 BV ID、`VideoSize`、ratio、
   target quad 及 build hash。若该 target geometry 已确认但画面仍拉伸，停止修改
   geometry，转向 SDK-supported material/UV 或 raw-Surface compositor 路径。
4. 重播 16:9 与超宽源，确认没有拉伸、重复 prepare、player/Surface handoff 或
   shadow/input footprint 回归。
5. 对左手 wrist/back-of-hand DEV panel 验收：手在视野内时跟随稳定且不遮挡主画面；
   pinch/控制器操作不误触；失去手追踪时隐藏；恢复后重现；不影响现有 Media3 output。

## Verification

- JVM tests cover contain calculation and diagnostic snapshot for 16:9, 9:16,
  ultrawide, non-square pixel ratio, and invalid video metadata.
- Run `:spatial-workbench-core:test`,
  `:spatial-workbench-compose:compileDebugKotlin`, `:app:testDebugUnitTest`, and
  `:app:assembleDebug`.
