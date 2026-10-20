# Journal - M0e_N00b (Part 1)

> AI development session journal
> Started: 2026-06-11

---



## Session 1: Hybrid app foundation

**Date**: 2026-06-11
**Task**: Hybrid app foundation
**Branch**: `master`

### Summary

Replaced the disposable Unity template with a Meta Spatial SDK Hybrid Android foundation, added shared core/ui modules, Meta app scaffolding, build docs, PICO migration notes, and captured Trellis specs.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `8e8e527` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: Finish compositor video cutter cleanup

**Date**: 2026-10-20
**Task**: Finish compositor video cutter cleanup
**Branch**: `main`

### Summary

Completed existing cutter/PLAN_1 removal; fixed orphan val compilation failure; kept flat shadow and carrier radius compensation; synced runtime terminology and aspect spec. Windows app tests: 183 passed; debug APK assembled; no device installation or launch. Unrelated Penpot and skill work untouched.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `5671cbb` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: Correct video aperture and compositor visibility

**Date**: 2026-10-20
**Task**: Correct video aperture and compositor visibility
**Branch**: `main`

### Summary

SDK 0.13.2 bytecode confirms custom shadow creator skips default aperture setup. Restored SDK quad/cylinder aperture on video creation and reshape; explicit opaque video and alpha-blended UI; preserved carrier correction and stage input; retired shadow override and inverse helpers. Synced terminology and spec. Windows tests 183 passed; debug APK built; actual headset visual acceptance pending; no install or launch.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `130967e` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: Unify stage geometry and full-stage danmaku

**Date**: 2026-10-20
**Task**: Unify stage geometry and full-stage danmaku
**Branch**: `main`

### Summary

Implemented one app-state parameter commit and PlaybackStageGeometry snapshot for video/crop/aperture and full-stage overlays. SDK Crop removes FIT-buffer bars with matching aperture footprint; danmaku option A keeps 16:9 viewport/raster. Reset native shape scale and explicitly synchronize ECS/native world pose after reshape. Fixed debug Apply state ordering. Windows tests: 188 passed; debug APK built. Headset validation of all four reported issues remains pending; no install/launch/device writes.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `27f4887` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 5: Verify SDK coordinate boundary and unify cylinder aperture dimensions

**Date**: 2026-10-20
**Task**: Verify SDK coordinate boundary and unify cylinder aperture dimensions
**Branch**: `main`

### Summary

Executed pinned SDK Pose/Quaternion; verified native Z reflection and cylinder axis-origin using local SDK binary. Confirmed aperture already shares carrier and base-radius method. Removed pre-divided shape and whole-panel footprint Crop in favor of final shared physical geometry with UV-only clip; preserved full-stage danmaku. Corrected coordinate/UI/spec docs. Windows debug build passed; 191 JVM tests passed. No install or launch; headset visual acceptance pending.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `47137cf` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 6: Refactor stage into one geometry controller and native batch renderer

**Date**: 2026-10-20
**Task**: Refactor stage into one geometry controller and native batch renderer
**Branch**: `main`

### Summary

Replaced Activity video/overlay reshape, one-shot replay, pending flags and direct reset/debug pose writes with PlaybackStageController plus PlaybackStageGeometrySystem and PlaybackStagePanelRenderer. Serializes inputs, waits for full panel set, coalesces latest layout and replays replaced identities. Fresh local poses compose through one sampled stage world pose; source/shape factories read one snapshot; full viewport danmaku and verified base-radius sign retained. Windows debug build passed and 201 JVM tests passed. Docs/spec synchronized. APK built with DEV 0c278276; no install/launch; headset visual acceptance pending.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `b6627f5` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 7: Remove synthetic decoder FIT crop causing 4:3 distortion

**Date**: 2026-10-20
**Task**: Remove synthetic decoder FIT crop causing 4:3 distortion
**Branch**: `main`

### Summary

Confirmed Media3 1.4.1 SurfaceView-specific scaling contract and Android MediaCodec Surface scaling semantics. Prior fixed-output-aspect black-bar assumption produced a .75-wide crop for full 4:3 frames, magnifying horizontal image contents. Regression failed against old code first, then removed StageVideoCrop arithmetic and mapped actual SDK Crop.NONE through the existing renderer. Corrected current UI/coordinate/spec docs; retained controller, Surface/player and curvature math. Windows build passed; 202 JVM tests passed. APK DEV 47814203. Read-only headset log queries returned no retained lines; no install/launch; visual acceptance pending.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `8b3fad5` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 8: Scale stage radius and carrier depth as one layout

