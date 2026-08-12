# Coordinate the Immersive Playback Canvas

## Goal

Add the pure Kotlin runtime state machine that selects the active immersive
Playback Canvas before binding theme slots to current Spatial panels. It makes
Quiet Watch, Playback, Browse, and Context explicit runtime states instead of
scattered panel callbacks.

## Scope

- Add platform-neutral core contracts for the regular `WATCH` interaction
  canvas: `QUIET_WATCH`, `PLAYBACK`, `BROWSE`, and `CONTEXT`.
- Add a deterministic reducer for:
  - primary stage action opening Playback from Quiet Watch;
  - explicit Browse/Context requests;
  - temporary canvas dismiss/back returning to Quiet Watch;
  - playback idle timeout returning Playback to Quiet Watch only during actual
    playback; and
  - playback pause preserving Playback controls.
- Resolve requested slots by canvas state, then include only theme-declared
  `PERSISTENT` slots as additional visible slots. `AUTO_FADE`, `ON_DEMAND`, and
  `TRANSIENT` slots remain hidden unless requested by the active canvas.
- Add JVM tests for reducer transitions, idle/pause behavior, persistent-slot
  preservation, and on-demand slot isolation.
- Update architecture/spec documentation with the runtime-to-Spatial adapter
  boundary.

## Non-Goals

- No modification of `PancakeActivity`, Compose UI, video player, Surface
  handoff, Bilibili source, or MediaStage target lifecycle.
- No Spatial SDK adapter yet. The app has not registered the required
  `PanelLayerAlpha` component/system for correct panel-layer fade, so it must
  not claim theme slot visibility is already applied to existing Quest panels.
- No new Entity, panel, scene anchor, static transform, Meta Spatial Editor
  change, Shorts runtime, Focus/PiP runtime, title/toolbar renderer, or
  Browse/Context content redesign.

## Adapter Boundary

```text
stage/panel input + player actual-playback signal
  -> PlaybackCanvasEvent
  -> PlaybackCanvasReducer (pure Kotlin)
  -> PlaybackCanvasState + resolved visible slots
  -> future Spatial PanelLayerAlpha/Visible adapter
```

The reducer does not receive Meta entities, Views, Activity, Surface, player,
coroutines, or timers. A later Spatial adapter will translate state changes to
existing panel layer visibility and input behavior.

## Acceptance Criteria

- Core unit tests cover deterministic canvas transitions and theme-policy slot
  resolution.
- Core source has no Android, Compose, Meta, Media3, player, Surface, network,
  Activity, or coroutine dependency.
- A primary stage action in Quiet Watch resolves to Playback; a Playback idle
  timeout only returns to Quiet Watch if media is actually playing.
- Browse and Context are mutually exclusive on-demand requested slots; neither
  appears in Quiet Watch merely because it is declared in a layout.
- Persistent theme slots remain visible across canvas states.
- Existing app behavior is unchanged until a later Spatial adapter explicitly
  consumes this state machine.
