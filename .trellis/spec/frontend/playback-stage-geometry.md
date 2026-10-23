# Playback Stage Geometry

## Curvature input boundary (2026-10-21 clarification)

Flat and cylinder shapes switch automatically through curvature input. Raw control
radius is inclusive from 1.5 to 8 metres: exactly 8 remains a cylinder; only a
radius above 8 becomes Flat. From Flat, the first finite positive tightening delta
enters Cylinder(8) exactly, without subtracting that delta. Further tightening
reduces radius; loosening increases it and crosses into Flat only above 8.
Zero/non-finite input leaves the state unchanged; loosening Flat stays Flat.

Persistence decoding, app controls and core raw normalization must agree at the
inclusive maximum. A raw radius of 8 at scale 2 produces an effective radius of
16, still curved. Never apply the raw threshold to the derived effective radius.
Cover the actual control-to-codec-to-host-to-layout path as well as boundary
transitions. This control contract does not validate headset clipping alignment;
the SDK rebuild Gate A remains failed until visual acceptance succeeds.

## Whole-group similarity scaling (2026-10-21)

Raw control/persistence curvature stays in PlaybackStageParameters. The controller
bakes scale once into width, height, effective radius and surface separations.
Effective radius = raw radius * scale; carrier local Z =
(baseLocalZ - raw radius) * scale (flat: baseLocalZ * scale).
PlaybackStageLayerGeometry.baseLocalZ is the already-scaled surface separation.
Native panel scale stays one. Never pass effective radii through fromRadius:
raw 7.9 at scale 2 renders radius 15.8 without becoming flat, and raw 1.5 at
scale 0.7 renders radius 1.05 without a minimum clamp. World parent translation
is not scaled. Preserve the accepted full-frame texture mapping. Headset aperture
drift acceptance remains separate from mathematical and build verification.

## 1. Scope / Trigger

Stage scale, source/target aspect, curvature, layer separation and panel readiness.
This is runtime ownership, not static scene authoring. Existing scene hierarchy,
visibility/input owners, compositor policy and Media3 output ownership stay separate.

## 2. Signatures

```kotlin
PlaybackStageController<T>(widthMeters, heightMeters, resolve, commit)
setParameters(PlaybackStageParameters(scale, targetAspect, curvature))
setVideoSize(width, height, pixelWidthHeightRatio)
setDanmakuDepth(value)
invalidate()
flush()
close()
PlaybackStagePanelRenderer.apply(layout, panels): Boolean
```

## 3. Contracts

- App-state and video-size callbacks only submit inputs. The controller serializes
  input changes and frame commits and keeps one immutable derived layout.
- The geometry system resolves VIDEO/BACKDROP/DANMAKU before any commit. Missing
  panels retain pending work; no independent video/overlay applied flags exist.
- Commit the newest complete layout, coalescing intermediate inputs. A replaced
  panel identity requires replay even if inputs did not change. False commit stays
  pending. Close prevents further resolution/commit and drops retained identities.
- Renderer owns all live shape, scale, UV and carrier-pose mutations. Registration
  factories use controller geometry but do not acknowledge application.
- SDK video mesh and layer share final videoContent shape. Danmaku/backdrop use the full
  viewport. `videoTextureCrop` is SDK Crop.NONE; renderer passes that same value
  to setCrop, resetting footprint and sampling the full frame. A fixed 1920x1080
  pixel budget does not prove any FIT bars in a direct decoder Surface. Never
  derive UV cropping from source/output aspect or divide physical dimensions by UV.
- Local child poses are rebuilt with identity rotation and scaled base minus effective radius Z (scaled base for
  flat). Update stage/bar spacing, sample its world pose once for diagnostics, and let the ECS TransformParent chain
  propagate the child pose. Do not also call native SceneObject position/rotation APIs for the same carrier.
- Preserve the player and UI Panel/Surface/Display. Video shape changes replace its Panel and Surface
  by identity-safe detach-before-destroy, then creation from final parameters. No video reshape.
  ECS Transform and Visible are the sole live pose and visibility owners;
  no new visibility or hit-test owner.
  Render-policy/debug depth/reset changes must go through submit/invalidate.

