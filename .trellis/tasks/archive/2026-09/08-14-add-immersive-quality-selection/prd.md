# 添加沉浸式播放画质选择

## Goal

在既有 immersive transport panel 中提供显式画质控制，让用户选择 `Auto`、`360p`、`480p`、`720p` 或 `1080p`。选择后使用同一个共享播放器重新解析当前 BV 的兼容 AVC DASH 轨，并尽可能保持当前位置和播放意图。

## Current State

- `BilibiliPlaybackProvider.createMediaSource(videoId)` 请求固定 `qn=80`，然后选择 DASH 列表中的第一个 AVC 视频轨。
- 当前没有共享画质偏好，也没有 Transport UI 控件。
- `ViriViriAppState` 已拥有唯一的 playback resolution job、请求序号、防止 stale completion 的策略和错误显示。
- 现有 `controls_id` 是 immersive transport panel；它已有 Browse、音量、速度、播放、前后选片控件。

## Scope

- Add a pure Kotlin `PlaybackQuality` preference (`Auto`, `360p`, `480p`, `720p`, `1080p`) including label, Bilibili qn request value, and maximum target height.
- Let the provider request the corresponding qn and deterministically select an AVC DASH stream: highest compatible stream for Auto; highest stream at or below the requested height, then the lowest compatible fallback if the requested height is unavailable.
- Store the selected quality preference in `ViriViriUiState`.
- Add an existing-transport-panel quality PopupMenu; do not create a new panel/entity.
- Quality selection for a currently selected video re-runs the existing AppState resolution path, preserves current player position and `playWhenReady`, and ignores stale completions.
- Show the selected preference label in the existing quality button. Actual delivered height remains API/availability dependent.
- Add focused pure JVM tests for quality selection and provider stream selection.

## Non-goals

- Do not add a second `ExoPlayer`, video Surface, Spatial panel, Entity, shader, or scene edit.
- Do not expose login-restricted qualities as guaranteed availability or bypass access restrictions.
- Do not change 2D TextureView aspect behavior, immersive aspect probe, media-source credentials, or Bilibili history behavior.
- Do not reconstruct the player for quality changes.

## Acceptance Criteria

- [ ] Quality preference/selection logic is pure Kotlin and unit-tested.
- [ ] Provider requests the matching qn and chooses deterministic compatible AVC video streams.
- [ ] Transport has a quality menu with the supported preferences and current label.
- [ ] Selecting quality re-resolves only the selected video, retains position/play intent where available, and cannot be overwritten by stale selection/quality requests.
- [ ] Fallback behavior is readable when requested quality is unavailable or source resolution fails.
- [ ] No extra player, Surface, panel, entity, scene mutation, or login bypass.
- [ ] Windows JDK 17 `:app:testDebugUnitTest :app:assembleDebug` passes with `:app:export`.
- [ ] Quest validation covers each preference, visible current label, playback continuity, unavailable-quality fallback, and single-output continuity.
