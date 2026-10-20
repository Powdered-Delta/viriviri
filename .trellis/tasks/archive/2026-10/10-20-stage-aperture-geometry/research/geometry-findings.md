# Geometry evidence and limits (2026-10-20)

## Verified SDK 0.13.2 implementation
- PanelSceneObject.reshape(PanelShapeConfig) replaces PanelShape, preserves its
  prior scale, and reapplies texture crop. It does not recreate PanelSurface or
  PanelDisplay. DpDisplayOptions in a shape-only reshape cannot resize UI pixels.
- PanelSceneObject.setCrop calls native footprint scaling with the crop's width
  and height and applies the same UV rectangle to SceneLayer.setClip. It is not
  equivalent to changing the physical shape aspect alone.
- SDK default cylinder mesh derives radius/height/arc from PanelShapeConfig.
  Native cylinder geometry itself is opaque to javap; do not claim that bytecode
  proves its visual origin or placement on the headset.

## Sources fetched via metavr
- https://developers.meta.com/horizon/reference/spatial-sdk/v0.13.2/com_meta_spatial_runtime_crop
  explicitly documents lockstep content sampling and SPC hole-punch footprint.
- https://developers.meta.com/horizon/reference/spatial-sdk/v0.13.2/com_meta_spatial_runtime_panelsceneobject
- https://developers.meta.com/horizon/reference/spatial-sdk/v0.13.2/com_meta_spatial_runtime_scenelayer
- https://developers.meta.com/horizon/reference/spatial-sdk/v0.13.2/com_meta_spatial_toolkit_cylindershapeoptions

## Implemented decisions
- One app-state emission commits display ratio, scale, curvature before rebuilding.
- PlaybackStageGeometry is the pure, host-owned group contract. Full-stage
  viewport belongs to danmaku/backdrop (option A); visible videoContent, pre-crop
  videoShape and videoCrop are separate values.
- A portrait 9:16 source inside the 16:9 FIT buffer occupies horizontal UV
  [0.341796875, 0.658203125]. At scale 1 its final footprint is 0.50625 x 0.9 m,
  while danmaku remains 1.6 x 0.9 m with a matching fixed 1280 x 720 dp raster.
- Native shape scale is reset to unit to avoid preserved-scale multiplication.
- Carrier ECS local Z remains authored base minus radius, with a fresh Pose
  component write. The same native PanelSceneObject also receives resolved world
  position/rotation after create/reshape, even if its ECS local Z already matched.
  This closes a missing explicit native synchronization boundary; it does not
  constitute headset proof that the aperture now stays pinned.
- Aspect debug Apply changes app state first; it must not pre-mark the local
  applied target and thereby suppress the state-driven rebuild.

## Remaining headset acceptance
Video with no buffer black bars in Cozy; shared scaling; full-width danmaku with
undistorted glyphs; aperture Z at several radii and flat; grab/move after reshape;
workbench dim visibility and Surface handoff. No device mutation was authorized.
