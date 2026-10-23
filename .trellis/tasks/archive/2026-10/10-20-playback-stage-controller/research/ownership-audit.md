# Stage ownership audit and refactor boundary

The user re-tested 01426d33e407bc4062a6015e6a758097540cd6b4 and rejected headset acceptance. Earlier SDK coordinate evidence is retained in the archived verify-cylinder-coordinate-contract task; this task does not reinterpret its sign or claim a new headset measurement.

## Direct call-chain findings
- Activity app-state emission updates scale/curvature/ratio; Media3 listener updates source dimensions.
- Before refactor: separate reshapeSpatialVideoPanel, applyOverlayStageShape, overlay pending replay, one-shot video replay, creation-time poses, debug depth and reset pose-only writes. Several distinct applied/pending guards could acknowledge different portions of a stage update.
- After refactor: controller retains newest inputs/layout and a pending revision. One frame system resolves the complete existing panel set; one renderer writes shapes/UV/unit scale and absolute local/native poses. Panel identity replacement also requires replay. Existing SDK/Media3 components remain.
- Renderer rebuilds identity local child rotations and samples the stage world pose after bar-spacing adjustment, then composes all child native poses from the same parent.
- Parent hierarchy, static scene, visibility/input owners, layer ordering and media Surface are preserved. No new entity or video target was created.

## Authoritative verification
metavr docs search "Spatial SDK PanelSceneObject reshape TransformParent cylinder panel lifecycle" returned pinned v0.13.2 PanelSceneObject/PanelCreator docs, confirming shape/display/input ownership and delayed panel-creation system:
https://developers.meta.com/vr/reference/spatial-sdk/v0.13.2/com_meta_spatial_runtime_panelsceneobject
https://developers.meta.com/vr/reference/spatial-sdk/v0.13.2/com_meta_spatial_toolkit_panelcreator
No new SDK API is introduced; actual existing signatures are checked by Windows compilation. The first compile caught nullable PanelSceneObject.entity and the renderer now preflights the whole entity set.

## Verification limits
Synchronous host batch submission is not a compositor transaction guarantee. Fake panel scheduling tests establish readiness/revision behavior; actual SDK math tests establish coordinate composition. Neither establishes headset viewport extent or visibility. No device install/launch/mutation was authorized or performed.
