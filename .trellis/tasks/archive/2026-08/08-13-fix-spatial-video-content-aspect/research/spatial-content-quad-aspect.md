# Spatial Content Quad Aspect Investigation

The previous `1920x1080` mono buffer and Media3 `SCALE_TO_FIT` correction
removed the inherited 3.55:1 stereo sample mismatch, but Quest validation shows
portrait content still stretches. The current custom `sceneMeshCreator` maps
all four front-video vertices to the fixed 16:9 mesh bounds. A Surface buffer
scaling policy cannot change that 3D geometry.

The correct boundary is an existing `TriangleMesh.updateGeometry()` call on the
front content quad when the existing shared player reports `VideoSize`.

For stage dimensions `W`/`H`, source display ratio
`R = videoWidth * pixelWidthHeightRatio / videoHeight`, and stage ratio
`S = W / H`:

```text
if R >= S: contentWidth = W; contentHeight = W / R
else:      contentHeight = H; contentWidth = H * R
```

The resulting content quad is centered at the existing stage origin. The
shadow geometry and the Spatial entity/panel dimensions remain full-stage,
which preserves existing input and transport overlay parent behavior.

A valid source update is event-driven (`Player.Listener.onVideoSizeChanged`),
not per-frame. Invalid dimensions preserve full-stage geometry until a usable
size arrives. No mesh recreation, Surface attach, player action, source reload,
or scene reparenting is involved.
