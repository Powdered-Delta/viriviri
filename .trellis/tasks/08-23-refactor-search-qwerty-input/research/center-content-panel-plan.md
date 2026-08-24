# Center Content Panel Plan

## Purpose

Record the agreed direction for the immersive center area before implementation so later work does not conflate Spatial panel ownership, visual composition, and route state.

## Current Problem

The current center panel couples its filter header to a visible video-list body. When a video is playing and the user opens Workbench, the desired state is:

```text
- Filter header: visible
- Center content body: absent
- Video list: absent
- Search history/recommendations: absent
- Search results: absent
- Input method panel: absent
```

The existing implementation cannot express that state because the recommendations route renders both the filter header and `VideoListPanel`.

## Decision

Use one actual `CenterContentPanel` Spatial panel, not two Spatial panels.

Inside this panel, combine:

1. A dedicated empty center route for structural state.
2. A transparent-root visual composition that makes the header and body appear as two separate blocks.

These are complementary, not competing alternatives.

```text
Structural route
  -> decides which body, if any, is rendered

Transparent visual composition
  -> decides how the header and body are visually separated
```

## Center Routes

The center-content route model must include:

```kotlin
enum class CenterContentRoute {
    WORKBENCH_EMPTY,
    RECOMMENDATIONS,
    SEARCH_EMPTY,
    SEARCH_RESULTS,
}
```

Expected body behavior:

```text
WORKBENCH_EMPTY
  Header: Filter Header
  Body: no content route container / no list

RECOMMENDATIONS
  Header: Filter Header
  Body: recommendation video list

SEARCH_EMPTY
  Header: search input header
  Body: search history + recommended keywords

SEARCH_RESULTS
  Header: search input header
  Body: search result list
```

## Visual Composition: Option 2

Use one transparent root panel, moved slightly closer to the user than the previous center panel placement. Within it, render two visually distinct child surfaces.

```text
         CenterContentPanel: one Spatial Compose panel

    . . . . . transparent root / no visible frame . . . . .

                 +-----------------------------------+
                 | Header Surface                    |
                 | [综合排序] [最新发布] [更多筛选]    |
                 +-----------------------------------+

                 +-----------------------------------+
                 | Content Route Surface             |
                 |                                   |
                 | WORKBENCH_EMPTY: no child content |
                 | RECOMMENDATIONS: video list       |
                 | SEARCH_EMPTY: history/recommend   |
                 | SEARCH_RESULTS: result list       |
                 +-----------------------------------+
```

Rules:

- The Spatial panel root stays transparent.
- Header Surface is always rendered for a visible center panel.
- Header Surface is moved lower inside the root so it reads as a top floating block.
- Content Route Surface is rendered only when the active route has content.
- `WORKBENCH_EMPTY` does not render an empty card, placeholder list, or invisible hit target beneath the header.
- Both surfaces are Compose children of the one root panel. They share one Spatial entity, one visibility lifecycle, and one panel transform.
- No second center Spatial panel is created.
- This does not affect the separate near-field `input_method_panel`.

## Video Playback Workbench Invocation

When a user calls Workbench from a playing video:

```text
video playback
  -> open Workbench
  -> CenterContentRoute = WORKBENCH_EMPTY
  -> CenterContentPanel visible
  -> Filter Header visible
  -> Content Route Surface omitted
  -> left/right panels follow normal playback Workbench rules
  -> Transport remains visible
```

Expected visual result:

```text
                     +-----------------------------------+
                     | [综合排序] [最新发布] [最多弹幕]    |
                     | [最多收藏] [更多筛选]              |
                     +-----------------------------------+

                     no center list surface below

                     +-----------------------------------+
                     | MediaStage / current video        |
                     +-----------------------------------+

                     +-----------------------------------+
                     | Transport                          |
                     +-----------------------------------+
```

## Why This Is Preferred

Do not split Header and Content into separate Spatial panels.

Two Spatial panels would introduce unnecessary coordination for:

```text
- transforms and relative depth
- visibility and alpha animations
- hit-test boundaries
- scene registration
- panel lifecycle cleanup
- resize and theme changes
```

One transparent root panel with two internal Compose surfaces gives the desired visual separation while preserving one owner and one lifecycle.

## Implementation Status

Implemented in the current branch:

```text
- `WorkbenchContent.WORKBENCH_EMPTY`
- `SearchWorkspaceRoute.WORKBENCH_EMPTY`
- `RevealTransport -> WORKBENCH_EMPTY -> CENTER_CONTENT visible`
- `TRANSPORT` canvas slots with a selected video set Search workspace to `WORKBENCH_EMPTY`
- transparent `CenterContentPanel` root
- independent Header Surface and conditional Content Route Surface
- `WORKBENCH_EMPTY` renders Filter Header and omits the body surface
- `WorkbenchCenterContent` moved from z=-0.06m to z=-0.12m through Meta Spatial Editor
```

Device inspection is still required to validate the perceived forward offset and the exact vertical spacing between the filter surface, MediaStage, and Transport.


1. Introduce `WORKBENCH_EMPTY` into the center route state.
2. Map video-to-Workbench invocation to `WORKBENCH_EMPTY`.
3. Decouple filter header rendering from video-list rendering.
4. Change CenterContentPanel root styling to transparent.
5. Add Header Surface and conditional Content Route Surface.
6. Lower the Header Surface within the root panel; move the root panel closer only if scene-level measurements require it.
7. Add JVM state tests:
   - playback Workbench invocation selects `WORKBENCH_EMPTY`
   - `WORKBENCH_EMPTY` exposes filter header state
   - `WORKBENCH_EMPTY` suppresses list-body state
   - recommendations/search routes retain their existing body contracts
8. Validate with Windows build script.

## Non-Goals

- Do not create a second video Surface or player.
- Do not create a separate Spatial panel for the center header.
- Do not change left/right rail ownership.
- Do not show the input method panel in `WORKBENCH_EMPTY`.
- Do not install, deploy, or start the APK unless explicitly requested.
