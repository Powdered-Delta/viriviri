# Spatial Video Surface To TextureView Handoff Research

## Phase B Device Conclusion

Subsequent physical Quest evidence supersedes the direct-replacement hypothesis
below. Retaining the old Spatial `VrActivity` until the destination first frame
left multiple Activities/ViewRoots, did not produce stable TextureView rendering,
and caused a later `Multiple VR activities in same process` failure. Calling
`setVideoSurface(newTextureSurface)` directly improved route speed but still did
not produce the first frame. Cold `PanelActivity` playback remains reliable.

The implemented conclusion is lifecycle-confirmed non-overlap: protect the one
prepared player, fully detach and destroy the Spatial host, then create/attach
the TextureView target. On return, destroy PanelActivity before launching one
fresh ImmersiveActivity. Decoder preservation across the Surface-type switch is
measured but not forced with player/media reprepare.

## Question

The direct cold `PanelActivity` path is healthy: its `TextureView` obtains a
valid `Surface`, Media3 reaches `READY`, renders a first frame, and emits a
`TextureView` update. The failed hybrid path is specifically an output change
from the Meta Spatial SDK-owned `VideoSurfacePanelRegistration` surface to that
same type of panel `TextureView` surface. The player position freezes and there
is no new Media3 rendered-first-frame or `TextureView` update.

This note investigates whether the existing `ExoPlayer` and decoder can be
kept, and the smallest safe output-switch test. Sources are the locally
resolved Meta Spatial SDK 0.13.0 and Media3 1.8.0 artifacts, plus GitHub API
queries restricted to official Meta samples and AndroidX Media issue/source
records.

## Result

There is no official Meta cross-Activity, Spatial-panel-to-Android-view video
handoff example or issue. The official direct-panel samples do establish that a
`VideoSurfacePanelRegistration` supplies an ordinary Android `Surface` intended
for `ExoPlayer.setVideoSurface(surface)`. Media3 explicitly supports replacing
the current video output with `setVideoSurface(newSurface)` while retaining the
player and prepared media. This is the correct first attempt for retaining the
same decoder, subject to device codec compatibility.

The current app introduces an avoidable no-output transition twice:

1. `ImmersiveActivity.onSpatialShutdown()` calls `detachPanelSurface()`, which
   calls identity-safe `clearVideoSurface(oldSpatialSurface)`.
2. `PlayerManager.attachSurface()` clears `previous` and then calls
   `setVideoSurface(next)` when the panel `TextureView` arrives.

For a route leaving immersive mode, the first clear happens before the 2D
panel's target exists. The second clear creates a further intermediate null
output immediately before the new target. Neither call reloads media, but both
force a surface-detach operation during the sensitive codec/compositor switch.

## Local API Evidence

### Meta Spatial SDK 0.13.0

Inspected local artifacts:

* `C:\Users\N00b\.gradle\caches\modules-2\files-2.1\com.meta.spatial\meta-spatial-sdk\0.13.0\9fa3c7d2660d8961aae013bea5d8fdb53feb2d81\meta-spatial-sdk-0.13.0.aar`
* `C:\Users\N00b\.gradle\caches\modules-2\files-2.1\com.meta.spatial\meta-spatial-sdk-toolkit\0.13.0\5b8a1aa99c3f76779eb64582bdc226c95277df66\meta-spatial-sdk-toolkit-0.13.0.aar`

The toolkit's `VideoSurfacePanelRegistration` constructs a panel scene object
for its entity and supplies the panel's Android `Surface` to the registered
consumer. The supplied surface is SDK-owned, not app-created. The app may
select it as Media3 output and may clear that output by identity, but it must
not call `Surface.release()`.

`Entity.createPanelEntity(..., Visible(...))` controls whether a dynamic
registered panel has a visible entity. `Visible` is presentation/entity state;
it is not a video-output detach or panel-registration teardown API. Hiding or
that the Android `Surface` has been detached from the Spatial compositor.

The public 0.13.0 surface-registration shape exposed to this app has a
surface-available consumer, not a paired public surface-destroy callback. The
app must therefore retain the supplied `Surface` identity and only clear it if
it is still Media3's current output.

`onSpatialShutdown()` is an SDK lifecycle callback, not an optional per-panel
unregistration signal. It must still call `super.onSpatialShutdown()` so the
Spatial activity/runtime can shut down. Official samples use it for terminal
application cleanup because their players are activity-owned:

* `MediaPlayerSampleActivity.onSpatialShutdown()` releases its activity-owned
  `ExoPlayer`, clears its field, then calls `super`.
* `PremiumMediaSample` destroys its activity-owned immersive model from
  `onSpatialShutdown()`; that model's terminal `destroy()` destroys the entity,
  sets the player surface to null, clears media items, and unregisters its
  dynamic registration.

