# Minimal Quest Immersive Rendering Host Research

## Decision

Use the Meta Spatial SDK toolkit host already resolved by this project:

* Make `ImmersiveActivity` extend `com.meta.spatial.toolkit.AppSystemActivity` (or, at the lowest level, `com.meta.spatial.runtime.VrActivity`).
* In `registerPanels()`, return exactly one toolkit `VideoSurfacePanelRegistration`.
* In the registration's surface consumer, pass the supplied `android.view.Surface` to the existing process-wide `PlayerManager.attachSurface(...)`.
* Create one entity carrying that registration when the Spatial scene is ready. The toolkit's `Entity.createPanelEntity(...)` helper is the intended minimal entity construction path.

This is the smallest practical SDK route for a visible immersive Activity on Quest because `AppSystemActivity` is a `VrActivity`, which initializes the Meta OpenXR/Spatial runtime and owns a `Scene`. The toolkit registration creates a `PanelSceneObject` in that scene, and its `surface()` is a real Android `Surface`. It is therefore a direct Media3/`ExoPlayer` output target, rather than a regular Android `TextureView` that Horizon OS may keep behind the three-dot loading compositor.

Do not attempt to make the existing Compose `AndroidView` / `TextureView` the immersive rendering target. It remains appropriate for the 2D `PanelActivity`, but Quest 2 validation shows that it does not establish a visible spatial render submission in the VR-category Activity.

## Recommended Minimal Host

### Activity and panel lifecycle

The first validation implementation should be deliberately small:

1. Change the immersive Activity superclass from `ComponentActivity` to `AppSystemActivity`.
2. Override `registerPanels()` and register one `VideoSurfacePanelRegistration` with a stable integer registration ID.
3. Override `onSceneReady()` and create a single panel entity using that registration ID. Give it a front-of-user transform, retaining a flat `QuadShapeOptions`; no glXF scene asset, curved screen, spatial editor content, physics, input, or custom OpenXR extension is needed.
4. In the registration's `surfaceConsumer`, attach the supplied Surface to the existing singleton player through the same identity-safe handoff coordinator used for the 2D `TextureView` Surface.
5. Treat the panel surface becoming available as the immersive destination's Surface-ready milestone. Treat a Media3 `Player.Listener.onRenderedFirstFrame()` after that specific surface is attached as the displayed-frame milestone. This is more meaningful than `TextureView.onSurfaceTextureUpdated()`, which is not involved in this host.
6. When the panel / immersive Activity is being torn down, detach only if that exact Spatial panel Surface is still the player target. Do not release the process-wide player during an in-process handoff.

The initial panel may contain only video. The app can add a second `ViewPanelRegistration` later for Compose controls, but that is not required to prove that a Spatial panel surface is visible and accepts a Media3 decoder output.

### Conceptual API shape

This is an API map, not drop-in code. Constructor defaults and the precise `MediaPanelSettings` options should be confirmed by compiling against `0.13.0`.

```kotlin
class ImmersiveActivity : AppSystemActivity() {
    override fun registerPanels() = listOf(
        VideoSurfacePanelRegistration(
            registrationId = VIDEO_PANEL_ID,
            surfaceConsumer = { _, surface ->
                playerManager.attachSurface(surface, HandoffTarget.IMMERSIVE, transitionId)
            },
            settingsCreator = {
                MediaPanelSettings(
                    shape = QuadShapeOptions(width = 2.0f, height = 1.125f),
                    // Default display/render/input options are sufficient for a first test.
                )
            },
        )
    )

    override fun onSceneReady() {
        super.onSceneReady()
        Entity.createPanelEntity(VIDEO_PANEL_ID, Transform(/* in front of user */))
    }
}
```

The exact entity helper has two overloads in `0.13.0`: registration ID plus `Transform`, or registration ID plus an additional integer and `Transform`. The implementation task should use the overload accepted by the Kotlin compiler and inspect the official sample source for its chosen transform convention.

## Verified Local SDK Contract

The following findings are from the locally resolved Gradle AARs, not inferred from documentation.