**Date**: 2026-10-21
**Task**: Scale stage radius and carrier depth as one layout
**Branch**: `main`

### Summary

Approved whole-stage similarity correction: effective radius r0*s, carrier local Z (base-r0)*s, scaled layer separations, native scale one and raw saved radius unchanged. Preserved full-frame video and full-stage danmaku. Updated specs and local-theme component diagram. Windows debug verification passed 205 tests and APK assembly; diagram text/mobile layout and identifier checks passed. APK DEV c4bce0f5. No headset install/launch; visual drift acceptance pending.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `a42f117` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 9: Aperture drift remains; prepare opt-in native mesh probe

**Date**: 2026-10-21
**Task**: Aperture drift remains; prepare opt-in native mesh probe
**Branch**: `main`

### Summary

Read live Quest 2 logs after user reproduction: 220 complete group batches show scale/radius/Z submissions are delivered; native bounds are not API coordinates. User reports drift persists in ALL_UI_MESH and after 2 seconds stopped. Root remains unresolved. Added opt-in debug aperture intersection probe with zero native click buttons, same video carrier, applied-layout settling gate, bidirectional bounded ray traversal and explicit miss/invalid status. Windows verification passes 211 tests and APK assembly. Diagnostic APK DEV c237abe6, SHA256 19B183034C570001074DA7DE422CC49AF76F01946D975664CC54856CED0F2EB1. No device writes/install/launch. Task remains in_progress for native measurements and proven fix.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `28a6bb1` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 10: Restore explicit video aperture and sole trigger owner

**Date**: 2026-10-21
**Task**: Restore explicit video aperture and sole trigger owner
**Branch**: `main`

### Summary

Compared historical explicit mesh and input fixes. Removed opt-in LineTest probe, legacy native video onClick callback and MR video ISDK dimensions publication. Restored explicit hole-punch mesh from final PanelShapeConfig width/height/radius, corrected inward arc and inverse carrier compensation; preserved full-frame portrait/4:3 and full-stage danmaku. Windows verification: 209 JVM tests and assembleDebug passed; diagram/UI identifier checks clean. APK DEV 802069c7. No install or launch this turn; aperture task remains in_progress pending headset visibility/alignment and input acceptance.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `d14f93f` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 11: Separate surface opening from video cylinder axis carrier

**Date**: 2026-10-21
**Task**: Separate surface opening from video cylinder axis carrier
**Branch**: `main`

### Summary

User clarified curve direction correct, center still drifts. Read-only installed APK SHA, 292 complete geometry batches and existing SDK Inspector confirm propagation and settled ECS compensation; final GPU pose remains unmeasured. Introduced dynamic PlaybackStageAperture sibling at stage surface origin (mesh center Z=0, carrier scaled base only). Video retains axis radius compensation and bounds but material writes no color/depth. Same layout/frame renderer owns both; aperture follows video visibility, stays NoCollision and is released explicitly. Preserved Surface/player, full-frame portrait/4:3, full-stage danmaku and single trigger edge owner. 210 JVM tests and Windows debug APK build pass; diagram and UI identifiers verified. APK DEV be917a7e; no device install/launch. Drift task stays in_progress pending headset acceptance.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `107bccb` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 12: Handoff rejected stage aperture implementation

**Date**: 2026-10-21
**Task**: Handoff rejected stage aperture implementation
**Branch**: `main`

### Summary

User rejected latest 107bccb build: video opening does not respond to curvature; danmaku geometry does respond with Z drift; scale still incorrect. User requests new-session handoff, no further runtime changes. Added docs/playback-stage-handoff-2026-10-21.md with exact build/evidence identities, current objects and update-path asymmetry, failed approaches and lessons, protected portrait/4:3 and input fixes, expected behavior/acceptance and copyable new-session prompt. Clarified SDK danmaku mesh is rendering integration, not a required business cutter; 292 batches/Inspector belong only to old shared-carrier build. Marked spec/diagram/task latest implementation failed; task stays in_progress. Docs-only; diff checks, source paths and diagram self-check passed; no build/device writes.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `8e0dd32` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
