# Quest Spatial Panel Debugging

## Scenario: Visible Media3 Video Panel in a Meta Spatial Activity

### 1. Scope / Trigger

Use this runbook when a Meta Horizon OS VR-category Activity enters an immersive
session but a programmatically created Spatial SDK video panel is missing,
back-facing, cropped, or loses the expected player Surface. It applies to the
Quest 2 validation path in `:app`.

Do not use a normal Android `ComposeView`, `AndroidView`, or `TextureView` as
the immersive output target. A VR-category Activity can enter the Horizon OS
immersive session while ordinary Android View content remains behind the system
loading compositor. `TextureView` remains valid for `PanelActivity` only.

### 2. Signatures

The immersive Activity uses the SDK-owned direct media-surface path:

```kotlin
class ImmersiveActivity : AppSystemActivity() {
    override fun registerFeatures(): List<SpatialFeature> = listOf(VRFeature(this))

    override fun registerPanels() = listOf(
        VideoSurfacePanelRegistration(
            VIDEO_PANEL_ID,
            surfaceConsumer = { _, surface ->
                playerManager.attachSurface(surface, HandoffTarget.IMMERSIVE, transitionId)
            },
            settingsCreator = {
                MediaPanelSettings(
                    shape = QuadShapeOptions(width = 2.4f, height = 1.35f),
                    display = PixelDisplayOptions(width = 1920, height = 1080),
                )
            },
        ),
    )

    override fun onVRReady() {
        Entity.createPanelEntity(
            VIDEO_PANEL_ID,
            Transform(panelPose),
            Visible(true),
        )
    }
}
```

`VideoSurfacePanelRegistration` owns the supplied `android.view.Surface`.
`PlayerManager` may detach it by identity, but must not release it.

### 2.1 Standalone Spatial Control Panel

The Stage B return control is a second, non-media Spatial panel. Meta Spatial
SDK `0.13.0` supports this compile-verified registration pattern:

```kotlin
override fun registerPanels() = listOf(
    VideoSurfacePanelRegistration(VIDEO_PANEL_ID, surfaceConsumer, videoSettings),
    ViewPanelRegistration(
        CONTROL_PANEL_ID,
        { _, context -> createImmersiveControlPanel(context, onEnterPanel) },
        {
            UIPanelSettings(
                shape = QuadShapeOptions(1.3f, 0.45f),
                display = DpDisplayOptions(1024f, 384f, 160),
            )
        },
    ),
)

override fun onVRReady() {
    Entity.createPanelEntity(VIDEO_PANEL_ID, Transform(videoPose), Visible(true))
    Entity.createPanelEntity(CONTROL_PANEL_ID, Transform(controlPose), Visible(true))
}
```

The `ViewPanelRegistration` dynamic creator returns an opaque native Android
`LinearLayout` with a readable title and `Enter 2D Panel` button. Do not return
a standalone `ComposeView`: its attachment requires a `ViewTreeLifecycleOwner`,
which the registration does not provide in this app and causes an
`IllegalStateException`. Use a native `View` hierarchy here, or the Spatial
Compose feature's official registration path when Compose is required. Create
its entity in `onVRReady()` at the same proven forward depth as the video and
above the video panel. The click handler logs the action and calls
`HybridTransitionController.returnToPanelInHome(this)`, retaining the official
Home plus `extra_launch_in_home_pending_intent` route.

This panel is control-only: it must not create a `Surface`, `TextureView`,
`ExoPlayer`, media item, decoder, or call `PlayerManager`. The video panel alone
owns the direct SDK media Surface. Remove native placement diagnostics once a
working video pose is established unless a separate device investigation needs
one.

### 3. Contracts

* Register `VRFeature(this)`. `AppSystemActivity` alone is not the complete
  immersive feature bootstrap.
* In `onSceneReady()`, configure the reference space and documented view origin
  before placing scene content. The current test host uses
  `ReferenceSpace.LOCAL_FLOOR` and `scene.setViewOrigin(0f, 0f, 0f, 0f)`.
