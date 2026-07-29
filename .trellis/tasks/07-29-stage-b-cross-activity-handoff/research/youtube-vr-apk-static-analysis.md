# YouTube VR APK Static Analysis Plan

## Scope

This document records the first static inspection of `temp/YouTube_1.79.11.apk` and defines the focused IDA follow-up. It is evidence for architectural comparison only. It does not claim access to YouTube VR source code or prove the runtime behavior of every code path.

The APK was inspected read-only. The original file was not modified.

## Artifact

| Field | Value |
| --- | --- |
| File | `temp/YouTube_1.79.11.apk` |
| Package | `com.google.android.apps.youtube.vr.oculus` |
| Version | `1.79.11` |
| Version code | `17911000` |
| Min SDK | 26 |
| Target SDK | 32 |
| Native ABI | `arm64-v8a` |
| DEX files | `classes.dex` through `classes5.dex` |

## Manifest Evidence

The application declares two primary YouTube VR activities.

### `YouTubeVrActivity`

```text
com.google.android.apps.youtube.vr.activities.YouTubeVrActivity
```

Observed attributes:

```text
launchMode = singleTask
excludeFromRecents = true
resizeableActivity = false
com.oculus.intent.category.VR
```

It is the dedicated immersive entry point. It also handles YouTube URL intents.

### `YouTubeVrPanel2Activity`

```text
com.google.android.apps.youtube.vr.activities.YouTubeVrPanel2Activity
```

Observed attributes:

```text
launchMode = singleTask
excludeFromRecents = true
resizeableActivity = true
com.oculus.intent.category.VR
com.oculus.intent.category.2D
```

It declares both VR and 2D Oculus categories and has an Android `<layout>` declaration with default width and height. This is direct evidence that YouTube VR has a real Horizon OS system 2D window entry point. It is a YouTube-owned system 2D Panel Activity with VR-related integration, not merely an in-scene flat panel rendered inside the immersive Activity.

There is also an internal alias:

```text
com.google.android.apps.youtube.vr.activities.InternalYouTubeVrPanel2Activity
    -> YouTubeVrPanel2Activity
```

and an internal VR alias:

```text
com.google.android.apps.youtube.vr.activities.InternalYouTubeVrActivity
    -> YouTubeVrActivity
```

The internal aliases are not exported. Both primary activities use `singleTask`, which is relevant when comparing task reuse and repeated mode switches.

### Oculus and XR capabilities

The APK requests or declares capabilities associated with Quest spatial rendering:

```text
com.oculus.permission.USE_SCENE
com.oculus.permission.USE_ANCHOR_API
com.oculus.permission.RENDER_MODEL
com.oculus.permission.PLAY_AUDIO_BACKGROUND
com.oculus.feature.PASSTHROUGH
android.software.vr.mode
android.hardware.vr.high_performance
```

It also declares optional OpenXR libraries and Android XR libraries. This does not by itself prove that every screen is rendered through OpenXR, but it confirms that the APK is built as a dedicated XR application rather than a conventional video player with a VR intent filter.

## Native Library Evidence

The APK contains these relevant native libraries:

```text
libyoutube_vr_impress_jni.so
libyoutubevrjni.so
libimpress_api_jni.so
libelements.so
libopenxr_loader.so
libgvr.so
libgvr_audio.so
libvpx.so
libvpxV2JNI.so
libogg_opus_encoder.so
libopusV2JNI.so
```

The most relevant libraries for the next analysis are:

1. `libyoutube_vr_impress_jni.so`
2. `libyoutubevrjni.so`
3. `libimpress_api_jni.so`
4. `libelements.so`

The names strongly suggest a native Impress/scene/rendering layer and a YouTube-specific JNI bridge. The library names do not prove the exact player implementation, but they make a pure Android `TextureView` handoff an unlikely complete model of the application.

## Resource Evidence

The APK contains a large native/entity asset set, including:

```text
assets/entities/androidviews/*
assets/entities/screen/screen-render-left.bin
assets/entities/screen/screen-render-right.bin
assets/entities/screen/video-screen.bin
assets/entities/theater-backdrop-collider.bin
assets/entities/theater-gimbal.bin
assets/entities/theater-group.bin
assets/entities/environments/*
assets/shaders/screen_texture.fplshader
assets/shaders/screen_texture_no_oes.fplshader
assets/shaders/screen_texture_ssaa.fplshader
assets/shaders/streaming_texture.fplshader
assets/shaders/streaming_texture_ssaa.fplshader
assets/shaders/streaming_texture_with_gradient.fplshader
```

The `androidviews` entity group shows that some Android UI is represented inside the app's spatial content system. The screen and streaming texture assets suggest that immersive video is integrated into a scene-level rendering pipeline, potentially using external textures or native texture import. This does not contradict the separate system 2D window entry point: YouTube VR may have one scene rendering path for immersive mode and a separate Android window path for `YouTubeVrPanel2Activity`.

This is an architectural indication, not proof of a particular decoder-to-texture path.

## DEX String Evidence

The DEX files contain references or string occurrences for:

```text
ExoPlayer
MediaCodec
PlayerView
TextureView
SurfaceView
SurfaceTexture
extra_launch_in_home_pending_intent
impress
openxr
scene
```

This establishes that the APK contains Android player APIs and the Home pending-intent key somewhere in its compiled code. It does not establish that YouTube VR uses the same Home pending-intent route as this project for its main mode switch. The string may belong to compatibility, platform integration, or an unrelated code path.

The presence of both Android player terminology and native XR libraries is consistent with a layered architecture:

```text
Java/Kotlin or generated Android layer
    -> player/session abstraction
    -> JNI bridge
    -> native scene and video screen renderer
```

