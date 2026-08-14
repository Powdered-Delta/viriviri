# Validate Runtime Panel Reshape for Immersive Video Aspect

## Goal

Validate whether Meta Spatial SDK's `PanelSceneObject.reshape(PanelConfigOptions)` can apply a selected contain aspect ratio to the existing immersive MediaPanel while preserving the one-player, one-output-Surface contract.

## Evidence

Quest validation of the prior mesh-only probe found:

- `TriangleMesh.updateGeometry(...)` kept video visible but did not visibly change the displayed aspect ratio.
- `SceneMesh.updateWithTriangleMesh(mesh, false)` caused the video picture to disappear after the first launch.
- No tested `Plan 1/2/3` produced an effective aspect-ratio correction.

The Meta `media_view` sample changes a live panel configuration through `PanelSceneObject.reshape(...)`, rather than replacing a `SceneMesh`.

## Scope

- Keep the existing `spatialized_video_panel`, its scene-authored parentage/transform, one process-wide ExoPlayer, and one active SDK-owned video Surface.
- Retain `Plan 1` as the known-visible mesh baseline.
- Remove unsafe `Plan 2` and `Plan 3` choices.
- Add a debug-only `Panel reshape` plan that applies the selected target ratio only after `Apply`.
- Use `MediaPanelSettings` with the existing fixed pixel display and existing stereo rendering options to create the new `PanelConfigOptions` for the same `PanelSceneObject`.
- Record the target shape and plan in the existing bounded `ViriViriAspect` diagnostic.

## Non-goals

- Do not create a second panel, player, Surface, or runtime entity.
- Do not modify Media3 stream selection, decoder output, VideoSize, source URL, player scaling, or the 2D TextureView path.
- Do not modify Meta Spatial Editor scene content, parentage, or fixed transforms.
- Do not claim that reshape is a production fix until Quest evidence confirms both video visibility and the observed aspect ratio.

## Acceptance Criteria

- [ ] Existing default is `Default + Plan 1` and remains known-visible.
- [ ] `Panel reshape` is available as an explicit apply-only probe; Plan 2 and Plan 3 are unavailable.
- [ ] A reshape reconfigures the existing `PanelSceneObject` and preserves its fixed pixel display and rendering configuration.
- [ ] No code creates/replaces a player, Surface, panel entity, or 2D output during a reshape.
- [ ] Pure Kotlin probe state tests cover the available plans and explicit Apply boundary.
- [ ] Windows JDK 17 app unit tests and debug build pass.
- [ ] Quest validation records video visibility, observed ratio, and `ViriViriAspect` logs for `9:16 + Panel reshape`.
