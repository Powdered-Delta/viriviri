# Quest Validation Notes

## 2026-08-02 Stage B Replan

The YouTube VR APK provides a corrected reference shape. It declares both a
dedicated VR Activity and a real system 2D Panel Activity. The 2D entry point
uses `com.oculus.intent.category.2D`, is resizeable, has system panel layout
dimensions, and uses `singleTask`. Therefore the project should continue to
test a real `PanelActivity` plus `ImmersiveActivity` route rather than replacing
the system window with an in-scene imitation.

The priority order is now:

1. Make Horizon OS Activity/task materialization repeatable in both directions.
2. Make media item and playback position restoration explicit and observable.
3. Preserve the same process-level player when it remains valid.
4. Treat same MediaCodec continuity as a best-effort device result.

The second-round failure currently occurs before `PanelActivity.onCreate()` and
therefore cannot be fixed by decoder recovery or Surface replacement. The next
device run must classify the failure as one of:

```text
destination Activity/task not materialized
destination Activity created but Surface not available
Surface attached but player/decoder failed
first frame missing after valid player output
```

The first implementation baseline may explicitly release and recreate the
host-bound player/decoder after a route, provided that it restores the same
media identity and position before enabling the destination UI. This gives the
project a deterministic fallback while preserving a later path to same-player
continuity experiments.

Do not add another decoder recovery mode until the destination Activity/task
failure is separately measured and reduced.

## Protected Spatial-to-Texture Recovery Follow-up

Quest 2 evidence: direct protected Spatial-to-Texture replacement can produce
Qualcomm MediaCodec `NO_INIT`/`BAD_VALUE`, ExoPlayer
`ERROR_CODE_DECODING_FAILED`, and `IDLE` before the panel TextureView attaches.
The app preserves the existing PlayerManager, ExoPlayer, media item, and position,
then performs at most one decoder reinitialization fallback after the destination
Surface is attached. The fallback logs `handoff_decoder_recovery` and increments
`handoffDecoderRecoveries`.

Acceptance is same player/manager/media identity, monotonic position, destination
first frame, and repeatable routes. `prepareCalls` and decoder initialization
continuity are platform-dependent after this recovery and are not the sole failure
criterion.

## 2026-07-29 Phase B Non-Overlap Redesign

Physical Quest evidence invalidated the retained-source overlap contract. Keeping
the old `ImmersiveActivity` and Spatial panel/session alive until the incoming
panel first frame prevented repeatable Spatial-to-TextureView rendering and left
multiple Activities/ViewRoots. A later panel-to-immersive route then failed with
Meta's `Multiple VR activities in same process` guard. The experimental direct
Spatial-to-TextureView `setVideoSurface(new)` path improved route speed but did
not produce a first frame. A cold PanelActivity renders correctly.

The new route is intentionally non-overlapping:

* Immersive-to-panel requests Home plus PendingIntent, protects the singleton,
  requests source task removal, always detaches the Spatial Surface and shuts
  down the SDK session, and withholds the panel TextureView until the old
  ImmersiveActivity has completed `onDestroy()`.
* Panel-to-immersive protects the singleton and removes PanelActivity first. Its
  post-destroy callback schedules exactly one fresh ImmersiveActivity after a
  zero-existing-VR check. No retained VrActivity is reused or brought forward.
* Surface attach uses normal clear-then-set. No route prepares, reloads, seeks,
  recreates, or releases the player.
* The 15-second timeout is now an explicit terminal failure rather than an
  indefinitely protected late-callback state.

Automated validation now covers both directional ordering rules, Surface-before-
first-frame ordering, idempotence, and terminal failure. Physical validation is
still required after build/install: force-stop `com.viriviri.app`, launch from
the headset library, perform at least five complete cycles, and capture Activity
counts plus `route_requested`, `source_finish_requested`,
`source_activity_destroyed`, destination attach/first frame, and completion.
Verify one VrActivity at all times, stable manager/player identities,
`prepare=1`, non-resetting position, no process guard error, and panel button
state. Record audio continuity, decoder initialization changes, and the measured
black/no-video gap without treating decoder recreation as an app reprepare.

### Build, Install, And Cold-Launch Smoke Result

`./gradlew.bat :app-meta:testDebugUnitTest --no-build-cache` and
`./gradlew.bat :app-meta:assembleDebug --no-build-cache` passed. The APK exists
at `app-meta/build/outputs/apk/debug/app-meta-debug.apk`. Authorized Quest 2
`1WMHHB63832104` was force-stopped before installation, installed successfully
with `adb install -r`, force-stopped again before launch, and started through the
normal launcher category.

