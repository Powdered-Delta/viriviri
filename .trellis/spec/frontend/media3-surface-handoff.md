# Media3 Surface Handoff

## Scenario: Activity Surface Handoff

### 1. Scope / Trigger

Use this pattern when one process-wide Media3 `ExoPlayer` moves its video output
between Android `Surface` targets while playback stays alive, including a route
between Android Activities. The player implementation belongs in a platform app
module; shared Compose UI receives a render-target composable and neutral
metrics only.

### 2. Signatures

```kotlin
fun PlayerManager.attachSurface(surface: Surface, target: HandoffTarget): Boolean
fun PlayerManager.detachSurface(surface: Surface)
fun PlayerManager.loadTestMedia()
```

`SurfaceHandoffMetrics` is the UI contract:

```kotlin
data class SurfaceHandoffMetrics(
    val prepareCalls: Int,
    val videoDecoderInitializations: Int,
    val surfaceHandoffs: Int,
    val playbackPositionMs: Long,
    val lastHandoffDurationMs: Long?,
)
```

### 3. Contracts

* `loadTestMedia()` is idempotent for the PlayerManager lifetime. It calls
  `setMediaItem()` and `prepare()` only on its first invocation.
* `attachSurface()` clears the previous output before setting the new valid
  surface. It does not recreate the player or load media, and reports whether
  this Surface became the current output.
* `detachSurface(surface)` only clears the output when `surface` is still the
  manager's current output.
* Every `Surface` created from a `SurfaceTexture` is released after it is
  detached.
* A Meta Spatial SDK `VideoSurfacePanelRegistration` supplies an SDK-owned
  `Surface`. Attach and detach it with the same identity checks, but never call
  `release()` on it; `PanelSceneObject` owns its lifetime. Use Media3's
  `Player.Listener.onRenderedFirstFrame()` after that exact Surface attaches as
  the immersive first-frame milestone because there is no `TextureView` update.
* A Spatial `VrActivity` can briefly receive `onStop` while Horizon OS establishes
  the immersive session. Do not release the process singleton from that callback;
  use `onDestroy` as the non-route terminal-release point after detaching the
  SDK-owned surface.
* During a cross-Activity route, launch the destination without releasing the
  player or finishing the source. Attach the destination Surface first, wait for
  its first `SurfaceTexture` update, then finish or remove the source task.
* Route callbacks carry a unique transition ID. Duplicate, stale, and
  post-timeout callbacks are ignored so an old destination cannot complete a
  newer route.
* Player ownership is process-wide only while an Activity owns visible playback
  or a route handoff is pending/completing. A regular 2D Activity may release
  the singleton from `onStop`/`onDestroy` when it is not changing configurations
  and has no pending or completing handoff. A Spatial `VrActivity` must retain
  the singleton through `onStop` and use `onDestroy` as its terminal fallback.
  Both route participants are protected while pending; a source remains
  protected until its destruction after destination first frame, even though the
  controller may accept a new route.
* An opaque transition mask is presentation-only: it remains until the first
  destination frame and must not call media lifecycle APIs.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Invalid Surface | Do not attach it. |
| Same Surface reattached | Do nothing and report that no replacement occurred. |
| Old TextureView destruction after new target attaches | Do not clear the new target. |
| Source Activity is finishing after first destination frame | Keep the singleton player alive; only tear down the Activity/task. |
| Destination misses the handoff timeout | Retain the source and ignore late callbacks. |
| Configuration change | Keep the singleton player alive; do not release it in the destroyed instance. |
| System-menu exit or app background without a route | Release a 2D host from `onStop`; a Spatial `VrActivity` releases from `onDestroy`. |

### 5. Good / Base / Bad Cases

* Good: `prepareCalls == 1`, decoder initialization count remains unchanged,
  and playback position increases across multiple layout switches.
* Base: a target is temporarily unavailable; audio may continue while no video
  Surface is attached.
* Bad: every target destruction calls parameterless `clearVideoSurface()`. A
  delayed old callback can clear the newly attached Surface.

### 6. Tests Required

* Unit test the manager's idempotent load behavior.
* Unit test that detaching a non-current Surface does not clear the current
  Surface.
* Unit test the ordered handoff state: first frame requires surface attachment,
  completion is idempotent, and late callbacks after timeout are ignored.
* Device test: repeatedly switch Activities and assert one prepare call, no
  additional decoder initialization, monotonically increasing position,
  destination attach before source finish, and continuous audio.

### 7. Wrong vs Correct

#### Wrong

```kotlin
override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
    player.clearVideoSurface()
    return true
}
```

#### Correct

```kotlin
override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
    renderSurface?.let { surface ->
        playerManager.detachSurface(surface)
        surface.release()
    }
    return true
}
```
