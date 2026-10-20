# Finish compositor video geometry cleanup

## Goal
Remove the reverse-curved hole-punch mesh, keep compositor-owned video shape and
the measured carrier radius correction, and remove the mesh-only aspect debug path.

## Existing work
The working tree already removes CurvedStageMesh and PLAN_1 and retains only a
flat bottom shadow. Preserve and validate these in-scope changes; unrelated Penpot,
skill, and prototype changes remain user-owned.

## Acceptance
- No custom curved cutter or hole-punch material remains in the video path.
- Creation and every aspect/scale/curvature update use the compositor panel shape.
- Carrier radius correction and inverse flat-shadow correction remain intact.
- Debug aspect controls expose target/apply only, using the shared display ratio.
- Runtime terminology and panel debugging spec describe the implemented ownership.
- Windows app unit tests and debug assembly pass; no device install or launch.

## Boundaries
SpatialVideoSampleActivity owns the existing video panel on videoSurfaceEntity,
parented to spatialized_video_panel. Existing visibility/input ownership is retained:
the video carrier is non-colliding and stage input remains with the existing host.
The compositor video layer is the sole video Surface owner (zIndex 1); backdrop is
2 and other UI is 3. The optional shadow is flat at the content bottom, inverse
compensated to the authored plane, and has no video pixel or crop ownership.

## Verification
- Passed git diff --check and checked numstat: no whole-file line-ending rewrite.
- Passed scripts/build-windows-debug.ps1 on Windows JDK 17: app unit tests and
  assembleDebug, BUILD SUCCESSFUL in 2m 33s.
- APK: app/build/outputs/apk/debug/app-debug.apk (177535499 bytes).
- SHA-256: F4CB25340FDCEC6EF6D69DA61AFC6A22CC6219CA3AB37D6FDF43C094CDF9E45C.
- All changed terminology identifiers resolve in the required source roots; full
  bare-identifier scan introduced no unresolved terms (3 pre-existing terms remain).
- Confirmed no old builder/plan/button references remain in app/src.
- Fixed the pre-existing incomplete deletion's orphan `val` in debug UI setup.
- Updated stale mesh-only aspect spec and cutter/depth comments.
- Meta layer/mesh docs verified via metavr docs search/fetch on 2026-10-20:
  https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-2dpanel-layers

Manual headset acceptance remains outside this request. No device mutations ran.
