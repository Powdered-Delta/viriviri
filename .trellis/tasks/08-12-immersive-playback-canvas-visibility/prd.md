# Apply Immersive Playback Canvas Visibility

## Goal

Bind the pure-core `PlaybackCanvasReducer` to existing Quest Spatial panels
through a proper panel-layer alpha, `Visible`, and input lifecycle adapter. This
makes Quiet Watch, Playback, Browse, and Context state affect actual Spatial
panel visibility without creating any new scene object or video output.

## Existing Entity Mapping

```text
MEDIA_STAGE     -> spatialized_video_panel  (always visible)
TRANSPORT       -> controls_id
SYSTEM_TOOLBAR  -> mode_panel
BROWSE          -> video_selector_panel
CONTEXT         -> mr_panel
```

## Scope

- Add a generated app ECS component `PanelLayerAlpha` and a Spatial system that
  maps its alpha to the existing `PanelSceneObject` layer color scale.
- Add an app-side `ImmersivePlaybackCanvasHost` adapter that owns only the
  pure-core `PlaybackCanvasState`, receives semantic events/player actual-state,
  resolves visible slots through `PlaybackCanvasReducer`, and requests existing
  panel visibility changes by `PanelSlot`.
- Apply the independently renderable existing panels only:
  - Quiet Watch: `MEDIA_STAGE` plus persistent `SYSTEM_TOOLBAR`;
  - Playback: add `TRANSPORT`;
  - Browse: add only `BROWSE` as the on-demand rail.
- Preserve `CONTEXT` in the pure-core state machine but defer its independent
  Spatial application. The existing `mr_panel` is currently parented to
  `video_selector_panel`; it cannot show independently while Browse is hidden.
  That fixed parent/anchor relation must be authored in Meta Spatial Editor
  before a runtime adapter binds the Context slot.
- Implement Spatial panel visibility as:
  - show: `Visible(true)` then panel layer alpha transitions to one;
  - hide: panel layer alpha transitions to zero, then `Visible(false)`;
  - `Visible(false)` is the final hit-test/input disable point.
- Reuse the existing `controls_id` transport timeout: it dispatches canvas idle
  timeout when actual playback continues. Stage primary input dispatches canvas
  primary action before considering playback toggle, so first interaction from
  Quiet Watch reveals Playback controls.
- Forward existing player actual-playing events to the canvas host.
- Add pure JVM tests for the host's semantic state/effect mapping and core
  regression tests where needed.
- Update Quest runbook with the applied canvas adapter lifecycle.

## Non-Goals

- No 2D UX change, Compose change, Bilibili source change, overlay renderer,
  danmaku/caption, Shorts runtime, Focus/PiP runtime, Browse/Context content
  redesign, or static scene layout edit.
- No new Entity, panel registration, anchor, Transform, player, ExoPlayer,
  video Surface, or MediaStage output.
- No `mse-agent`/Meta Spatial Editor installation or fixed-scene change.
- No final tween library integration; an adapter-local bounded alpha animation
  is sufficient for this first mapping.

## Acceptance Criteria

- Existing `spatialized_video_panel` remains visible and hittable as the sole
  video stage; no extra video output exists.
- Existing Browse panel is hidden in Quiet Watch/Playback and appears only when
  explicitly requested by the canvas host. Core Context state remains testable,
  but its independent Spatial rail is explicitly deferred pending scene authoring.
- Existing Transport panel is shown in Playback and fades to final hidden
  `Visible(false)` on actual-playback idle timeout.
- A hidden Spatial panel does not retain a visible layer or panel hit target.
- Player pause from Quiet Watch opens Playback controls; initial idle/not-ready
  player state does not spuriously open controls.
- `:spatial-workbench-core:test`, `:app:testDebugUnitTest`, and
  `:app:assembleDebug` pass.
- Quest validation remains manual and is explicitly recorded in the next
  archived APK sidecar.
