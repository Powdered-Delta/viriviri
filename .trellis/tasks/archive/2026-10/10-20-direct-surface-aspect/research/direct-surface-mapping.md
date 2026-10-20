# Direct Surface versus imagined FIT canvas

## Evidence
- User reports 4:3 physical mesh but image contents stretched toward 16:9 after the crop patch.
- app/build.gradle.kts pins Media3 1.4.1. Its ExoPlayer.java docs explicitly scope videoScalingMode applicability to SurfaceView-owned outputs. The app instead attaches SDK PanelSceneObject.surface directly; no PlayerView/AspectRatioFrameLayout draws a letterboxed intermediate frame.
  https://raw.githubusercontent.com/androidx/media/1.4.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java (setVideoScalingMode, lines 1628-1636)
- Android MediaCodec SCALE_TO_FIT is documented as scaling to surface dimensions. The Surface needs the final display aspect; fixed allocation dimensions do not establish a black-bar producer contract.
  https://developer.android.com/reference/android/media/MediaCodec#VIDEO_SCALING_MODE_SCALE_TO_FIT
- Meta v0.13.2 SceneLayer.setClip selects a swapchain UV subregion. Re-fetch via metavr confirmed this; the error is the assumed region, not evidence that this API changes physical curvature.
  https://developers.meta.com/horizon/reference/spatial-sdk/v0.13.2/com_meta_spatial_runtime_scenelayer
- Pinned local runtime.jar bytecode: PanelSceneObject.clearCrop unconditionally calls setCrop(Crop.NONE); setCrop writes footprint width/height and invokes applyTextureCrop, forwarding minUV/maxUV to SceneLayer.setClip. Thus Crop.NONE resets previous direct clip and unit footprint. Actual SDK Crop.NONE bounds are checked by JVM tests.

## Failed regression before repair
Ran Windows JDK17 Gradle testDebugUnitTest selecting PlaybackStageGeometryTest.fourByThreeMustSampleFullDecodedFrameWithoutSyntheticBufferBars, against the old implementation plus that regression. Result: 1 test, 1 assertion failure at line 14; BUILD FAILED (1m17s).
Old calculation: source/output=(4/3)/(16/9)=.75, minU=.125, maxU=.875; required full frame width=1. This introduces horizontal magnification 1/.75=4/3. Physical mesh already has aspect 4:3, so this is a second content transformation. Matches the symptom, but no headset pixel capture was taken.

## Repair
Delete StageVideoCrop and inferred black-bar arithmetic. Geometry exposes SDK Crop.NONE; renderer applies that exact value to the existing panel. Keep fixed pixel configuration, full-source/target physical shape, group controller, full-stage danmaku and verified base-radius anchoring.

## Device boundary
Doctor and device list checked read-only; Quest 2 online. ViriViriStage and ViriViriAspect retained-log queries returned no lines. No installation, launch, screenshot or device write. Final pixel acceptance remains a headset task.
