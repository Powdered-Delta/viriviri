# Spatial Foreground/Backdrop Design

Quest validation showed the previous contained-content calculation did not
change visible geometry. Inspection of Meta Spatial SDK 0.13.2 shows
`TriangleMesh.updateGeometry()` updates the TriangleMesh native object while
`SceneMesh.updateWithTriangleMesh(triangleMesh, ...)` is the explicit scene-mesh
commit operation. The current implementation omitted that latter call.

This task uses one existing `PanelSceneObject`, one existing `SceneMesh`, one
existing media texture, and one existing Surface. The custom triangle mesh gains
separate primitives/material ranges:

```text
black backdrop (front + back) -> fixed full 16:9 stage, translucent black
video foreground (front + back) -> centered contain quad using media texture
existing shadow -> unchanged full-stage footprint
```

The background is a `SceneMaterial` with `SceneMaterial.UNLIT_SHADER`,
translucent alpha/blend mode, and an albedo `Color.argb(192, 0, 0, 0)`. It has
no video texture and is rendered behind the front content by geometry depth.
The media texture remains assigned only to the foreground material. This does
not create another video target.

The `VideoSize` callback rebuilds geometry on the already owned TriangleMesh
and commits it to the already rendered SceneMesh with
`updateWithTriangleMesh(triangleMesh, false)`. Invalid size retains full-stage
foreground geometry.

For build traceability, Gradle obtains `git rev-parse --short=8 HEAD` once at
configuration time with a safe `nogit` fallback. `BuildConfig.GIT_SHA` is shown
in the already registered `mode_panel` only when `BuildConfig.DEBUG` is true.
