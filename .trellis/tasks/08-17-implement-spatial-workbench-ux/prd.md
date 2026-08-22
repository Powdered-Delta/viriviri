# First Spatial Workbench Version: Left Detail and Center Browse

## Goal

Implement the first approved Spatial Workbench version while preserving the
known-working angled layout:

- Left angled panel: current-video Detail.
- Center: the sole MediaStage plus one on-demand Search/VideoList content panel.
- Right angled panel: existing Context/Status panel.
- Top: existing navigation panel.
- Front: existing Transport.

The Web Demo defines information responsibility and component composition; it
is not copied as a flat Spatial layout.

## Spatial Boundaries

- Keep one process-wide player and one active SDK-owned video Surface.
- `spatialized_video_panel` remains the only video output host.
- Keep the existing left `-45°` and right `+45°` runtime transforms.
- Keep existing stage input, overlay `MeshCollision.NoCollision`, visibility
  deduplication, Surface handoff, and danmaku changes.
- Add only one non-video MSE panel, `WorkbenchCenterContent`, bound to
  `@id/center_content_panel`.
- The center panel is parented to MediaStage at runtime, has no `Grabbable`, and
  is visible only for Browse/Search/List content.

## Workbench Modules

```text
NAVIGATION       mr_panel
DETAIL_RAIL      video_selector_panel
CENTER_CONTENT   WorkbenchCenterContent
VIDEO_CONTEXT    mode_panel
TRANSPORT        controls_id
MEDIA_STAGE      spatialized_video_panel
```

Normal controls show Navigation, Detail, MediaStage, Context, and Transport.
Browse/Search adds CenterContent. Quiet Watch hides every Workbench panel and
keeps MediaStage.

## Left Detail

The existing angled `video_selector_panel` always renders video details:

```text
body
  title
  metrics
  like / coin / favorite availability
  creator
  description
footer
  comments
```

Comments use a full-height opaque collapse inside the same panel. Unsupported
writes remain disabled and never report success.

## Center Search and List

The center panel contains two standardized modules:

- `SearchPanel`: session, input method, explicit actions, panel style, input
  style, visibility, and modifier.
- `VideoListPanel`: list state, thumbnails, grid/list mode, style, palette,
  selection callback, and modifier.

Home selects `VIDEO_LIST` and returns to recommendations. Search selects
`SEARCH`. Transport's existing Browse action opens the retained list source.
Selecting a video uses existing `ViriViriAppState`, returns Playback, hides the
center panel, and keeps the same MediaStage/Surface.

## Atomic Style Contract

Shared atoms live in `spatial-workbench-compose` and accept one
`WorkbenchPanelStyle` derived from `CinemaPalette`. Standard visual interfaces
accept `style` and `modifier`; interactive interfaces expose explicit callbacks.
They do not import Meta SDK, Media3, Bilibili, Activity, player, Surface, or
network APIs.

Token mapping:

```text
background       BACKGROUND
surface          SURFACE
surfaceStrong    SECONDARY_BUTTON
border           BORDER
text             NORMAL_TEXT
secondaryText    SECONDARY_TEXT
accent           PRIMARY_BUTTON
accentContent    PRIMARY_BUTTON_LABEL
danger           DANGER
disabled         SECONDARY_TEXT-derived alpha
```

## Deferred

- Search module input method rewrite: the current application keyboard remains
  a separate follow-up and must not be changed during Workbench UI work.
- Complete the right Source/Context panel. The current first version does not
  yet provide the approved right-side content responsibilities.
- Complete and Quest-validate MediaStage canvas-size switching; future curved
  canvas support must reuse the same single MediaStage/Surface.
- Implement grab-after-recenter repositioning so the whole Workbench returns to
  a stable user-facing pose without moving child panels independently.
- Account, Settings, Focus/PiP, and real theme scene loading.
- Real comment/reaction write APIs.
- Danmaku high-density optimization and device validation remain separate from
  the Search input-method rewrite.

## Validation

- Focused reducer, host dedup, center mode, and style-token tests.
- Full `scripts/build-windows-debug.ps1` build.
- No APK install or launch.
- Quest validation must confirm startup memory, original angles, center panel
  visibility/input, and one active video Surface.
