# Integrate the Immersive MediaStage

## Goal

Make the existing Quest `spatialized_video_panel` consume the unified pure-core
`MediaStageReducer` before any 2D UX rewrite. This task integrates only the
current immersive video target and Media3 clock lifecycle; it does not create
an overlay renderer yet.

## Scope

- Add an app-side `ImmersiveMediaStageHost` adapter that:
  - owns the mapping from the semantic `immersive-video` target ID to the
    SDK-owned `PanelSceneObject.surface`;
  - registers that target as the one enabled `VIDEO_OUTPUT` in
    `MediaStageTargetRegistry`;
  - dispatches attach/clock/seek events to `MediaStageReducer`;
  - executes only the returned immersive video attach effect through the
    existing process-wide `PlayerSession`; and
  - removes its Media3 listener when the Spatial Activity is destroyed.
- In `SpatialVideoSampleActivity`, replace direct calls to
  `PlayerSession.attachImmersiveSurface()` with the host adapter when the
  existing video `PanelSceneObject` is created or resumed.
- Forward meaningful Media3 events to the adapter:
  - ready/playing changes update `MediaClockSnapshot`;
  - position discontinuity reports a seek/discontinuity; and
  - player errors never trigger a second player, target, or Surface.
- Preserve the existing `spatialized_video_panel` entity, its MediaPanel,
  geometry, MR mode behavior, and 2D handoff behavior.
- Add app JVM tests for adapter effect execution and lifecycle behavior, plus
  focused core regression tests as needed.
- Add event-level logging for target attach and reducer lifecycle effects. Do
  not add per-frame logs.

## Non-Goals

- No 2D UX redesign and no change to `PancakeActivity` or `RecommendationUi`.
- No danmaku/caption source, overlay target, text renderer, Compose overlay,
  Meta overlay entity, or second panel Surface.
- No new static scene entity or anchor. `mse-agent` remains unavailable and is
  not needed because this task reuses the existing runtime video panel.
- No change to Bilibili source handling, player creation, or media loading.
- No player release or app-level clearing of the SDK-owned immersive Surface
  during the protected immersive-to-2D route.

## Lifecycle Contract

```text
existing PanelSceneObject.surface (SDK-owned)
  -> ImmersiveMediaStageHost.attachSurface(surface)
  -> MediaStageEvent.AttachVideoOutput("immersive-video")
  -> MediaStageEffect.AttachVideoOutput("immersive-video")
  -> PlayerSession.attachImmersiveSurface(surface)
```

`ImmersiveMediaStageHost` never releases the panel Surface. If the panel Surface
changes while the immersive host remains active, replacement is delegated to the
existing identity-aware `PlayerSession`. On an old/duplicate callback, the core
reducer produces no duplicate attach.

On a protected immersive-to-2D route, the existing `beginOutputHandoff()` stays
as the only route preparation and the Spatial host does not dispatch an explicit
immersive detach during Activity shutdown. The destination 2D target owns the
replacement. The adapter only removes its player listener at terminal host
destruction.

## Acceptance Criteria

- The existing Spatial video panel remains the only immersive `VIDEO_OUTPUT`
  target and still uses one `ExoPlayer`/one active video Surface.
- Panel creation and `onResume` attach through `ImmersiveMediaStageHost`, not a
  direct Activity-to-PlayerSession call.
- The SDK-owned panel Surface is never released by app code.
- Reducer attach effects are identity-safe; reusing the same target/surface does
  not issue duplicate player attachment.
- Player state/discontinuity updates enter the core clock lifecycle without
  per-frame polling or per-frame logs.
- `:spatial-workbench-core:test`, `:app:testDebugUnitTest`, and
  `:app:assembleDebug` pass.
- Quest validation confirms the app starts into immersive mode, retains one
  active process, and has no crash-buffer output. Visual panel behavior remains
  validated manually from the headset library.
