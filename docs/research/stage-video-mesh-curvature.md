# Stage video layer: mesh ownership and curvature

Status: implemented in `SpatialVideoSampleActivity` (unverified on device).

## Symptom (user report)

> ?????????????????????? grab bar ??????z+??????????? ???????????????

All three symptoms have one cause: the video layer stopped being drawn by our own mesh.

## SDK facts this rests on (verified against the 0.13.2 jars)

1. `PanelShapeConfig.generateSceneMeshCreator()` returns the user-supplied `sceneMeshCreator` when it
   is non-null and otherwise falls back to the SDK mesh generated from the panel shape.
2. `PanelSceneObject.reshape(config)` rebuilds the whole `PanelShape` from the *new* config, so it
   re-runs `generateSceneMeshCreator()` against that config. A reshape therefore replaces the panel
   mesh, and a reshape whose config carries no `sceneMeshCreator` silently swaps our hand-built mesh
   for the SDK generated one.
3. `TriangleMesh` constructor (com.meta.spatial.runtime.TriangleMesh):
   `TriangleMesh(vertexCount: Int, indexCount: Int, materialIndexRanges: Int[], materials: Array<SceneMaterial>)`.
   `materialIndexRanges` is one `(indexStart, indexCount)` pair per entry of `materials`. The original
   video mesh passes `intArrayOf(6, 6, 12, 6, 0, 6)` for `[reflect, shadow, holePunch]`, i.e. reflect
   covers `[6, 12)`, shadow `[12, 18)` and holePunch `[0, 6)` ? which falls out of the general
   formula `[front, front, front * 2, 6, 0, front]` at `front = 6`.

## Root cause

`createVideoPanel()` does supply `sceneMeshCreator` (8 vertices / 18 indices: video quad, flat drop
shadow, hole punch), but `reshapeSpatialVideoPanel()` built a fresh `MediaPanelSettings(...).toPanelConfigOptions()`
with no `sceneMeshCreator`. Every scale or curvature change routes through

    rebuildStageGeometry() -> updateSpatialVideoContentQuad() -> reshapeSpatialVideoPanel()

so the very first thumbstick push already replaced the hand-built mesh with the SDK generated one and
dropped the video / shadow / hole-punch materials. It was worse for curvature because
`videoShapeOptions()` returned `CylinderShapeOptions(radius, width, height)` while the video layer was
curved, so the SDK generated a *cylinder* mesh whose surface sits a whole radius away from the panel
origin. Curvature steps at 4 m/s, so the first frame of the first push took the radius from
`MAX_RADIUS_METERS` (20 m) to ~19.93 m: the stage relocated ~20 m along the panel local Z, further
steps moved it by millimetres (looks frozen) and the scale change was no longer perceivable.

## Fix

- The video layer is always a flat `QuadShapeOptions`; its curvature is never handed to the SDK shape.
- `createVideoSceneMesh(texture, width, height)` is now the single owner of the video mesh and bakes the
  video layer curvature into the vertices via the existing `CurvedStageMeshBuilder` /
  `PlaybackStageCurvature.toStageGeometry()`. Flat stays one quad, so its mesh is byte-for-byte the
  original one (same 8 vertices, 18 indices, same `(6, 6, 12, 6, 0, 6)` ranges); curved becomes a
  32 x 18 double-sided grid plus the same flat shadow footprint.
- The creator is passed at creation *and* on every reshape, so a reshape can no longer drop it.
- `updateSpatialVideoContentQuad` only takes the in-place `updateGeometry` path (`PLAN_1`) while the
  surface is still the flat four-vertex quad; a subdivided surface always goes through the reshape.

## Open items

- The danmaku and backdrop overlays still curve through `CylinderShapeOptions` (`overlayShapeOptions`).
  If the SDK cylinder convention really does place its surface one radius from the panel origin, those
  two layers will relocate the same way once the curvature target is cycled to danmaku/backdrop.
  They can reuse this mesh-baking approach (they would need the SDK panel material).
- Verify on device: first left/right push bends the screen in place, up/down still scales, and
  switching the curvature target does not move the stage.

## Stage 2 findings (2026-09-11, unverified on device)

Two independent defects were still present after the mesh-ownership fix above.

1. **The overlay reshape addressed the wrong entity.** `reshapeSpatialVideoPanel` and
   `flushPendingStageOverlayReshape` looked the overlay panels up with
   `Entity(R.id.danmaku_overlay_panel)` / `Entity(R.id.stage_backdrop_panel)` - panel
   *registration* ids, not the entity ids returned by `Entity.createPanelEntity`.
   `SceneObjectSystem.getSceneObject` therefore never resolved those panels, `reshapeStageOverlay`
   always deferred, and the danmaku/backdrop kept the shape their registration produced. That is
   why they never followed the stage scale and why curving them did nothing, while the video layer
   (whose `PanelSceneObject` we hold directly) kept working. The overlays are now reshaped through
   the stored entity handles.

2. **Entity `Scale` on a child overlay detaches it.** Stage 1 drove the overlays with
   `setComponent(Scale(...))` on the danmaku/backdrop entities while their panel shapes stayed at
   the unscaled footprint. On device the danmaku plane came out very small / far from the stage.
   A UI panel physical size comes from its shape, so the stage scale is baked into that shape again
   (`scaledStageWidth()` / `scaledStageHeight()`), both at registration and on every re-assert, and
   entity `Scale` is no longer used for the overlays.

