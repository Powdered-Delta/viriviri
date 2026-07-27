# PoC: In-Activity Media3 Surface Handoff

## Goal

Validate that one Media3 ExoPlayer can move its video output between a compact
2D TextureView and a full-panel TextureView in the same PanelActivity without
loading media again or recreating the player.

## Requirements

* Keep exactly one ExoPlayer instance in the Meta application layer.
* Load and prepare one public test video once when the PanelActivity starts.
* Switch the output Surface when the user changes between compact and cinema
  layouts.
* Avoid clearing a newer Surface when a disposed TextureView reports its late
  destruction callback.
* Show useful PoC metrics: prepare calls, video decoder initializations,
  handoffs, current position, and last handoff latency.
* Keep `:ui-compose` free of Meta Spatial SDK and Media3 player implementation
  types.

## Acceptance Criteria

* [x] Repeated layout changes do not call `setMediaItem` or `prepare` again.
* [x] The player position is sampled continuously through a Surface handoff.
* [x] Metrics expose video decoder initialization count for device verification.
* [x] The Kotlin sources compile when existing build-gate failures are skipped.

## Out of Scope

* PanelActivity to ImmersiveActivity handoff.
* Meta Spatial SDK or OpenXR Surface integration.
* Bilibili parsing, DASH streams, danmaku, MediaSession, or foreground service.

## Technical Approach

`PlayerManager` owns the ExoPlayer and explicitly attaches/detaches individual
Surface instances. Each TextureView delegates lifecycle changes to the manager.
A Compose control switches between two target layouts in the same Activity and
renders metrics emitted from the player manager.

## Technical Notes

* `app-meta` is the platform host and owns the Media3 implementation.
* `ui-compose` remains reusable and receives platform rendering content as a
  composable parameter.
* The Surface handoff validates Android Surface-to-Surface transfer only; it
  does not prove a future Spatial/OpenXR renderer can expose a MediaCodec
  compatible Surface.
* Full APK assembly remains blocked by pre-existing build configuration:
  AGP 8.5/compileSdk 35 are incompatible with the declared AndroidX versions,
  and the Meta KSP processor reports no provider. This PoC does not alter that
  separate dependency-upgrade scope.