The exact boundaries require targeted decompilation and native cross-reference analysis.

## Current Architectural Hypothesis

The strongest current hypothesis is:

```text
YouTubeVrActivity
    -> dedicated system VR Activity

YouTubeVrPanel2Activity
    -> dedicated system 2D Panel Activity

Both entry points
    -> shared YouTube VR product/session state, possibly
       through process-level or native playback state
    -> native Impress/scene/video rendering components where applicable
```

This differs from the current project:

```text
ImmersiveActivity
    -> Spatial video surface

PanelActivity
    -> ordinary Android TextureView surface

Home + PendingIntent
    -> Activity/task handoff between the two hosts
```

The APK evidence currently favors dedicated system 2D and VR entry points backed by shared application-specific media/session components. It does not yet prove whether the media player or decoder is shared across those entry points. The evidence should not be interpreted as saying that the 2D experience is only an in-scene panel.

## Questions for IDA

IDA should answer these questions in order of value:

### 1. Activity-to-native initialization

Determine whether both primary activities invoke the same JNI registration or native initialization path.

Target methods and symbols:

```text
JNI_OnLoad
RegisterNatives
Java_*
nativeCreate*
nativeDestroy*
nativePause*
nativeResume*
```

Compare the native call paths reached from:

```text
YouTubeVrActivity
YouTubeVrPanel2Activity
```

### 2. Surface and texture ownership

Search for references to:

```text
Surface
SurfaceTexture
ANativeWindow
AHardwareBuffer
EGL
OpenGL
external texture
OES
attach
detach
setSurface
setVideoSurface
```

The goal is to determine whether the video output is:

```text
MediaCodec -> Android Surface
```

or:

```text
MediaCodec/decoder -> SurfaceTexture or external texture -> native scene screen
```

### 3. Player and decoder lifetime

Search for:

```text
MediaCodec
AMediaCodec
ExoPlayer
player
prepare
release
flush
seek
buffer
decoder
```

Look for process-level singletons, static managers, native handles, or session objects that survive Activity callbacks. Compare creation and destruction paths for the two activities.

### 4. Activity lifecycle handling

Search Java/DEX and native code for:

```text
onCreate
onStart
onResume
onPause
onStop
onDestroy
onNewIntent
configuration
singleTask
```

The key distinction is whether a mode switch is implemented as:

```text
same Activity or same native session with a changed presentation target
```

or:

```text
new Activity and new player/decoder with state restoration
```

### 5. Home and task routing

Search for:

```text
extra_launch_in_home_pending_intent
PendingIntent
ACTION_MAIN
CATEGORY_HOME
taskAffinity
FLAG_UPDATE_CURRENT
FLAG_CANCEL_CURRENT
```

This determines whether the known Home route is a core YouTube VR mode-switch mechanism or only a compatibility path.

## What Static Analysis Can and Cannot Prove

### Static analysis can likely establish

- Whether both activities share a native initialization entry point.
- Whether a native video screen/entity abstraction exists.
- Whether surface attach/detach functions exist.
- Whether a player or media session is represented by a singleton or process-level handle.
- Whether the code includes explicit pause, release, flush, or decoder recreation paths.
- Whether Home pending-intent routing is present in the active YouTube VR flow.
- Whether `YouTubeVrPanel2Activity` is launched through the same Home handoff contract used by this project.

### Static analysis cannot establish by itself

- Which experiment or server-controlled feature branch is active on a Quest.
- Whether a discovered function executes for a particular video type.
- Whether the same `MediaCodec` instance survives a real Activity transition.
- Whether Horizon OS materializes the destination task successfully on every repetition.
- Whether the app uses a discovered implementation path on the installed headset build.
- Exact runtime ownership of codec and compositor resources without lifecycle logs.

These questions require runtime validation with `logcat`, `dumpsys activity`, `dumpsys SurfaceFlinger`, and `dumpsys media.codec`.

## Recommended IDA Workflow

1. Extract the `arm64-v8a` libraries without modifying the APK.
2. Open `libyoutube_vr_impress_jni.so` in IDA and identify exports, JNI registration, and strings.
3. Repeat for `libyoutubevrjni.so`.
4. Use DEX decompilation to map native method declarations and call sites back to Java classes.
5. Build call graphs around surface, video, screen, player, pause, resume, and destroy references.
6. Compare the call graphs for `YouTubeVrActivity` and `YouTubeVrPanel2Activity`.
7. Validate only the high-value hypotheses on Quest with runtime diagnostics.

Do not attempt to fully decompile every DEX class or every native library first. The APK is large, partially obfuscated, and contains substantial unrelated Google services and input-method code. A focused JNI/surface/player analysis has a much higher expected return.

## Implications for This Project

Until IDA or runtime evidence shows otherwise, YouTube VR should not be treated as proof that a process-wide ExoPlayer can survive the current cross-Activity Spatial-to-system-Panel route. However, it is valid evidence that a production Quest application can provide a real system 2D window and a separate VR Activity while sharing one product-level media experience.

The implementation lessons that are already supported by the APK are narrower:

1. Use dedicated VR and system 2D entry points with explicit `singleTask` semantics when both modes are first-class.
2. Keep media/session state under one application-owned layer where possible, even if the system creates separate VR and 2D Activity instances.
3. Make media item and playback position restoration a deterministic fallback before optimizing same-player continuity.
4. Avoid assuming that a normal Android `TextureView` is equivalent to a native spatial video screen.
5. Treat decoder continuity as an implementation detail to verify, not as an architectural assumption.
6. Continue recording Activity/task creation, Surface ownership, decoder initialization/release, and first-frame milestones separately.

The next implementation decision should wait until the focused JNI and surface ownership analysis is complete.