Both are delivered by `applyOverlayStageShape()`, which replaces `applyOverlayStageScale`. A DEBUG
log (`ViriViriStage`) reports "overlay reshape deferred" while the panel scene object is missing,
so a reshape that never lands is now visible in logcat.

### The curvature range is now short enough to read (2026-09-11)

`MAX_RADIUS_METERS` was 20, which made the first seconds of thumbstick input invisible: the edge
offset is `halfWidth^2 / (2R)`, about 2.5 cm at R = 20 m for the 2 m wide stage, so "flat" looked
unchanged. A step only became visible below roughly 8 m.

The flatmost radius is now 6 m, so the first step reads immediately while the stick still reaches
`Flat` by continuing to loosen. `PlaybackStageCurvatureTest` was updated for the new numbers.

`fromRadius()` resolves anything at or beyond the flatmost radius to `Flat`, matching the threshold
`adjustedBy()` already used. It used to clamp such values to `Cylinder(MAX_RADIUS_METERS)` instead,
which is why narrowing the range turned every stale 20 m-era preference into exactly `Cylinder(6.0)` —
see Stage 3e.


## Stage 3 (2026-09-11): the video layer shape is what bends the video

The video layer is drawn by the OS compositor, not by our mesh: MediaPanelRenderOptions.applyTo
unconditionally calls PanelConfigOptions.setLayerConfig (confirmed in the 0.13.2 toolkit bytecode),
so the visible pixels of the panel are the quad/cylinder shape handed to the layer, and
createVideoSceneMesh only feeds the drop shadow and the hole punch.

CORRECTION (2026-09-11, re-verified against git): the claim that `videoShapeOptions` "returned
QuadShapeOptions for every curvature" is false. Commit `53f70ca` (2026-09-10 17:51 — a full day
before the APK the user tested) already returned
`CylinderShapeOptions(curvature.radiusMeters, width, height)` whenever `appliedVideoCurvature` was a
`Cylinder`; the uncommitted work only reshapes that same call into an explicit `curvature`
parameter. The tested package therefore DID hand the compositor a cylinder, and
"r = 1.5 m still looks flat" was observed with this path present. The "stale package" explanation is
unproven and must not be used as an acceptance argument.

Fix: videoShapeOptions(width, height, curvature) now mirrors overlayShapeOptions and hands the layer a
CylinderShapeOptions(radius, width, height) while the video layer is curved. Both call sites (panel
creation and reshapeSpatialVideoPanel) pass appliedVideoCurvature, so a curvature change reaches the
compositor on the next reshape.

CORRECTION (2026-09-11): position compensation is NOT zero. Curving a layer does move it, and the
layer entity has to be offset by one radius to pin the surface back in place. The argument below was
the `XR_KHR_composition_layer_cylinder` reading; the runtime behaves like the OVROverlay convention
(origin = cylinder axis). See "Stage 3c" at the end of this file. The original reasoning was:

* XR_KHR_composition_layer_cylinder defines the pose as the position and orientation of the center
  point of the view of the cylinder, i.e. the centre of the visible section, and only the INTERIOR of
  the surface must be visible. The SDK hands that pose over as the transform of the panel scene object
  (SceneCylinderLayer(scene, swapchain, radius, width / radius, width / height, pivotOffsetWidth,
  pivotOffsetHeight, stereoMode, sceneObject) matches the XR struct with the pose taken from the scene
  object), so the surface centre stays where the flat quad centre was.
* With the axis one radius on the viewer side of the surface, the sheet wraps toward the viewer: for a
  2 m stage at r = 1.5 m the edges sit about 0.32 m closer than the centre and the viewer ends up
  about 0.5 m from the axis, i.e. inside the inscribed sphere the compositor requires (a cylinder
  layer fades out as the camera approaches that sphere). Wrapping around the viewer is the only layout
  that keeps the screen anchored AND satisfies that constraint.

The OVROverlay branch above is the one that holds: the fix is a stage-local minus-radius offset on
the layer entity. See "Stage 3c" below.

Follow-ups deliberately left out of this stage:

* createVideoSceneMesh still bends the hole-punch mask toward +Z (away from the viewer), while the
  curved layer wraps toward -Z. The mask is invisible while the video is opaque, but it has to be
  flipped (normals included) before the punch is relied on in passthrough.
* The compositor only shows up to half a cylinder (arc angle must be smaller than 180 degrees) and
  centralAngle = width / radius, so a wide canvas at the 1.5 m minimum radius can exceed it
  (width above about 4.7 m). Either keep the stage scale under about 2.3x or clamp the effective
  radius to width / pi.

## Stage 3b (2026-09-11): the video reshape could be dropped before the panel existed

