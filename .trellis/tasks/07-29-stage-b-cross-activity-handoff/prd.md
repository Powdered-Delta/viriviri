# Stage B Cross-Activity Surface Handoff

## Goal

Validate the real Meta Hybrid route between `ImmersiveActivity` and the Horizon
OS system 2D `PanelActivity` while one process-wide Media3 `ExoPlayer` continues
playing the bundled `rick.mp4`. The app must launch immersive by default, allow
the user to enter the system panel and return to immersive, and preserve player,
decoder, position, and audio continuity across every Activity transition.

## What Is Already Known

* Android builds use AGP 8.9.1, Gradle Wrapper 8.11.1, compile SDK 36, and target
  SDK 35.
* `./gradlew.bat :app-meta:assembleDebug --no-build-cache` is the established
  packaging command and has passed before this task.
* Stage A proved that one global `ExoPlayer` can hand video output between two
  `TextureView`/`Surface` targets in one `PanelActivity` without preparing again
  or reinitializing the decoder.
* The bundled media URI is `asset:///poc/rick.mp4`.
* The current manifest exposes `PanelActivity` as the Android launcher while
  `ImmersiveActivity` only has the VR category.
* The current transition controller immediately calls `finishAndRemoveTask()`
  after launching the destination. This destroys the source before the target
  Surface is known to be ready.
* `PanelActivity.onDestroy()` currently releases the singleton player whenever
  that Activity is finishing. This is incompatible with cross-Activity player
  continuity.
* Delayed destruction of an old `TextureView` is already safe because
  `detachSurface(surface)` only clears the player output if that exact Surface
  is still current.
* A very short white frame can occur during Surface replacement. Stage B may
  mask it visually but must not reload media or recreate the player.

## Requirements

* Make `ImmersiveActivity` the default application launcher and keep its Meta VR
  category. `PanelActivity` remains the Horizon OS 2D Activity but is no longer
  the default launcher.
* Keep the official Meta Hybrid routing behavior:
  * Immersive to panel uses Home plus
    `extra_launch_in_home_pending_intent` so the destination is a Horizon OS
    system 2D panel.
  * Panel to immersive uses an explicit VR Activity intent.
* Replace immediate source-Activity teardown with a coordinated handoff:
  * The source requests and launches the destination without releasing the
    player or immediately finishing itself.
  * The destination creates its `TextureView` and valid `Surface`.
  * The destination Surface attaches to the existing player before the old
    Activity or old Surface is destroyed.
  * The destination reports its first displayed frame.
  * Only then may the transition controller finish/remove the source task.
* Keep a single process-wide `PlayerManager`/`ExoPlayer` instance. Activity
  destruction during a route transition must never call `release()`.
* Keep player ownership separate from the user-selectable transition playback
  policy. All policies reuse the same player and decoder:
  * `AUTO`: select a playback policy using measured handoff performance.
  * `CONTINUE_PLAYBACK`: keep media and audio advancing during the transition.
  * `PAUSE_AND_RESUME`: pause before handoff and resume after the destination is
    visibly ready so the user does not miss video content.
* Use descriptive labels in UI and metrics rather than exposing ambiguous
  `A`/`B` names. A future settings screen may present all three policies for
  explicit user selection.
* Call `setMediaItem()` and `prepare()` only on the first process-lifetime media
  load. Both Activities may call the idempotent load entry point.
* Use Meta Spatial SDK's flat `VideoSurfacePanelRegistration` as the temporary
  visible immersive target. It supplies an Android `Surface` to the existing
  player without requiring a curved screen, scene asset, or custom raw OpenXR
  renderer. Actual curved-screen/OpenXR scene output remains out of scope.
* Both Activities render the bundled video, current metrics, and an explicit
  route action:
  * Immersive: `Enter 2D Panel`.
  * Panel: `Return to Immersive Mode`.
* Preserve existing metrics and extend them for cross-Activity validation. At a
  minimum the UI and logs must expose:
  * prepare call count;
  * video decoder initialization count;
  * Surface handoff count;
  * playback position;
  * last handoff duration;
  * source/destination route or target identity;
  * whether the destination Surface and first frame became ready;
  * measured transition/first-frame timing needed to diagnose visible gaps.
* Define handoff duration as the route request to destination first-frame
  interval. Surface attach work may also be logged separately if useful.
* Add a black/faded transition mask over each target. Keep the destination mask
  opaque until its first `SurfaceTexture` update, then fade it out. The mask
  must not trigger player/media lifecycle operations.
* Keep Surface ownership strict: each `Surface` created from a
  `SurfaceTexture` is detached by identity and released exactly once.