* Create the dynamic panel entity in `onVRReady()`, not merely in
  `onSceneReady()`. `onVRReady()` is the lifecycle point used by Meta's
  `SpatialVideoSample` for its video panel entity setup.
* Create the entity with an explicit `Visible(true)` component. Surface creation
  and Media3 first-frame events do not prove that the Spatial panel layer is
  composited visibly.
* For normal monoscopic video, preserve the default
  `MediaPanelRenderOptions`/`StereoMode.None`. `StereoMode.MonoLeft` samples a
  stereo-eye region and cropped the bundled monoscopic test video to its
  upper-left quarter.
* A flat quad is single-facing. The current local coordinate convention and
  view-origin transform must be validated physically. When a visible panel is
  reversed, rotate its pose 180 degrees about the vertical Y axis using
  `Quaternion(w, x, y, z) = Quaternion(0f, 0f, 1f, 0f)` instead of changing
  player or decoder ownership.
* Lighting is not a prerequisite for `VideoSurfacePanelRegistration` or
  `ViewPanelRegistration` compositor layers. Use unlit native meshes only for
  placement diagnostics; do not add global lighting to fix an absent panel.
* Keep a maximum of one direct video panel and one video output Surface for the
  handoff experiment. Placement diagnostics must not register additional video
  panels or construct another `ExoPlayer`.
* A UI control panel is permitted as a second Spatial panel only when it remains
  independent of media ownership and is created visibly in `onVRReady()`.
* Log the ordered milestones: scene ready, VR ready, visible panel entity
  created, panel Surface callback, player Surface attach, decoder initialization,
  and Media3 first rendered frame.

### 3.1 Playback Control Synchronization

The process-wide Media3 player is the sole source of truth for immersive
transport state. The Spatial Activity must not treat an Activity-local boolean
as authoritative because video selection, buffering, panel handoff, and other
player callers can change state without a panel-button click.

- The play/pause icon follows `player.playWhenReady`: a buffering player that
  still has play intent shows Pause.
- Controller fade and environment lights follow `player.isPlaying`, the actual
  playback state.
- Synchronize the existing controls panel from Media3 player-state,
  playing-state, and position-discontinuity callbacks, and immediately after
  the panel button is created.
- A seek drag records the pre-drag `playWhenReady`; it pauses temporarily only
  when that intent was true, then restores the same intent at drag end. A video
  already paused before seeking must remain paused.
- While seek dragging, position-discontinuity and periodic progress updates
  must not overwrite the thumb position.

### 3.2 Transport Overlay Runtime Behavior

The existing `controls_id` panel is the current `TRANSPORT` implementation. It
is already parented to `spatialized_video_panel` with the existing local
front-depth offset; until Meta Spatial Editor is available, do not change its
fixed pose, size, parent, or add an alternative static panel.

- While `player.isPlaying`, transport uses a bounded idle timeout of about four
  seconds. Hover, stage input, and control interaction reveal/reset it.
- When the transport is hidden, its Android root becomes `INVISIBLE` after the
  fade and is non-clickable/non-focusable. Alpha alone is not sufficient because
  transparent child controls can still receive input.
- When the stage is clicked with hidden transport, reveal transport only. A
  click while transport is already visible toggles player play intent.
- Paused or non-playing media keeps transport visible. The existing playback
  synchronization remains responsible for deciding actual-playback status.
- These changes affect only the existing controls panel root; they do not add a
  video output, Spatial panel, Entity, or Surface.

### 3.3 Playback Canvas Spatial Visibility Adapter

`PlaybackCanvasReducer` is applied to the existing Spatial panels through
`ImmersivePlaybackCanvasHost`, `PanelLayerAlpha`, and
`SpatialPanelVisibilityController`:

```text
core PlaybackCanvas event
  -> resolved PanelSlot set
  -> existing entity PanelLayerAlpha component
  -> PanelLayerAlphaSystem / compositor layer alpha
  -> Visible(false) after fade-out completes
```

