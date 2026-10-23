# Correct video visibility through the main scene buffer

## Acceptance target
The entire video rectangle is visible independently of danmaku glyphs; transparent
UI blends over video; stage shape and aperture share the SDK cylinder builder.

## Evidence
User reports video visible only through danmaku silhouettes after e9057e80307419827da5700a3706ada5b46b8ebe.
SDK 0.13.2 PanelShapeConfig.generateSceneMeshCreator returns a custom creator
before configuring layer blend/shader/alpha mode. The shadow-only override
therefore removes the video's eye-buffer aperture. Default UI Layer is MASKED,
which generates a MASKED_HOLE_PUNCH mesh. The glyph-aperture explanation is an
inference consistent with source and user observation, pending headset proof.

## Scope
- Remove both video sceneMeshCreator overrides and custom shadow mesh/helper.
- Let SDK generate video aperture and compositor cylinder from the same shape.
- Preserve measured carrier radius compensation and reshape-only aspect path.
- Use ALPHA_BLEND for transparent UI layers, retaining explicit video/backdrop/UI
  compositor order 1/2/3 through the existing startup ordering pass and reshape.
- Correct docs: parentage is a transform/visibility relationship, not offscreen
  group composition; environment uses the projection buffer, not a panel zIndex.
- No environment model modification or new static entity. No video Surface,
  player, or input ownership changes. No device mutation.

## Panel boundaries
Existing host owns videoSurfaceEntity under spatialized_video_panel, persistent
video visibility, NoCollision, and one media Surface. SDK-generated aperture is
part of that same panel, with the same shape/carrier; it has no independent state
or input owner. StageBackdrop and DanmakuOverlay remain separate transparent
panels at their authored relative depths and keep their existing visibility owners.
Subtitles have no registered runtime layer yet. No actual compositor group exists.

## Verification
Windows app tests and debug build; diff/identifier checks; SDK bytecode review.
Headset acceptance: full video with danmaku off/on; flat/curved at minimum radius;
backdrop hidden/shown with workbench; scene/passthrough; input and handoff intact.
Do not claim visual acceptance from compilation.

## Results
- Windows scripts/build-windows-debug.ps1: BUILD SUCCESSFUL in 1m 32s;
  app unit tests and assembleDebug passed. APK size 177532087 bytes;
  SHA-256 088DC49E65BFAF85184BA406FD47A492B0308CC727756D2D909F72D7E6FA1793.
- git diff --check passes; LF conventions preserved, no whole-file rewrite.
- SDK bytecode findings recorded in research/sdk-findings.md; extracted binary
  artifacts were removed after inspection.
- Removed unused inverse-shadow helpers and changed carrier regression assertions
  to concrete authored-base positions across radii and restoration to Flat.
- Layer diagnostics now include blend mode and custom-mesh override presence.
- Manual headset acceptance is pending; no APK install/launch or device mutation.