## 4. Validation / Error Matrix

| Condition | Required result |
| --- | --- |
| Non-16:9 source | Full decoded-frame UVs; source aspect belongs in physical shape |
| One panel missing | No geometry commit; newest layout retained |
| Parent/components unavailable | Renderer returns false; revision stays pending |
| Panel replaced | Replay whole current group |
| Curvature changes, aspect unchanged | New layout still commits |
| Native commit throws | Do not acknowledge revision; propagate failure |
| Activity destroyed | Close controller before SDK teardown |

## 5. Good / Base / Bad Cases

- Good: portrait + scale + radius inputs arrive before panels; one complete latest
  layout lands after readiness, with full-width danmaku.
- Base: unchanged input/identity on idle frames produces no reshape.
- Bad: resetting only carrier Z before the matching flat shape arrives; or marking
  aspect diagnostics applied when one native panel never received the geometry.

## 6. Tests Required

PlaybackStageControllerTest covers delayed/missing panels, latest-input coalescing,
replacement, failed commit retry, curve-only changes, debug changes, disposal and
actual SDK pose composition under rotated parents. Geometry tests cover aspect,
pixel ratio, complete-frame sampling and full-stage danmaku. Run Windows debug verification.
Build/JVM success does not prove headset pixels or compositor visibility.

## 7. Wrong vs Correct

```kotlin
// Wrong: pose-only debug bypass plus independent overlay retries.
video.setPosition(flatPosition)
overlayPending = true

// Correct: replace all desired tuning values; frame owner commits one layout.
controller.setParameters(parameters.copy(curvature = PlaybackStageCurvature.Flat))
// PlaybackStageGeometrySystem.execute(): controller.flush()
```


## Production B migration (2026-10-22)

One production rendering path replaces the rejected custom aperture and removes
SDK_REBUILD/LEGACY runtime selection and SDK diagnostic backend. Core interfaces
remain contracts only; there is no app adapter or fallback backend.

GeometrySystem schedules one latest-value main-thread frame every 33 ms. Native
writes, Surface detachment/replacement and Media3 attachment share this owner.
The video ECS entity/visibility/input remain stable; only PanelSceneObject and
its owned Surface are replaced when final VIDEO geometry changes. No custom
mesh, extra opening Entity, UV scaling, player recreation, prepare, reload or seek.
Own candidates before registration/bind can fail; retries detach and dispose
owned candidates first. Never destroy a still-bound Surface after detach fails.

Danmaku remains real Compose content and retains its View/Display/Surface.
Fixed 1280x720 dp at 160 dpi, ALPHA_BLEND, no glass, explicit layer zIndex 2
(video 1). After creation/reshape disable all RGBA writes (including alpha)
and depth writes on ONLY its scene material. Show first content only after B.
Do not hide its compositor layer. Backdrop and workbench retain existing owners.
No real subtitle layer exists; never ship POC fake text as a production feature.

Only ECS base-minus-effective-radius local poses are written; TransformParent
propagates world transforms. Successful resource replacement is acknowledged
using the new panel identity. appliedLayout exists only after complete success,
is cleared on failure/missing panel/close and is used for geometric input.

Test replacement during commit without duplicate UI replay, latest request after
native failure, closed owner and existing scale/aspect/pose/resource contracts.
Headset acceptance remains necessary for actual content, aspect/portrait,
foreground occlusion, video hide/restore and 2D/VR route handoff.

## Rejected video reshape candidate (2026-10-23)
Media3 1.4.1 resets first-frame state on any changed output and may render a frame
while paused. A retained decoder Surface with SDK video reshape was tried, but
FAILED headset checks: scale desynchronizes and entering a curve disappears.
It was withdrawn. Keep final-parameter video recreation until a different path
passes all three visual gates. Paused stepping remains unresolved; do not infer
geometry correctness from SDK docs, output identity assertions or JVM tests.

Detailed failure record: [video reshape failure](../../../docs/video-reshape-failure-2026-10-23.md).
This rejection applies to the retained video PanelSceneObject path, not the
retained UI overlay reshape path. New candidates must pass scale alignment,
curved visibility and paused-frame retention together; no docs-only acceptance.
