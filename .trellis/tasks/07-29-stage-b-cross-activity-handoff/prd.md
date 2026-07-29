# Stage B Cross-Activity Surface Handoff

## Replan: 2026-08-02

The YouTube VR APK confirms that the target product shape is not a single
Spatial Activity pretending to be a system window. It has a dedicated VR
Activity and a dedicated, real Horizon OS 2D Panel Activity. Stage B therefore
retains the two-host architecture and changes the priority order:

```text
1. Stable real system 2D Panel <-> VR Activity routing
2. Shared application-level media/session state
3. Same ExoPlayer continuity when the platform permits it
4. Same MediaCodec continuity as an observation, not a prerequisite
```

The current `Home + PendingIntent` route remains the platform integration under
test. The next work must first explain and reduce the second-round destination
Activity/task materialization failure. It must not add more decoder recovery
logic while the destination Activity is absent.

## Goal

Implement and validate a repeatable route between the real Horizon OS system
`PanelActivity` and `ImmersiveActivity`, using a shared application-level
`PlaybackSession` for media item, position, play state, and player ownership.
The route must follow the Quest/Meta Spatial SDK constraint that a Spatial
`VrActivity` and its panel session must fully shut down before the system-panel
`TextureView` can be expected to render reliably.

The first reliable implementation may release and recreate the decoder or even
the player after a host transition, provided that the media item and position
are restored explicitly. Same-player continuity is the preferred optimization,
not the routing correctness contract.

## Phase B Device Finding

Quest device evidence disproved the original overlap design. Keeping the old
`ImmersiveActivity` alive until the panel's first frame left multiple Activities
and ViewRoots in the process, prevented a stable Spatial-to-TextureView render,
and later caused Meta's `VrActivityProcessGuard` failure:
`Multiple VR activities in same process`.

A direct `setVideoSurface(newTextureSurface)` replacement reduced route latency
but did not produce the panel's first frame. A cold `PanelActivity`, after the
Spatial host is gone, renders correctly. Therefore the earlier requirement that
the destination Surface attach before source Activity teardown is not achievable
for Meta Spatial `VrActivity` to system-panel routing on this device.

Phase B prioritizes platform-correct Activity/task ownership, repeatable
routing, and explicit media/session restoration. A short no-video interval is
acceptable and measured. Audio may pause during the host transition. Decoder
initialization may increase when the platform switches between Spatial and
TextureView Surface types. The app must not use decoder recovery to mask a
missing destination Activity.

## Requirements

### Three-Phase Non-Overlap Route

1. Immersive to 2D:
   * Mark the route and player protected before lifecycle teardown.
   * Request the official Home plus `extra_launch_in_home_pending_intent` route.
   * Request `ImmersiveActivity.finishAndRemoveTask()` immediately after the
     official route is accepted.
   * Always detach the SDK-owned Spatial Surface and call
     `super.onSpatialShutdown()`.
   * Do not allow the panel `TextureView` to exist/attach until
     `ImmersiveActivity.onDestroy()` has completed and the coordinator has
     received the explicit source-destroyed callback.
2. Panel target attach:
   * Create the `TextureView` only after source destruction is confirmed.
   * Use protected direct Spatial-to-TextureView replacement without app-level Spatial detach; normal paths remain identity-safe clear-then-set.
   * Do not use the experimental direct Spatial-to-TextureView replacement.
   * Complete the route only after destination Surface attach and first frame.
3. Panel to immersive:
   * Mark the player protected, then finish/remove `PanelActivity` first.
   * Launch one new `ImmersiveActivity` only after `PanelActivity.onDestroy()`.
   * Confirm no existing registered `ImmersiveActivity` remains before launch.
   * Use a main-thread post after source destruction; bounded short retries are
     allowed only when a confirmed old VR Activity has not yet cleared.
   * Never retain, reuse, bring forward, or `CLEAR_TOP` an old VrActivity.

### State And Ownership

* Exactly one VR Activity may exist in the process. A duplicate registration is
  logged as an error, the duplicate is removed, and the route fails explicitly.
* A transition has explicit ordered phases: waiting for source destruction,
  ready to launch destination when applicable, waiting for destination Surface,
  waiting for first frame, completed, or failed.
* A 15-second timeout is terminal for the current transition. It records an
  explicit failure and clears pending state; it must not leave a stale protected
  route indefinitely.
