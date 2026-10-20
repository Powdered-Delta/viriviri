# Injecting controller input inside the immersive app (feasibility study)

Status: researched, not implemented. Date 2026-10-20. SDK: Meta Spatial SDK 0.13.2
(`gradle/libs.versions.toml`), app `com.m0e_n00b.viriviri`.

Question: can we plant a stub/hook **inside** the immersive app that synthesizes controller
events (ray aim, trigger/A, thumbstick, panel clicks) so the VR UI can be tested
automatically, without a human holding a controller?

Short answer: **yes, in-process, with no root and no external input daemon.** The SDK
already ships the exact seam its own `PanelClickSystem` uses: build a `HitInfo`, hand it to
`PanelSceneObject.onInput(...)`, and the SDK's `PanelInputListener` turns it into real
Android `MotionEvent`s on the panel's virtual display. Three independent injection levels
cover different fidelity needs; the un-injectable remainder is native raycast/ISDK grabbing.

## 1. Why host-side automation cannot reach this UI

| Approach | Why it does not work here |
| --- | --- |
| `adb shell input tap/swipe/keyevent` | Addresses the Android display. The immersive scene is rendered by the VR compositor; there is no window at those coordinates to hit. |
| UI Automator / Espresso on the activity | Sees the activity's window only. Spatial panels live on a `VirtualDisplay` + `Presentation`, and 3D entities have no Android view at all. |
| Meta Spatial Simulator | Explicitly **2D Android apps only** ("It is for 2D Android apps only" — `spatial-sim-overview`). `SpatialSimulatorKt.isSpatialSimPlatform()` only checks `Build.DEVICE` for `spatialsim`, so it is a different runtime, not a hook we can use. |
| Quest system-level controller injection | No public OpenXR/OS API to feed synthetic controller poses/buttons into another app. |

So the injection point has to be *inside the app process*, between the SDK's native input
and the app's consumers.

## 2. What the SDK exposes (verified against the 0.13.2 AARs)

Method: `javap` on `classes.jar` extracted from the AARs in
`%USERPROFILE%\.gradle\caches\modules-2\files-2.1\com.meta.spatial\...`, plus a CFR
decompile of `meta-spatial-sdk-0.13.2.aar` (`com.meta.spatial.runtime.PanelInputListener`,
`Scene`, `PanelSceneObject`) and `meta-spatial-sdk-toolkit-0.13.2.aar` (`PanelClickSystem`).

### 2.1 Panel pointer path (the strong seam)

```
controller ray (native)
  -> Scene.onInput(HitInfo, source, changedButtons, buttonState)      [public]
  -> SceneObject.onInput(...)                                        [public, dispatches to listeners]
  -> PanelInputListener.onInput(...)                                 [public]
  -> PanelDisplayBase.dispatchEvent(MotionEvent, hover)              [public]
  -> MotionEvent into the panel's VirtualDisplay (InputManager.injectInputEvent via reflection)
  -> Android View / Compose hit test
```

Verified details:

- `PanelSceneObject` registers its listener in `attachDisplay`:
  `addInputListener(PanelInputListener(ctxt, clickButtons, scene))`. `SceneObject` keeps the
  listeners in `inputListeners` and `onInput` fans out to them.
- `PanelInputListener.onInput(receiver, hitInfo, sourceOfInput, changedIn, buttonState, downTime)`:
  - masks both ints with the panel's `clickButtons`;
  - maps `hitInfo.textureCoordinate` to pixels: `x = u * display.widthInPx`,
    `y = v * display.heightInPx`;
  - claims hover for `sourceOfInput`; on a new hover owner it can synthesize
    `ACTION_HOVER_ENTER` (`synthesizeHoverEnterOnPress`);
  - emits `ACTION_DOWN` when `buttonState != 0` **and** `changed != 0`, `ACTION_UP` when
    `changed != 0 && buttonState == 0`, otherwise `ACTION_MOVE`/`ACTION_HOVER_MOVE`;
  - plays the UI click sound and haptics on press, then `dispatchEvent`.
- `Scene.onInput(hitInfo, source, changed, state)` resolves the target through
  `sceneObjects_[hitInfo.sceneObjectHandle]`, so a `HitInfo` produced by
  `Scene.lineSegmentIntersect(...)` routes with correct occlusion.
- `HitInfo` has a **public constructor**:
  `HitInfo(entity, sceneObjectHandle, nodeId, meshElementId, distance, point, normal, textureCoordinate)`.
