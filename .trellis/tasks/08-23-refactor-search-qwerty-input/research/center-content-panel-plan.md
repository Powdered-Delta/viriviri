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

## Reference Screens: Application Home vs Creator Home

The supplied YTBVR screenshots establish two distinct center-workspace routes. They must not be treated as variants of one generic recommendation list.

### Application Home

The first supplied expanded-workbench screenshot is the **application home**. It is the canonical visual reference for all of these entry points:

```text
- first startup
- video playback -> click [视频列表]
- click the top ViriViri / Home logo
```

Expected composition:

```text
transparent floating workbench
├── left current-video detail rail
├── center application-home content
│   ├── top application navigation / category tabs
│   ├── recommendation video rows or cards
│   └── optional local home actions
├── right up-next / related rail
└── MediaStage remains behind the workbench with only the stage backdrop dimmed
```

`RECOMMENDATIONS` is the application-home route and must be entered by all three actions above. It must not render creator-banner data or creator-specific tabs.

### Creator Home

The later supplied screenshot with the HIMEHINA banner and channel tabs is the **creator home**. It is a separate route, not an application-home filter state.

Expected composition:

```text
transparent floating workbench
├── left current-video detail rail
├── center creator-home content
│   ├── creator-specific navigation: 首页 / 视频 / 播放列表 / 简介
│   ├── creator banner
│   ├── creator identity / subscribe state
│   └── creator videos, live items, and playlists
└── right up-next / related rail
```

Creator home must preserve the MediaStage, Transport, and existing left/right rail ownership. It may reuse the center panel shell and route-body contract, but it must own distinct creator state and data bindings.

### Search Input Target Contract

The Search header has two explicit input targets:

```text
click query field
  -> INTERNAL
  -> query text is read-only at the Android TextField layer
  -> click is intercepted
  -> show app-owned input_method_panel

click [IME] icon
  -> SYSTEM
  -> hide input_method_panel
  -> request Compose focus
  -> show Android system IME
```

The default route-enter and route-return target is `INTERNAL`. This prevents a normal search-field click from accidentally invoking the system keyboard. Both targets update the same committed query through `updateSearchQuery`.


## Transparency Contract

The YTBVR reference corrects an earlier assumption: the Workbench is not a semi-transparent panel system.

```text
Workbench root panel      -> transparent
Workbench UI surfaces     -> opaque
Left / center / right UI  -> opaque floating surfaces
MediaStage                -> remains behind the UI
StageBackdrop             -> the only translucent dim layer
```

Implemented behavior:

```text
SpatialPanelVisibilityController.visibleAlpha = 1.0
StageBackdrop alpha = 0.42
StageBackdrop visible = Workbench visible
```

Do not apply shared compositor alpha to Workbench UI panels. Do not use a semi-transparent center root or semi-transparent Header/Content surfaces to simulate MediaStage dimming.

## Reference Usage

These screenshots are primarily **spatial and state-transition references**, not a requirement to reproduce YouTube's visual assets, navigation labels, or data model.

Use them to preserve:

```text
- the shallow curved relationship between left rail, center content, and right rail
- MediaStage behind all workbench UI
- a backdrop-only dim layer over MediaStage
- opaque floating UI surfaces rather than a semi-transparent workbench root
- a center Header whose state changes with the active content route
- a body route that changes below the Header without rebuilding the spatial shell
```

The key Filter Header contract is:

```text
WORKBENCH_EMPTY
  -> application-level filter header remains visible
  -> body omitted

RECOMMENDATIONS / application home
  -> application-level filter header remains visible
  -> application recommendation body visible

SEARCH_EMPTY / SEARCH_RESULTS
  -> header changes to search input/navigation state
  -> body changes to search discovery or search result state

CREATOR_HOME
  -> header changes to creator-level navigation state
  -> body changes to creator banner and creator content
```

The left rail, right rail, MediaStage, and Transport remain spatially stable across these center Header/body transitions.


```text
WORKBENCH_EMPTY
  -> filter header only; no body

RECOMMENDATIONS
  -> application home

SEARCH_EMPTY
  -> history + recommended search terms

SEARCH_RESULTS
  -> search results

CREATOR_HOME (future dedicated route)
  -> creator banner + creator navigation + creator content
```