* Duplicate route clicks and stale/mismatched lifecycle or Surface callbacks are
  ignored.
* The shared `PlaybackSession` owns the media item, last known position, play
  intent, and current host. The session is process-local only while the process
  survives.
* A route records the media snapshot before source teardown. The destination
  restores the same media identity and position before becoming interactive.
* Same `PlayerManager` and `ExoPlayer` identity is preferred when the existing
  player remains valid, but explicit player release/recreation is allowed in the
  recovery baseline.
* Activity teardown must not release session state. It may release host-owned
  player/Surface resources when the selected route policy requires it.
* Playback position must not reset. It should resume from the recorded timeline
  within a documented tolerance after a player recreation.
* Decoder initialization and release counts are diagnostic. They are not a
  Phase B blocker once media identity, position restoration, and first frame are
  correct.

### UI And Metrics

* `ImmersiveActivity` remains the default launcher and exposes `Enter 2D Panel`
  through the Spatial native control panel.
* `PanelActivity` remains the Horizon OS 2D destination.
* The panel mask remains opaque until first frame.
* The panel return button is disabled while the incoming route is waiting for
  source shutdown, Surface attach, or first frame, and while an outgoing route
  is active. An explicit failed route enables a retry action.
* Metrics/logs expose manager/player identity, prepare count, decoder count,
  Surface handoffs, position, route state, source-destroyed time, destination
  Surface time, first-frame time, completion/failure, and failure reason.
* Handoff duration remains route request to destination first frame. The
  intentional no-video interval is represented by source-destroyed and
  first-frame timestamps.

### Scope Protection

* Do not modify the unrelated provider guide or `temp/` directory.
* Do not add unverified manifest/task changes in the first routing experiment. A
  YouTube-like manifest comparison (`singleTask`, `excludeFromRecents`, panel
  resizeability) is a separate controlled experiment with its own logs.
* Do not add a service or process-death playback restoration path.

## Acceptance Criteria

* [ ] Normal Quest launch enters exactly one `ImmersiveActivity`.
* [ ] `Enter 2D Panel` uses Home plus PendingIntent and opens a stable Horizon OS
  `PanelActivity` after the old Spatial Activity/session is destroyed.
* [ ] Panel video reaches a first frame after the non-overlap attach gate opens.
* [ ] `Return to Immersive Mode` removes the panel before launching exactly one
  fresh `ImmersiveActivity`.
* [ ] No `Multiple VR activities in same process` error occurs across at least
  five physical round trips.
* [ ] Every route either creates the expected destination Activity and reaches
  first frame or fails with a classified terminal reason; no silent 15-second
  wait is accepted.
* [ ] At least five physical round trips complete with one real system
  `PanelActivity` and one VR Activity at each stable endpoint.
* [ ] The same media identity is restored after every route.
* [ ] Playback position resumes within the declared restoration tolerance after
  every route.
* [ ] Same `PlayerManager` and `ExoPlayer` identities are recorded when
  retained; player recreation is explicitly recorded when used.
* [ ] `prepareCalls == 1` is a preferred continuity result, not a hard blocker
  for the routing baseline.

## Continuity Levels

The implementation is evaluated in levels:

1. **Routing baseline:** both destination Activities materialize reliably, no
   duplicate VR Activity exists, and every route reaches a terminal state.
2. **Media restoration:** the same media item and playback position are
   restored after a route, even if the player or decoder is recreated.
3. **Player continuity:** the same `PlayerManager` and `ExoPlayer` survive a
   route when the platform allows it.
4. **Decoder continuity:** the same decoder survives the Surface/session
   transition. This is a best-effort device result and is not required for the
   routing baseline.

* [ ] The selected continuity level is logged per route; no stronger level is
  claimed from weaker evidence.
* [ ] Decoder initialization and release are logged on every host transition.
* [ ] A decoder error is classified separately from Activity/task launch
  failure.
* [ ] Player recreation, when used, restores media identity and position before
  the destination becomes interactive.
* [ ] Source Activity destruction precedes destination Surface attachment in
  both directions.
* [ ] Delayed old-Surface destruction cannot clear a new output.
* [ ] The panel route action has no click race and reflects pending, completed,
  and failed route states.
