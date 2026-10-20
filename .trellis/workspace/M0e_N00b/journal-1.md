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