* Project version: `com.meta.spatial:*:0.13.0`, declared in `gradle/libs.versions.toml` and used by `app-meta`.
* `meta-spatial-sdk:0.13.0` provides `com.meta.spatial.runtime.VrActivity`. It extends `android.app.Activity`, owns `Scene` and `SpatialContext`, initializes the Spatial runtime in `onCreate`, and invokes `onSceneReady()` after the session has resumed.
* `meta-spatial-sdk-toolkit:0.13.0` provides `AppSystemActivity`, which extends `VrActivity`. It automatically registers the panel registrations returned by `registerPanels()` and executes toolkit systems each scene tick.
* Toolkit `VideoSurfacePanelRegistration` receives `(Entity, android.view.Surface)` in its surface consumer. Its local bytecode constructs `PanelSceneObject(scene, entity, settings.toPanelConfigOptions())` and immediately supplies `PanelSceneObject.getSurface()` to that consumer.
* `PanelSceneObject.surface()` and `PanelSurface.surface` are explicitly `android.view.Surface` APIs. This is the required direct bridge for `ExoPlayer.setVideoSurface(surface)` / the project `PlayerManager.attachSurface` abstraction.
* `QuadShapeOptions` implements `MediaPanelShapeOptions`; it is a flat panel with width and height. This satisfies the visible-host requirement without curved cinema geometry.
* `PanelConfigOptions` defaults can render as a panel layer. The API exposes `PanelShapeType.QUAD`, `CYLINDER`, and equirectangular types, confirming that a flat quad is a supported first step.
* The current official `HybridSampleActivity` registers `VRFeature(this)`, then in `onSceneReady()` sets `ReferenceSpace.LOCAL_FLOOR` and `scene.setViewOrigin(0f, 0f, 2f, 180f)`. The same bootstrap is required before dynamically registered panel entities are reliably visible in this host.
* `MediaPanelSettings` exposes shape, display, render, style, and input settings but no material-sidedness setting. The local toolkit `Mesh`, `Material`, and `Entity` APIs can create independent unlit diagnostics; `Scene.drawDebugLine` is frame-scoped and therefore belongs in `onSceneTick()`.

## 2026-07-29 Scene Bootstrap And Diagnostics

`ImmersiveActivity` now follows the current HybridSample bootstrap without copying its device-rejected manifest declarations: `registerFeatures()` contains `VRFeature(this)` and `onSceneReady()` uses `LOCAL_FLOOR` plus the sample's explicit `(0, 0, 2, 180 degrees)` view origin.

The former six `ViewPanelRegistration` numbered markers were removed. They depended on additional Android panel layers and did not establish a native scene coordinate system. An independent unlit `mesh://axis` entity and signed debug lines now visualize `+X/+Y/+Z` in bright red/green/blue and their negative directions in darker matching colors. This is independent of video-panel registration and requires no lighting because panel layers are compositor-owned surfaces and the diagnostic material is explicitly unlit.

The 0.13.0 `MediaPanelSettings` API has no two-sided or material-sidedness option. A separate yellow unlit `mesh://axis` cue is therefore placed immediately behind/beside the video panel instead of changing panel registration or adding another surface.

Relevant local artifacts:

* `C:\Users\N00b\.gradle\caches\modules-2\files-2.1\com.meta.spatial\meta-spatial-sdk\0.13.0\9fa3c7d2660d8961aae013bea5d8fdb53feb2d81\meta-spatial-sdk-0.13.0.aar`
* `C:\Users\N00b\.gradle\caches\modules-2\files-2.1\com.meta.spatial\meta-spatial-sdk-toolkit\0.13.0\5b8a1aa99c3f76779eb64582bdc226c95277df66\meta-spatial-sdk-toolkit-0.13.0.aar`
* `C:\Users\N00b\.gradle\caches\modules-2\files-2.1\com.meta.spatial\meta-spatial-sdk-vr\0.13.0\d27b958dff2674f0cea37148dd1a7e44fd5275ab\meta-spatial-sdk-vr-0.13.0.aar`

## Dependencies

### Required for this approach

The module already has all needed dependencies:

```kotlin
implementation(libs.meta.spatial.sdk)         // `VrActivity`, Scene, panel Surface runtime
implementation(libs.meta.spatial.sdk.toolkit) // `AppSystemActivity`, registration helpers
implementation(libs.media3.exoplayer)         // Existing Media3 output owner
```

Keep the existing Meta Spatial Gradle plugin. The project already applies `com.meta.spatial.plugin`, so no plugin introduction is required for a programmatically created flat panel.

`meta-spatial-sdk-vr` and `meta-spatial-sdk-physics` are not required by the proposed host code: `AppSystemActivity` is in the toolkit and extends the core SDK's `VrActivity`; the toolkit POM itself depends on `meta-spatial-sdk`. The app already resolves both extra artifacts, so the smallest *implementation* is to leave dependencies unchanged. A cleanup task may later remove `vr` and `physics` after proving no build/plugin/native packaging dependency requires them.