- `PanelCreationSystem.forEachPanel { entity, CompletableFuture<PanelSceneObject> -> }` is
  public and is how the SDK's own click helper finds panels.
- `PanelDisplayBase.dispatchEvent(MotionEvent, Boolean)` is public — an even lower fallback.
- `SceneObject.addInputListener(InputListener)` / `removeInputListener` are public, so a test
  can also *observe* input without changing routing.

### 2.2 The SDK's own test hook (proof + a free smoke test)

`com.meta.spatial.toolkit.PanelClickSystem` is a shipped reference implementation. Its
decompiled lambdas build a synthetic hit and call the panel directly:

```kotlin
val hit = HitInfo(
    entity, /* sceneObjectHandle = */ 0L, 0, 0, 0f,
    PANEL_CLICK_POINT,            // PanelClickSystemKt.point
    Vector3(1f, 0f, 0f),
    Vector2(0.5f, 0.5f),          // dead centre
)
panelSceneObject.onInput(hit, Entity(2000L), /* changed = */ 1, /* state = */ ButtonBits.ButtonA, 1000L)
```

`AppSystemActivity.registerTestingIntentReceivers()` additionally wires three broadcasts:

- `com.meta.spatial.apps.experiments.SET_UP_PANEL_TESTING` → creates a `PanelClickState(PRESS_DOWN)`
  entity → `PanelClickSystem` clicks the **centre of every panel**;
- `com.meta.spatial.apps.experiments.SET_VIEW_ORIGIN` / `UPDATE_VIEW_ORIGIN` → move the scene origin.

It is not called by the SDK itself — the app must call it (one line in `onCreate`), and the
click is centre-only, all-panels. Useful as a 5-minute smoke test, not as a test driver.

### 2.3 Controller state path

- `com.meta.spatial.toolkit.Controller` has public setters: `buttonState`, `changedButtons`,
  `isActive`, `type`, `directTouchButtonState`, `laserEnabled`.
- Edge semantics (decompiled): `isDown(b) = state & b`, `isPressed(b) = state & changed & b`,
  `isReleased(b) = ~state & changed & b`. `changedButtons` is a one-frame edge mask.
- `ButtonBits`: `A=1, B=2, X=4, Y=8, TriggerL=16, TriggerR=32, SqueezeL=64, SqueezeR=128`,
  thumbsticks 256+, `Menu=268435456`, `System=536870912`.
- `TickControllerSystem.execute()` calls `SpatialInterface.tickControllers()` — the native
  pass that fills `Controller`/poses each frame. A system registered *after* it can overwrite
  the component for that frame.
- System ordering is available via `SystemManager.registerEarlySystem/registerLateSystem` and
  `SystemDependencies(mustRunBefore/mustRunAfter = setOf(SystemDependencyConfig(KClass, ...)))`.

### 2.4 Panel button masks (a real gotcha)

`PanelInputListener` drops any press whose bit is not in that panel's `clickButtons`.
`PanelConfigOptions.DEFAULT_CLICK_BUTTONS = A | X | TriggerL | TriggerR` (decompiled).
Panels built with `PanelInputOptions(0)` accept nothing. In this app:

- `selectorPanelSettings()` explicitly uses `A | TriggerL | TriggerR`;
- `grabBarPanelSettings()`, `stageOverlayPanelSettings()` use `PanelInputOptions(0)`;
- everything else uses the default mask.

A synthetic click should therefore send `A | TriggerL | TriggerR` (masking makes the extra
bits harmless on every interactive panel), and non-interactive panels are expected to ignore it.

## 3. Three injection levels and their fidelity

| Level | Mechanism | Drives | Fidelity | Cost |
| --- | --- | --- | --- | --- |
| **L0 — panel pointer** | `PanelSceneObject.onInput(hit, source, changed, state, t)` or `Scene.onInput(lineSegmentIntersect(...))` | Compose/XML panels: buttons, lists, keyboard, hover | Real `MotionEvent` path; L0b also gets occlusion | Low |
| **L1 — controller state** | virtual controller entity + `Controller` fields, or overwrite the real controller's component after `tickControllers()` | app systems: stage trigger, A summon, thumbstick scale/curvature, focus/hover math in `PointerInfoSystem` | App-level logic exact; native raycast does not see it | Medium |
| **L2 — native** | OpenXR/hand input, `IsdkSystem` grabbing | raycast occlusion, ISDK grab/drag, system hover | Not injectable from app code | — |