`applyPlaybackVideoCurvature` records the requested curvature in `appliedVideoCurvature` and only
then calls `rebuildStageGeometry()`. The app-state collector runs during `onCreate`, while the media
panel scene object is created later, and `reshapeSpatialVideoPanel` begins with
`spatialVideoPanelSceneObject ?: return`. When that ordering happens the reshape is dropped, and the
`if (appliedVideoCurvature == curvature) return` guard then blocks every later retry, so the
persisted curvature never reached the panel. This is the same creation-order hazard k18 fixed for
the overlays; the video panel had no replay.

Fix: `StageOverlayReshapeSystem` now also calls `reassertAppliedVideoStageGeometry()`, which re-runs
the content-quad path once per panel instance (tracked by identity), so the ordering stops mattering.

Diagnostics: `logVideoPanelShape` emits one `ViriViriCurve` line per distinct shape, e.g.
`video panel shape -> cylinder r=1.50 m 2.00x1.13`, quantised to 0.25 m so a held thumbstick does
not spam logcat. Read it as:

* no line while the stick bends the layer -> the curvature never reached the panel (wiring bug on
  our side, keep looking in `SpatialVideoSampleActivity`);
* a `cylinder` line while the image stays flat -> our side did hand the compositor a cylinder and the
  runtime did not bend it, i.e. the compositor / convention branch above (position compensation or
  unsupported layer shape for media panels).

## Stage 3c (2026-09-11): a curved layer moves, so its entity has to move back

Confirmed: curving a layer translates it by one radius. The runtime anchors the composition cylinder
at the scene object's origin and puts the visible surface one radius away from that origin along the
layer's local Z, while a flat quad has its surface ON the origin. Bending therefore has to be paid
for with a matching local-Z offset on the layer entity, or the stage leaves its authored position.

There is no shape-level lever for this: `CylinderShapeOptions` is `(radius, width, height)` only, and
`PanelConfigOptions` exposes `radiusForCylinderOrSphere` / `pivotOffsetWidth` / `pivotOffsetHeight`
but no axial offset. The compensation is a transform change.

Law and its single unknown:

* `PlaybackStageCurvature.surfaceAnchorOffsetMeters()` returns `0` when flat and
  `SURFACE_ANCHOR_OFFSET_SIGN * radius` when curved, so the offset vanishes exactly when the layer is
  flat and no existing placement can regress.
* `SURFACE_ANCHOR_OFFSET_SIGN` (-1) is the one thing that cannot be derived from the code, because it
  is the direction the runtime displaces the surface. It is a single constant; flip it if a device
  run shows a curved layer moving the wrong way (or twice as far) instead of staying put.

Applied so far — the two overlay layers, which already own their own entities and their own local Z
(the backdrop at `+0.01`, the danmaku layer at `-0.01`):

* `stageBackdropBaseLocalZ` / `stageDanmakuBaseLocalZ` are the authored separations, and
  `applyStageLayerCurvatureOffset()` recomputes `base + offset` on every reshape. It recomputes from
  the base rather than adding to the current Z so repeated reshapes cannot accumulate.
* `reshapeStageOverlay()` now takes that base and returns whether the reshape landed, which also let
  `replayPendingStageOverlayReshape()` drop its hand-rolled landing loop.

The VIDEO layer is done the same way. The media panel used to be bound to
`R.id.spatialized_video_panel`, but that entity is the stage ROOT: it parents `video_selector_panel`,
`controls_id`, `mr_panel`, `mode_panel`, `center_content_panel`, `input_method_panel`,
`stage_backdrop_panel` and `danmaku_overlay_panel`. Offsetting the root would have dragged all eight,
so the handoff's "root minus radius, children plus radius" plan needed bookkeeping that every future
child has to remember. Instead the surface got its own runtime child entity (`videoSurfaceEntity`,
created by `ensureVideoSurfaceEntity()` with the rail panels' proven `Transform` + `TransformParent`
pattern) and only that entity moves; the stage root's pose is never touched. The panel's scene object,
`Hittable`, `IsdkPanelDimensions`, `updateIsdkComponentProperties`, `addSceneObject` and the `onResume`
lookup all follow it.

Two consequences the split had to carry:

* `AnalogMediaStageTuningSystem` is registered **before** the panel is created and gated the thumbstick
  on `pointerInfo.rightEntity == mediaStageEntity`. Once the ray lands on the child surface the old
  fixed handle would never match and the stick would silently die — which is exactly the gesture used
  to test curvature — so the constructor now takes `mediaStageEntity: () -> Entity` and resolves the
  target per frame, mirroring the existing `barAnchorProvider` pattern.
* `IsdkCurvedPanel` is the ISDK component for curved panels and is the natural companion for a cylinder
  surface. NOT yet applied: the panel still carries `IsdkPanelDimensions` only, so ISDK hit geometry
  for the curved case is unverified and is the first thing to suspect if input on a bent stage
  misbehaves.

`applyStageLayerCurvatureOffset()` re-asserts x/y = 0 next to the Z, because these layers sit on the
stage-local axis and `TransformParent` preserves the WORLD pose while reparenting — so a local
transform that got re-derived on attachment is repaired the next time the offset is applied.

Reading the sign on device: cycling the curvature target to DANMAKU and bending it is still the
cheapest check, now that all three layers carry the same offset. "Stays put" confirms -1; "jumps the
wrong way" or "moves twice as far" means `SURFACE_ANCHOR_OFFSET_SIGN` must flip.