* [ ] A route either reaches first frame and completes or times out/fails
  explicitly; no pending transition remains indefinitely.
* [x] Pure unit tests cover both directional orderings, first-frame ordering,
  idempotence, and explicit failure terminal state.
* [x] `./gradlew.bat :app-meta:testDebugUnitTest --no-build-cache` succeeds.
* [x] `./gradlew.bat :app-meta:assembleDebug --no-build-cache` succeeds and the
  APK exists at `app-meta/build/outputs/apk/debug/app-meta-debug.apk`.
* [x] Before device launch, force-stop `com.viriviri.app` to clear orphaned
  Activities/process state from the previous overlap model.
* [ ] Physical Quest evidence records at least five cycles, Activity/task count,
  media identity, position restoration, manager/player identity, prepare count,
  decoder count and release count, audio behavior, first-frame timings, and
  visible no-video interval.

## Technical Design

`HybridTransitionController` owns one process-local pending transition and a
weak registration of the currently live `ImmersiveActivity`. A playback snapshot
is captured before source teardown and restored by the destination host. The pure
`HybridRouteTransition` rejects out-of-order events. The controller protects the
singleton before requesting source finish, receives `onActivityDestroyed()` only
after each Activity's `super.onDestroy()`, and then opens the target attach gate
or starts the fresh immersive destination.

For immersive to panel, Horizon OS may create `PanelActivity` before the old VR
host is gone. This is allowed, but Compose withholds `SurfaceHandoffTextureView`
until the source-destroyed signal. For panel to immersive, no destination intent
is sent until panel destruction; the application context performs a new-task
launch after a main-thread post and a bounded check that no VR host remains.

`PlayerManager` retains identity-safe Surface ownership. The first routing
baseline uses the safest host-bound cleanup policy: detach/release the old host
output explicitly, then prepare the destination output after Activity creation.
A later continuity experiment may use direct Spatial-to-TextureView replacement
only after the routing baseline is stable. SDK-owned Spatial Surfaces are never
released by the app; app-created TextureView Surfaces are detached by identity
and released exactly once.

## Decision (ADR-lite)

**Context:** The first-frame-confirmed overlap model conflicts with Meta Spatial
SDK's one-VrActivity process guard and does not render reliably on Quest when a
Spatial host remains alive. Direct output replacement does not fix first frame.

**Decision:** Use lifecycle-confirmed non-overlap with a shared playback/session
snapshot. Fully destroy the source host, materialize the real destination host,
then restore the media output. First prove routing and media restoration. Treat
same-player and same-decoder retention as progressively stronger platform
observations rather than reasons to overlap Activities.

**Consequences:** Route correctness and repeatability are separated from
decoder behavior. The baseline may show a longer no-video interval and may
reprepare, but it has a deterministic recovery path. Later experiments can
remove reprepare only after the Activity/task path is proven stable.

## Out Of Scope

* Curved-screen/OpenXR rendering, PICO routing, network playback, DRM, and media
  selection.
* Playback restoration after Android process death.
* Guaranteed decoder preservation across incompatible platform Surface types.
* Eliminating the compositor gap by overlapping Meta VR and system Activities.

## Definition Of Done

* Code and pure tests implement the ordered state machine and explicit playback
  snapshot/restore contract.
* PRD, research, device notes, and durable Media3 spec record the Phase B device
  constraint.
* Unit tests and no-cache debug assembly pass.
* The APK is installed after a force-stop when a device is available.
* Remaining physical validation is listed without claiming unobserved results.
* No commit is created for this task, per user instruction.
## Current Experimental Findings

The three visible handoff modes were tested. `Direct + recovery` completed four round trips in one run and failed on the fifth; `Clear + recovery` completed the first and failed on the second in two runs; `Reprepare baseline` completed the first and hung on the second in two runs. Common later failures occur before `PanelActivity.onCreate()` or TextureView attachment, while Spatial shutdown produces Qualcomm `NO_INIT/BAD_VALUE` and ExoPlayer decoder failure. This means the modes cannot yet be compared as pure Surface strategies on later rounds.

The next acceptance target is stable routing and explicit resource cleanup, not decoder continuity. Same-player/media/position continuity is a second-level target; decoder continuity remains device-dependent. See `research/lifecycle-cleanup-and-reference-samples.md`.
