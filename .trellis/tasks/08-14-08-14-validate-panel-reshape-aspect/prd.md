# Validate Runtime Panel Reshape for Immersive Video Aspect

## Goal

Use Meta Spatial SDK's `PanelSceneObject.reshape(PanelConfigOptions)` as the default source-aspect correction for the existing immersive MediaPanel while preserving the one-player, one-output-Surface contract. Retain the debug override workflow for future manual aspect selection.

## Evidence

Quest validation of the prior mesh-only probe found:

- `TriangleMesh.updateGeometry(...)` kept video visible but did not visibly change the displayed aspect ratio.
- `SceneMesh.updateWithTriangleMesh(mesh, false)` caused the video picture to disappear after the first launch.
- No tested `Plan 1/2/3` produced an effective aspect-ratio correction.
- Quest validation of `9:16 + Panel reshape` kept the video visible and corrected the displayed aspect ratio.

The Meta `media_view` sample changes a live panel configuration through `PanelSceneObject.reshape(...)`, rather than replacing a `SceneMesh`.

## Scope

- Keep the existing `spatialized_video_panel`, its scene-authored parentage/transform, one process-wide ExoPlayer, and one active SDK-owned video Surface.
- Use `Default + Panel reshape` as the default source-aspect correction.
- Remove unsafe `Plan 2` and `Plan 3` choices.
- Retain `Plan 1` and `Panel reshape` as debug-only override choices; selected targets apply only after `Apply`.
- Use `MediaPanelSettings` with the existing fixed pixel display and existing stereo rendering options to create the new `PanelConfigOptions` for the same `PanelSceneObject`.
- Record the target shape and plan in the existing bounded `ViriViriAspect` diagnostic.

## Non-goals

- Do not create a second panel, player, Surface, or runtime entity.
- Do not modify Media3 stream selection, decoder output, VideoSize, source URL, player scaling, or the 2D TextureView path.
- Do not modify Meta Spatial Editor scene content, parentage, or fixed transforms.
- Do not expose the debug override as a release UI control in this task.

## Acceptance Criteria

- [x] `Default + Panel reshape` is the source-aspect correction default.
- [x] `Panel reshape` remains an explicit debug apply-only override; Plan 2 and Plan 3 are unavailable.
- [x] A reshape reconfigures the existing `PanelSceneObject` and preserves its fixed pixel display and rendering configuration.
- [x] No code creates/replaces a player, Surface, panel entity, or 2D output during a reshape.
- [x] Pure Kotlin probe state tests cover the available plans and explicit Apply boundary.
- [x] Windows JDK 17 app unit tests and debug build pass.
- [x] Quest validation confirmed `9:16 + Panel reshape` keeps video visible and corrects the observed ratio.
