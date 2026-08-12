# Immersive Transport Timeline Boundary

`SeekTimeline` is an existing semantic transport slot. This task makes the
already-registered `controls_id` seek bar readable and lifecycle-owned; it does
not author a new Spatial object.

The Media3 player is the sole timeline truth source. A local pure
`ImmersiveTransportTimeline` helper normalizes player snapshots for display:

- finite non-negative durations permit seeking;
- elapsed position is clamped to known duration;
- unavailable duration is non-seekable and renders `--:--`;
- a drag snapshot can replace player position only while the user is dragging.

The Activity owns the single 500 ms runnable through its existing main-thread
`canvasHandler`, starts it after the controls panel resolves, and removes it in
`onDestroy()`. The runnable does not create, prepare, seek, attach, detach, or
otherwise change the existing player/video Surface.