Practical consequence: L1+L0 covers most of this app's flows; ISDK grabbing
(`IsdkGrabbable.grabState`, fed by the native ISDK system) has no injection seam, so grab
coverage must either stub the component/transform (`grabState` does have a public setter, but
ISDK overwrites it per frame) or be excluded from automated coverage.

## 4. Constraints and failure modes

1. **Thread affinity.** Panel input, entity mutation and `Scene` calls run on the scene/UI
   thread. Drive from a `SystemBase.execute()` or `canvasHandler.post`, never from a test's
   background thread or a socket thread.
2. **Panels are created asynchronously.** `forEachPanel` gives a
   `CompletableFuture<PanelSceneObject>`; `getNow(null)` is null for a few frames. Retry, do
   not cache a null.
3. **Hover ownership is single-valued per panel.** `PanelInputListener` has one
   `hoveringEntity_` and one `lockedInputEntity_`. A real controller resting on the panel can
   steal/exit hover between synthetic events. For CI, keep controllers idle/away, or drive
   with the same source entity the real controller uses. There is no "quiet input" switch.
4. **Edges are one-frame.** Send `changed != 0` with the press/release, then send
   `changed = 0, state = held` (or release) on the next event. Repeating a press with
   `changed` set is a second DOWN, not a hold.
5. **`input = 0` and invisible panels.** `Visible(false)` panels are out of the native
   raycast; L0 can still deliver events to them, so tests must gate on visibility if they
   mean to assert "cannot be clicked".
6. **Ordering.** A controller-override system must run after `TickControllerSystem` and
   before the app's consumers. Register it first in `onSceneReady`, and/or declare
   `SystemDependencies(mustRunAfter = setOf(SystemDependencyConfig(TickControllerSystem::class, true)))`.
   `registerLateSystem` is too late.
7. **Release isolation.** Keep the driver in `app/src/debug/` (or behind `BuildConfig.DEBUG`)
   and keep it out of `registerPanels()`/`onSceneReady` in release. No reflection is needed
   for L0/L1, so R8 is not a factor — but an always-on debug bridge in release would be an
   attack surface.
8. **Instrumentation runtime on Quest.** The activity must actually be running and focused;
   the headset must not sleep, and the proximity sensor can pause the app. Keep-awake and
   `device configure-testing` are device mutations and need explicit approval.
9. **Assertions.** Prefer app state (`ViriViriApplication.appState.state.value`,
   `playerSession.player`) over pixels; XML panels expose `PanelSceneObject.getRootView()` for
   `findViewById`/text checks. Compose semantics through a panel `ComposeView` (which lives on
   a `Presentation`/`VirtualDisplay`) is unverified — treat Compose-test assertions as an
   experiment. Pixel checks need `metavr capture screenshot` (gated).

## 5. Recommended shape

One debug-only seam, two transports:

```
androidTest (in-process)  ─┐
                           ├─> TestInputDriver (debug) ─> PanelSceneObject.onInput / Controller / Scene
host script (localhost TCP) ┘                     └─> AppState / panel view tree / log markers
```

`TestInputDriver` API (single source of truth, used by both transports):

```kotlin
internal class TestInputDriver(
    private val scene: Scene,
    private val systemManager: SystemManager,
) {
    fun panel(entity: Entity): PanelSceneObject?
    fun click(panel: Entity, u: Float, v: Float, mask: Int = CLICK_MASK)
    fun press(panel: Entity, u: Float, v: Float, mask: Int)
    fun release(panel: Entity, u: Float, v: Float, mask: Int)
    fun hover(panel: Entity, u: Float, v: Float)
    fun aimController(hand: Hand, pose: Pose)      // virtual controller entity
    fun pressButton(hand: Hand, bits: Int)
    fun releaseButton(hand: Hand, bits: Int)
    fun snapshot(): Map<String, Any?>              // destination, isPlaying, scale, curvature…
}
```

Sketch of the panel call (validated against the decompiled listener):

```kotlin
val hit = HitInfo(panelEntity, 0L, 0, 0, 0f, Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f), Vector2(u, v))
val t = SystemClock.uptimeMillis()
panel.onInput(hit, sourceEntity, mask, mask, t)              // ACTION_DOWN
panel.onInput(hit, sourceEntity, mask, 0, t + 32)            // ACTION_UP
```

For geometric fidelity (occlusion, "ray actually hits the panel"), replace the hand-built
`HitInfo` with `scene.lineSegmentIntersect(origin, target)` and call
`scene.onInput(hit, sourceEntity, mask, mask)`; it routes through
`sceneObjects_[handle]` exactly like native input.

