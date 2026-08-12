# Align the Immersive Transport Overlay

## Goal

Turn the existing `controls_id` panel into the first usable immersive
`TRANSPORT` overlay behavior without changing fixed scene geometry. It already
parents to `spatialized_video_panel`; this task makes its runtime visibility,
hit behavior, and primary-stage interaction match the documented Playback
Canvas model.

## Scope

- Reuse the existing `controls_id` and `spatialized_video_panel` entities only.
  Do not add a fixed scene entity, panel, or anchor.
- Keep `controls_id` as the existing front-of-stage runtime child. No pose,
  size, parent, or Mesh geometry change while `mse-agent` is unavailable.
- Replace the current 100ms control fade with a short, approximately four-second
  idle timeout while actual playback continues.
- Make transport visibility behavior explicit:
  - playing: temporary controls fade after idle;
  - paused/not actually playing: controls remain visible;
  - hover, input, panel control action, or first primary stage click: controls
    become visible and reset the playing timeout;
  - when hidden, controls are not left as an alpha-zero interactive view.
- Change direct stage click behavior:
  - when transport is hidden, first click reveals transport only;
  - when transport is visible, click toggles play intent using the existing
    player-as-source-of-truth path.
- Keep the one `ExoPlayer`, one current video Surface, current `PlayerSession`,
  `ImmersiveMediaStageHost`, and playback-control synchronization intact.
- Add pure JVM tests for visibility/primary-click decisions and update the Quest
  panel runbook.

## Non-Goals

- No 2D UX work, no Compose work, no overlay renderer, no danmaku/caption,
  no new spatial scene entity, no static transform editing, and no Meta Spatial
  Editor installation attempt.
- No final theme renderer, title row, volume/replay/config expansion, grab
  handle redesign, or Shorts layout in this task.
- No Media3 source, player, Surface ownership, or handoff change.

## Acceptance Criteria

- `controls_id` remains parented to the existing `spatialized_video_panel` and
  is the only transport panel touched.
- A playing video fades transport after the idle timeout; paused/buffering
  transport remains visible according to actual playback state.
- A hidden transport receives no Android-view touch input until it is shown.
- First stage click after fade reveals transport without changing player intent;
  a subsequent click while visible toggles play intent.
- No new Entity, PanelSceneObject, ExoPlayer, or video Surface is created.
- `:spatial-workbench-core:test`, `:app:testDebugUnitTest`, and
  `:app:assembleDebug` pass.
