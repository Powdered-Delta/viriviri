# Lifecycle Cleanup and Reference Samples

## Device evidence

The three visible handoff modes were tested in clean and repeated runs:

- `Direct + recovery`: one run completed four round trips, then failed on the fifth immersive-to-panel route with a long black screen and crash.
- `Clear + recovery`: first round trip completed; second immersive-to-panel route failed with a long black screen and crash in two runs.
- `Reprepare baseline`: first round trip completed; second immersive-to-panel route hung in two runs.

The shared failure is often before `PanelActivity.onCreate()`:

```text
source_activity_destroyed
MediaCodec NO_INIT/BAD_VALUE
player ERROR_CODE_DECODING_FAILED -> IDLE
destination Activity/Surface callback absent
transition timeout
```

The system also logs `No component for base intent of task`. The app process may remain alive after the player is released. The correct diagnosis is incomplete Activity/task/Spatial/native resource cleanup, not a claim that ART GC is absent.

## MediaSpatialAppTemplate

`VideoFragment` creates a local ExoPlayer and releases it in `onStop()`:

```kotlin
override fun onStop() {
  super.onStop()
  player?.release()
  player = null
}
```

This is a normal Activity/Fragment lifecycle example. It does not preserve a player across Spatial and system 2D Activities.

## Meta Spatial SDK MediaPlayerSample

`MediaPlayerSampleActivity` creates an ExoPlayer for its Spatial video surface and releases it in `onSpatialShutdown()` before calling the superclass:

```kotlin
override fun onSpatialShutdown() {
  exoPlayer?.release()
  exoPlayer = null
  super.onSpatialShutdown()
}
```

The sample uses `VideoSurfacePanelRegistration`, calls `setVideoSurface(surface)`, then sets a MediaItem and prepares. It has no cross-Activity handoff.

## PremiumMediaSample

`PremiumMediaSample` keeps one ExoPlayer while an immersive video entity exists. Its entity teardown is explicit:

```kotlin
entity.destroy()
exoPlayer.setVideoSurface(null)
exoPlayer.clearMediaItems()
controlPanelPollHandler.stop()
currTween?.cancel()
unregisterPanel(id)
```

It does not cross into a system 2D Activity and does not establish decoder continuity across Spatial session shutdown.

## Conclusions

The references do not prove that Qualcomm decoder state can survive Spatial session shutdown and move to a system `TextureView`. Their reliable pattern is explicit stop, detach, unregister, clear, and release at the owner boundary. The next implementation must first make resource ownership and terminal cleanup observable and deterministic, then evaluate same-player continuity.

## Planned implementation order

1. Add a single terminal route cleanup operation that removes controller Handler callbacks and clears source/destination references.
2. Make `PlayerManager.release()` cancel recovery callbacks, clear surface ownership, remove listeners, and log a resource snapshot.
3. Make Panel position refresh explicitly cancellable and stop it at `onStop`/`onDestroy`.
4. Add decoder release/initialization and Surface ownership metrics.
5. Add repeated five-round device validation with a clean process baseline.
