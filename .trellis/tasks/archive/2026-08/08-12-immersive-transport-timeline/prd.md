# Synchronize Immersive Transport Timeline

## Goal

Make the existing immersive transport timeline reflect the shared Media3 player
consistently and ensure its periodic updater is owned and cancelled by the
Spatial Activity lifecycle.

## Scope

- Reuse the existing `controls_id` panel and existing seek bar.
- Add compact elapsed and duration text alongside the seek bar without changing
  Spatial scene geometry, panel registration, dimensions, parent, or Surface.
- Add a pure app-layer timeline state/format helper with unit tests.
- Render seek bounds, position, elapsed, and duration from a single helper.
- Treat unavailable/unknown duration as `--:-- / --:--` and disable seeking.
- Preserve active seek-drag precedence over Media3 discontinuity and periodic
  updates; the dragged elapsed label follows the user thumb.
- Replace the local repeating `Handler` with an Activity-owned timeline updater
  explicitly canceled in `onDestroy()`.
- Refresh after player state/position changes, panel creation, and every bounded
  periodic tick while not dragging.
- Document the lifetime and single-player/single-Surface invariants.

## Non-Goals

- No new player, Surface, Entity, Spatial panel, scene transform, media source,
  seek strategy, or 2D redesign.
- No chapter markers, buffered-range rendering, live-edge controls, thumbnails,
  subtitle/danmaku, or Context rail work.

## Acceptance Criteria

- A finite known duration produces readable elapsed/duration labels and a
  clamped enabled seek bar.
- Unknown/unavailable duration is visibly non-seekable and does not emit seeks
  from panel interaction.
- Active seek drag is not overwritten by listener/timer state.
- No repeating transport callback can outlive Activity destruction.
- Pure helper tests plus `:app:testDebugUnitTest` and `:app:assembleDebug` pass.
