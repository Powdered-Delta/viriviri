# Open the Immersive Browse Canvas

## Goal

Make the applied immersive `BROWSE` canvas reachable through the existing
transport and close it back to `PLAYBACK` after a different recommendation is
selected. Reuse the existing `video_selector_panel` and shared app selection
flow.

## Scope

- Add a clear `Browse` command to the existing `controls_id` panel layout.
- On Browse command:
  - record the currently selected video ID as the Browse baseline;
  - dispatch core `OpenBrowse`; and
  - let the existing Playback Canvas Spatial adapter reveal only
    `video_selector_panel` as the on-demand rail.
- Add a pure core `OpenPlayback` event so an explicit user outcome can return
  Browse to Playback without overloading primary-stage input.
- Observe the process-wide app state only while the Spatial Activity lives. If
  Browse is active and the shared selected video ID changes from its baseline,
  dispatch `OpenPlayback`. The existing selection flow remains responsible for
  Bilibili requests and Media3 source replacement.
- Cancel the Activity-owned Browse observer on destroy.
- Add focused core/app JVM tests for Browse -> selection -> Playback transition.
- Update architecture/runbook documentation.

## Non-Goals

- No 2D UX redesign, no new Spatial panel/entity/anchor/transform, no Context
  rail change, no Meta Spatial Editor change, no new video Surface/player, and
  no Bilibili provider change.
- No Browse search/content redesign. The existing selector panel remains the
  current Browse content until its own UX task.
- No automated device installation. Quest validation remains recorded in a
  later archived package.

## Acceptance Criteria

- Existing transport exposes one explicit Browse command.
- Browse makes the existing selector panel visible through the existing canvas
  visibility adapter, not through an ad hoc View/Entity operation.
- Choosing a different recommendation while Browse is active returns the canvas
  to Playback and preserves the one-player/one-active-video-output invariant.
- Opening Browse with no new selection does not bounce immediately back to
  Playback.
- Observer lifecycle is Activity-owned and cancelled on destroy.
- `:spatial-workbench-core:test`, `:app:testDebugUnitTest`, and
  `:app:assembleDebug` pass.