## Stage 3d (2026-09-11): the curvature controls could lock the user out

Found while testing 3c. The workbench is entered from the MediaStage (stage click -> PlaybackCanvas ->
`WorkbenchEvent`), and the stage's hit geometry belongs to the video surface entity, which is exactly
what the curvature offset moves. With a bad curvature the stage stops accepting the click, so the
workbench cannot be raised — and `R.id.mode_panel`, the rail that hosts `curvature_text`,
`curvature_layer_button` and `reset_stage_y_button`, is `WorkbenchModule.VIDEO_CONTEXT`, i.e. it only
exists while the workbench is up. Closed loop: no workbench -> no reset -> bad curvature -> no workbench.

Two things made it worse and are worth remembering:

* the bad curvature is PERSISTED (`setPlaybackCurvature` writes `AppPreferences`), so relaunching the app
  restores it. Clearing app data was the only way out.
* `wrist_debug_panel`, the one surface that is attached to the hand and therefore independent of the
  stage AND the workbench, is created `Visible(false)` and nothing in the tree ever sets it true — it is
  dead code, so it was not an escape route either.

Fix — an input source that depends on no panel being hittable:

* `AnalogWorkbenchSummonSystem` (DEBUG only): a press of A calls `summonWorkbenchFromController()`,
  which runs `onStagePrimaryAction()` — the exact function the stage click now calls. Routing it through
  the canvas rather than dispatching a workbench event is load bearing; see Stage 3f.
* `reset_curve_button` in `mode_panel.xml`, next to the other debug controls: sets all three layers back
  to `Flat` through `setPlaybackCurvature` so the persisted values are overwritten too, then re-asserts
  all three entity offsets synchronously (the state observer is asynchronous, and this path exists for
  the states the user cannot fix by hand).

Note on the A key: `selectorPanelRegistration` lists A in the left rail's `PanelInputOptions`
(`clickButtons`), so A is nominally that rail's click button. In practice `ImmersiveLeftPanel` has a
single live control (comments) and three `IconButton(onClick = {}, enabled = false)` no-ops, so there is
no meaningful click to collide with and a plain press is fine. `Controller.isPressed(bit)` provides the
edge, so no hold timer or latch is needed.

## Stage 3e (2026-09-11): the "overlays sit behind the video" report was a stale preference

Device report after 3c: the stage position is now right, but the danmaku and backdrop layers both look
like they sit BEHIND the video layer. The debug rail read `video: r=5.88 m`, `danmaku: r=6.00 m`,
`backdrop: r=6.00 m`.

The 6.00 is the tell. `MAX_RADIUS_METERS` is 6 m and `adjustedBy()` collapses to `Flat` as soon as a
step reaches or passes it, so **the thumbstick cannot produce `Cylinder(6.00)` at all**. Only
`fromRadius()` could, because it clamped out-of-range values to `MIN..MAX` instead of treating them as
flat. Every preference persisted while the flatmost radius was 20 m therefore came back as exactly
`Cylinder(6.0)` the moment the range narrowed — a state no control can reach, which then:

* handed the overlay panels `CylinderShapeOptions(6.0, w, h)` instead of `QuadShapeOptions`, and
* applied a full `-6.0 m` `surfaceAnchorOffsetMeters()` to each overlay entity.

So the two overlays were never deliberately curved; they were stale values resurrected as a barely
curved cylinder, carrying a whole radius of offset. Fix: `fromRadius()` resolves anything at or beyond
`MAX_RADIUS_METERS` to `Flat`, the same threshold `adjustedBy()` already used, with tests for the
boundary (`fromRadius(MAX)`, `MAX + eps`, 20, 100 -> `Flat`) and for the last cylinder still inside the
range (`MAX - 0.01`). A DEBUG log now prints the three restored curvatures once per launch, so a
resurrected value shows up in logcat instead of only in the rail.

**What this does NOT settle.** It says nothing about whether a UI-panel cylinder displaces its surface
by one radius the way the media panel's compositor layer does — the question the `offsetScale` idea
(1.0 for the media panel, possibly 0.0 for UI panels) was meant to answer. With both overlays back to
`Flat` their offset is 0 either way, so the current report cannot distinguish the two. Answering it now
needs a deliberate experiment: curve ONLY the danmaku layer to a mid radius (say r = 3 m) and watch
whether it stays put (UI panels displace like the media panel, `offsetScale = 1`) or jumps by a whole
radius (they do not, `offsetScale = 0`). Until then `offsetScale` stays uniform at 1.0 rather than
guessing.

## Stage 3g (2026-09-11): the display path may be switchable, which would end this whole class of bug

Prompted by the YouTube VR stack review (they curve the stage with a mesh deformer, `lullaby`
`DeformSystem`, texturing external OES video onto their own mesh rather than delegating to a compositor
layer). Three facts read out of the 0.13.2 toolkit/runtime jars:

* `MediaPanelRenderOptions.applyTo` really does call `PanelConfigOptions.setLayerConfig` unconditionally
  (bytecode offset 82), so a media panel always gets a layer config.