The mapping is fixed to current existing entities:

```text
TRANSPORT      controls_id
SYSTEM_TOOLBAR mode_panel
BROWSE         video_selector_panel
```

`spatialized_video_panel` is the persistent `MEDIA_STAGE` and is deliberately
not included in the hide map. The adapter does not create an entity, register a
new panel, change a fixed Transform, attach a Surface, or own a player.

`mr_panel` is intentionally absent from this first adapter. It is currently a
child of `video_selector_panel`, so it cannot be the independent `CONTEXT` rail
specified by the canvas contract while Browse is hidden. Do not reparent it in
Kotlin. Independent Context visibility is blocked until Meta Spatial Editor can
author the correct scene anchor/parent relationship.

- Show sets `Visible(true)` before layer alpha rises.
- Hide lowers the Spatial compositor layer alpha, then sets `Visible(false)`;
  that final component state removes the hidden Spatial panel hit target.
- New slot requests cancel any prior slot fade before applying the current
  target visibility.
- Actual player pause from Quiet Watch opens Playback; transport idle timeout
  returns actual-playing Playback to Quiet Watch. Browse is the first applied
  on-demand rail; core Context state remains independent but its Spatial mapping
  waits for the scene-authored parent/anchor correction.
- The existing controls Android root fade is only its local content treatment.
  It must not substitute for the Spatial layer/visibility lifecycle.
- The existing transport Browse command records the current selected video ID,
  returns the shared selector UI to recommendations, then opens core Browse.
  A different selected video ID from that existing panel returns the canvas to
  Playback while `ViriViriAppState` alone replaces the Media3 source. A primary
  stage action from Browse also returns to Playback without toggling player
  intent; only a primary stage action already in Playback toggles play intent.

### 3.4 Playback Speed Control

The existing transport panel exposes a fixed, controlled speed menu:
`0.75x`, `1x`, `1.25x`, `1.5x`, and `2x`. It is an Android menu anchored to
an existing transport button, not a new Spatial panel/entity.

- The label and selected menu item reflect `player.playbackParameters.speed`.
- Menu selection updates only the existing process-wide player parameters using
  `withSpeed`; it must not prepare/reload media, seek, create a player, or
  attach/detach a video Surface.
- Invalid or externally supplied unsupported speed displays as safe `1x` without
  silently overwriting player parameters.
- Popup rendering and input in the embedded Quest panel require manual device
  validation. If it is not viable, replace only the menu shell in a later task;
  preserve the same controlled speed action and player ownership boundary.

### 3.5 Playback Volume Control

The existing transport panel exposes a controlled volume menu with fixed choices
`0%`, `25%`, `50%`, `75%`, and `100%`. It is an Android popup anchored to the
existing `controls_id` panel and not a new Spatial object.

- The label and checked menu item follow the process-wide Media3 `player.volume`
  through panel setup and `onVolumeChanged`.
- Selection writes only the existing player's `volume`; it must not prepare,
  reload, seek, set a source, recreate a player, or attach/detach a Surface.
- Unknown/non-finite external values display as safe `Vol 100%` without
  silently rewriting player state.
- Popup visibility and controller/hand input in the embedded Quest panel remain
  manual device acceptance items, as for playback speed.

### 3.6 Transport Timeline Ownership

The existing transport `SeekTimeline` is rendered from the process-wide Media3
player. Its compact elapsed/duration labels and seek bar remain Android views in
`controls_id`; no spatial geometry or video target is added.

- Project a player snapshot through `ImmersiveTransportTimeline` so finite
  duration, clamping, timecode formatting, and unknown-duration handling are
  deterministic and JVM-testable.
- A finite duration enables seeking and clamps the displayed position. An
  unavailable, negative, or over-`Int` duration renders `--:-- / --:--` and
  disables the seek bar; it must not emit a seek from an invalid timeline.
