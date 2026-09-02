# Align Immersive Video Stage Hit Target

## Goal

Keep immersive video input bounds synchronized with the visible `PanelSceneObject` shape after aspect-ratio reshape, so a portrait video does not retain an invisible 16:9 stage click target.

## Evidence

Quest validation confirmed `PanelSceneObject.reshape(...)` corrects the visible portrait aspect ratio, but the pause/play stage trigger remains the original 16:9 size. The video entity owns `Hittable` and `IsdkPanelDimensions`; the reshape path did not refresh its ISDK component properties.

The bundled SpatialVideoSample refreshes `updateIsdkComponentProperties(videoPanelEntity)` when the panel configuration needs to be kept current.

## Scope

- After reshaping the existing `PanelSceneObject`, update ISDK properties for the existing `spatialized_video_panel` entity.
- Preserve the existing one-player, one-SDK-Surface contract and panel identity.
- Preserve existing primary stage interaction semantics: a hidden transport is revealed first; only an already-visible Playback stage toggles play intent.
- Add bounded diagnostics identifying the reshaped panel dimensions and ISDK synchronization.

## Non-goals

- Do not add a black 16:9 backdrop or invisible secondary hit target.
- Do not create/reparent a panel or entity, change scene-authored transforms, or change the Media3 source/output path.
- Do not expose a new release control for manual aspect selection.

## Acceptance Criteria

- [ ] Each panel reshape refreshes ISDK component properties for the existing video entity.
- [ ] Portrait video has no invisible 16:9 stage hit target outside its visible panel bounds on Quest.
- [ ] Stage interaction continues to reveal hidden transport before toggling playback intent.
- [ ] No extra panel/entity/player/Surface is created.
- [x] Windows JDK 17 app unit tests and debug build pass.
