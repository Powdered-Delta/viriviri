# Existing Host Boundaries

## Current Player Ownership

`ViriViriAppState.PlayerSession` owns the process-wide `ExoPlayer` and retains
only one current Android `Surface`. `attachImmersiveSurface()` and
`attach2dSurface()` delegate to one identity-aware attach operation; an old
surface cannot detach a newer one. This remains the platform media owner.

## Current Hosts

- `PancakeActivity` renders `RecommendationContent(..., showPlayer = true)`.
  The legacy `PlayerOutput` creates a `TextureView` and hands its Android
  `Surface` to `PlayerSession`.
- `SpatialVideoSampleActivity.createVideoPanel()` creates a Meta
  `PanelSceneObject` for `spatialized_video_panel` and hands its SDK-owned
  surface to `PlayerSession`.
- The existing scene maps `spatialized_video_panel` to semantic `MEDIA_STAGE`.
  It is the only current Spatial video output, not an overlay target.

## Consequence

A unified MediaStage cannot own a platform Surface. The pure core owns semantic
target IDs, desired lifecycle, clock snapshots, and effects. The 2D/Spatial host
adapters own their corresponding platform handles and perform returned effects.

## Product Boundary

The current Pancake UI and Spatial panel are legacy hosts. This task must not
place danmaku or captions in either. A future redesigned 2D host will register
a flat video target plus flat overlay targets; a future Spatial renderer will
register its video target plus Spatial overlay targets. Both consume the same
contract and retain the one-player/one-active-video-output invariant.