* The curvature reaches the compositor through `CylinderLayerConfig`, which carries
  `getRadius()` / `setRadius()` plus `getCentralAngle(shape)` / `getAspectRatio(shape)`. That is the
  cylinder the compositor draws, and therefore the thing whose surface lands one radius from the entity.
* `PanelConfigOptions` also has `forceSceneTexture` and `enableLayer`, and `applyTo` calls
  `setForceSceneTexture` but never touches `enableLayer`. `enableLayer` carries
  `Lkotlin/Deprecated;` — it is the legacy switch, not the supported one.

Why this matters: if the panel can be made to render through OUR mesh instead of the compositor layer,
curvature stops being a compositor-layer property at all. It becomes a vertex deformation of the video
surface — `CurvedStageMeshBuilder` already does exactly that — and then:

* the rendered surface and the hit geometry are the same geometry, so nothing is displaced;
* `surfaceAnchorOffsetMeters()` and every per-layer offset coefficient become unnecessary;
* the shadow / hole-punch mesh stops being one radius out;
* the thumbstick cannot be killed by a curvature change, because nothing it depends on moves.

That would delete the entire class of bug this file has been documenting rather than patching it.

Not yet verified, and the reason this is written as an experiment instead of a change: `enableLayer` is
`@Deprecated`, so it may be ignored, and `forceSceneTexture` is set by the panel itself, so it is not yet
established that disabling the layer leaves the pixels coming out of our mesh rather than leaving the
panel blank. Cheapest probe: add `enableLayer = false` next to the existing `sceneMeshCreator` assignment
in `createVideoPanel` / `reshapeSpatialVideoPanel` and look — if the picture survives and curves with the
mesh, the offset machinery can be retired; if it blanks, revert and the answer is "no".

## Stage 3f (2026-09-11): device results — one-tick curvature, and A diverging from the click

**From `Flat`, pushing the stick right takes effect for exactly one tick, then stops responding.**

This is the curvature offset feeding back into the input gate, and it is the clearest confirmation of
the render/input split from 3c. `AnalogMediaStageTuningSystem` gates the whole stick on
`pointerInfo.rightEntity == mediaStageEntity()`, i.e. on the ray hitting the entity that carries the
panel. One tick in, the curvature goes from `Flat` to about `Cylinder(5.93)`, so
`surfaceAnchorOffsetMeters()` jumps from 0 to -5.93 and that entity moves ~6 m along its local Z. The
compositor surface is pulled back to the authored plane — which is why the picture itself now looks
right — but the HIT geometry travels with the entity, so the ray that was pointing at the picture no
longer hits anything, `isTargeted` goes false, and the stick goes dead. The same displacement is what
swallows the stage click and therefore the workbench.

The structural consequence, stated plainly: **a hittable curved layer cannot have both its rendered
surface and its hit geometry on the authored plane.** One entity drives the compositor pose, the ISDK
hit geometry and the scene mesh, so moving it to fix the render necessarily breaks the hit.

**Fix: target the stage geometrically instead of moving the input to another entity.** A first instinct
was to introduce a separate never-curved hit entity, but that only answers "which entity gets hit" and
re-introduces the same class of bug the next time something moves. `PointerInfoSystem` already exposes
`rightRayOrigin` / `rightRayTarget`, which it rebuilds every frame as a fixed-length segment from the
controller and which therefore do NOT depend on hit-testing at all (`rightEntity` is the part that does).
`StageRayTargeting` intersects that segment with the stage rectangle — the plane of the stage ROOT,
which never receives a curvature offset — so the gesture keeps working wherever the render carrier has
travelled. Pure float math, covered by `StageRayTargetingTest`.

`AnalogMediaStageTuningSystem` now gates on that instead of `pointerInfo.rightEntity == <stage>`, and
also runs the stage's primary action on a trigger press. That replaces the dependency on the panel's own
`onClick`, whose hit geometry is the thing being displaced. (The panel listener is left in place: it
still fires while nothing is curved, and re-running the primary action is idempotent.)

**First device run of that gate: dead, and the cause was POSE SPACE.** The `ViriViriTarget` diagnostic
showed a perfectly well-formed frame — axes exactly (1,0,0)/(0,1,0)/(0,0,1), half extents
1.163 x 0.654 m, ray `(0.09,1.41,0.25) -> (0.40,1.84,5.23)` — and `geometry=false`. The frame had been
built from `Entity(R.id.spatialized_video_panel).getComponent<Transform>().transform`, but `Transform`
holds the LOCAL pose: the stage root hangs under `workbenchRootEntity`, so it read `(0, 0.71, 0)` with
z = 0, while the stage actually sits at the anchor's world z (about 2 m). The ray starts at z = 0.25
heading +z, so it crossed that local plane at `s = (0 - 0.25)/4.98 = -0.05` — behind its own origin — and
every frame missed, killing the thumbstick outright. Against the world plane the same ray gives
`s = 0.351` and lands at x = 0.199, comfortably inside the 1.163 m half-width.

Fix: `worldPoseOf()` composes an entity's `Transform` with every `TransformParent` above it. The SDK
exposes no world-transform accessor on `Transform` or `Entity` in 0.13.2 (checked with javap), so this
has to be done by hand, with a depth guard. `StageRayTargetingTest` pins the whole failure with the exact
device numbers.