* Log handoff milestones with enough information to correlate route request,
  target Surface attach, target first frame, source finish, position, prepare
  count, and decoder initialization count.
* Record the active transition playback policy and timing data needed for a
  future `AUTO` implementation, including destination first-frame time and time
  spent playing without a visible destination frame.
* Do not modify or remove the unrelated untracked `temp/` directory.

## Acceptance Criteria

* [ ] A normal app launch on Quest enters `ImmersiveActivity`, not
  `PanelActivity`.
* [ ] Immersive playback starts from bundled `rick.mp4` using the shared player.
* [ ] `Enter 2D Panel` opens `PanelActivity` as a Horizon OS system 2D panel.
* [ ] `Return to Immersive Mode` returns to `ImmersiveActivity`.
* [ ] Repeated immersive-to-panel-to-immersive cycles use the same
  `PlayerManager` and `ExoPlayer` object identities.
* [ ] `prepareCalls == 1` for the entire process-lifetime test session.
* [ ] Decoder initialization count does not increase after initial decoding when
  switching Activities.
* [ ] Playback position remains non-zero and advances across switches rather
  than resetting to zero.
* [ ] Audio remains continuous during both transition directions.
* [ ] Destination Surface attach occurs before source Surface destruction and
  source Activity teardown, as demonstrated by milestone logs.
* [ ] A delayed old-Surface destruction callback cannot clear the new output.
* [ ] The destination mask fades after its first displayed frame and does not
  call `setMediaItem()`, `prepare()`, or `release()`.
* [ ] Any remaining white/black-frame interval is recorded with route and timing
  data during Quest validation.
* [ ] Metrics/logging identify the transition playback policy and provide the
  timing inputs required to classify repeated slow handoffs later.
* [ ] Automated tests cover idempotent media loading, stale-Surface detach, and
  transition completion ordering where practical.
* [ ] `./gradlew.bat :app-meta:assembleDebug --no-build-cache` succeeds.
* [ ] The APK exists at
  `app-meta/build/outputs/apk/debug/app-meta-debug.apk`.
* [ ] Quest device validation results are recorded, including the number of
  route cycles, metric values, audio continuity, and observed visual gap.

## Technical Approach

### 1. Process-Wide Playback Ownership

Keep `PlayerManager` as the process singleton and remove Activity-finish-based
release behavior. Android process teardown is the terminal owner for this PoC;
route transitions only detach/replace video outputs. Preserve the idempotent
`loadTestMedia()` guard so either Activity can safely initialize its UI.

### 2. Explicit Handoff Coordinator

Extend the Meta-side transition controller into an in-process handoff
coordinator with a small transition state: unique transition ID, source mode,
destination mode, request timestamp, weak source-Activity reference, and target
readiness milestones. Route methods start the correct Meta intent but defer
`finishAndRemoveTask()`.

The target `SurfaceHandoffTextureView` reports two ordered callbacks:

1. Valid Surface created and attached to the shared player.
2. First `onSurfaceTextureUpdated()` received for that attached Surface.

The controller completes only the matching pending transition and then finishes
the weakly referenced source Activity. Repeated clicks and stale callbacks must
be idempotent. If the source has already been destroyed by the OS, completion is
still recorded without failing or touching the player.

### 3. Surface and First-Frame Contract

Add neutral callbacks to `SurfaceHandoffTextureView` rather than exposing
Activity or Meta types to shared Compose UI. `PlayerManager.attachSurface()`
continues to atomically clear the old output and set the new valid Surface. Old
Surface destruction remains identity-checked and therefore becomes a no-op for
player output after the new target is current.

### 4. Shared UI and Visual Mask

Use separate panel and immersive composable entry points, both receiving a
platform-provided video target and route callback. The panel uses its
`TextureView`; the immersive Activity uses the Spatial SDK-owned video-panel
Surface. The immersive layout remains visually distinct. Each Activity owns a
simple target-ready state used only to fade an opaque mask after the first frame
callback.

### 5. Metrics and Diagnostics

Extend `SurfaceHandoffMetrics` with cross-Activity route/first-frame information
while retaining existing fields. Update metrics at route request, Surface
attach, first frame, and source completion. Log object identity and milestone
timestamps in `:app-meta`; expose only neutral strings/enums/numbers through
`:core` and `:ui-compose`.

### 6. Manifest and Routing

Move the launcher intent filter to `ImmersiveActivity`. Keep the 2D and VR Meta
categories on their respective Activities. Verify merged-manifest behavior as
part of the build rather than changing only Compose buttons.

### 7. Verification Sequence