The final-build bounded smoke log recorded one preparation with player `23efd8a`, manager
`c2ca373`, Spatial scene-ready, audio session `497`, one Qualcomm AVC decoder
initialization, and `READY`/`is_playing=true`. `dumpsys activity activities`
contained exactly one viriviri Activity, `ImmersiveActivity` in task `1463`, and
no `PanelActivity`. The capture contained no app `FATAL EXCEPTION`,
`multiple_vr_activity_detected`, or `Multiple VR activities in same process`.

This validates the clean install/cold-launch baseline only. Shell launch stopped
the immersive host before an interactive Spatial route could be exercised, so
the source-destroy gate, panel first frame, return ordering, repeated-cycle
identity/prepare/position metrics, decoder behavior across Surface types, audio
continuity, and measured visual gap remain physical headset validation items.

## 2026-08-02 Confirmed Protected Spatial-To-TextureView Replacement

The confirmed Quest failure was caused by app-level
`PlayerManager.clearVideoSurface(old Spatial surface)` during immersive
destruction. The call blocked for about 2 seconds, threw Media3
`ExoTimeoutException: Detaching surface timed out`, put the player in `IDLE`,
and left the later Panel TextureView attached to an IDLE player with no first
frame. For a protected immersive-to-panel handoff, `onSpatialShutdown()` and
`onDestroy()` now log `skip_app_clear_for_spatial_shutdown`, call the required
Spatial superclass lifecycle, and preserve manager ownership until destination
replacement. The SDK still owns and releases its compositor Surface.

The protected destination replacement calls `setVideoSurface(new TextureView
Surface)` directly and logs `direct_spatial_to_texture`; it does not call
`clearVideoSurface(oldSurface)`. All other target replacements retain
clear-then-set behavior. No prepare, reload, player recreation, or seek is part
of this fix.

This remains a hardware-sensitive `MediaCodec.setOutputSurface` transition even
though the app no longer performs the blocking detach. Expected successful
Quest logs are: `route_requested`, protected
`skip_app_clear_for_spatial_shutdown`, TextureView availability, one
`Surface replacement mode=direct_spatial_to_texture` with immersive old target
and panel new target, `play()`, advancing position/audio, first frame, and
TextureView update. A delayed old Spatial detach must log `reason=not_current`.
If the route stalls or emits a player error, capture `ViriviriPlayerPoC`,
`MediaCodec`, and `SurfaceUtils` logs; do not add automatic recovery that
reprepares or reloads the player.

### Build, Install, And Bounded Launch Result

`./gradlew.bat :app-meta:testDebugUnitTest --no-build-cache` and
`./gradlew.bat :app-meta:assembleDebug --no-build-cache` passed. The resulting
APK installed successfully with `adb install -r` to Quest 2 `1WMHHB63832104`.
After clearing logcat, force-stopping the package, and starting its launcher
through ADB, the bounded capture showed the normal player preparation, Spatial
scene-ready callback, audio session, Qualcomm AVC decoder initialization, and
`READY`/`is_playing=true` events. Horizon OS invoked `onStop` before a Spatial
panel Surface callback, so this shell-launched run cannot exercise the manual
control-panel route or produce `direct_spatial_to_texture` evidence. Launch
from the headset library, press `Enter 2D Panel`, and collect the expected
replacement logs above before judging the experimental path.

## 2026-07-29 Panel Post-Attach Playback/Render Stall Recovery

Latest Quest evidence isolates the failure after the PanelActivity route has
already succeeded: its TextureView Surface attaches at about 3.5 seconds to the
same PlayerManager (`prepare=1`, `decoder=1`), but position remains frozen at
3890 ms, audio stops, and neither a TextureView update nor first-frame callback
arrives for at least 15 seconds. This is not a Home PendingIntent delivery
failure.

On every successful replacement Surface attachment, PlayerManager now calls the
existing player's `play()` once. This explicitly reasserts playWhenReady and
allows Media3 to reacquire audio focus after the VR Activity loses focus. A
same-Surface attach remains a no-op. The recovery never calls `setMediaItem`,
`prepare`, `seek`, `release`, or creates another player. Player errors are
recorded only; they do not trigger automatic reload or recovery.

Expected next device log order for immersive-to-panel is: `route_requested`,
`route_intent_home_pending_intent`, `route_intent_panel_activity_created`,
`TextureView surface available size=...`, `Surface attachment verified
replacement=true`, `Surface replacement requested playback`, then one or more
`player_event` state/playWhenReady/isPlaying or audio-session events, `TextureView
surface attach success=true`, `destination_surface_attached`, `TextureView first
texture update`, `destination_first_frame`, and `source_finish_requested`.
If first texture update remains absent, retain the transition per the existing
late-callback contract and use the player event state, audio session, video size,
and error logs to distinguish audio-focus/state loss from missing TextureView
frame delivery. TextureView update logging is first-update-only, not per-frame.

