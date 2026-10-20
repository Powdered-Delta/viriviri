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
