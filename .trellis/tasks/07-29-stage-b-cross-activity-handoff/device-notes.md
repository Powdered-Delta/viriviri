# Quest Validation Notes

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
