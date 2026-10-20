# SDK 0.13.2 visibility evidence (2026-10-20)

Inspected installed Gradle AARs using JDK 17 javap, rather than inferring from
newer documentation or the names of render options.

## PanelShapeConfig.generateSceneMeshCreator

Bytecode offsets 0..15: if getSceneMeshCreator() is non-null, return it directly.
Offsets 49..302 (only on the default path): a non-null LayerConfig prepares
enableLayer and the alpha mode/shader by blend type:
- OPAQUE, no feathering -> AlphaMode.HOLE_PUNCH, HOLE_PUNCH_SHADER.
- MASKED -> forceSceneTexture true, MASKED_HOLE_PUNCH, panel alpha shader.
- ALPHA_BLEND -> forceSceneTexture true, TRANSLUCENT_HOLE_PUNCH, panel alpha shader.
Offsets 302 onward select quad/cylinder/equirect geometry using panelShapeType.

PanelShape constructor calls this generator, creates the SceneMesh, then creates
the SceneLayer from the SAME config. A custom shadow creator bypasses the
aperture setup but does not bypass layer creation. This proves a configuration
regression in 5671cbb. The user's exact glyph-only output remains a device-level
observation, with the remaining MASKED UI apertures a strongly supported cause.

## UIPanelRenderOptions / MediaPanelRenderOptions

UI defaults to PanelRenderMode.Layer(layerBlendType=MASKED). applyTo writes
LayerConfig plus the layerBlendType. ALPHA_BLEND is explicitly supported.
Media applyTo writes forceSceneTexture=false, enableTransparent=false,
alphaMode=OPAQUE, and a LayerConfig using its zIndex. It still relies on the
default mesh generator for the scene aperture.

## Layers and grouping

LayerConfig zIndex controls relative layer order; equal values sort by distance
to entity origin. This does not create a render group from TransformParent.
The environment and skybox are rendered to the projection/eye buffer. The
compositor also receives video and UI textures. Their scene apertures integrate
these paths; no environment model is cut or modified.

The publicly exposed LayerConfig has alphaBlend, colorScaleBias, zIndex, secure,
and filters; no offscreen child-group compositor or main-projection placement
switch is exposed here. Do not promise that positive panel zIndex puts the
panel above every pixel of the main scene regardless of its aperture.

## Authoritative docs fetched via metavr

- https://developers.meta.com/horizon/documentation/spatial-sdk/os-compositor-layers
- https://developers.meta.com/horizon/documentation/spatial-sdk/spatial-sdk-blend-modes
- https://developers.meta.com/horizon/reference/spatial-sdk/v0.13.2/com_meta_spatial_runtime_layerconfig

The compositor docs distinguish the main projection buffer from additional
directly composed textures. The blending docs confirm MASKED writes depth
only at passing alpha samples; ALPHA_BLEND does not write depth. Exact global
eye-buffer placement is not specified by the LayerConfig zIndex reference.

## Remaining acceptance

Build/unit tests cannot prove native material/aperture output or headset visual
alignment. Test full video with danmaku absent/present, flat/cylinder and radius
changes, workbench dim visibility, environment/passthrough, and stage input.
