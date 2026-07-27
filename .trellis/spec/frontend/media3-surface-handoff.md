# Media3 Surface Handoff

## Scenario: In-Activity Video Output Handoff

### 1. Scope / Trigger

Use this pattern when one Media3 `ExoPlayer` moves its video output between
Android `Surface` targets while playback stays in the same activity. The player
implementation belongs in a platform app module; shared Compose UI receives a
render-target composable and neutral metrics only.

### 2. Signatures

```kotlin
fun PlayerManager.attachSurface(surface: Surface)
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
  surface. It does not recreate the player or load media.
* `detachSurface(surface)` only clears the output when `surface` is still the
  manager's current output.
* Every `Surface` created from a `SurfaceTexture` is released after it is
  detached.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Invalid Surface | Do not attach it. |
| Same Surface reattached | Do nothing. |
| Old TextureView destruction after new target attaches | Do not clear the new target. |
| Activity is finishing | Release the player. |
| Configuration change | Keep the singleton player alive; do not release it in the destroyed instance. |

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
* Device test: repeatedly switch the two targets and assert one prepare call,
  no additional decoder initialization, and monotonically increasing position.

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