### Build, Install, And Launcher Result

`./gradlew.bat :app-meta:testDebugUnitTest --no-build-cache` and
`./gradlew.bat :app-meta:assembleDebug --no-build-cache` passed. The debug APK
installed successfully with `adb install -r` to Quest 2 `1WMHHB63832104`. A
force-stop followed by one normal launcher invocation produced the following
bounded log sequence:

```
player_event=playback_state=BUFFERING transition=null target=null ...
Prepared bundled media; player=506379c manager=70d3188
Spatial scene ready; referenceSpace=LOCAL_FLOOR ...
player_event=audio_session_changed=361 transition=null target=null ...
Video decoder initialized: OMX.qcom.video.decoder.avc
player_event=playback_state=READY transition=null target=null ...
player_event=is_playing=true transition=null target=null ...
```

Horizon OS stopped the shell-launched immersive Activity before a Spatial panel
Surface callback, so this capture proves installation and the new player-event
diagnostics through active audio/video playback but does not exercise the manual
immersive-to-panel route. Run that route from the headset library and compare its
logs with the expected order above.

## 2026-07-29 Slow Handoff And Single-VR-Activity Fix

The 15-second controller timeout is metric-only. It logs
`milestone=transition_timed_out` with `source retained; matching late callbacks
remain eligible to complete`, records `transitionTimedOut=true`, and keeps the
pending transition plus player-release protection alive. A matching late
destination Surface and first frame still logs `destination_surface_attached`,
`destination_first_frame`, and `source_finish_requested`; no player release is
permitted while that route remains pending. An actual source or destination
destruction before completion logs `transition_cancelled_activity_destroyed` and
safely cancels the route. If the destroyed participant is the incoming
destination, both its destruction callback and the retained source remain
player-protected; that protection is removed when the source explicitly exits.

The incoming panel renders disabled non-clickable text, `Transition in progress
- waiting for video`, until its first frame. It then re-enables `Return to
Immersive Mode`. Panel lifecycle logs now include `onCreate`, `onStart`,
`onResume`, `onStop`, and `onDestroy`, with transition and task IDs. Route logs
are distinct: `route_intent_home_pending_intent`,
`route_intent_panel_activity_created`, `route_intent_reuse_immersive`, and
`route_intent_new_immersive_fallback`.

Panel-to-immersive reuses a retained source across its task boundary via explicit
`NEW_TASK | CLEAR_TOP | SINGLE_TOP`; its `onNewIntent` reattaches a valid
SDK-owned panel Surface when available. This prevents Meta
`VrActivityProcessGuard` from creating a second VR Activity. Without a retained
source, the same explicit intent follows the logged new-immersive fallback. No
manifest `singleTask` was added because a previous Quest rejection was coupled
to sample-manifest changes. Device route-cycle validation follows this build and
install run.

### Build, Install, And Launcher Result

`./gradlew.bat :app-meta:testDebugUnitTest --no-build-cache` passed, followed by
`./gradlew.bat :app-meta:assembleDebug --no-build-cache`. The resulting
`app-meta/build/outputs/apk/debug/app-meta-debug.apk` installed successfully with
`adb install -r` to authorized Quest 2 `1WMHHB63832104`. A force-stop followed
by normal package launch produced this runtime evidence:

```
Prepared bundled media; player=506379c manager=213746
Spatial video panel final render mode=StereoMode.None ...
Spatial panel surface callback; transition=null ... valid=true
Surface attachment verified transition=null target=IMMERSIVE replacement=true ... prepare=1 decoder=0
Video decoder initialized: OMX.qcom.video.decoder.avc
Spatial panel first rendered frame; transition=null ...
```

`dumpsys activity activities` reported one top-resumed
`com.viriviri.app/.meta.ImmersiveActivity` in task `1411`; no second VR Activity
or `AndroidRuntime` failure appeared in the capture. This validates APK
installation, normal immersive launch, the preserved StereoMode.None rendering
path, single-player initial attach, and initial decoder/frame milestones. It does
not validate the physical control-panel click or a Home-to-panel-to-immersive
cycle: those require headset interaction. During that check, collect
`route_intent_home_pending_intent`, `route_intent_panel_activity_created`, then
`route_intent_reuse_immersive` (or the explicit fallback), and verify exactly
one `ImmersiveActivity`, `prepare=1`, no new decoder initialization, and late
timeout completion if Horizon delays the panel longer than 15 seconds.

## 2026-07-29 Lifecycle Fix

Automated validation covers the lifecycle ownership decision matrix. ADB device
`1WMHHB63832104` installed `app-meta-debug.apk` successfully on 2026-07-29;
after force-stopping and launching the package, `dumpsys activity activities`
reported `com.viriviri.app/.meta.ImmersiveActivity` as the top resumed app
Activity. An ADB `KEYCODE_HOME` test without a pending route emitted
`Released player manager=924ce34` in `ViriviriPlayerPoC`, confirming the
no-handoff stop path releases the singleton.