Those are terminal ownership examples, not a requirement to clear an output
before an in-process handoff. They must not be copied into this process-wide
player route. The app's `onSpatialShutdown()` must remain for `super`, but its
app-level surface detach is a separate policy decision.

### Registration and unregistration

Official `PremiumMediaSample` dynamically registers a
`VideoSurfacePanelRegistration` through `AppSystemActivity.registerPanel(...)`.
Its terminal `ExoVideoEntity.destroy()` sequence is:

1. destroy the entity;
2. `exoPlayer.setVideoSurface(null)`;
3. clear media items and stop UI work;
4. `unregisterPanel(id)`.

The sample's `unregisterPanel` helper removes the ID from the activity's
registration map and removes its creator from `PanelCreationSystem`; its own
comment says this prevents functions from leaking objects. It is a terminal
dynamic-registration cleanup mechanism. It is not used as a prerequisite to
switch an ExoPlayer to another output, and no official sample performs
cross-Activity surface handoff.

For this app's static `registerPanels()` registration, do not add manual
unregistration as part of the handoff experiment. It would change the Spatial
entity/registration lifecycle and could prevent the retained immersive host
from reusing its valid panel surface on return.

## Media3 1.8.0 Evidence

Inspected local artifacts:

* `C:\Users\N00b\.gradle\caches\modules-2\files-2.1\androidx.media3\media3-common\1.8.0\27ff85653e3436399a9d485dd6e3f84c4f9c97b8\media3-common-1.8.0.aar`
* `C:\Users\N00b\.gradle\caches\modules-2\files-2.1\androidx.media3\media3-exoplayer\1.8.0\3c972cbcc05645a14fd133e1f92648a7c3c0b0a8\media3-exoplayer-1.8.0.aar`

The Media3 `Player` contract provides both forms:

* `setVideoSurface(surface)`: selects the output surface; the caller tracks its
  lifecycle and must set null if that surface is destroyed.
* `clearVideoSurface(surface)`: clears only if the supplied object is the
  current output; otherwise it does nothing.

This identity-specific clear is the right stale-callback safety primitive. It
does not require an explicit `clearVideoSurface(old)` before replacing with a
new valid surface.

The AndroidX 1.8.0 `ExoPlayerImpl` implementation of
`setVideoSurface(surface)` removes any view callbacks and calls its internal
`setVideoOutputInternal(surface)`. Its identity overload
`clearVideoSurface(surface)` clears only when `surface == videoOutput`.
The implementation intentionally does not make a same-output replacement a
no-op so that `onRenderedFirstFrame` can still occur.

The renderer accepts `MSG_SET_VIDEO_OUTPUT` and routes a non-null replacement
to `MediaCodec.setOutputSurface(surface)` on API 23+. This is the decoder
preservation path. It can retain the configured codec; it does not imply a
`setMediaItem`, `prepare`, seek, or a new `ExoPlayer`.

However, Media3 cannot guarantee every codec accepts every valid Android
surface as a replacement. AndroidX issue #457 reports an `IllegalArgumentException`
from `MediaCodec.setOutputSurface` on an Amlogic decoder during output switching,
while another device eventually recovered only after several seconds. AndroidX
issue #3011 separately reports this same framework call failing when a
`TextureView` surface is rapidly replaced or invalidated. These reports make
surface validity and ordering material, even with one player.

## Recommended Smallest Testable Hypothesis

**Hypothesis:** The hybrid stall is caused by the two intermediate null-output
operations, particularly the app-initiated clear in `onSpatialShutdown()` before

The isolated implementation test should do only the following:

1. In `ImmersiveActivity.onSpatialShutdown()`, retain `super.onSpatialShutdown()`
   but do not call the app's `detachPanelSurface()` solely because this is a
   protected immersive-to-panel handoff. Keep the current identity reference;
   do not release the SDK-owned surface.
2. In `PlayerManager.attachSurface(next, ...)`, when `next` is a different,
   valid surface, call `player.setVideoSurface(next)` directly. Do not first
   call `player.clearVideoSurface(previous)`.
3. Retain the existing identity-safe `detachSurface(old, ...)` for actual
   destruction. After a direct replacement it will be a no-op because Media3's
   identity overload sees that `old` is no longer current. For a non-handoff
   terminal shutdown, it still clears the current output before the app-owned
   texture surface is released.
4. Do not call `setMediaItem`, `prepare`, `stop`, `release`, `clearMediaItems`,
   `unregisterPanel`, `Entity.destroy`, or `Surface.release` as part of this
   test. Do not change `Visible` state.
5. Preserve `player.play()` once after an actual replacement. It is independent
   of the output switch and remains useful evidence for focus loss. Log before
   and after its call.

Expected successful log order is: route request, Spatial shutdown (without an
app-level clear), panel `TextureView` available, one direct
`setVideoSurface(newTextureSurface)`, `play()`, advancing position/audio, Media3
`onRenderedFirstFrame`, then `TextureView.onSurfaceTextureUpdated`. The old
Spatial surface's delayed detach must log `not_current` and must not change the
new output.