- During a user drag, retain the dragged position for the thumb and elapsed
  label. Media3 discontinuity callbacks and periodic rendering must not
  overwrite it until drag completion.
- The Activity owns exactly one timeline runnable on its existing main-thread
  handler. Start it only after the controls panel resolves and remove it in
  `onDestroy()` before clearing handler callbacks. It updates view state only;
  it must not create/prepare/seek a player or alter video Surface ownership.

### 3.7 Immersive Video Aspect Ratio

The persistent `spatialized_video_panel` anchors the stage. Its child
`videoSurfaceEntity` carries the compositor video panel and SDK-generated aperture.
The output buffer stays fixed while the physical panel shape follows content:

- Use `1920x1080` output pixels with `StereoMode.None`. Do not carry the
  upstream SpatialVideo sample's `3840x1080` LeftRight stereo buffer into this
  monoscopic Bilibili path.
- `VIDEO_SCALING_MODE_SCALE_TO_FIT` is NOT a promise of an aspect-preserving
  letterbox/pillarbox canvas inside the fixed pixel configuration. Media3 1.4.1
  scopes this setting to SurfaceView-owned outputs; this app attaches the SDK
  Surface directly. MediaCodec documents SCALE_TO_FIT as scaling to Surface
  dimensions. Final physical shape must supply the intended display aspect.
- `videoShapeOptions(width, height, curvature)` owns compositor dimensions and
  radius. Every aspect/scale/curvature change uses `PanelSceneObject.reshape`;
  never restore the old mesh-only PLAN_1 debug path. Target/Apply uses the shared
  display-ratio state. One `applyPlaybackStageParameters` call commits ratio,
  scale and curvature from an app-state emission before rebuilding anything.
- `PlaybackStageGeometry` distinguishes full-stage viewport and visible video
  content. `videoShape` equals videoContent; `videoTextureCrop` is the actual SDK
  `Crop.NONE`. The renderer submits it through setCrop, which restores unit
  footprint and full [0,1] texture coordinates. Do not compute a source/output
  UV crop from 1920x1080: 4:3 yields width .75, magnifying actual frame contents
  by 4/3. The fixed pixel budget does not establish black bars in the producer.
  Do not pre-divide shape or separately scale the aperture. Keep player/Surface.
- Danmaku uses option A: full 16:9 viewport with a fixed matching 1280x720 dp
  raster, independent of source/target video ratios. Backdrop uses the same full
  viewport. SDK 0.13.2 reshape only replaces PanelShape; changing DpDisplayOptions
  there does not resize PanelDisplay/PanelSurface. Narrowing physical UI geometry
  while retaining the old raster distorts glyphs. Proportional whole-stage scale
  is allowed; anisotropic source-ratio scaling of danmaku is not.
- Leave `sceneMeshCreator` unset at video creation AND reshape. SDK 0.13.2
  `PanelShapeConfig.generateSceneMeshCreator()` returns an explicit creator
  BEFORE preparing hole-punch alpha mode and shader. A shadow-only override
  removes the video aperture; remaining UI glyph apertures can reveal only
  silhouettes of video. The generated quad/cylinder aperture and compositor
  must use the same shape and measured carrier radius compensation.
- The host submits parameters/source dimensions to `PlaybackStageController`; only
  `PlaybackStageGeometrySystem` flushes pending work. The controller waits for the
  complete panel set and acknowledges only a successful group commit. Replacement
  panel identity triggers replay without requiring another state emission.
- `PlaybackStagePanelRenderer` is the only live stage geometry writer. It restores
  unit scale, applies UV-only video clip and writes fresh absolute local poses
  (identity child rotation), then native poses composed from ONE stage-world pose.
  Update the stage/bar offset before sampling that parent. Never add a creation,
  debug/reset, overlay-only or aspect-diagnostic-gated geometry writer.
- See [Playback stage geometry](./playback-stage-geometry.md) for scheduling,
  readiness, lifecycle and test contracts. Keep player/Surface ownership unchanged.
