# Existing Transport State

`SpatialVideoSampleActivity.onVRReady()` already configures the existing
`controls_id` panel as a child of `spatialized_video_panel`:

```kotlin
Transform(Pose(Vector3(0f, -0.6f, -0.15f), Quaternion(20f, 0f, 0f)))
TransformParent(Entity(R.id.spatialized_video_panel))
```

The negative local Z is the existing front-of-stage relation in this scene's
coordinate convention. It is reused unchanged because no `mse-agent` is
available for visual anchor editing.

## Problems

- The old `CountDownTimer(100, 100)` fades controls after 100ms.
- Fade changes only root View alpha, leaving input behavior implicit.
- Clicking the video panel always toggles play/pause; it cannot first reveal
  a hidden transport.

## First Runtime Increment

Use a pure decision helper for overlay visibility and stage-primary-click
behavior. The Spatial Activity maps it onto only the existing controls root:
visible controls use alpha 1 and enabled input; hidden controls use alpha 0 and
non-clickable/non-focusable input. Player state remains the existing truth
source. This does not alter any static Transform, scene entity, player, or
Surface.
