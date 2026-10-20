# SDK 0.13.2 coordinate and aperture contract (2026-10-20)

## Reproducible artifacts

Runtime AAR resolved from the project's Gradle dependency cache, version 0.13.2.
libMetaSpatialSDK.so SHA-256:
44a1ecc1a9a30324f22e681d25b6548c91701736d257f8762df1b8b8ce485e19.
JDK 17 javap/javac/java and Android NDK 29 llvm-nm/llvm-objdump used locally.
CoordinateProbe.java is retained next to this report; extracted JAR/SO/classes
are temporary inspection artifacts and excluded from the final task record.

## Actual SDK math execution (not an inferred rendering convention)

```text
identity.forward=(0.0, 0.0, 1.0)
identity.right=(1.0, 0.0, 0.0)
Vector3.Forward=(0.0, 0.0, 1.0)
Vector3.Right=(1.0, 0.0, 0.0)
Vector3.Up=(0.0, 1.0, 0.0)
yaw+90.forward=(0.99999994, 0.0, 0.0)
```

Project API forward is +Z. The architecture doc's -Z-forward statement must
not be applied to this Kotlin math as if they were identical spaces.

## Native boundary evidence

1. GraphicsHSR::SceneObjectSetPosition starts at 0x160f2ac. Instructions
   0x160f350..0x160f378 reflect position Z and quaternion X/Y before forwarding
   to the native entity transform. This corresponds to F=diag(1,1,-1),
   pNative=F*pApi, RNative=F*RApi*F. No extra reflection belongs in app call sites.
2. AetherMeshAsset::buildFromCompactTriangleRenderMesh at 0x1817a98 uploads
   positions: 0x1817d68 loads vertex Z, 0x1817d70 negates it, 0x1817d7c stores
   the reflected GPU position. Normals' Z is likewise negated at 0x1817dac.
3. TriangleRenderMesh::makeCylinderSurface at 0x1bb0ea4 generates API mesh
   positions. 0x1bb1108..0x1bb1188 computes angle
   (0.75 + (u-0.5)*arcFraction)*2pi and sincos; 0x1bb11a4..0x1bb11d0 stores
   x=r*cos(angle), z=-r*sin(angle), y=height*(0.5-v). Equivalent centered
   formula: x=r*sin(theta), z=r*cos(theta), theta=(u-0.5)*width/r.
   At u=0.5 the API surface point is (0,y,+r), uploaded as native (0,y,-r).
4. AetherCylinderLayer::buildLayerPresentation at 0x17faecc reads native entity
   scale. 0x17faf44..0x17faf58 checks all three scale axes for equality;
   0x17fafa4..0x17fafbc multiplies configured radius by scale X and copies angle
   and aspect. Its diagnostic string at VA 0x80f00b is:
   "AetherCylinderLayer: scale must be uniform for cylinder layer".

Thus base-r is justified by the verified axis-origin cylinder, not by a guess
about generic OpenXR poses. The compensated API central point is base; in native
space (-base+r)+(-r)=-base. Stage rotation must be composed after local correction.

## Aperture method audit

Current creation and reshape both use videoSurfaceEntity and the same
PanelSceneObject. PanelShape constructs its SceneMesh and SceneLayer from that
object. Both paths invoke applyStageLayerCurvatureOffset, which delegates to
PlaybackStageCurvature.entityLocalZForSurface. Therefore a missing invocation
for the current SDK aperture is NOT established by the source.

The prior implementation had another transform path: PanelSceneObject.setCrop
calls SceneObject.setFootprintScale(crop.width,crop.height,1), then applies UV
clip to the layer. GraphicsHSR::SceneObjectSetFootprintScale at 0x160f6e8 writes
the separate SceneObjectInstance footprint field (+0x5c..+0x64), whereas cylinder
radius/angle/aspect are submitted through AetherCylinderLayer using entity scale.
A general Crop API description cannot prove these spatial paths are equivalent,
especially for cylindrical geometry with a uniform-scale requirement.

## Patch boundaries

- One final videoContent/videoShape is passed to the shared videoPanelConfig
  for creation, reshape and diagnostics; no pre-division by texture crop fractions.
- clearCrop restores the whole-panel footprint, native scale is unit, and only
  SceneLayer.setClip samples the decoded-content rectangle. The mask remains the
  same full final physical shape as the compositor layer. This removes the
  independent spatial scale path without adding another guessed Z compensation.
- Existing base-r carrier method and full-stage danmaku option A stay intact.
- DEBUG logs include sharedCarrier, submitted world Z, expected surface-center
  stage Z, actual SDK mesh bounds and panel shape. These distinguish data/transform
  evidence from final headset pixels; submittedWorldZ is not a measured pixel pose.

## Source docs and limitations

Metavr fetched/searched official Spatial SDK architecture/editor/panel/layer docs.
Editor transforms confirms child local coordinates and view-origin dependency:
https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-editor-transforms-coordinates
Layer clip is a swapchain source UV rectangle:
https://developers.meta.com/horizon/reference/spatial-sdk/v0.13.2/com_meta_spatial_runtime_scenelayer

Read-only discovery found online Quest 2 at 192.168.0.234:5555. Related logcat
tag queries returned no retained lines; app metadata could be read. No install,
launch, property write, screenshot, or device mutation ran. Final viewport size,
occlusion and visual Z acceptance still require a headset run of this patch.