Manual headset validation is still pending. Run at least five
immersive-to-panel-to-immersive cycles and then use the Quest system menu to
background or exit the app from each Activity. Confirm that route cycles retain
the same player identity and continuous audio, while a no-handoff stop releases
the singleton player.

## 2026-07-29 Flat Panel Visibility Diagnosis

Quest 2 entered the Spatial session with the expected black background and
tracked hands, while the app process remained low CPU/memory. The concurrent
spatial-persistence telemetry storm was a separate system service signal, not
evidence of application resource pressure.

Local Meta Spatial SDK `0.13.0` bytecode establishes the rendering correction:

* Meta Spatial SDK `Quaternion(w, x, y, z)` uses Kotlin defaults for the identity
  rotation `(w=1, x=0, y=0, z=0)`. The local SDK bytecode establishes that an
  identity `Pose.forward()` is `+Z`.
* Physical Quest 2 validation is decisive: the identity panel at `z=+2m` was
  visible below and behind, while identity at `z=-2m` was not visible. This
  demonstrates that the `+Z` location is the proven renderable placement, but
  the identity-oriented flat quad faces away from the user there.
* The correction is the full pose `Pose(Vector3(0f, 0f, 2f),
  Quaternion(0f, 0f, 1f, 0f))`: 180 degrees around the vertical `+Y` axis.
  It maps the SDK local `+Z` panel normal to `-Z`, toward the user from the
  known-visible `+Z` position. This is a rotation correction, not another Z-axis
  toggle. The entity-created log now reports translation, quaternion, forward,
  and up vectors for headset verification.
* `Entity.createPanelEntity(...)` calls `Entity.Companion.create(...)`; the
  helper creates the registered entity in the data model. It does not require a
  separate scene-add or transform-parent operation.
* `QuadShapeOptions(2.4f, 1.35f)` and `PixelDisplayOptions(1920, 1080)` already
  configure a visible flat panel. No visibility, size, or manifest flag was
  added.

`ImmersiveActivity` retains distinct `ViriviriHybridPoC` milestones for
scene-ready, entity-created or entity-creation-failed, panel surface callback,
player attach success/failure, and the Media3 first rendered frame. Reinstall
and confirm those milestones in order during the next headset check. Confirm the
logged pose reads translation `(0.0, 0.0, 2.0)`, quaternion `(0.0, 0.0, 1.0,
0.0)`, and forward `(0.0, 0.0, -1.0)`. The ADB launch on the connected Quest 2
reached scene-ready and entity-created, but did not reach the surface callback
within 12 seconds. Those paused-session ADB observations were not a visibility
test and must not override the physical device placement result. They only
establish that no player attach or first rendered frame was possible in that
paused run.

## 2026-07-29 Rotated +Z Pose Install

The updated debug APK was installed successfully to connected Quest 2
`1WMHHB63832104` and launched into `com.viriviri.app/.meta.ImmersiveActivity`.
The runtime log confirms the exact submitted pose:

```
pose.translation=(0.0, 0.0, 2.0)
pose.quaternion=(0.0, 0.0, 1.0, 0.0)
pose.forward=(0.0, 0.0, -1.0)
pose.up=(0.0, 1.0, 0.0)
```

It also reached a valid Spatial SDK panel surface callback, Media3 surface
attachment, decoder initialization, and first rendered frame. This is ADB
installation/runtime evidence only. Manual in-headset visibility verification is
still required; do not mark the rotated panel as device-validated until the user
confirms it is visibly in front of them.

## Blocker / Risk

`AndroidView`/`ComposeView` rendering through a `TextureView` is only the
temporary immersive target. It does not establish that generic Compose content
renders in the Meta VR compositor. Spatial compositor/scene hosting is out of
scope for this lifecycle fix and requires a separate Meta Spatial SDK validation
task.

## 2026-07-29 Scene Bootstrap And Native Axis Diagnostic

The immersive host now follows the current official `HybridSample` scene
bootstrap: `VRFeature(this)`, `ReferenceSpace.LOCAL_FLOOR`, and an explicit
`scene.setViewOrigin(0f, 0f, 2f, 180f)`. The sample manifest declarations remain
absent because this Quest previously rejected them.

The six invisible `ViewPanelRegistration` numbered markers were removed. The
scene now has an independent unlit `mesh://axis` entity below the video panel,
plus native debug lines: bright red/green/blue are `+X/+Y/+Z`; darker matching
red/green/blue are `-X/-Y/-Z`. This diagnostic does not register a panel, attach
a Surface, or touch `PlayerManager`. Panel layers are compositor-owned surfaces,
and the diagnostic's unlit material means scene lighting is not needed for either
the video layer or this visibility check.