Do not map application-home actions (`first startup`, `[视频列表]`, `logo`) to `CREATOR_HOME`.


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

## Workbench Empty Return Header Repair

The previous `WORKBENCH_EMPTY` implementation incorrectly rendered a legacy combination of return action, Search action, and list Filter Header. That combination is removed.

```text
WORKBENCH_EMPTY
  -> exactly one context-return button
  -> VIDEO_LIST source: [视频列表]
  -> SEARCH_RESULTS source: [搜索结果]
  -> no Filter Header
  -> no Search button
  -> no body route surface
```

Playback source is recorded when a recommendation is selected:

```text
RECOMMENDATIONS selection
  -> WorkbenchReturnTarget.VIDEO_LIST

SEARCH_RESULTS selection
  -> WorkbenchReturnTarget.SEARCH_RESULTS
```

The legacy `CenterContentSession` bridge was removed from the runtime center-panel decision path. `SearchWorkspaceRoute` is now the only center content visual route source.

MediaStage behavior while Workbench is visible is also constrained:

```text
MediaStage click
  -> keeps Workbench visible and reveals Transport

WorkbenchOuterDismiss click
  -> the only Workbench dismiss owner
```


Implemented after the initial empty-route pass:

```text
WORKBENCH_EMPTY Header
  -> explicit [视频列表] action
  -> RECOMMENDATIONS
  -> content body expands into the recommendation list

Filter Header
  -> all sort tabs and additional date/duration filters are enabled
  -> RECOMMENDATIONS applies the filter locally
  -> SEARCH_RESULTS maps the filter to Bilibili remote search options
```

Local recommendation filtering supports:

```text
综合排序: preserve provider order
最新发布: publishedAt descending
最多弹幕: danmakuCount descending
最多收藏: favoriteCount descending
日期: 今天 / 本周 / 本月
时长: 短片 / 中等 / 长视频
```

## Input and Keyboard Repairs

Implemented in the current patch:

```text
Keyboard layers
- numeric mini-panel remains unchanged
- letter layer switch is fixed at the third-row right edge: [符号]
- symbol layer switch is fixed at the same position: [字母]
- symbol layer no longer duplicates a language switch
- language switch remains on the letter-layer bottom row and communicates target mode: English / 拼音
- Pinyin shift/caps and apostrophe segmentation remain unchanged
- both letter and symbol layers preserve the same wide space-key weight

Search input
- visible search field is always read-only display
- clicking it always opens internal input_method_panel
- a transparent, attached 1dp BasicTextField is the system-IME proxy
- only [IME] switches to SYSTEM and focuses the proxy
- system proxy changes synchronize through updateSearchQuery

Workbench interaction
- stage hover and generic stage input no longer reveal Workbench
- stage trigger click reveals Workbench when hidden
- stage click dismisses it when visible
- outer-dismiss geometry remains the outside-of-Workbench dismiss owner
```


YTBVR-style right-thumbstick stage scaling remains intentionally unimplemented in this pass.

Observed constraints:

```text
- current project only uses scene InputListener click / hover callbacks
- Meta Spatial SDK 0.13.2 InputSystem public API in the local SDK jar does not expose a thumbstick axis getter
- controller-axis web documentation could not be retrieved because the configured fetch and search providers failed
```

Do not bind stage scaling to hover, generic panel MotionEvent, or an invented controller callback. Before implementation, verify the official controller-axis bridge, right-hand identity, trigger gate, and axis ownership.

Required target behavior after the API is verified:

```text
right hand ray hits MediaStage
  -> trigger locks the stage as the scale target
  -> right thumbstick forward/back adjusts only MediaStage Scale
  -> releasing or targeting another surface clears the mode
  -> other panels retain their own input behavior
```

## Non-Goals

- Do not create a second video Surface or player.
- Do not create a separate Spatial panel for the center header.
- Do not change left/right rail ownership.
- Do not show the input method panel in `WORKBENCH_EMPTY`.
- Do not install, deploy, or start the APK unless explicitly requested.