- Coordinates must be verified against the pinned SDK, not a generic convention:
  actual Pose().forward() is API +Z; native mesh/pose upload reflects Z to the
  rendering/OpenXR space. API cylinder central point is +radius, so existing
  entityLocalZForSurface(base)=base-radius is correct. Aperture and layer share
  the same native PanelSceneObject and both receive this method. Never flip
  SURFACE_ANCHOR_OFFSET_SIGN to investigate visual drift. Read
  docs/spatial-coordinates.md and distinguish API, stage-local and native spaces.
- Explicitly use OPAQUE for the video's full content rectangle and ALPHA_BLEND
  for transparent UI layers. The default UI Layer is MASKED (binary alpha with
  depth writes), inappropriate for smooth dimming and transparent overlapping UI.
- `LayerConfig.zIndex` orders compositor layers (video 1, backdrop 2, other UI 3);
  it does not replace the aperture needed to integrate with the main scene eye
  buffer or repair curvature/position. Environment meshes render into that buffer,
  not a separate low-zIndex panel. Transform parents do not create a compositor
  group or flatten their child textures. Hole punching changes the buffer alpha,
  not the environment model. Do not assume a video layer needs no aperture merely
  because its zIndex is positive.
- Debug builds log source aspect and applied panel shape. A successful reshape
  or unit test is not evidence of visual correctness on a headset.
- Preserve the panel instance, player and Surface when dimensions change.
  Invalid video dimensions retain the full stage rectangle; deferred panel
  availability is handled by the controller, without independent replay flags.
- Regression coverage must retain pending/apply aspect behavior, contained
  aspect dimensions and absolute carrier correction across radii and back to flat.
  Device acceptance covers flat/curved transitions, ratio changes, layer order,
  complete video without synthetic FIT cropping, full-width uncompressed danmaku, shared scale,
  radius-position synchronization, transparent UI and stage input with
  no extra video output. Never use glyph visibility as proof of full-video visibility.
- Debug builds display `DEV <BuildConfig.GIT_SHA>` on the existing `mode_panel`.
  Use this value, rather than APK filename or install time, to identify the
  running build during Quest testing.
- A right-edge stretched strip on a landscape source is a diagnostic signal:
  first confirm this monoscopic 16:9 buffer contract and `StereoMode.None`, then
  compare another source. Do not compensate by changing the scene transform or
  adding a second video output.

### 3.8 Wrist Debug Panel

Debug builds may create one small, non-interactive runtime panel bound to the
local avatar's left hand. It displays only `DEV <hash>` for Quest tracking
validation and is neither a video target nor a static scene object.

- Use an ECS system driven by local `AvatarBody` head and left-hand `Transform`
  components. The panel must hide when either transform is unavailable and may
  reappear when tracking returns.
- It must not own a Media3 player, Surface, source request, transport command,
  input listener, or live-room behavior. Destroy its dynamic entity with the
  Activity.
- Quest acceptance checks tracking stability, hand/controller input isolation,
  visibility on tracking loss/recovery, and main-stage non-occlusion.

### 3.9 Current Media Status

The existing `mode_panel` is the first immersive current-media status surface.
It displays the selected title and author from `ViriViriAppState`, with viewer
playback errors replacing only the author/detail line while retaining the title.
It is an existing Android panel and must not own a player, Surface, Bilibili
request, Entity, or new Spatial registration.

- The Activity projects app state through the pure `immersiveMediaStatus` helper
  before setting its existing TextViews. It uses bounded strings and XML
  single-line ellipsis so title/error growth cannot alter fixed panel geometry.
- Browse/feed/search pagination errors remain in their list UI. They must not
  masquerade as a current-media playback failure in the status panel.
- The initial panel setup renders the latest state, and the existing
  Activity-owned app-state collector refreshes it after selection or viewer
  error. Its existing `onDestroy` cancellation remains the cleanup path.