`MediaPanelSettings` in local SDK 0.13.0 has no sidedness setting. A yellow
unlit axis mesh behind/beside the video panel is the front/back cue; it is not a
second panel, video surface, or ExoPlayer.

Manual headset check: launch immersive mode and report whether the video is
visible, whether the bright/dark signed axis lines are visible below it, and
whether the yellow rear cue appears only when looking around the panel edge.
Confirm the panel still faces the user from `(0, 0, +2)` with quaternion
`(0, 0, 1, 0)` and that no extra panel or video surface appears.

## 2026-07-29 Build And Install Attempt

`./gradlew.bat :app-meta:testDebugUnitTest --no-build-cache` and
`./gradlew.bat :app-meta:assembleDebug --no-build-cache` both succeeded. The
APK is present at `app-meta/build/outputs/apk/debug/app-meta-debug.apk`.

Installation was blocked before any APK command: `adb devices` reported Quest 2
`1WMHHB63832104` as `offline`, and `adb reconnect` removed it from the device
list. No install, launch, route-cycle, decoder, audio, panel-visibility, axis,
rear-cue, or visual-gap observation is claimed from this attempt. Reconnect and
authorize the headset, then install the built APK and perform the manual check
above before recording device results.

## 2026-07-29 Physical Axis Calibration

The user confirmed that the native axis diagnostic is visible on Quest 2 and
that green `+Y` is vertical up while blue `+Z` points behind the user. The video
panel is therefore recalibrated to local pose `(0, +0.25, -3.5)` with identity
rotation `(1, 0, 0, 0)`: it is slightly above the local origin and farther in
front along negative Z, with the quad's local `+Z` normal facing back toward the
user. The axis and yellow rear cue move with this calibration for the next
manual check.

## 2026-07-29 Clean Video Visibility Test

The yellow rear cue, axis mesh entities, and frame-scoped signed debug lines
were removed. The yellow cue was never physically visible, and the partial axis
observation cannot determine panel placement because it compounded
`LOCAL_FLOOR`, `scene.setViewOrigin(...)`, and entity-local coordinates.

The clean scene retains the documented current `HybridSample` setup:
`ReferenceSpace.LOCAL_FLOOR` and `scene.setViewOrigin(0f, 0f, 2f, 180f)`. It
uses exactly one video panel entity at local pose `(0f, 1.5f, 0f)` with identity
quaternion `(1f, 0f, 0f, 0f)`. In this sample-defined frame, the panel is at
plausible eye height and two meters forward of the configured view origin. No
additional calibration yaw or diagnostic-derived transform is applied.

The only placement logs are the final reference space/view origin at scene ready
and the video panel's submitted translation, quaternion, forward, and up vectors
when its entity is created. Manual headset validation: launch immersive mode,
remain facing the initial forward direction, and confirm that one video panel is
visible at eye height in front of the user. Then verify the panel surface,
player-attach, and first-rendered-frame milestones in logcat. Do not use absent
diagnostic geometry to accept or reject this pose.

## 2026-07-29 Clean Test Build And Install

`./gradlew.bat :app-meta:testDebugUnitTest --no-build-cache` and
`./gradlew.bat :app-meta:assembleDebug --no-build-cache` passed. The debug APK
installed successfully to Quest 2 `1WMHHB63832104`, then launched through the
launcher Activity. The launch log recorded:

```
referenceSpace=LOCAL_FLOOR
viewOrigin=(0.0, 0.0, 2.0, 180.0)
pose.translation=(0.0, 1.5, 0.0)
pose.quaternion=(1.0, 0.0, 0.0, 0.0)
pose.forward=(0.0, 0.0, 1.0)
pose.up=(0.0, 1.0, 0.0)
```

The same ADB launch initialized `OMX.qcom.video.decoder.avc`. It did not provide
an in-headset visibility result or reach a panel-surface/first-rendered-frame
log within the eight-second capture window. Those observations remain pending
physical validation.

## 2026-07-29 Explicit Visible Component Test

The next focused build changes only dynamic-panel render state, retaining the
clean `LOCAL_FLOOR` reference space, `(0, 0, 2, 180)` view origin, and one-panel
pose. The video panel entity is now created with the Spatial SDK
`Visible(true)` component through `createPanelEntity(..., Transform, Visible)`.
The creation milestone logs `visible=true` and the entity component list so the
runtime state can be checked without adding another panel, player, diagnostic,
lighting entity, or manifest declaration.