Virtual controller (L1):

```kotlin
val virtualRight = Entity.create(
    listOf(
        Controller(buttonState = 0, changedButtons = 0, isActive = true,
                   type = ControllerType.CONTROLLER),
        AvatarAttachment("right_controller"),
        Transform(Pose(Vector3(0f, 1.3f, -0.4f))),
    )
)
```

A driver `SystemBase` writes `buttonState`/`changedButtons` and the `Transform` each frame,
clearing `changedButtons` after one frame.

## 6. Coverage plan for this app

| Target | Path | Method |
| --- | --- | --- |
| Transport: play/pause, seek, volume, speed, quality, prev/next | `controls_id` (XML) | L0 click at `(u,v)` |
| Browse list / detail rail / search results | `video_selector_panel` (Activity→Compose), `center_content_panel` (Compose) | L0 click + drag/scroll (`buttonState` with thumb bits) |
| Search keyboard | `input_method_panel` (Compose keyboard) | L0 clicks on keys, assert `appState.searchInput` |
| Workbench open/close via stage | app systems (`AnalogMediaStageTuningSystem`) | L1 trigger while aimed at the stage |
| Stage scale / curvature | `AnalogMediaStageTuningSystem` thumbstick | L1 `ButtonThumbLU/RU/LD/RD/LL/RR` |
| A-button summon (debug only) | `AnalogWorkbenchSummonSystem` | L1 `ButtonA` |
| Grab bar / outer-dismiss | ISDK / app | L1 for hover math; grab only via stub |
| Negative: backdrop/danmaku/grab bar ignore clicks | `input = 0` | L0 click must not change state |

## 7. PoC plan and acceptance criteria

1. `TestInputDriver` + one `androidTest`: click the play/pause button on `controls_id`,
   assert `isPlaying` toggles, and that the click log line appears. **Acceptance:** repeatable
   10×, no manual controller.
2. L1 virtual controller: aim at the stage, press/release the trigger, assert the workbench
   visibility transition.
3. Thumbstick: hold `ButtonThumbRU`, assert `appState.playbackStageScale` increases by the
   expected step, then returns on the opposite stick.
4. Hover/scroll: `hover` + thumb scroll on the list, assert the first visible item index
   changes.
5. Optional host transport: debug-only TCP JSON (`{"op":"click","panel":"controls_id",
   "u":0.5,"v":0.5}`) so a host script can explore without rebuilding the test APK.
6. CI: `connectedDebugAndroidTest` on a lab headset with keep-awake; artifact = JUnit XML +
   logcat + one screenshot per failed step.

Effort estimate: driver + tests 1 (≈1–2 days), tests 2–4 (≈1–2 days), host transport + CI
stabilization (≈2–4 days), assuming a headset that stays awake and controllers left idle.

## 8. Open questions / risks

- **SDK upgrade surface.** `PanelSceneObject.onInput` and `PanelCreationSystem.forEachPanel`
  are public but undocumented. Pin 0.13.2 and re-run the smoke test on upgrade; consider a
  thin adapter so a rename is a one-file fix.
- **Real-controller interference.** Single hover owner per panel. Verify whether an idle
  controller's ray produces periodic `onInput` calls that clear synthetic hover; if yes, park
  controllers out of view or point the ray at empty space.
- **Compose assertions.** Whether `androidx.compose.ui.test` can attach to a panel
  `ComposeView` on a `VirtualDisplay` is untested; fall back to state + view-tree text.
- **Instrumentation vs VR focus.** `ActivityScenario` + VR activity may lose focus when the
  test runner starts; validate early, and prefer driving a running app through a debug bridge
  if the test runner disturbs the scene.
- **Grab coverage.** ISDK grab is native; only a component/transform stub is available.

## 9. Confirming the click actually landed where intended

Short answer: **do not use ADB screenshots as the pass/fail oracle.** Use a target-identity
click API plus in-process hit/press/state assertions; keep screenshots (panel-local first,
whole-device second) as failure artifacts only.

Why ADB screenshots are a poor oracle for this app:

- `adb exec-out screencap` grabs the Android display. The immersive scene is composed by the
  VR compositor from texture/layer submissions, so the grab typically misses layers or
  returns the 2D mirror — not what the eye buffer shows. Meta's capture docs describe
  "full-resolution, undistorted, single-eye, full-layer-support" screenshots as a separate
  system capture path (Sharing menu / MQDH / `metavr capture screenshot`), not `screencap`.