Do not add a separate AndroidX XR, raw OpenXR Java binding, OpenGL renderer, or custom shader dependency. Those alternatives create a rendering host from scratch while the Spatial SDK already provides a validated Android-Surface-to-immersive-panel bridge.

### Manifest baseline

The project already has the key Horizon declaration and VR intent category. The official current Meta `HybridSample` and `MediaPlayerSample` manifests also declare:

```xml
<uses-feature android:name="android.hardware.vr.headtracking" android:required="true" />
<uses-feature android:glEsVersion="0x00030001" />
<application>
    <uses-native-library android:name="libossdk.oculus.so" android:required="true" />
</application>
```

The sample immersive Activity also uses a fullscreen black theme, `singleTask`, and configuration-change handling. These manifest pieces should be compared with the *merged* manifest before device testing. The local `0.13.0` AAR manifests were not sufficient to establish which of them the Gradle plugin contributes automatically, so adding or retaining the sample baseline is a validation item rather than an unqualified claim that every line is mandatory.

## Surface Handoff Design

The existing process-wide PlayerManager design remains appropriate, with a different immersive endpoint:

* 2D destination: `SurfaceHandoffTextureView` creates a `Surface(TextureView.surfaceTexture)` and attaches it to the player.
* Immersive destination: `VideoSurfacePanelRegistration` gives the host a Spatial panel `Surface`; attach that exact surface to the same player.
* At any moment the player owns one active output Surface. The handoff coordinator must replace outputs atomically and make all delayed detach/release callbacks identity-safe.
* The Spatial panel Surface is SDK-owned. Do not call `surface.release()` merely because it was passed to Media3. Its owner is the `PanelSceneObject` / panel display lifecycle. The app should call player detach by identity when the panel is disposed, then let the SDK destroy the panel.
* The existing `SurfaceHandoffTextureView` owns and releases the Surfaces it creates from `SurfaceTexture`; that ownership rule does not transfer to the SDK-owned panel Surface.

The existing first-frame mask cannot be a Compose overlay in the immersive Activity unless it is hosted by another spatial panel. For the first visible-host proof, use the panel itself plus Media3's first-frame event as the readiness metric. A later visual mask can be implemented as a second spatial UI panel or by hiding/showing the video panel, without changing player ownership.

## Why Not the Other Options

### Plain `VrActivity` plus direct `PanelSceneObject`

This can be made to work and is technically one layer smaller, but it requires manually creating `PanelConfigOptions`, `PanelSceneObject`, and the associated scene lifecycle. The toolkit host eliminates that plumbing and already owns the registration and entity-creation systems. `AppSystemActivity` plus one `VideoSurfacePanelRegistration` is the smallest practical, low-risk implementation.

### `ViewPanelRegistration` / compose panel

The official Hybrid sample uses a 2D panel to host Android/Compose UI in immersion. It proves a panel can be visible, but it would again put Media3 behind a regular `TextureView` inside an Android view hierarchy. Use it later for controls if wanted; use `VideoSurfacePanelRegistration` for the video target because it exposes the panel's direct Surface.

### Raw OpenXR rendering

Raw OpenXR would require session, swapchain, frame-loop, image layout, and Android lifecycle management. It has no advantage for the stated goal because the Spatial SDK already owns that infrastructure and exposes a compatible Android Surface. It is substantially larger and would complicate the single-player handoff experiment.

## Official Evidence

* Meta's public `HybridSample` describes its immersive experience as hosting the same 2D panel, which supports keeping the system 2D Activity and immersive host as separate responsibilities: <https://github.com/meta-quest/Meta-Spatial-SDK-Samples/tree/main/HybridSample>
* Meta's public `MediaPlayerSample` describes immersive playback and Compose panels, and points implementers to `registerPanels` for panel spawning: <https://github.com/meta-quest/Meta-Spatial-SDK-Samples/tree/main/MediaPlayerSample>
* The current public sample repository lists Quest 2 as supported on Horizon OS build v69+ and uses AGP 8.11.1 / JDK 17, broadly consistent with this project except for the project's Gradle 8.11.1 wrapper: <https://github.com/meta-quest/Meta-Spatial-SDK-Samples>
* The sample manifests retrieved during this research establish the expected VR Activity shape and head-tracking/native-library declarations. They use an older sample API/SDK target than this project's Horizon declaration, so use them as a baseline rather than copying their target versions.

## 2026-07-29 Explicit Panel Visibility Fix

