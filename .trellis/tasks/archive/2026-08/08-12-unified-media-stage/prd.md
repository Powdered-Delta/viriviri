# Unified MediaStage Runtime Contracts

## Goal

Create the first executable, platform-neutral `MediaStage` runtime contract in
`:spatial-workbench-core`. It must let a redesigned 2D host and the Quest
Spatial host consume the same stage state and lifecycle rules without binding
this milestone to either current UI.

`MediaStage` is the media-only portion of the workbench. It owns semantic stage
presentation, the one active video-output target, clock snapshots, and the
lifecycle of non-video overlay targets. Browse, search, creator/context views,
transport layout, protocol adapters, and account flows remain outside it.

## Product Decision

Do not render danmaku in the existing `PancakeActivity` or attach it to the
current Spatial panel as a shortcut. Both current hosts are legacy adapters.
The first implementation establishes the shared MediaStage contract that the
new 2D UX and the future Spatial renderer will each adopt.

## Scope

- Add pure Kotlin MediaStage contracts for:
  - stage presentation (`WATCH`, portrait `SHORTS`, and `FOCUS` PiP);
  - flat/cylinder stage geometry with valid physical dimensions;
  - a clock snapshot supplied by the existing Media3 adapter, not polled by
    core;
  - renderer target declarations for exactly one video output plus any number
    of Flat or Spatial overlay targets;
  - target enablement/activation state and target identity;
  - reducer events for video-output attachment, overlay enablement, playback
    updates, seek/discontinuity, and presentation changes; and
  - declarative effects for platform adapters and overlay pipelines.
- Enforce these invariants in validation/reducer behavior:
  - only a `VIDEO_OUTPUT` target may become active video output;
  - exactly zero or one video target may be active at a time;
  - an overlay target is never a video Surface and can coexist with the active
    video output;
  - a disabled or unregistered target cannot become active;
  - a seek/discontinuity clears active overlays before rescheduling;
  - disabling an overlay target clears only that target;
  - pause/resume changes overlay lifecycle without fabricating a player state;
  - stage geometry/presentation changes request stage-locked overlay cleanup
    rather than creating a second stage or player.
- Add JVM unit tests for target validation, video-output exclusivity, stale
  detachment, overlay enable/disable behavior, pause/seek lifecycle effects,
  and presentation/geometry changes.
- Update the architecture spec with the concrete host adapter boundary.

## Non-Goals

- No Compose screens or rework of `PancakeActivity` in this task.
- No Meta Spatial SDK entity, panel, pose, or scene-anchor binding.
- No Media3/ExoPlayer, Android `Surface`, `TextureView`, `Activity`, coroutine,
  network, Bilibili XML/Proto, subtitles, danmaku source, or actual renderer.
- No real glyph layout, track collision, viewer-projection occlusion, caption
  translation, or overlay source scheduling.
- No second player, second active video output, or new fixed scene entity.

## Adapter Boundary

```text
PlayerSession (Media3, process-wide)
  -> MediaClockSnapshot + MediaStageEvent
  -> MediaStageReducer (pure Kotlin)
  -> MediaStageEffect
  -> 2D host adapter / Spatial host adapter / overlay renderer adapter
```

The existing `PlayerSession` remains sole owner of ExoPlayer and its active
video Surface. A host reports target availability and player-clock snapshots to
the reducer, performs the returned attach/detach effect with its own platform
handle, and reports the result. Core never receives a platform handle.

`OverlaySurfaceSpec` remains the semantic overlay target configuration from the
prior core task. A MediaStage renderer target references it by ID; it does not
turn it into a video output target.

## Acceptance Criteria

- `:spatial-workbench-core:test` passes with focused MediaStage reducer tests.
- `:app:testDebugUnitTest` remains green without changing existing 2D or
  Spatial host behavior.
- Core source imports no Android, Compose, Meta, Media3, Bilibili, network,
  `Activity`, `Surface`, or coroutine APIs.
- Equal state/event input gives equal state/effect output.
- Target replacement yields an ordered detach-old then attach-new video-output
  effect; a stale detach cannot remove the newer output.
- Overlay target disable and seek use explicit cleanup effects and never touch
  video-output ownership.
- A stage presentation change preserves the single video-output target while
  requesting only the appropriate stage-locked overlay reset.