## Stage 3h (2026-09-11): three device findings — overlays do not displace, and two ownership leaks

**1. UI-panel cylinders do NOT displace. The offset belongs to the media panel only.**

This closes the `offsetScale` question left open in Stage 3e, and the device answered it rather than a
reasoning step. With video r = 1.75 and danmaku r = 1.5, the danmaku layer ended up BEHIND the video. The
video entity is at -1.75 and its compositor surface is pulled back to the authored plane, so the picture
is right; the danmaku entity was pushed to -1.51 and stayed there, i.e. the -1.5 offset was pure error.
Had the UI panel displaced by its radius the way the media panel does, -1.51 + 1.5 = -0.01 would have put
it 1 cm in FRONT of the video, which is the authored relationship.

So `applyStageLayerCurvatureOffset` now takes an `offsetScale`: `MEDIA_PANEL_CURVATURE_OFFSET_SCALE = 1`
for the video surface, `UI_PANEL_CURVATURE_OFFSET_SCALE = 0` for danmaku and backdrop. The two overlay
layers keep their SHAPE (they are still handed `CylinderShapeOptions`) — they simply curve in place,
because their cylinder is ordinary geometry anchored on the entity rather than a compositor layer.

**2. The render carrier must not be hittable, or it swallows the pointer ray.**

The curvature offset moves the entity, and the entity's scene mesh moves with it, so a `Hittable` video
surface sat a whole radius away from the picture it represents. `PointerInfoSystem` resolves hits with
`getScene().lineSegmentIntersect(origin, target)`, which is exactly what a hittable mesh feeds — so the
displaced mesh intercepted the controller ray and its cursor dot for everything behind it, which is what
made the panels unusable. Removed `videoSurface.setComponent(Hittable())`; the surface is now a pure
render carrier and the stage's tap is geometric, needing no hit geometry.

**Confirmed on the NEXT device run: `IsdkPanelDimensions` was the interceptor.** Dropping `Hittable`
alone did not stop the ray from being caught by an invisible object whose distance tracked the curvature,
so the remaining hit source on that entity had to be the ISDK panel dimensions — and it was. The moving
surface now carries `Hittable(MeshCollision.NoCollision)` explicitly, no `IsdkPanelDimensions`, and no
`updateIsdkComponentProperties` publish. Note that dex presence is NOT a usable check here: the name
still appears in the app dex because the ISDK library references the class internally, so the only thing
that changed is that the app no longer sets it on this entity. The stage is consequently no longer an
ISDK-interactive object; `setMrMode`'s re-publish still targets the stage ROOT, which never moves, so it
cannot reintroduce a curvature-varying collider.

**Max radius raised to 10 m on request.** `MAX_RADIUS_METERS = 10f`, with `fromRadius` still resolving
anything at or beyond it to `Flat`. Worth remembering why it had been narrowed to 6: the visible edge
offset is `halfWidth^2 / (2R)`, so the first thumbstick step out of `Flat` now lands near 9.93 m and
moves the edge about 5 cm — the top of the range is inherently the least legible part of it.

**Corrected again by the next device run — the overlays DO displace.** Curving the danmaku with
`offsetScale = 0` drifted the WHOLE layer toward +Z, and the drift shrank as r shrank, i.e. it is
proportional to r. That is exactly the one-radius surface displacement the media panel shows, so
UI-panel cylinders are anchored at the same convention after all:

| | `scale = 1` | `scale = 0` |
|---|---|---|
| UI panels displace (`d = +r`) | surface = base, no drift | surface = base + r, DRIFT ∝ r ✓ measured |

So `UI_PANEL_CURVATURE_OFFSET_SCALE` is `1f` after all, and the arithmetic table that argued for 0 was
wrong: it treated the earlier "buried behind the video" report as a placement fact, but a 1 cm authored
separation cannot settle draw order against the video's COMPOSITOR layer. Geometry was fine; the layer
was simply composited under the video.

What is actually still open is that authored separation, not the offset. The danmaku's base local Z is
now steppable at runtime (`DANMAKU_BASE_LOCAL_Z_CANDIDATES`, the "Danmaku Z (cycle)" button, current
value shown in `curvature_text`) between -0.01 / +0.01 / -0.05 / +0.05, so the value that renders on top
of the video is read off the headset rather than guessed for a third time.

## Stage 3i (2026-09-11): the compensation is mutually exclusive with a clean pointer ray

Device report: below video r = 2.0 the controller ray and its cursor can no longer reach the workbench
panels (transport, left rail, video list). The threshold is exact and it identifies the culprit. The
anchor sits at world z = 2 (`applyStageWorldY`) and the controller ray starts near z = 0.25, so the video
mesh, which rides the entity at stage-local z = -r, lands at world z = 2 - r. It crosses the ray origin
at r = 1.75, and below that it sits BETWEEN the controller and the panels, in a 5 m pointer segment that
otherwise reaches them. So the interceptor is the video panel's own scene mesh.

That yields the structural statement this file has been circling:

| entity offset | rendered surface | mesh (stage-local z) | pointer ray |
|---|---|---|---|
| `-r` (compensated) | correct | `-r`, i.e. in front of the rails at -1.05 | blocked |
| `0` (uncompensated) | off by r | `0`, behind the panels | clean |

