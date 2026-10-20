# Shared stage geometry and independent full-stage danmaku

## Revised acceptance (user headset feedback and group-state clarification)
- One immutable geometry snapshot drives video/aperture, backdrop and danmaku.
- One app-state emission applies ratio, scale and curvature together; child
  panels do not independently observe or derive tuning parameters.
- Video/aperture show only actual decoded pixels, excluding FIT buffer bars.
- Danmaku uses option A: full 16:9 stage viewport, independent of source ratio;
  its fixed raster and physical aspect remain matched, preserving glyph shape.
- All geometry consumes the same stage scale and curvature. Carrier local Z is
  authoredBase - radius for cylinders, authoredBase for flat; explicitly sync
  the resulting native PanelSceneObject pose when creating/reshaping its layer.
- Subtitles remain a documented future consumer, not a fake runtime entity.

## Evidence / implementation direction
SDK 0.13.2 reshape(PanelShapeConfig) does NOT resize PanelSurface/PanelDisplay;
it preserves previous PanelShape scale. This explains why changing a UI raster's
DpDisplayOptions during reshape cannot fix glyph anisotropy.
Fixed 1920x1080 Media3 FIT output contains pillarbox/letterbox bars. Current code
narrows the whole buffer again and still opens the entire narrowed rectangle.
SDK Crop is documented to crop texture and SPC aperture footprint in lockstep.
Use the normalized decoded-content crop plus pre-crop physical shape so the
resulting visible footprint equals the aspect-fitted video rectangle.

## Ownership
Existing app-state/input pipeline owns parameters. Host builds PlaybackStageGeometry
and applies it to existing videoSurfaceEntity (one Surface, NoCollision) under
spatialized_video_panel, StageBackdrop and DanmakuOverlay. Video and SDK aperture
share one panel/shape/crop. Backdrop/danmaku use full stage, separate authored Z
and existing visibility owners. No new static scene objects or device writes.

## Validation
Pure geometry regression tests: portrait/landscape/ultrawide, crop black bars,
round-trip final footprint, stage scaling, full-stage danmaku, finite fallback,
radius changes and flat restoration. Then Windows debug build/app tests once.
Headset visual acceptance remains separate; do not claim it from a build.

## Results
- Implemented immutable geometry snapshot and one atomic app-state tuning entry.
- SDK Crop now samples decoded content and shrinks video aperture together;
  pre-crop physical dimensions preserve the desired final visible footprint.
- Danmaku/backdrop use full-stage viewport, matching fixed 16:9 raster (option A).
- Unit native scale reset and explicit ECS/native world pose commit on reshape.
- Fixed debug Apply pre-marking local state, which could suppress the rebuild.
- Windows build-windows-debug.ps1 passed (1m 18s): app unit tests and assembleDebug.
- APK SHA-256: 64121B39C6A016A1C9B1249A6FA9BB810C07BC44DCE216FB2B85098C738F7027.
- git diff --check and added terminology identifier check passed; LF preserved.
- No install, launch, screenshot or other device writes. metavr doctor only.
- Remaining: the four reported symptoms must be rechecked on the headset;
  native visual geometry is not established by the passing JVM tests/build.