- It is asynchronous and coarse: you cannot tell from a 1024x1024 still *which* view consumed
  the event, only that something changed somewhere.
- It adds a device/transport dependency to every assertion, which is exactly the flakiness
  this harness is meant to remove.

### 9.1 Preferred: click by control identity, not by (u,v)

The root cause of "did I hit the right point" is that raw texture coordinates are opaque.
Make the driver resolve the target first:

```kotlin
// XML panel
fun clickView(panel: Entity, viewId: Int) {
    val root = panelObject(panel).getRootView() ?: error("panel not ready")
    val target = root.findViewById<View>(viewId) ?: error("no view $viewId")
    val cx = (target.left + target.right) / 2f + target.translationX
    val cy = (target.top + target.bottom) / 2f + target.translationY
    clickUv(panel, cx / root.width, cy / root.height)   // same px space PanelInputListener uses
}
```

For Compose panels, resolve a `testTag` (or text) through the semantics tree and use
`boundsInRoot.center`; add `Modifier.testTag(...)` behind `BuildConfig.DEBUG` where missing.
Once the driver clicks by identity, "the trigger point" is true by construction, and the test
reads as `clickView(controlsPanel, R.id.play_pause_button)`.

### 9.2 Assert the hit, then the press, then the effect

1. **Hit-test oracle (pre-flight).** Walk the panel root for visible/clickable views whose
   bounds contain `(x, y)` and assert the topmost one is the expected view. This catches
   layout/DPR/panel-size drift before injecting. For Compose, walk the semantics tree for
   nodes with `SemanticsActions.OnClick` and `boundsInRoot` containing the point.
2. **Press state (post-DOWN).** For XML panels, `target.isPressed == true` while DOWN is
   outstanding proves the event reached that exact view. Inject DOWN, assert, then UP —
   do not send both in the same frame or the state is already cleared.
3. **Effect (post-UP).** Assert the app's own state (`isPlaying`, `appState.destination`,
   `playbackStageScale`) and/or the existing trace logs (`ViriViriWorkbench`,
   `ViriViriStageTap`). This is the real "did the click do its job" signal.

A failure message should carry all three: `panel=controls_id target=R.id.play_pause_button
uv=(0.42,0.61) px=(303,283) hit=R.id.play_pause_button pressed=true before=Playing after=Paused`.

### 9.3 Panel-local bitmap instead of a device screenshot

`PanelSceneObject.getRootView()` is a normal Android view hierarchy, so the panel can be
rendered in-process without the compositor:

```kotlin
val root = panelObject.getRootView() ?: return
val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
root.draw(Canvas(bmp))        // UI thread, after layout
// save to context.getExternalFilesDir("test-shots")/controls_after.png
```

This gives a per-panel, deterministic image at the panel's native resolution (e.g.
1280x720 overlays, 361x464 left rail), with no adb, no compositor layers and no system UI.
Crop around `(x, y)` for a tight diff of the pressed/selected state. `SceneTexture`
additionally exposes `readAverageColor(out, mip, jitterX, jitterY)`, but it averages the
**whole** texture, so it only answers "did the panel change at all" — not "what is at this
point".

### 9.4 Whole-device capture: artifact, not gate

Use `metavr capture screenshot` (or MQDH/Sharing) when a human needs to see the scene, e.g.
attached to a failed CI run. Treat it as evidence with no assertion value, and keep it out of
the pass/fail path. If `adb exec-out screencap -p` is used at all, verify once per Horizon OS
version what it returns for this app and never depend on it.

### 9.5 Failure bundle

Per failed step, attach: the click trace line (9.2), the panel bitmap before/after (9.3),
`logcat -s ViriViriWorkbench ViriViriStageTap ViriViriSpatial`, and optionally one
`metavr capture screenshot`. That combination localizes the fault to "wrong target", "event
not delivered", "handler ran but state did not change", or "state changed but render did not".

## Appendix: evidence commands

```powershell
# AAR locations (0.13.2)
#  ...\.gradle\caches\modules-2\files-2.1\com.meta.spatial\meta-spatial-sdk\0.13.2\...\*.aar
#  ...\meta-spatial-sdk-toolkit\0.13.2\...\*.aar

javap -p -classpath classes.jar com.meta.spatial.runtime.SceneObject
javap -p -classpath classes.jar com.meta.spatial.runtime.PanelInputListener
javap -p -classpath classes.jar com.meta.spatial.toolkit.PanelCreationSystem
java -jar cfr.jar classes.jar --outputdir out   # decompile, then read PanelInputListener.java
```
