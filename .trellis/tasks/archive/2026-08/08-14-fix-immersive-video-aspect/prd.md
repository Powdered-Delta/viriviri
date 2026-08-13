# 修复沉浸式视频纵横比拉伸

## Goal

让沉浸式 Spatial video panel 根据 Media3 `VideoSize` 保持原始显示比例，同时不影响已经正常工作的 2D `TextureView`、单播放器和单视频 Surface 路径。

## Observed Behavior

- 2D 模式能按源视频纵横比 contain 渲染。
- 沉浸模式仍把竖屏视频拉伸到横向舞台比例。
- 现有纯 Kotlin contain 计算对 `1080x1920` 可得到正确的窄内容 quad。

## Current Evidence

- `SpatialVideoContentQuad` 和 `SpatialVideoAspectDiagnostic` 的计算覆盖了像素宽高比。
- `sceneMeshCreator` 初始创建自定义 `TriangleMesh`，随后将其保存并在 `onVideoSizeChanged` 中调用 `TriangleMesh.updateGeometry()`。
- 当前代码没有在后续几何更新后可靠提交已创建的 `SceneMesh`。
- 历史 Quest 验证显示直接恢复 `SceneMesh.updateWithTriangleMesh(...)` 曾导致视频不可见，因此不能盲目采用；必须保持可见性路径或用 SDK 证据确认正确调用时机。

## Scope

- 保持现有 `spatialized_video_panel`、MediaPanel、Scene-authored parentage、唯一 `ExoPlayer` 和唯一 SDK-owned video Surface。
- 修复或验证动态前景 quad 的实际 runtime geometry 提交，使沉浸式画面使用 contain 尺寸。
- 保持 full-stage shadow/input footprint 与 transport parent 行为不变。
- 为 geometry commit 逻辑增加可测试的纯 Kotlin helper/diagnostic，覆盖 16:9、9:16、超宽和非方形像素。
- 在现有 `mode_panel` 中增加 Debug-only aspect probe 控件，不创建新的 Spatial panel/entity、播放器或 Surface。
- 提供 `Geometry only`、`Commit false`、`Commit true` 三条手动路径；切换路径立即用当前 `VideoSize` 重放一次 geometry 更新。
- 面板显示当前 probe 模式，debug label 显示 source dimensions 和目标 quad 尺寸；实际视频可见性和拉伸结果由 Quest 人工观察记录。

## Non-goals

- 不改 2D TextureView 缩放逻辑。
- 不添加第二个视频 panel、播放器、Surface、黑色底板、shader 资产或运行时 Entity。
- 不猜测或注入未证实的 material/UV uniform。
- 不恢复已知会导致视频不可见的 API 路径，除非本任务先确认正确的 SceneMesh 更新时序。
- 不修改 Meta Spatial Editor 场景布局。

## Acceptance Criteria

- [x] 纯 Kotlin aspect/geometry tests pass and cover valid/invalid metadata.
- [x] Windows JDK 17 `:app:testDebugUnitTest :app:assembleDebug` passes with `:app:export` in the task graph.
- [x] Immersive geometry update path exposes an explicit manual probe for the otherwise unverified SceneMesh commit step; the default runtime remains the known-visible geometry-only path.
- [x] No second player, Surface, panel, entity, shader asset, or 2D regression is introduced.
- [x] Debug-only `mode_panel` aspect probe provides `Geometry only`, `Commit false`, and `Commit true` without adding a panel/entity/player/Surface.
- [x] Probe selection reapplies the current `VideoSize` and exposes source/quad diagnostics in the existing panel/log.
- [ ] Quest validation records a portrait source, expected `ViriViriAspect` metadata, visual result, and whether the video remains visible after each probe mode.

## Quest Probe Procedure

Use a known portrait video, preferably `1080x1920`, and restart the app before each mode so one failed native mesh experiment cannot contaminate the next one.

1. Open the immersive `mode_panel` and record the `DEV <hash>` value.
2. Confirm `Aspect probe: Geometry only`; record the source dimensions, display aspect, and quad half-size. Expected for `1080x1920` on the `1.6 x 0.9` stage: display aspect `0.5625`, quad half-size about `0.253125 x 0.45`. Confirm whether the image remains stretched.
3. Restart the app, choose `Commit false`, and record whether the video remains visible and whether the portrait image is contained.
4. Restart the app, choose `Commit true`, and record the same observations.
5. Capture bounded logs only:

   ```powershell
   adb -s 1WMHHB63832104 logcat -d -s ViriViriAspect:I ViriViriSpatial:I
   ```

If either commit mode makes the video disappear, record that result and return to `Geometry only`; do not leave the diagnostic mode enabled as the default fix.
