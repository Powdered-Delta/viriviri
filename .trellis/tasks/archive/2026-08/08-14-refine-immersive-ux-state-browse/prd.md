# 收敛沉浸式 UX 状态与浏览轨道

## Goal

完善现有 immersive Playback Canvas 的 Browse session 生命周期，使进入 Browse、取消、重选当前视频和选择新视频都有明确、可测试的 Playback return 行为，同时保持当前单播放器、单视频 Surface 和 Spatial panel 场景结构。

## Current State

- Core already defines `QUIET_WATCH`, `PLAYBACK`, `BROWSE`, and `CONTEXT` through `PlaybackCanvasReducer`.
- `ImmersivePlaybackCanvasHost` maps core canvas state to existing Spatial panel slots.
- Activity-owned `awaitingBrowseSelection` and `browseSelectionBaselineId` decide whether only a different selected video closes Browse.
- Browse has no explicit cancel command. Re-selecting the current video remains in Browse even though the user has completed an intentional selection.
- `mr_panel` is parented to `video_selector_panel`; independent `CONTEXT` display remains blocked until Meta Spatial Editor authors its intended anchor/parent relationship.

## Scope

- Extract a pure Kotlin browse-session policy with explicit actions: open, cancel, selection of same item, selection of different item, and unrelated app state updates.
- Use it in `SpatialVideoSampleActivity` instead of separate baseline/awaiting booleans.
- Add a `Back to playback` callback in the immersive `RecommendationPanel` browse host. The 2D host keeps its own existing navigation and does not acquire Spatial routing dependencies.
- On a valid browse selection, return the canvas to Playback regardless of whether the selected video matches the pre-browse item; `ViriViriAppState` remains the sole owner of playback resolution.
- Preserve Quiet Watch, Playback transport timeout, and Context core state semantics.
- Add focused JVM tests for browse session decisions and compose callback wiring intent.

## Non-goals

- Do not create/reparent Scene entities or panels. Static layout and `mr_panel -> video_selector_panel` parentage remain Meta Spatial Editor work.
- Do not map `CONTEXT` to a Spatial entity until the scene parentage is corrected.
- Do not create a Player, Surface, media source, route, or new Bilibili request from UI code.
- Do not change MediaRoom, video aspect probe behavior, 2D TextureView scaling, or 2D/immersive Surface handoff.

## Acceptance Criteria

- [x] Browse session behavior is pure Kotlin and unit-tested.
- [x] Opening Browse records the current selected video baseline and requests recommendations.
- [x] Canceling Browse or selecting any valid recommendation returns the core canvas to Playback.
- [x] Non-selection app updates do not close Browse; stale/duplicate state emission does not issue repeated return actions.
- [x] Immersive Browse host exposes an explicit Back to playback action without passing an Activity, Meta SDK object, player, or Surface into Compose.
- [x] Context remains core-only without runtime reparenting.
- [x] Windows JDK 17 `:app:testDebugUnitTest :app:assembleDebug` passed in 1m 3s with `:app:export` in the task graph.
- [ ] Quest validation: Browse cancel, same-video selection, different-video selection, and single-output playback continuity.
