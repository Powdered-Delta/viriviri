# Integrate Immersive Danmaku Layer

## Goal

Render Bilibili XML danmaku above the existing immersive video panel while preserving one Media3 player and one active video output Surface.

## Current Evidence

- `spatial-workbench-core` already contains pure danmaku topology, lane, style, and allocator contracts.
- The app currently has no Bilibili XML danmaku parser, no retained `cid`, and no runtime renderer.
- Meta Spatial SDK samples provide `ComposeViewPanelRegistration` for transparent dynamic UI panels, but the app does not yet enable the required `ComposeFeature`.
- A danmaku UI panel can be a dynamic, non-video, non-hittable child of the existing video panel. It must not own Media3 output or create a video Surface.

## Planned Scope

1. Preserve the resolved playback `cid` and load the public Bilibili XML danmaku source with cancellation on video change.
2. Parse ordinary scrolling, top-fixed, and bottom-fixed XML comments into existing core `DanmakuEvent` values.
3. Enable the documented Spatial Compose feature and register one transparent, non-interactive dynamic overlay panel.
4. Drive a bounded Canvas renderer from shared Media3 position; sync its shape with the existing `PanelSceneObject.reshape(...)` aspect path.
5. Add an existing mode-panel toggle for danmaku visibility. Do not add a video output, player, or Surface.

## Non-goals

- Do not add credentials, posting, moderation, live chat, historical segmented danmaku, or a second video panel.
- Do not intercept video stage input, reparent authored panels, or change scene-authored transforms.
- Do not claim the renderer is usable until Quest validation confirms text visibility, overlay depth, no input interception, and correct timing.

## Acceptance Criteria

- [ ] A selected video loads cancellable XML events associated with its resolved cid.
- [ ] Parser tests cover timing, XML escaping, supported modes, and malformed entries.
- [ ] A transparent dynamic overlay panel displays bounded active events without owning a video Surface.
- [ ] Overlay shape follows source/forced display ratio and does not intercept stage click.
- [ ] Playback, seek, pause, quality change, and 2D handoff keep one player and one active video output Surface.
- [ ] Windows JDK 17 tests and debug build pass.
- [ ] Quest validation verifies visibility, timing, seek cleanup, aspect alignment, and input isolation.
