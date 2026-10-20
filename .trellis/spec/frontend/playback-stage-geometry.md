# Playback Stage Geometry

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
- Video/aperture share final videoContent shape. Danmaku/backdrop use the full
  viewport. `videoTextureCrop` is SDK Crop.NONE; renderer passes that same value
  to setCrop, resetting footprint and sampling the full frame. A fixed 1920x1080
  pixel budget does not prove any FIT bars in a direct decoder Surface. Never
  derive UV cropping from source/output aspect or divide physical dimensions by UV.
- Local child poses are rebuilt with identity rotation and scaled base minus effective radius Z (scaled base for
  flat). Update stage/bar spacing, sample its world pose once, then compose every
  child from that same parent. Do not flip the verified coordinate sign.
- Preserve native panel, Surface and player. No new visibility or hit-test owner.
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

## Separate surface-origin aperture and axis-origin media carrier (2026-10-21)

PlaybackStageAperture owns one dynamic sibling entity under the stage root, no Panel
registration, player, Surface or input listener. Its visible hole mesh is centered:
Z=r*(cos(theta)-1), center Z=0 for flat and all radii. Its carrier uses scaled
baseLocalZ only, never +/-radius. SceneObject visibility mirrors video Visible;
there is no independent UI visibility state. Activity closes/unregisters the object.

Video config first initializes SDK settings, then its sceneMeshCreator updates the
surface opening using the final PanelShapeConfig width/height/effective radius.
The existing video carrier still uses base-effectiveRadius, and retains axis-origin
mesh bounds (+r center) for culling. That carrier's material is OPAQUE/unlit with
color writes zero and depth writes DISABLE: it must never punch a second hole.
The decoder Surface, compositor shape/crop, zIndex and video compensation stay intact.
The existing stage renderer commits both origins from one sampled stage-world pose.

All builds keep both carriers NoCollision and video clickButtons=0. No native video
InputListener may call the stage action; geometric Controller.isDown rising-edge
handling is the sole owner. No video ISDK dimensions publication on MR/VR transitions.

PlaybackStageApertureMeshTest checks actual centered opening vertices against world
surface points under rotated parents, flat/cylinder transitions, scale/aspect and
both windings. Separately check video axis bounds. Build/JVM success does not prove
GPU/compositor alignment. User confirmed direction correct but center drift persists
in the shared-carrier build; this structural correction still requires headset QA.

## Headset rejection of the surface-origin slice
107bccb also FAILED: user reports video aperture does not respond to curvature, danmaku geometry responds with Z drift, and scale is still wrong. The separate-origin implementation above describes current code, not a validated pattern that must be retained. See docs/playback-stage-handoff-2026-10-21.md before further changes. Prior 292 batches/Inspector snapshots belong to the old shared-carrier APK; do not apply that evidence to the new callback/mesh path.