- When viewer playback resolution has settled with an error, the same existing
  mode panel shows Retry. Its click delegates only to
  `ViriViriAppState.retrySelectedVideo()`; while resolution is in flight the
  button is visible but disabled as `Retrying...`. Browse/list errors never
  expose this action.
- The current-media detail explicitly prioritizes viewer resolution state:
  `Loading video...` while resolving, the settled viewer error after failure,
  then selected author after success. Source resolution is bounded to 45 seconds;
  its current-attempt timeout settles as `Video source resolution timed out` and
  enables existing Retry. A superseded selection/retry cancellation remains
  silent and cannot clear the newer Loading state. This is a pure state
  projection; it must not add polling, player commands, or a second observer.

### 3.10 MediaStage Adapter Boundary

The existing `spatialized_video_panel` is the immersive `VIDEO_OUTPUT` target.
`SpatialVideoSampleActivity` provides its SDK-owned `PanelSceneObject.surface`
to `ImmersiveMediaStageHost`; the adapter dispatches the pure-core
`MediaStageReducer` target event and executes its attach effect through the
process-wide `PlayerSession`.

- The core reducer receives only the stable target ID `immersive-video`, clock
  snapshots, and lifecycle events. It never receives the SDK `Surface`.
- The host must never release the SDK-owned Surface. At terminal Activity
destruction it removes its Media3 listener and drops its local reference only.
- `onResume` may repeat the target availability callback. The same Surface is a
  no-op; a replacement Surface for the same target delegates to
  `PlayerSession` identity-aware replacement without registering another video
target.
- Feed player state, playing-state, and position-discontinuity callbacks into
  the host clock lifecycle. Do not add a frame timer or per-frame logging for
  MediaStage state.
- The protected immersive-to-2D route retains `beginOutputHandoff()` and must
  not add an immersive detach/clear during Spatial teardown; the destination
  Surface owns replacement.

### 4. Validation & Error Matrix

| Symptom or condition | Required diagnosis and action |
| --- | --- |
| Three-dot system placeholder with audio | Verify that the Activity uses `AppSystemActivity` plus `VRFeature`; ordinary Compose/TextureView is not an immersive target. |
| Black immersive session with tracked hands | Confirm `onSceneReady()` and `onVRReady()` logs separately; then verify the panel entity is created with `Visible(true)`. |
| Panel Surface callback absent | The panel registration/entity lifecycle has not completed. Do not change ExoPlayer or call `prepare()` again. |
| Surface and Media3 first-frame logs exist but no visible panel | Check explicit `Visible(true)`, panel entity creation in `onVRReady()`, and only then pose/quad orientation. |
| Panel visible but reversed | Apply a 180-degree Y yaw to the panel pose. Keep player, Surface, and media unchanged. |
| Only upper-left quarter of a normal video is shown | Remove `StereoMode.MonoLeft`; use `StereoMode.None`/default full-frame monoscopic sampling. |
| Debug geometry only partially visible or off-center | Do not infer panel coordinates from it when reference space and view origin are both transformed. Remove multi-axis diagnostics and test one panel first. |
| System menu exit freezes controller tracking or menu input | Capture lifecycle elapsed logs and Horizon OS service/task logs. Treat it as a separate platform lifecycle/performance issue until evidence ties it to app code. |

### 5. Good / Base / Bad Cases

* Good: `onVRReady()` creates one `Visible(true)` panel; the SDK returns one
  valid Surface; the same player attaches once; `prepareCalls == 1`; a complete
  monoscopic frame appears facing the user.
* Base: an ADB launch creates the Activity but leaves the immersive session
  paused. It may log scene setup or decoder initialization without providing a
  physical visibility verdict. Launch from the headset library for visibility
  acceptance.
* Bad: repeatedly changing Z coordinates, adding lighting, or creating multiple
  player/panel instances before validating entity lifecycle and visibility. This
  adds load and hides the actual rendering contract failure.

### 6. Tests Required