Official Spatial SDK guidance treats visibility as an explicit entity component,
not an implicit property of a panel registration. The public
`PremiumMediaSample` creates its direct video surface panel entity with its
panel registration component, `Transform`, and `Visible(false)`, then enables
that component during its fade-in lifecycle. This establishes that a dynamic
panel entity must carry `Visible` deliberately before it can be expected to
render.

For the focused clean-pose test, `ImmersiveActivity` now creates its single
video entity with `Visible(true)` in the compile-verified
`Entity.createPanelEntity(registrationId, transform, vararg components)`
overload. It logs both `visible=true` and the entity component list immediately
after creation. The existing `LOCAL_FLOOR` / `(0, 0, 2, 180)` view origin and
single panel pose remain unchanged.

The registration also uses `MediaPanelRenderOptions(stereoMode =
StereoMode.MonoLeft, zIndex = 0)`, matching the direct mono-video intent. No
`PanelStyleOptions` was added: the local 0.13.0 API accepts only a theme resource
ID, and this app has no established transparent panel style resource. Adding one
would exceed this visibility-focused test.

Evidence:

* Meta Spatial SDK entity/component overview: <https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-entities/>
* Meta Spatial SDK Samples `PremiumMediaSample`: <https://github.com/meta-quest/Meta-Spatial-SDK-Samples/tree/main/PremiumMediaSample>

## Uncertainty And Required Validation

1. Compile a minimal `AppSystemActivity` with one `VideoSurfacePanelRegistration` against the project's exact `0.13.0` dependencies. Confirm the Kotlin default constructors for `MediaPanelSettings`, the correct `createPanelEntity` overload, and the transform units/forward direction. Bytecode confirms the API contracts but not ergonomic Kotlin call syntax.
2. Confirm that one programmatically created `QuadShapeOptions` panel is visible on the connected Quest 2 without a Spatial Editor-exported scene asset. The API permits programmatic panel construction; this still requires actual device proof.
3. Add the official sample's head-tracking, GLES 3.1, and `libossdk.oculus.so` manifest declarations only as needed after inspecting the merged manifest and device launch result. Verify that current Horizon OS target SDK 76 remains valid with the `0.13.0` runtime.
4. Validate video output using the bundled non-DRM `rick.mp4` first. Confirm that the panel Surface can replace the 2D TextureView Surface without another `prepare()` or decoder initialization.
5. Establish panel-surface destruction timing. The registration gives a Surface-ready callback, but its public API does not expose a matching destroy callback. Identify the panel/entity lifecycle callback or keep the active-surface identity in the coordinator and detach when the immersive Activity / scene is actually stopping.
6. Confirm Media3's `onRenderedFirstFrame` occurs after the Spatial panel Surface is attached and is reliable enough to release the cross-Activity mask/finish the source. Log both panel Surface arrival and player first-frame times.
7. Exercise at least five Panel -> Immersive -> Panel cycles on Quest 2. Verify a visible Spatial panel, player identity stability, `prepareCalls == 1`, decoder continuity, audio continuity, and no SDK-owned surface release by app code.
8. Test the existing Home-plus-pending-intent panel route after the Activity changes. `VrActivity` has its own pause/focus semantics, so do not assume the current ComponentActivity lifecycle ownership decision matrix remains complete.
9. Quest 2 validation on this project rejected an Activity launch when the sample
   `uses-native-library android:name="libossdk.oculus.so" android:required="true"`
   declaration was added. Do not add that declaration unless the target Horizon
   OS image exposes the required native library and accepts the launch. The
   Spatial SDK's packaged native libraries are sufficient for this 0.13.0 host.
   The same Quest shell also rejected ADB launch while a mandatory
   `android.hardware.vr.headtracking` feature triggered its controller-required
   gate with no active controllers. Keep that feature absent for this minimal
   video-only host unless controller interaction is actually required.
   The attached Quest 2 also rejected a manifest containing the sample GLES
   feature and `singleTask`/`configChanges` additions despite no explicit
   controller feature in the merged manifest. Keep the existing minimal VR
   manifest shape for this proof and add sample declarations one at a time only
   with device evidence.

## Scope Boundary

This recommendation intentionally does not require a 3D curved display, glXF scene, Spatial Editor asset, custom shader, DRM/protected surface, passthrough, hand tracking, physics, Spatial SDK VR input feature, or raw OpenXR extension. Those can be layered on after the one flat video panel proves the immersive compositor path and the Surface handoff.