**Fixing the picture and keeping the mesh out of the ray's path cannot both be done by moving one
entity**, because that entity carries both. Every symptom in this family — the jammed thumbstick, the
swallowed stage tap, the intercepted cursor — is a restatement of that one coupling.

Why the earlier attempts missed it: `PanelSceneObject` and `PanelConfigOptions` expose NO collision
switch (checked with javap), and the danmaku/backdrop overlays stay unhittable through their `Panel`
component (`Panel(id, MeshCollision.NoCollision)`). The video panel never set a `Panel` component at all,
so its generated mesh stayed hittable no matter what `Hittable(...)` said on the entity. This run adds
`Panel(R.id.spatialized_video_panel, MeshCollision.NoCollision)` alongside `Hittable(NoCollision)`.

**Fixed properly in the next attempt, without any new entity or deprecated flag: bake the inverse into
the MESH VERTICES.** The whole file had been treating the entity as the only lever, but
`createVideoSceneMesh` builds the video mesh ourselves, so render and hit geometry can be separated after
all:

| entity offset | compositor surface | mesh (without inverse) | mesh (with inverse baked in) |
|---|---|---|---|
| `-r` | 0, correct | `-r`, in the ray's path | 0, at the authored plane |

`translatedZ(positions, -surfaceAnchorOffsetMeters())` shifts every vertex back onto the authored plane,
so the mesh leaves the ray's path (the panels sit at -0.5 .. -1.05) for every radius, and the drop shadow
and hole punch stop being a radius out of place as a side effect. Zero cost while flat, where the offset
is 0.

The lesson for this file: the exclusivity in the table above was real but it was a statement about
*one lever*, not about the system. Hit geometry, rendered surface and scene mesh are three things that
merely happened to share an owner.

**Recorded so it is not repeated.** The attempt before this one — adding
`Panel(R.id.spatialized_video_panel, MeshCollision.NoCollision)` to switch the panel's own collision off —
CRASHED the app on launch (build 904A4B03) and was reverted. The idiom was copied from the danmaku and
backdrop overlays, which HAVE a panel registration because they come from `Entity.createPanelEntity`; the
video surface is a hand-built `PanelSceneObject` and has none, so a `Panel` component on it throws at
startup. Unverified components do not belong in the startup path: a headset symptom is far cheaper than a
crash.

## Stage 3j (2026-09-11): the video layer draws over scene panels, and curvature sync would not fix it

Two device reports that turn out to be one root cause:

* the video covers a tighter-curved danmaku layer, but only while the video radius sits in a window
  (1.6 .. 1.99 m against a danmaku radius of 1.5 m);
* the video covers the workbench's video list, even though that rail sits about 1.05 m IN FRONT of the
  video plane.

The second is the diagnostic one. A 1.05 m depth advantage losing to the video cannot be a depth-sorting
result, so the video's COMPOSITOR LAYER is being drawn over scene geometry regardless of depth. Given
that, the first report is the same thing seen through two curved surfaces: the video layer wins wherever
its arc and the danmaku's arc overlap, and the two arcs only cross inside that radius window.

**This is why curvature sync is not the fix for the first report.** Making the three radii equal would
remove the crossing, but the video would still be a compositor layer and the danmaku still a scene panel,
so the ordering would merely become uniformly wrong instead of wrong inside a window. Sync is still worth
doing for its own reasons — one parameter set, one offset, one base separation — but it must not be sold
as the cure for this.

The cure is to stop drawing the video as a layer: `applyVideoSurfaceRenderPath()` sets
`PanelConfigOptions.enableLayer = false`, so the panel's pixels come from our own scene mesh (the media
panel already sets `forceSceneTexture`, which is what surfaces them to it) and the video is depth-sorted
against the rails and overlays like any other object. Because the inverse offset is already baked into the
mesh vertices, the surface stays on the authored plane whether or not the layer draws it.

Status: EXPERIMENT. `enableLayer` is `@Deprecated` in 0.13.2 and may simply be ignored, in which case
nothing changes. Revert is making `applyVideoSurfaceRenderPath()` a no-op. Deliberately implemented as a
property set on an existing config object inside the same `apply { }` block that already runs, NOT as a
new component on the startup path — see the crash note above.

## Stage 3k (2026-09-11): layer ordering is not ours to control

Two knobs tried against "the video layer draws over the video list", both measured as no-ops on device:

* `PanelConfigOptions.enableLayer = false` — no change (build F9E418AD2);
* `MediaPanelRenderOptions(zIndex = -1)` — no change (build F158ABB7).

The last candidate is `LayerConfig.zIndex`, which is a DIFFERENT object from the render options and is the
one `applyTo` actually hands to `setLayerConfig` (see `applyVideoLayerDepth`). `MediaPanelRenderOptions`
writes that config during `toPanelConfigOptions()`, i.e. before the `apply { }` block that mutates it, so
mutating it there does win the ordering of writes.