This tests only whether an intermediate null output is causing the freeze. It
does not claim that the Spatial compositor can keep rendering after its runtime
shutdown, nor does it require both outputs to be simultaneously rendered.

## Risk Assessment

| Risk | Assessment | Mitigation / decision signal |
| --- | --- | --- |
| The Spatial runtime invalidates its surface during `super.onSpatialShutdown()` before the 2D target arrives. | Medium. The app cannot keep a Spatial panel surface alive after the SDK shuts down. Retaining only the Media3 reference briefly is still preferable to actively issuing a null-output command first. | Log `old.isValid` before/after shutdown and the exact direct-set time. If Media3 reports an output-surface exception, abandon decoder preservation for this device route rather than re-preparing automatically. |
| The Quest hardware codec rejects a Spatial-surface-to-TextureView `MediaCodec.setOutputSurface` transition because of buffer usage/protection constraints. | Medium to high. Direct cold TextureView success does not prove a configured decoder can retarget from a compositor surface. AndroidX has device-specific failures in this API. | Capture `onPlayerError`, error code/cause, decoder name, and logcat `MediaCodec`/`SurfaceUtils`. A deterministic `IllegalArgumentException` is evidence that one-decoder transfer is unsupported in practice on this device. |
| A late old-surface lifecycle callback clears the new TextureView output. | Low with identity-specific clear. | Keep `clearVideoSurface(old)` only through `detachSurface(old)` and retain the current identity checks. Verify the old callback logs `not_current`. |
| Removing the app-level detach leaks an SDK-owned Surface reference. | Low if the field is cleared when the terminal activity destruction or late detach occurs; the SDK, not the app, remains owner. | Do not call `release()`. Clear only the app's reference after the replacement or terminal lifecycle point, without sending a second player clear. |
| `onSpatialShutdown()` is skipped or `super` is not called. | High and unacceptable. This risks breaking Meta runtime teardown. | The test must leave `super.onSpatialShutdown()` in place and measure it. Only conditionally omit the app's player-output clear during a protected route. |
| Calling `play()` cannot repair an invalid output surface. | High certainty. It only reasserts play/focus; it cannot make MediaCodec accept an invalid target. | Treat lack of position/audio progress and no first frame after direct switch as output transition evidence, not a signal to loop `play()` or reprepare. |
| Official samples release/clear in `onSpatialShutdown()`. | Not applicable to the shared-player policy. Their cleanup is terminal and activity-owned. | Keep the process-wide ownership contract; do not import sample teardown wholesale. |

## Official Source Record

GitHub API queries were used; no raw HTTP or curl was used.

* Meta Spatial SDK Samples, `MediaPlayerSampleActivity.kt`: direct
  `VideoSurfacePanelRegistration` creates an `ExoPlayer` and calls
  `setVideoSurface(surface)`; its `onSpatialShutdown()` releases that
  activity-owned player. Source:
  `https://github.com/meta-quest/Meta-Spatial-SDK-Samples/blob/main/MediaPlayerSample/app/src/main/java/com/meta/spatial/samples/mediaplayersample/MediaPlayerSampleActivity.kt`
* Meta Spatial SDK Samples, `PremiumMediaSample` `ExoVideoEntity.kt`: direct
  panel registration uses `setVideoSurface(surface)` and terminal `destroy()`
  calls `entity.destroy()`, `setVideoSurface(null)`, clears media, then
  unregisters the dynamic panel. Source:
  `https://github.com/meta-quest/Meta-Spatial-SDK-Samples/blob/main/PremiumMediaSample/app/src/main/java/com/meta/spatial/samples/premiummediasample/entities/ExoVideoEntity.kt`
* Meta Spatial SDK Samples, `PremiumMediaSample` `Utils.kt`: `unregisterPanel`
  removes the dynamic registration and panel creator from the toolkit maps.
  Source:
  `https://github.com/meta-quest/Meta-Spatial-SDK-Samples/blob/main/PremiumMediaSample/app/src/main/java/com/meta/spatial/samples/premiummediasample/Utils.kt`
* AndroidX Media `Player.java` and `ExoPlayerImpl.java`: the documented
  identity-aware clear and replacement surface APIs. Sources:
  `https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/Player.java`
  and
  `https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java`
* AndroidX Media issue #457, open: codec output surface switching can fail on
  specific hardware. `https://github.com/androidx/media/issues/457`
* AndroidX Media issue #3011, closed: rapid TextureView output replacement can
  reach `MediaCodec.setOutputSurface` with an invalidated surface.
  `https://github.com/androidx/media/issues/3011`

No Meta sample or official Meta issue found by the allowed GitHub code/issue
search implements a Spatial `VideoSurfacePanelRegistration` to separate Android
Activity `TextureView` handoff while retaining one prepared `ExoPlayer`.
