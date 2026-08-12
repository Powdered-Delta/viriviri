# Immersive MediaStage Host Integration

## Existing Spatial Video Target

`SpatialVideoSampleActivity.createVideoPanel()` already creates exactly one
`PanelSceneObject` for `Entity(R.id.spatialized_video_panel)`. The object owns
an SDK-provided `Surface`; the app currently passes it directly to
`PlayerSession.attachImmersiveSurface()` at creation and again from `onResume`.

The panel is a runtime object associated with an existing scene/panel ID. This
task does not add an `Entity.create()` object, a static anchor, or another media
panel.

## Adapter Design

`ImmersiveMediaStageHost<Output>` uses a generic host output handle so its
lifecycle can be unit tested without an Android `Surface`:

```text
SDK Surface
  -> host.attachOutput(surface)
  -> core AttachVideoOutput("immersive-video")
  -> AttachVideoOutput effect
  -> PlayerSession.attachImmersiveSurface(surface)
```

The core reducer knows only the target ID. The host retains the platform handle
only while its Spatial Activity is alive. A repeated callback with the same
handle emits no player attach. A replacement SDK handle for the same semantic
target is delegated to the existing identity-aware `PlayerSession` even though
the target ID does not change.

## Lifecycle

- Media3 listener updates core clock state only on player state/playing changes
  and position discontinuities, never on a frame timer.
- `onDestroy()` removes the listener and drops the host handle reference.
- The app does not release the SDK-owned panel Surface.
- The immersive-to-2D route retains its existing `beginOutputHandoff()` behavior
  and does not introduce an explicit core/video detach during Spatial teardown.