1. Run focused automated tests for player and coordinator invariants.
2. Build `:app-meta` with the no-build-cache command.
3. Inspect the generated APK path and merged manifest/launch Activity.
4. Install/launch on Quest when an ADB device is available.
5. Cycle immersive to panel to immersive at least five times while collecting
   logcat and on-screen metrics.
6. Record prepare count, decoder count, positions before/after, route timings,
   audio continuity, and any visible white/black-frame duration.

### 8. Transition Playback Policy Evolution

Keep a typed policy boundary between routing and player control so policy can be
selected without changing Activity or Surface ownership. Stage B uses
`CONTINUE_PLAYBACK` to validate the strongest continuity invariant and gathers
the measurements needed for later comparison with `PAUSE_AND_RESUME`.

A later settings task will expose:

* `Auto` - choose based on observed handoff performance.
* `Continue playback` - preserve uninterrupted audio and timeline progress.
* `Pause during transition` - preserve unseen video content on slow devices.

The later `AUTO` implementation should evaluate multiple recent transitions,
not react to one noisy sample. If repeated handoffs exceed the slow-transition
threshold, the app may suggest that the user review the transition setting. The
suggestion must be rate-limited, dismissible, and must not silently override an
explicit user choice.

## Decision (ADR-lite)

**Context**: Immediate `finishAndRemoveTask()` follows the basic Meta Hybrid
sample but conflicts with continuous Surface handoff because the source can
release the player and destroy its output before the destination exists.

**Decision**: Use an in-process, first-frame-confirmed handoff coordinator. Start
the platform route immediately, attach the destination Surface to the existing
player, wait for the destination's first displayed frame, and only then finish
the source Activity/task. Keep playback process-scoped and use a UI-only mask to
hide unavoidable compositor gaps.

**Consequences**: This maximizes continuity and gives measurable milestones,
but the two Activities may overlap briefly and the controller must ignore stale
or duplicate callbacks. Process death remains a cold-start path and is not
treated as a resumable handoff. Playback behavior during the overlap remains a
separate policy choice and never requires a second player instance.

## Out of Scope

* Meta Spatial SDK 3D scene hosting, curved cinema geometry, or OpenXR Surface
  integration.
* Network playback, Bilibili APIs, DRM, adaptive streams, or media selection.
* Persisting playback across Android process death.
* Eliminating every compositor-level transition artifact at the cost of player
  continuity.
* Production background-playback/service ownership policy. Stage B validates
  one player for the app process and Hybrid route.
* The settings UI for selecting `AUTO`, `CONTINUE_PLAYBACK`, or
  `PAUSE_AND_RESUME`.
* Automatic policy switching, persisted handoff history, slow-transition
  thresholds, and user prompts recommending a different policy. Stage B only
  preserves the extension point and records the required measurements.
* PICO implementation.

## Definition of Done

* The implementation and tests satisfy the acceptance criteria.
* The existing build baseline remains unchanged and the debug APK packages.
* Quest validation is completed if a device is connected; otherwise the exact
  unverified device steps and blocker are recorded without claiming success.
* Durable cross-Activity handoff contracts learned during implementation are
  added to `.trellis/spec/frontend/media3-surface-handoff.md`.
* Trellis quality checks are completed before implementation is committed.

## Technical Notes

* Primary code areas:
  * `app-meta/src/main/AndroidManifest.xml`
  * `app-meta/src/main/java/com/viriviri/app/meta/HybridTransitionController.kt`
  * `app-meta/src/main/java/com/viriviri/app/meta/ImmersiveActivity.kt`
  * `app-meta/src/main/java/com/viriviri/app/meta/PanelActivity.kt`
  * `app-meta/src/main/java/com/viriviri/app/meta/player/PlayerManager.kt`
  * `app-meta/src/main/java/com/viriviri/app/meta/player/SurfaceHandoffTextureView.kt`
  * `core/src/main/java/com/viriviri/core/state/SurfaceHandoffMetrics.kt`
  * `ui-compose/src/main/java/com/viriviri/ui/immersive/ImmersiveScreen.kt`
  * `ui-compose/src/main/java/com/viriviri/ui/browse/BrowseScreen.kt`
* Existing guidance:
  * `.trellis/spec/frontend/media3-surface-handoff.md`
  * `.trellis/spec/frontend/component-guidelines.md`
  * `.trellis/spec/guides/cross-layer-thinking-guide.md`
  * `.trellis/tasks/archive/2026-06/06-11-hybrid-foundation/research/meta-hybrid.md`
  * `research/minimal-quest-vr-host.md` - Quest 2 device validation established
    that a normal ComposeView/TextureView is not visible within a VR-category
    Activity; the supported minimal replacement is a Spatial SDK video panel.