* Unit test player and handoff state independently: one media load, stale
  Surface detach safety, and first-frame completion only after verified Surface
  attachment.
* Build test: `./gradlew.bat :app:testDebugUnitTest --no-build-cache`.
* Package test: `./gradlew.bat :app:assembleDebug --no-build-cache`.
* Quest device test, launched from the headset library:
   * verify a full-frame, correctly facing direct video panel;
   * verify the opaque control panel is legible above the video and `Enter 2D
     Panel` logs a click before opening the Horizon OS system 2D panel;
   * verify the control click does not add a video Surface, prepare call, or
     decoder initialization before the normal route handoff occurs;
   * verify the ordered app logs above;
  * verify `prepareCalls == 1` and no decoder reinitialization on route cycles;
  * verify only one direct video Surface is attached;
  * perform a system-menu exit while capturing lifecycle durations and Horizon
    OS logs separately from video handoff metrics.

### 7. Wrong vs Correct

#### Wrong

```kotlin
override fun onSceneReady() {
    Entity.createPanelEntity(VIDEO_PANEL_ID, Transform(pose))
}

MediaPanelRenderOptions(stereoMode = StereoMode.MonoLeft)
```

This relies on an early lifecycle callback, leaves panel visibility implicit,
and crops a normal monoscopic stream as a stereo-eye texture.

#### Correct

```kotlin
override fun onVRReady() {
    Entity.createPanelEntity(
        VIDEO_PANEL_ID,
        Transform(pose),
        Visible(true),
    )
}

MediaPanelSettings(
    shape = QuadShapeOptions(2.4f, 1.35f),
    display = PixelDisplayOptions(1920, 1080),
    // Default StereoMode.None renders a normal full-frame monoscopic file.
)
```

This waits for the VR-ready panel lifecycle, makes compositing intent explicit,
and preserves full-frame sampling without rebuilding the player.


### 3.12 Scene geometry used for clipping observations

When authoring a diagnostic background through `mse-agent`, primitive scale is
not its size in metres. The v16 CLI `readme` specifies a cube base edge of
0.4 m: scale (4, 2.5, 0.05) yields (1.6, 1.0, 0.02) m before parent transforms.
Check the exported geometry and headset view before calling that background a
valid clipping control. A foreground scene mesh can occlude video; its dark
silhouette alone does not identify the original aperture defect.

The independent geometry experiment uses an editor-authored `MediaRoom` node
from the demo/product room asset at identity transform. Its Activity applies
`Mesh.defaultShaderOverride = SceneMaterial.UNLIT_SHADER`, matching the product
room visibility setup without changing media panel configuration. Asset export
and a passing build do not establish that video and scene cutout match visually.


### 3.13 Independent geometry slider submissions

The debug experiment's native SeekBars submit an `ExperimentGeometry` in integer
centimetres: width 100..240, curved radius 80..300 or null for flat, height derived
as width * 9/16. Width never changes radius. Radius input on flat is a no-op.
The existing preset buttons reset both slider values without synthesizing user
input; flat disables radius adjustment.

`ExperimentGeometryUpdates` keeps one latest complete request. The Activity
schedules a single 100 ms main-thread callback; subsequent drag events replace
its payload without restarting the timer. Release flushes the final value.
Preset replacement and shutdown increment an epoch and cancel pending work;
callbacks must check that epoch even when cancellation was requested. Do not let
an old callback consume a newer drag request. Apply identical successful geometry
as a no-op, but retain explicit retry after creation/cleanup failure.

The debug path recreates the panel using final shape parameters, retaining the
existing detach-by-identity before destroy policy and the same player/media.
It does not establish reshape behavior or continuous-update visual correctness.
Test coalescing, late callback invalidation, release and close with a controlled
scheduler; verify ray dragging and any recreation flicker separately on headset.


### 3.14 Independent pseudo overlays and centre-depth correction

