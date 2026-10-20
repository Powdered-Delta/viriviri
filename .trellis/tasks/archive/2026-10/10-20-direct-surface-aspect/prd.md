# Correct direct decoder Surface sampling for non-16:9 sources

## Acceptance
AUTO 4:3 source uses a 4:3 physical video/aperture shape and full decoded-frame sampling, without horizontal enlargement. Portrait/ultrawide and non-square pixels use their display aspect; danmaku remains full stage. Preserve player/Surface, controller and verified curvature/Z math.

## Verified correction
Android MediaCodec SCALE_TO_FIT scales content to the Surface dimensions, not a guaranteed aspect-preserving letterbox canvas. Media3 1.4.1 ExoPlayer.java explicitly scopes videoScalingMode behavior to SurfaceView-owned surfaces. The SDK-owned Surface is attached directly. The previous sourceAspect/outputAspect synthetic crop has no producer contract. For 4:3, it selects U=.125..875 and magnifies horizontal content by 4/3, matching the reported symptom.

## Patch
Remove synthetic FIT crop arithmetic/model. Use actual SDK Crop.NONE for full frame and unit footprint on each video reshape. Physical shape alone owns source/target aspect. Correct previous documentation claims and retain the now-centralized geometry controller.

## Verification
First run a regression against old synthetic crop and record failure; then run Windows full tests/debug build. Verify pinned SDK clearCrop/setCrop bytecode and actual Crop.NONE bounds. Do not deploy or launch headset. Visual acceptance remains pending.

## Verification result
- Regression against prior implementation: 1 test, 1 failure (4:3 full frame width expected 1, synthetic crop .75).
- Final Windows scripts/build-windows-debug.ps1: BUILD SUCCESSFUL; 202 tests, 0 failures/errors; assembleDebug passed.
- SDK Crop.NONE bounds and unchanged full-stage danmaku verified by JVM tests. Current source/doc identifiers and git diff --check passed.
- APK DEV/build SHA 47814203; SHA-256 F457DE683DF1E4C6BB722AD8DEF402FA28ABF5D9ACAC6B579E11FFA0165ECCA6.
- No install/launch/screenshot/device mutation; headset pixel acceptance remains pending.
