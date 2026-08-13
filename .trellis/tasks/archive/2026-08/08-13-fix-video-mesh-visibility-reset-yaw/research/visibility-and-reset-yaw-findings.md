# Video Visibility And Reset-Yaw Findings

## Video visibility regression

The prior `b252fc8` path updated the retained `TriangleMesh` vertices and returned
`SceneMesh.fromTriangleMesh(...)` from the panel creator. It did not call
`SceneMesh.updateWithTriangleMesh` after later vertex updates, and the device
still showed the video image (with the known aspect defect).

The `d8c4bd53` path added a retained `SceneMesh` plus
`updateWithTriangleMesh(mesh, false)` after `TriangleMesh.updateGeometry`. The
reported Quest result retained the panel's hit testing but showed no video
image. There is no public SDK evidence in the repository or available search
result that establishes the boolean parameter's runtime semantics. The current
fix therefore removes the runtime update call and retains the known-visible
TriangleMesh update path. It does not claim portrait aspect is solved.

## Reset-angle investigation

Project records state that the app must not apply a second yaw compensation after
Horizon OS changes `LOCAL_FLOOR` during a long-press orientation reset or a 2D /
immersive route. Current code calls `scene.setReferenceSpace(LOCAL_FLOOR)` and
`scene.setViewOrigin(0, 0, 0, 0)` only from `onSceneReady`; `setMrMode`, resume,
and session callbacks do not rewrite panel transforms. The official checked-in
SpatialVideoSample and other SDK samples also use this zero view-origin setup.

A `0, 0, 2, 180` value appears in a project runbook example, but no historical
implementation commit or device evidence establishes it as the prior fix. It
must not be introduced as a guess. The current debug build logs
`ViriViriSpatial` at view-origin initialization, VR-ready video pose, and each
Spatial session state. Quest evidence should compare those events with the
system long-press reset before any application yaw change.

The wrist panel remains a debug-only dynamic test object and is unrelated to
both failures.