The panel registration now requests direct mono rendering through
`MediaPanelRenderOptions(stereoMode = StereoMode.MonoLeft, zIndex = 0)`.
`PanelStyleOptions` was intentionally not used because the local 0.13.0 API
requires a theme resource ID and this app has no existing transparent style to
reuse safely. The research note records the official Meta entity guidance and
`PremiumMediaSample` evidence for the explicit visibility lifecycle.

`./gradlew.bat :app-meta:testDebugUnitTest --no-build-cache` and
`./gradlew.bat :app-meta:assembleDebug --no-build-cache` passed. The APK was
installed successfully to authorized Quest 2 `1WMHHB63832104`, force-stopped,
and launched through the normal app launcher. The focused runtime evidence was:

```
Spatial scene ready; referenceSpace=LOCAL_FLOOR viewOrigin=(0.0, 0.0, 2.0, 180.0)
Spatial video panel entity created; registration=1 ...
  pose.translation=(0.0, 1.5, 0.0)
  pose.quaternion=(1.0, 0.0, 0.0, 0.0)
  visible=true
Spatial panel surface callback; ... valid=true
Surface attachment verified ... target=IMMERSIVE ... prepare=1 decoder=0
Video decoder initialized: OMX.qcom.video.decoder.avc
Spatial panel first rendered frame; ...
```

This confirms the explicit `Visible(true)` component state at app runtime, a
valid direct panel Surface, one player attachment, decoder initialization, and
a Media3 first-frame callback. It does not substitute for physical headset
visibility validation.

Manual validation: put on the headset, launch the app while facing the initial
forward direction, and confirm exactly one video panel is visible at eye height
in front of the user. Do not change the headset pose, reference space, or panel
coordinates for this test. Confirm video is visibly moving and audio is present.
Then perform at least five immersive-to-panel-to-immersive cycles, checking that
the panel remains visible on return, audio remains continuous, `prepareCalls`
stays at one, the decoder count does not increase after the initial frame, and
playback position advances. Record any visible black/white gap and whether it
occurs before or after the destination first-frame milestone.

## Exit-Latency TODO

Collect the `onStop`, `onSpatialShutdown`, `detachPanelSurface`,
`releasePlayerIfUnowned`, and `onDestroy` elapsed-time logs during a normal Quest
system-menu exit and during a completed cross-Activity handoff. Compare the two
paths before drawing conclusions about exit latency; these measurements are
diagnostic only and do not change lifecycle ownership or introduce blocking work.

## 2026-07-29 Physical Orientation Finding

The user confirmed that the current single video panel is visible, but its
orientation is reversed. Keep its existing `onVRReady` creation timing,
`(0f, 1.5f, 0f)` position, `Visible(true)` state, one-panel/player ownership,
scene bootstrap, and exit-latency instrumentation. Its pose now uses the exact
Spatial SDK local `Quaternion(w, x, y, z)` value `(0f, 0f, 1f, 0f)`, a 180-degree
yaw about vertical `+Y`, so the quad faces the user.

## 2026-07-29 Full-Frame Mono Rendering Fix

Physical Quest validation confirmed the rotated panel now faces the user at the
existing `onVRReady` timing and pose, but `rick.mp4` showed only its upper-left
quarter, centered in view. The explicit
`MediaPanelRenderOptions(stereoMode = StereoMode.MonoLeft)` was removed without
changing panel position, orientation, player ownership, or routing.

Local Meta Spatial SDK `0.13.0` bytecode confirms that the
`MediaPanelSettings` default rendering is `MediaPanelRenderOptions()` and its
default `stereoMode` is `StereoMode.None`. This is the selected final mode for
the bundled normal monoscopic file, so the SDK samples the full frame rather
than a stereo-eye region. `ImmersiveActivity` logs that final mode and the
unchanged panel position when it creates the entity.

The single native coordinate arrow remains only as a calibration diagnostic but
now uses an independent pose below and to the right of the video panel. Its log
records that separate pose and `calibrationDiagnostic=true`; it does not
register a panel or interact with Media3.

Manual validation still required: verify that the immersive panel shows the
full `rick.mp4` frame, then separately use the Quest system menu to exit the
app and observe the existing lifecycle elapsed-time logs for exit latency.

`./gradlew.bat :app-meta:testDebugUnitTest --no-build-cache` and
`./gradlew.bat :app-meta:assembleDebug --no-build-cache` passed. The resulting
APK installed successfully to authorized Quest 2 `1WMHHB63832104`. A subsequent
ADB launch reached the expected render configuration and playback milestones:

```
Spatial video panel final render mode=StereoMode.None ... position=(0.0, 1.5, 0.0)
Spatial coordinate arrow entity created ... pose.translation=(1.45, 0.5, 0.0) ... calibrationDiagnostic=true
Spatial panel surface callback ... valid=true
Surface attachment verified ... target=IMMERSIVE ... prepare=1 decoder=0
Video decoder initialized: OMX.qcom.video.decoder.avc
Spatial panel first rendered frame
```