The debug geometry experiment adds UI-only `ExperimentLayer.DANMAKU` and
`ExperimentLayer.SUBTITLE` alongside VIDEO. All three consume the same final
`ExperimentGeometry`. Derive each carrier local Z as `baseLocalZ - radius`
(flat: `baseLocalZ`), using the pinned SDK coordinate audit in
`docs/spatial-coordinates.md`. Base depths are 0, -0.01, -0.02 m; zIndex is
0, 1, 2 respectively, with the existing input controls at 3. Recompute from
base after each slider change; never accumulate radius deltas.

The two overlays use `PanelSceneObject(scene, spatialContext, ent, config, getView)`
with transparent native Views, ALPHA_BLEND, no glass and NoCollision. Pass the
Activity's `spatialContext` to this constructor; the Android Activity itself is
not a SpatialContext (it remains valid as the native View's Android Context). Their UI
Surfaces remain SDK-owned, with no media consumer. Their physical width, height
and radius match video. Do not pass media PixelDisplayOptions to UI settings;
use DpDisplayOptions(1280, 720, 160). The Activity's ExperimentRetainedOverlays owns
two UI carriers independently of video generation. Record ownership before SDK
initialization and retain entries after partial failure. Normal geometry updates
call reshape and change ECS Z without replacing Entity, View, UI Display or Surface.
Assert Surface/Display identity after reshape and keep texture dimensions fixed;
do not resize or detach the UI. Shutdown closes all UI independently of the video
consumer and retains failed cleanup entries for retry.

Pseudo content reads the existing player's position. It must not prepare, seek,
attach another media Surface, or reset its animation timeline on geometry change.
The overlay clock must not depend on transient video generation or disappear
while video replacement runs. Update failure hides both overlays and retains UI
for retry; successful replacement must not hide them or restart their animation.
Verify identity reuse, partial-update retry, closed ownership, centre-depth algebra
and timeline boundaries in JVM tests. Reshape internally rebuilds shape/layer, so
resource retention alone does not prove zero visual interruption; verify on headset.


### 3.15 Switchable overlay scene-mask candidates (debug only)

`ExperimentMaskMode` selects ORIGINAL, ALIGNED or HIDDEN for the two retained
UI overlays, with ORIGINAL as baseline. No video configuration, Spatial panel
registration, extra aperture Entity or UI Surface is added. Mode-only changes
invalidate the retained role cache, replay at last committed geometry and assert
video generation/Surface are unchanged. Keep the selected mode on failure, show
an error and hide partial overlays; do not silently fall back.

ALIGNED installs createExperimentMaskSceneMesh after calling settings.init(),
which selects the SDK alpha-punch material policy before the custom factory can
short-circuit default initialization. Explicit vertices use the API axis origin:
(x,y,z) = (r*sin(arcX/r), y, r*cos(arcX/r)), flat z=0, inward normals, full [0,1]
UVs and single triangle winding with a double-sided material. It consumes the
existing live UI texture and final PanelShapeConfig width/height/radius. Existing
carrier base-r compensation is applied exactly once. Do not import production
geometry update/compensation helpers or infer visual correctness from CPU math.

HIDDEN retains SDK mesh geometry for bounds and compositor association, but
calls setColorWrite(0) and setDepthWrite(DISABLE) on only overlay scene materials
after every creation/reshape. Zero disables RGBA, including alpha. It does not
hide the Entity, stop animation, detach Display or change the video aperture.
Original/ALIGNED switching regenerates fresh mesh materials to restore writes.

Test analytic mesh extent, arc/radius, centre depth, UVs, winding/index bounds,
and retained-cache invalidation without reallocating UI. Log mode, geometry and
stable View/Surface identities. Both candidates require headset evaluation for
text visibility, silhouette, curvature, clipping and flicker; no promised repair.


The user accepted B visually on 2026-10-22, while reporting cross-mode influence.
Keep the accepted use case (text inside video) distinct from unverified mode
isolation, B-only cold startup, outside-video text and foreground occlusion.
See docs/geometry-overlay-poc.md before replacing production rendering.
