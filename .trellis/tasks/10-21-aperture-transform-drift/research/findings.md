# Aperture drift investigation, 2026-10-21

## Latest headset acceptance
User rejects a42f117 scaling patch: visible curved black viewport center moves fore/aft with curvature; center also moves vertically with size. User explicitly confirms center movement, not merely curved-edge displacement. Preserve full-frame portrait/4:3 mapping. Whole-group scaling is implemented but does not resolve this visual fault.

## Actual device evidence
Read-only Quest 2 192.168.0.234:5555, installed package updated 2026-10-21 02:35:19. User reproduced at 02:44:52..02:45:04. Retained ViriViriStage logs contain 220 complete batches (660 layer lines). Radius and scale reach VIDEO, BACKDROP and DANMAKU; local carrier formula matches baseZ-effectiveRadius within float tolerance. Example effective radius 1.5804589: VIDEO carrierZ -1.5804589, submittedWorldZ 0.41954112; native mesh bounds Z [-1.5804589,-1.3609602]. All roles have matching cylinder footprint. This disproves an input-delivery failure but does not measure final world pose or compositor pixels.

## Native coordinate audit
Pinned SDK 0.13.2 AAR only. TriangleRenderMesh::makeCylinderSurface (0x1bb0ea4) generates the API-side axis-origin cylinder. The upload loop AetherMeshAsset::buildFromTwoUVTriangleRenderMesh at 0x18187e4..0x18187f8 negates vertex Z. SceneMeshComputeCombinedBounds 0x1608c28 and convertBound3D 0x2f47ab8 forward native bounds without restoring API handedness. Therefore negative bounds Z is NOT evidence that carrier compensation sign should flip. GraphicsHSR::SceneObjectSetPosition and setTransforms convert API position Z to negative native Z. Native AetherCylinderLayer::buildLayerPresentation samples the bound Aether entity object transform. No proven geometry sign correction from these readings.

## Remaining boundary
Current log is emitted immediately after submission, not after the next SDK frame. No runtime SceneObject world-pose getter exists in the pinned public class. Visible window object identity is still not established: video aperture versus transparent overlay aperture. Existing debug candidate 3 ALL_UI_MESH can isolate transparent compositor layers without another APK: user asked to switch and report whether the center drift persists. Do not change signs, offsets, shader, visibility or layer order speculatively.

## Secondary visible movement
Stage center Y currently follows currentBarOffsetY as footprint height changes: workbench anchor remains at the grab bar, stage root local Y changes by half the height delta. That explains vertical center movement structurally; whether the user wants to retain bottom anchoring is separate from the fore/aft defect.

## Device safety
Only docs, doctor, device list, app info, pid and retained logcat reads performed. No install, launch, screenshot, property write, input injection or device configuration.

## Isolation follow-up
User reports drift remains in ALL_UI_MESH and remains after input stops for 2 seconds. Retained policy log proves candidate 3 writes mesh render mode for UI while video remains Layer, but retained candidate-3 batches are flat; no curved batch is retained after that transition. Record user observation separately from this limited log coverage. It does not prove the video alone is the only possible visible surface. Next slice is a debug opt-in mesh-intersection probe with explicit miss/occlusion results, not another sign correction.

## Recovery after rejected diagnostic build
Git history 064219b/3f13a1c used explicit inverse vertex compensation; 130967e replaced it with the SDK mesh. The old curve had the wrong edge direction, so restore inverse compensation with centered r*(cos(theta)-1), not the old outward arc. Use the final SDK shape receiver values on every creation/reshape. This is a concrete recovery implementation, not proof of the whole visual root cause.

Concrete input regression: the video still registered onClick -> onStagePrimaryAction alongside AnalogMediaStageTuningSystem's Controller.isDown rising edge. Probe LineTest made the legacy path reachable. Remove that listener, keep NoCollision and clickButtons=0 in every build. setMrMode also still published video ISDK dimensions despite its retired collider ownership; remove that publication.

Latest read-only device checks: online Quest 2; no running app PID or useful retained ViriViriAperture samples. No native hit positions available. No new install/launch authorized this turn. Keep task in_progress pending headset acceptance.

## Bug analysis: duplicated stage trigger owner
- Category C/D: incomplete propagation and integration test gap. Geometric press-edge input replaced native input in intent, but the video onClick callback survived. Opt-in LineTest made the dormant owner reachable.
- Prior probe prevention (clickButtons=0) was insufficient; remove the native listener itself and retain NoCollision universally. MR/VR must not republish retired video ISDK dimensions.
- Prevention: one action owner structurally; geometry regression tests exercise generated vertices rather than submitted radius alone. Specs and component diagrams now describe explicit aperture creation from final shape parameters.
- Remaining limitation: no settled native samples or headset acceptance of this recovery slice. Previous math-only fixes and submitted logs did not prove visible alignment; do not archive this task or label all visual faults repaired.

## New headset rejection and narrow native recheck
The explicit aperture recovery d14f93f also failed: user sees the curved black window behind them on transition from flat. Never describe the +r mesh / -r carrier CPU cancellation as a proven display fix.

Pinned libMetaSpatialSDK.so JNI registration table locates TriangleMesh.nativeUpdateGeometry at 0x2ebfa44. It calls float-array Vec3 conversion 0x2ea4d00, whose loop 0x2ea4e24..0x2ea4e68 copies XYZ without negation; GraphicsHSR::TriangleMeshUpdateGeometry 0x1617f44..0x1617f6c also copies position XYZ. SceneMesh.fromTriangleMesh passes createBVH, not a coordinate toggle, to nativeCreateWithTriangleMesh. The mesh upload Z reflection remains at 0x18187ec. These paths do not yet reveal a custom-only extra Z reflection. No sign change justified by this review alone.

Read-only device discovery online; no current app PID or retained stage logs. Requested one flat-to-cylinder transition to read the existing stage diagnostics. No new device mutation or speculative runtime patch. Next inspection must compare actual mesh bounds and submitted carrier under the user's failing transition, and keep visible window identity distinct from mesh A/B/C labels.

## Direction clarified, settled ECS inspected, surface-origin correction
User clarifies direction correct and center still drifts. Retained reproduction confirms installed SHA and 292 complete parameter batches. Existing receive-only SDK Inspector at port 8011 additionally shows stage/anchor without ECS Scale override and persistent compensated child Transform values; no app mutation or new APK was needed to obtain this. Evidence captured in reproduction-analysis.md and settled-stage-inspector.json (only relevant spatial entities retained). It is not a native GPU readback.

Shared axis compensation cancellation has passed math tests repeatedly but not visual acceptance; do not claim a specific SDK bug. Current correction removes radius from the opening's translation entirely: a dynamic sibling SceneObject at the surface origin, center vertex Z=0, inward r*(cos(theta)-1) edges. Video retains existing compensated axis carrier and full-frame compositor. Its retained bounds material is OPAQUE/unlit with color mask 0 and depth write disabled, so it cannot punch a second hole or occlude the scene. NoCollision/no native click callback on either path. Same final shape data supplies both meshes; existing renderer commits both poses from one stage pose. Visibility follows video; explicit close releases the new object and mesh.

Regression prevention: mesh origin is explicit (SURFACE versus CYLINDER_AXIS), primary aperture tests compare generated centered vertices to world surface points, and flat/radius20/15 transitions no longer rely on +r-r cancellation. Exact original native-world discrepancy remains unmeasured; real headset visibility/alignment acceptance still required.