This is installation/runtime evidence only, not physical confirmation of the
full-frame image or system-menu exit latency.

## 2026-07-29 Confirmed Immersive Playback And Exit Regression

Physical Quest 2 validation confirmed that the Spatial video panel now faces the
user and displays the complete bundled `rick.mp4` frame. The final rendering
mode is `StereoMode.None`; `StereoMode.MonoLeft` had incorrectly cropped the
normal monoscopic file to its upper-left quarter.

The same validation found a separate exit-latency regression. After leaving
viriviri through the Quest system menu, the headset briefly remains out of
passthrough while head tracking continues. It then enters passthrough, but
controller tracking freezes and the system menu responds slowly. Tracking and
menu responsiveness recover after waiting, or after a lock/unlock interval with
several seconds of no tracking.

The app's player process was previously observed at low CPU and approximately
171 MB PSS, so this is not currently attributed to video decoding. Lifecycle
timing instrumentation is present around `onStop`, `onSpatialShutdown`, panel
Surface detach, player release, and `onDestroy`, but this occurrence did not
leave a retained logcat sample. Follow up by capturing a fresh system-menu exit
trace and comparing application lifecycle elapsed times with Horizon OS task,
tracking, and Spatial service logs. Do not change the confirmed single-player
handoff to speculate on a fix.

## 2026-07-29 Spatial 2D Panel Control

`ImmersiveActivity` now registers the visible Stage B control using the local,
compile-verified Meta Spatial SDK `0.13.0` pattern:
`ViewPanelRegistration(CONTROL_PANEL_ID, dynamicViewCreator, settingsCreator)`
with `UIPanelSettings` and `DpDisplayOptions`, then
`Entity.createPanelEntity(..., Visible(true))` in
`onVRReady()`. Its entity shares the established video-panel depth and rotation
but is centered above the video at `y=2.45m`; the native coordinate arrow was
removed so it cannot obstruct the control.

Regression note: a standalone `ComposeView` returned directly from
`ViewPanelRegistration` crashes on Quest with
`IllegalStateException: ViewTreeLifecycleOwner not found from ComposeView`.
The registration does not provide the lifecycle host required by arbitrary
Compose content in this app. The control panel must use a native Android View
hierarchy, or the Spatial Compose feature's official registration path, rather
than bolting lifecycle owners onto the panel.

The native opaque `LinearLayout` contains a readable title and an `Enter 2D
Panel` button. It is exclusively a UI/input owner: it creates no `Surface`,
video view, ExoPlayer, media load, or decoder and does not access
`PlayerManager`. Its click logs `Spatial control panel Enter 2D Panel clicked`
and calls
`HybridTransitionController.returnToPanelInHome(this)`, preserving the official
Home plus PendingIntent system-panel route.

Device test requirements: launch from the Quest library, verify the control is
legible above the full-frame video, then press it and confirm the click log
precedes the usual route request. Confirm the resulting 2D system panel opens,
the single direct video Surface is only replaced by the normal handoff target,
and `prepareCalls` remains one with no additional decoder initialization. Return
to immersive and repeat for at least five cycles while checking audio continuity
and that the control reappears above the video.

### Build, Install, And Runtime Result

`./gradlew.bat :app-meta:testDebugUnitTest --no-build-cache` passed after the
new control registration compiled against SDK 0.13.0. The first compile caught
and corrected a real API boundary: `PixelDisplayOptions` is media-only, while a
`ViewPanelRegistration` requires `DpDisplayOptions` through `UIPanelSettings`.
`./gradlew.bat :app-meta:assembleDebug --no-build-cache` then passed and
generated `app-meta/build/outputs/apk/debug/app-meta-debug.apk`.

The APK installed successfully to authorized Quest 2 `1WMHHB63832104` and the
launcher opened `com.viriviri.app/.meta.ImmersiveActivity`. In the ten-second
ADB capture, the app logged scene-ready and decoder initialization, but Horizon
OS paused the shell-launched immersive session before `onVRReady()`. Therefore
there is no ADB evidence yet for the control entity/view creation or click, no
physical legibility result, and no route-cycle result. Launch it from the
headset library for the control-specific device tests above; do not treat this
paused ADB run as a visibility or input failure.

### Native View Regression Result

The native-view replacement APK was installed over the connected Quest 2
`1WMHHB63832104` and `ImmersiveActivity` was launched once through ADB. After
clearing logcat, the active session logged both panel entities, then
`Spatial control panel view created; registration=2 ownsMedia=false` at
`18:23:48.090`, followed by normal video Surface attachment and first frame.
The process remained alive, and the capture contained no `AndroidRuntime`,
`FATAL EXCEPTION`, or `ViewTreeLifecycleOwner` failure. This confirms that the
standalone native View hierarchy avoids the confirmed Compose lifecycle crash.
This ADB run does not replace headset-library validation of physical legibility
or the control click route.
### Destination Activity materialization gate

