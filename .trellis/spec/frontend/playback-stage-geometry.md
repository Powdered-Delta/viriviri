# Playback Stage Geometry

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
- Local child poses are rebuilt with identity rotation and base-radius Z (base for
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