If this is ignored too, the conclusion is that a media panel's compositor layer cannot be ordered against
scene geometry from the app at all, and the display path itself has to change:
`forceSceneTexture` already makes the panel render into a `SceneTexture` that our `sceneMeshCreator`
receives, so the panel can be demoted to a texture SOURCE and the video drawn on our own mesh — which is
scene geometry, depth-sorted against the rail and the overlays like everything else.

**RESOLVED: `LayerConfig.zIndex` was the lever.** Mutating `config.layerConfig?.zIndex` (see
`applyVideoLayerDepth`) put the video layer behind scene geometry and the video list displayed correctly.
The two failures before it were not the same knob:

| attempt | object | result |
|---|---|---|
| `PanelConfigOptions.enableLayer = false` | panel config | no change |
| `MediaPanelRenderOptions(zIndex = -1)` | render options | no change |
| `LayerConfig.zIndex = -1` | the config actually handed to `setLayerConfig` | **works** |

The structural route above — demoting the panel to a texture source — is therefore RETIRED. It was the
right fallback reasoning, but it is no longer needed, and it would have been a much larger change for a
result this one field already delivers.

Two consequences worth knowing, because they change what is on screen:

* the danmaku and backdrop overlays now actually pass in front of the video, since they are scene geometry
  and the layer sorts behind them. This is very likely the fix for the "video covers the danmaku between
  r 1.6 and 1.99" report as well — same root cause, one field;
* `StageBackdrop`'s dimming becomes effective for the first time. It was always intended to dim the
  MediaStage while the workbench is open (`setVisible(PanelSlot.MEDIA_STAGE, ..., modules.isNotEmpty())`,
  alpha 0.42); the video layer used to draw over it. Its creation comment still says it awaits "a uniform
  Spatial material [to replace] compositor-dithered UI alpha", so a banded dim is the recorded placeholder,
  not a new defect.

Also raised `MAX_STAGE_SCALE` from 1.50 to 2.00 on request.

**Radius and scale are independent, and that is confirmed on device, not argued.** Every path passes the
radius through untouched — `CylinderShapeOptions(radius, width, height)`, `surfaceAnchorOffsetMeters()`,
the `translatedZ` inverse, and `CurvedStageMeshBuilder` — so the bend is identical at 0.5x and 2x and only
the distance the mesh TRAVELS changes. Scale changes the arc a stage covers, `centralAngle = width / radius`
(about 45 degrees at 0.5x, 89 at 1x and 178 at 2x for r = 1.5 m), and the half-cylinder limit the
compositor can draw is therefore a theoretical ceiling only: it was not observed to bite at 2.0x. Do not
present it as a curvature/scale coupling — it is not one.

**3. Stage input has to be off while the workbench is up.**

`AnalogMediaStageTuningSystem` is now gated on
`!(immersiveWorkbenchHost.state.visible)`. The stage sits in the middle of the interaction volume the
panels occupy, so a live stick and trigger underneath them made the panels themselves unusable — the
control is a playing-only affordance, the same shape as `GrabBarHoverSystem`'s playing-only rule.

Also worth noting for the same reason: the hand-built mesh on the render carrier — the drop shadow and
the hole punch — travels with the entity, so it sits one radius off the picture whenever a layer is
curved. Nothing reads it for input any more, but it is still wrong on screen and is the next thing to
fix there.

**The A-summoned workbench differs from the stage-click one: no transport, and it sometimes will not
close.**

Not a platform quirk — an implementation error of the 3d escape hatch.
`applyPlaybackCanvasSlots` is the single place that both derives the workbench event AND calls
`appState.openWorkbenchEmpty()`. The A path called
`immersiveWorkbenchHost.dispatch(WorkbenchEvent.RevealTransport)` directly and skipped the canvas, so
the canvas stayed in `QUIET_WATCH` while the workbench went visible; slot visibility never ran and the
two state machines disagreed. Fix: the stage click's body is now `onStagePrimaryAction()` and A calls
that same function, so click and button produce identical canvas + workbench state by construction.
The lesson generalises: when a state machine is downstream of another one, an "escape hatch" must enter
at the UPSTREAM entry point, not at the downstream reducer.

## Stage 3l (2026-09-11): the stage has ONE curvature again

(Note: the Stage 3x sections in this file are APPENDED in the order they were written, which is not
strictly chronological — several were inserted at anchors rather than at the end. Read the dates.)

Device report: "曲率没绑定" — the backdrop kept its own radius instead of following the video's. That was the
design as built (three independent per-layer values plus a target-layer selector), and it is wrong for this
product: the backdrop EXISTS to dim the MediaStage, so it cannot dim it from a different radius, and a
danmaku plane on another radius slices through the video instead of lying on it.

`ViriViriAppState.setPlaybackCurvature` now takes a single curvature and writes all three fields in
lockstep (persisting all three keys with the same value), so every layer is always on the same radius. The
layer target and the panel's "Next curve target" button are gone.

Deliberately NOT the full collapse: the three state fields, the three persisted keys, `curvatureFor` /
`withCurvature` / `cyclePlaybackCurvatureEditLayer` and `PlaybackStageCurvatureLayer` are now redundant.
They are kept so this stays a behaviour fix rather than a persistence migration, and they are safe because
lockstep keeps them equal. Collapsing them into one field and one key is a follow-up cleanup, and it should
be done before anyone adds a second curvature source.