- Repeated Quest testing showed that accepting the Home PendingIntent `START` does not guarantee that Horizon OS will instantiate `PanelActivity`.
- Finishing the immersive source immediately after the system `START` can leave the app process `CACHED_EMPTY`; on later cycles the pending 2D Activity may never reach `onCreate`, followed by the 15-second transition timeout and audio loss when the unowned player is released.
- Immersive-to-panel routing now keeps the source Activity alive until `PanelActivity.onCreate()` registers the destination. The Panel does not create its TextureView yet. It first requests activity-only source finish, waits for the post-`super.onDestroy()` source signal, and only then attaches the destination Surface.
- This permits a short Activity overlap but not a video-Surface overlap and not two VR hosts: `PanelActivity` is a non-VR Activity, while only one `ImmersiveActivity` may be registered.

Follow-up device evidence showed Horizon OS may not call `PanelActivity.onCreate()` while the Spatial VR Activity remains alive, even though ActivityTaskManager has accepted the 2D `START`. Waiting indefinitely for destination creation therefore deadlocks. The route now uses a bounded 750 ms task-materialization grace after the Home PendingIntent. Destination creation can end the grace early; otherwise the source receives activity-only `finish()` when the grace expires. Destination Surface attachment remains blocked until the VR Activity has completed post-`super.onDestroy()` cleanup.
## Current Handoff Findings and Cleanup Plan

### Three visible experiment modes

| Mode | Observed result |
| --- | --- |
| `Direct + recovery` | Completed four round trips in one run; the fifth immersive-to-panel attempt became a long black screen and crashed. |
| `Clear + recovery` | First round trip completed; the second immersive-to-panel attempt became a long black screen and crashed in both recorded runs. |
| `Reprepare baseline` | First round trip completed; the second immersive-to-panel attempt hung in both recorded runs. |

Many later failures occur before the destination panel reaches `onCreate()` or creates its TextureView, so those failures do not provide a fair comparison of Surface replacement strategy.

### Repeated failure signature

```text
route_requested
route_intent_home_pending_intent
source_finish_requested
source_activity_destroyed
MediaCodec NO_INIT/BAD_VALUE
player ERROR_CODE_DECODING_FAILED -> IDLE
no route_intent_panel_activity_created
no TextureView surface available
transition_timeout
Released player
```

The headset also reports `No component for base intent of task`. After failures, the app process can remain alive with hidden task/native state even though the player was released. This is evidence of incomplete Activity/task/Spatial/native cleanup, not proof that Java GC is disabled.

### Resource ownership conclusions

The relevant resources are larger than Java objects:

- Spatial SDK session and compositor state
- Qualcomm MediaCodec and decoder renderer
- SDK-owned Spatial video Surface
- TextureView-created Surface and SurfaceTexture
- ExoPlayer listeners and analytics listeners
- route Handler callbacks
- decoder recovery Handler callbacks
- Panel position-refresh coroutine
- panel polling/tween/entity registrations
- Activity/task records

The current implementation does not yet emit decoder-release events, and `PlayerManager.release()` does not cancel all recovery callbacks or explicitly clear every ownership field. These are cleanup work items.

### Reference-project findings

`temp/MediaSpatialAppTemplate` releases its Fragment ExoPlayer in `onStop()`. `temp/Meta-Spatial-SDK-Samples/MediaPlayerSample` releases its Spatial player in `onSpatialShutdown()`. `PremiumMediaSample` explicitly destroys the entity, detaches and clears player media, stops polling, cancels tweens, and unregisters the panel. None implements a Spatial `VrActivity` to system 2D `TextureView` handoff.

### Acceptance levels

1. Stable routing: five round trips without crash, timeout, duplicate VR Activity, or task accumulation.
2. Player continuity: same ExoPlayer and media item, monotonic position, decoder reprepare permitted.
3. Decoder continuity: no decoder release/reinitialization and `prepareCalls == 1`; device-dependent and explicitly optional for the routing baseline.

### Planned cleanup work

- Centralize route terminal cleanup and remove every pending Handler callback.
- Cancel decoder-recovery callbacks when the player is released or a route completes.
- Make Panel position refresh lifecycle-bound rather than an unbounded loop without explicit ownership.
- Make Surface ownership explicit at every Activity destroy path.
- Add player/decoder/surface/panel registration diagnostics and end-of-route snapshots.
- Fix stale transition IDs being reported by later Spatial shutdown callbacks.
