# 修复视频网格可见性与重置偏角

## Problem

The `d8c4bd53` Quest build retains stage hit testing but renders no video image.
The regression began when `SceneMesh.updateWithTriangleMesh(mesh, false)` was
added to the existing adaptive `TriangleMesh` update path. The earlier
`b252fc8` device build retained visible video, although its portrait contain
geometry did not visually correct the raw Spatial texture stretch.

The same device test reports an angular offset after the Horizon OS long-press
orientation reset. Existing project evidence says application code must not
apply a second LOCAL_FLOOR yaw compensation: the system reset changes reference
space and manual compensation compounds the offset.

## Scope

- Restore the known-visible video path by removing the unsupported runtime
  `SceneMesh.updateWithTriangleMesh(...)` call and its retained SceneMesh field.
- Retain the one custom video mesh, one adaptive TriangleMesh calculation, one
  Player, and one SDK-owned video Surface.
- Change the debug aspect event to state only source metadata and
  `triangleMeshUpdated=true`; it must not claim a compositor/GPU geometry commit.
- Add bounded debug lifecycle state logs for scene reference-space/view origin
  initialization, VR-ready panel transform, and session state. Do not log per
  frame and do not poll or mutate transforms.
- Preserve the current reference-space contract: configure LOCAL_FLOOR and view
  origin only in `onSceneReady`; do not write panel transform/scale/yaw in
  `setMrMode`, resume, session callbacks, or any reset path.
- Preserve the debug wrist panel unchanged; it is out of scope other than
  ensuring it does not own or affect the video path.

## Non-Goals

- Do not attempt another portrait UV/shader/compositor fix in this task.
- Do not flip the video mesh, add a second mesh/panel/video Surface, change
  Player scaling, re-create the player, or use scene transforms as a video
  aspect workaround.
- Do not create an application-level long-press reset handler or yaw
  compensation without an SDK reset event and pose evidence.

## Device Acceptance

1. Confirm the `DEV <hash>` label after installation.
2. Verify the video becomes visible and stage clicks still control playback.
3. For a portrait BV, capture `ViriViriAspect`; it should report source metadata
   and `triangleMeshUpdated=true`, not `meshCommit=true`.
4. Long-press reset orientation while observing `ViriViriSpatial` logs. Record
   build hash, session states, view-origin initialization, and VR-ready video
   panel pose. No app log may show a transform rewrite after initialization.
5. Recheck 16:9 playback, transport, browse, output handoff, and wrist panel.

## Verification

Run core/compose/app tests and `:app:assembleDebug`. Manual Quest validation is
required before changing material/UV or reference-space behavior again.
