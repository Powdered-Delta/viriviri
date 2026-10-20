# Verify coordinates before changing cylinder geometry

## User acceptance
Establish project API world, stage-local, and native compositor cylinder coordinates;
correct contradictory docs; verify whether aperture uses the existing visual-anchor
method; then fix aperture/video dimensions without guessing or flipping a sign.

## Verified findings
- Running installed SDK 0.13.2 Java classes: Pose().forward() = (0,0,+1),
  right() = (1,0,0); Quaternion(0,90,0) rotates forward to +X.
- Native SceneObjectSetPosition flips position Z and quaternion X/Y at the
  API/render boundary. AetherMeshAsset upload flips vertex Z and normal Z.
- Native makeCylinderSurface generates center vertex (0,y,+radius) in API mesh
  coordinates, uploaded as native (0,y,-radius). Existing carrier baseZ-radius
  pins the center to authored base. No sign change is justified or planned.
- Aperture and compositor bind the SAME PanelSceneObject and carrier; both
  creation and reshape invoke applyStageLayerCurvatureOffset. The hypothesis
  that the current aperture simply skipped the method is not supported by code.
- setCrop separately writes SceneObject footprintScale and SceneLayer UV clip;
  native cylinder dimensions originate from radius/angle/aspect plus entity scale,
  not the footprintScale field. The previous pre-divided videoShape and cropped
  footprint split aperture/video dimension ownership.

## Patch
- One final videoContent/videoShape for SDK aperture and layer, unit native scale.
- Clear whole-panel Crop/footprint scale and apply decoded-content UV rectangle
  directly to SceneLayer.setClip; no extra spatial shrink or pre-divide.
- Preserve baseZ-radius and the full-stage danmaku option A.
- Correct coordinate doc and related runtime terminology/spec. Label API, stage
  and native coordinates explicitly; preserve uncertainty about headset pixels.
- Regression tests use the actual SDK Pose/Quaternion and production geometry.

## Verification
Read-only device inventory/log attempts permitted; no install/launch or device
writes. Connected Quest 2 found, no relevant retained tag logs returned.
Windows unit tests/debug build and source/doc checks, then headset acceptance
remains separate. No new static objects or media Surface/player ownership changes.

## Completed host verification
- Windows scripts/build-windows-debug.ps1: BUILD SUCCESSFUL, testDebugUnitTest and assembleDebug.
- 191 JVM tests, zero failures/errors; includes actual SDK coordinate math and rotated-stage radius anchoring.
- git diff --check passed; no newly unresolved bare identifiers in UI terminology doc.
- No APK install/launch. Headset viewport extent, visual Z and occlusion acceptance remain pending.
