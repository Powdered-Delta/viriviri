# Scale/curvature audit — 2026-10-21

## Confirmed defect, not a guessed sign
The user confirms whole-group similarity scaling: effective radius r0*s, carrier local Z (base-r0)*s. Current source applies scale only to width/height at PlaybackStageController.kt:125; passes unchanged parameters.curvature at :126. PlaybackStageLayerGeometry.localPose at :23 has no scale input. PlaybackStagePanelRenderer.kt:35 resets native scale to one. Input dispatch and persistence are shared/working; the missing factor is downstream geometry derivation, not absence of a state emission.

StageScaleProbe.java was compiled with JDK17 against the existing app/build/tmp/kotlin-classes/debug (actual application classes), pinned SDK runtime.jar and cached kotlin-stdlib 2.0.21. It executes the production controller and localPose with s=1,1.5,2 and r0=2.7. The retained result confirms width 1.6,2.4,3.2 while radius and video local Z remain 2.7 and -2.7. No app source or binary rebuild was done.

At s=2, target radius is 5.4 and target VIDEO/BACKDROP/DANMAKU local Z values are -5.4/-5.38/-5.42; actual values are -2.7/-2.69/-2.71. Both layer spacing and radius correction omit scale. The raw control clamp (1.5 <= r0 < 8) must not be re-applied to effective radii: r0=7.9,s=2 remains a cylinder at 15.8.

## SDK identity/shape evidence
Pinned SDK 0.13.2 runtime AAR and toolkit AAR were inspected with javap; native SO with LLVM tools. No dependency was updated.

- PanelShape constructor bytecodes 112..145 invoke config.generateSceneMeshCreator and sceneObject.setSceneMesh(mesh,"panel"). Bytes 151..161 then invoke createLayer(config,that same sceneObject,scene).
- PanelShape.createLayer bytes 276..331 compute radius=max(config.radius,width/(2*pi)), angle=width/radius, aspect=width/height, and construct SceneCylinderLayer with that SceneObject.
- PanelShapeConfig.cylinderMeshCreator$lambda$5 bytes 14..49 compute the same radius and arc fraction width/(2*pi*radius); bytes 201..212 invoke SceneMesh.Companion.cylinderSurface(radius,height,arcFraction,...).
- PanelSceneObject.setScale invokes BOTH SceneObject.setScale and PanelShape.setScale; PanelShape.setScale forwards to SceneLayer.setScale. Thus reset-to-one reaches both sides, not just mesh.
- MediaPanelRenderOptions.applyTo explicitly disables enableTransparent, forceSceneTexture and initially sets OPAQUE; app subsequently selects OPAQUE layer blend. The default generator selects HOLE_PUNCH rather than the UI's TRANSLUCENT_HOLE_PUNCH path.
- Native GraphicsHSR::createCylinderLayer at 0x15584ac receives the SceneObjectInstance; 0x155851c passes its Aether entity to the manager. AetherCylinderLayer::buildLayerPresentation at 0x17faecc uses entity transform scale and multiplies configured radius by uniform scale at 0x17fafb4.

This establishes shared application object identity and configuration. It does NOT measure post-SDK-frame mesh/layer pose equality on headset. No separate application video-cutter entity exists in the current source. Danmaku and backdrop are other panel scene objects with their own mesh/layer paths.

## User window description and unresolved visual root
User says it resembles the danmaku/transparent-area case but is a black curved window through which video must be seen; outside the window is grid-like translucent with workbench open. Record as observed appearance, not as object identity. Transparent UI geometries and eye-buffer hole-punch interactions are candidates; do not label one as proven without identification.

The confirmed scaling defect alone does not prove relative VIDEO-aperture separation: at video base Z=0, actual -r0+r0 still equals zero. A separate post-generation transform, an SDK propagation/material issue, or another panel's aperture would require distinct evidence. Never claim the root is simply missing local Z while production code already computes and submits local Z.

## Environment and next boundary
Read-only metavr doctor/device list on 2026-10-20 local evening returned Quest 2 192.168.0.234:5555 offline; no reconnect/reboot or device write was attempted. No actual runtime logs or screenshots could be obtained. User asked to review spec before further runtime modifications. Diagram and spec are updated; implementation remains unchanged.

## Additional audit and diagram verification
- The compiled-code probe also changed raw radius from 2.7 to 1.5 at scale 2: local video Z changes -2.7 to -1.5, proving the curvature correction is present. Target similarity scaling requires -5.4 to -3.0.
- SceneMesh.Companion.cylinderSurface calls nativeCreateCylinderSurfaceWithMaterial. Native GraphicsHSR wrapper 0x1608ebc forwards to 0x15dfc38; at 0x15dfc90 it invokes makeCylinderSurface with original float parameters. This follows the actual PanelShape mesh creator beyond the generic generator.
- Diagram style was explicitly selected by user as local project design-system extraction. Palette/families come from docs/prototypes/workbench/index.html. The per-artifact CSS applies that palette without modifying the installed skill defaults or persisting a global profile.
- Diagram self_check passed. Headless Edge rendered the artifact: Inter, Noto Sans SC and Geist Mono all loaded; title/node computed families match the declared local-source stack. No node text overflow, desktop page overflow or 390px mobile page overflow; both SVGs have local horizontal scrollers. Temporary browser screenshots were inspected for layout. No PNG export was delivered.
- Repository-only verify-geometry.py/lint-render.py are absent from the installed skill; connector geometry was manually checked against the skill rules (orthogonal elbows, separate ports, no non-endpoint transit).

## Implementation follow-up (2026-10-21)
The user authorized the correction after reviewing the diagram. Historical scale-probe-result.txt predates the fix. Production now derives effective curvature in buildLayout and scales surface separation in PlaybackStageLayout; localPose subtracts effective radius. Native scale stays one. The probe source now reads authored depth from the role, since layer.baseLocalZ now means effective separation. Regression tests cover the scale/radius matrix, complete-group readiness, raw preference restoration, repeated flat/curve transitions, debug depth and actual SDK rotated-parent composition. Mathematical correctness does not identify the observed black window or prove headset aperture acceptance.
