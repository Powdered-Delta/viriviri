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
  `ReferenceSpace.LOCAL_FLOOR` and `scene.setViewOrigin(0f, 0f, 2f, 180f)`.
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

### 3.5 Transport Timeline Ownership

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

### 3.6 Immersive Video Aspect Ratio

The persistent `spatialized_video_panel` has a fixed 16:9 physical mesh. Its
single media output buffer must match that geometry:

- Use `1920x1080` output pixels with `StereoMode.None`. Do not carry the
  upstream SpatialVideo sample's `3840x1080` LeftRight stereo buffer into this
  monoscopic Bilibili path.
- The shared `PlayerSession` explicitly uses Media3
  `VIDEO_SCALING_MODE_SCALE_TO_FIT`. Portrait, standard landscape, ultrawide,
  and non-square-pixel sources must retain their display aspect within the one
  existing Surface through pillarbox/letterbox rather than stretch or crop.
- The custom mesh contains two independent visual layers on the existing media
  panel: a fixed full-stage translucent black backdrop behind an adaptive video
  foreground quad. Both live in the same `SceneMesh`; the backdrop is not a
  panel/entity/video target and uses no additional Surface.
- On `VideoSize`, update foreground vertices and call
  `SceneMesh.updateWithTriangleMesh(...)` after `TriangleMesh.updateGeometry()`
  so the new geometry is committed to the mesh that is currently rendered.
  The fixed backdrop and shadow/input footprint remain unchanged.
- Do not rebuild the panel, mesh, player, or Surface when `VideoSize` changes;
  invalid or unavailable video dimensions retain full-stage foreground geometry.
- Debug builds display `DEV <BuildConfig.GIT_SHA>` on the existing `mode_panel`.
  Use this value, rather than APK filename or install time, to identify the
  running build during Quest testing.
- A right-edge stretched strip on a landscape source is a diagnostic signal:
  first confirm this monoscopic 16:9 buffer contract and `StereoMode.None`, then
  compare another source. Do not compensate by changing the scene transform or
  adding a second video output.

### 3.8 Current Media Status

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

### 3.9 MediaStage Adapter Boundary

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
