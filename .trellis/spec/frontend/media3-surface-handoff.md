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
    val handoffDecoderRecoveries: Int,
    val surfaceHandoffs: Int,
    val playbackPositionMs: Long,
    val lastHandoffDurationMs: Long?,
)
```

### 3. Contracts

#### 3.0 Resource ownership boundary

The process-wide player does not make native resources process-wide by itself. ExoPlayer, MediaCodec, Spatial compositor sessions, SDK-owned Surfaces, TextureView Surfaces, route callbacks, panel jobs, and entity registrations each require an explicit owner and terminal cleanup path. Java garbage collection is not a substitute for `release()`, `setVideoSurface(null)`, callback cancellation, coroutine cancellation, panel unregistration, or entity destruction.

Reference projects under `temp/` use explicit owner-bound cleanup. `MediaSpatialAppTemplate` releases its Fragment player in `onStop`; `MediaPlayerSample` releases its Spatial player in `onSpatialShutdown`; `PremiumMediaSample` destroys the entity, detaches and clears player media, stops polling, cancels tweens, and unregisters the panel. None implements this project's cross-Activity handoff.

* `loadTestMedia()` is idempotent for the PlayerManager lifetime. It calls
  `setMediaItem()` and `prepare()` only on its first invocation.
* `attachSurface()` normally clears the previous output by identity before setting the new
  valid surface. For a protected immersive-to-system-panel destination replacement only,
  it sets the new TextureView Surface directly without app-level detachment of the old
  Spatial Surface. This avoids the confirmed Quest detach timeout; all other replacements
  remain clear-then-set. After a replacement only, the manager explicitly calls the
  existing player's `play()` to reassert `playWhenReady` and reacquire audio
  focus after an Activity focus change. It does not recreate the player, load
  media, seek, or prepare; a same-Surface no-op must not issue this playback
  request.
* On a protected immersive-to-system-panel attach, the direct replacement still happens
  first. If a decoder/video error was observed or the player entered `IDLE`, the manager
  consumes one recovery for that transition, retains the existing media item and player,
  seeks to the monotonic position, calls `prepare()` on that item, and calls `play()`. This
  is a decoder reinitialization fallback, not a media reload. Recovery failure remains an
  explicit failed transition and listener callbacks cannot loop it.
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
* `ImmersiveActivity.onSpatialShutdown()` and `onDestroy()` must call the required
  lifecycle super methods without app-level `clearVideoSurface()` for a protected
  immersive-to-system-panel route. Preserve manager ownership until the destination
  replacement; the SDK still releases its compositor Surface. The SDK-owned Surface
  is never released by the app.
* Quest/Meta Spatial SDK requires non-overlap for this route. Protect the player,
  request the official route, fully destroy the source Activity and Spatial
  panel/session, then allow the destination Surface to attach. A short no-video
  interval is expected and measured.
* Route callbacks carry a unique transition ID. Duplicate and mismatched
  callbacks are ignored so an old destination cannot complete a newer route.
  A bounded timeout is terminal: record an explicit failed state, clear pending
  protection, and permit an explicit retry where a UI host remains. Never retain
   stale transition state indefinitely. Every terminal route state must remove all
   controller Handler callbacks, clear source/destination references, cancel
   decoder recovery callbacks, and emit a resource snapshot. A stale Activity
   callback must not carry an old transition ID into a later route.
* Panel-to-immersive return must finish/remove `PanelActivity`, wait for its
  post-`super.onDestroy()` callback, confirm that no registered VrActivity
  remains, and only then launch one fresh `ImmersiveActivity`. Never reuse or
  bring forward a retained VrActivity. Meta's `VrActivityProcessGuard` rejects
  multiple VR Activities in one process.
* Player ownership is process-wide only while an Activity owns visible playback
  or a route handoff is pending/completing. A regular 2D Activity may release
  the singleton from `onStop`/`onDestroy` when it is not changing configurations
  and has no pending or completing handoff. A Spatial `VrActivity` must retain
  the singleton through `onStop` and use `onDestroy` as its terminal fallback.
  Both route participants are protected while pending, including the intentional
  source-destroyed-to-target-startup gap.
* An opaque transition mask is presentation-only: it remains until the first
  destination frame and must not call media lifecycle APIs.
* Player diagnostics log state, playWhenReady, isPlaying, errors, video size,
  rendered first frame, and audio-session changes with the current transition
  and target. TextureView diagnostics log available dimensions, attach result,
  first texture update, and destruction. These are event-level logs only, never
  per-frame logs.
* Replacement diagnostics log `replacementMode=clear_then_set`, old/new targets,
  and Surface identities. Route diagnostics additionally log ordered route state,
  source destruction, destination launch/attach, first frame, and explicit
   failure reason. Decoder initialization without decoder-release observation is
   insufficient for leak diagnosis. Record decoder initialization, release,
   error, player release, current Surface ownership, and panel/entity cleanup at
   route boundaries.

### 4. Validation & Error Matrix

| Condition | Required behavior |
| --- | --- |
| Invalid Surface | Do not attach it. |
| Same Surface reattached | Do nothing and report that no replacement occurred. |
| Replacement Surface attaches but playback stalls | Reassert `player.play()` once and inspect the ordered player/TextureView diagnostic events; do not prepare, reload, seek, or recreate. |
| Spatial-to-Texture destination | For the protected handoff, skip app-level Spatial clear during shutdown, fully tear down the Spatial Activity/session, then attach the TextureView with direct replacement. Normal paths remain clear-then-set. |
| Old TextureView destruction after new target attaches | Do not clear the new target. |
| Source Activity is finishing after first destination frame | Keep the singleton player alive; only tear down the Activity/task. |
| Destination misses the handoff timeout | Record an explicit failed state, clear pending state, and expose retry where possible. Do not accept stale late callbacks. |
| Configuration change | Keep the singleton player alive; do not release it in the destroyed instance. |
| System-menu exit or app background without a route | Release a 2D host from `onStop`; a Spatial `VrActivity` releases from `onDestroy`. |

### 5. Good / Base / Bad Cases

* Good: the same player/media identity is retained, playback position is monotonic, and
  the destination produces a first frame across repeatable routes. `prepareCalls` and
  decoder initialization continuity are platform-dependent after a protected recovery.
* Base: a target is temporarily unavailable; audio may continue while no video
  Surface is attached.
* Bad: every target destruction calls parameterless `clearVideoSurface()`. A
  delayed old callback can clear the newly attached Surface.

### 6. Tests Required

* Unit test the manager's idempotent load behavior.
* Unit test that detaching a non-current Surface does not clear the current
  Surface.
* Unit test both directional non-overlap orderings: source destruction gates
  target attach/launch, first frame requires Surface attachment, completion is
  idempotent, and failure is terminal.
* Device test: force-stop before launch, repeatedly switch Activities, assert one
  VrActivity, same player/manager/media identities, monotonic retained position,
  source destruction before target attach, destination first frame, and explicit route
  completion. Log prepare and decoder reinitialization as platform-dependent results;
  a protected recovery is allowed only once per transition.

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
