# Rebuild Immersive Workbench

## Product Model

The immersive product has exactly two spatial containers:

1. `Player`: the MediaStage and its video, subtitle, danmaku, and short-lived playback feedback layers. It retains the sole active Media3 output Surface.
2. `Workbench`: one movable, composable operation group. Navigation, lists, details, comments, transport, configuration, and keyboard are Workbench modules, not separate product panels.

## Top Navigation

`GlobalNavigation` is always the top-most Workbench module in both normal and Shorts Controls. It contains Home/logo, search, and profile/account actions. It does not appear below the player or move with a side region.

## Quiet Watch

- Normal: Player only, plus conditional debug hand panel.
- Shorts: Player plus an always-visible transparent quick action strip: previous, like, feedback/not-interested, next.
- Workbench modules are hidden and do not receive input.

## Normal Controls

- Top: GlobalNavigation.
- Left: author, metadata, description, and an expandable comments drawer.
- Center: shared tabs/back header and a video list body with one- or two-column layout.
- Right: source-aware tabs for parts, collection/playlist, danmaku list, and relevant content.
- Bottom: transport controls and title. The footer exposes autoplay mode.

## Shorts Controls

- Top: GlobalNavigation.
- Left: short metadata and creator detail, without a comments drawer.
- Center: shared header with an explicit leave-Shorts tab; playback progress controls are hidden by default.
- Right: comments panel, including a read-only or unavailable composer when account/API support is absent.
- Bottom: volume, previous, like, feedback, next, content More, and Settings.
- More owns report. Settings owns quality, speed, display, danmaku, and optional shorts progress controls.

## Theming

All action menus and selection controls must use CinemaPalette semantic tokens. Platform-default Spinner, Select, and PopupMenu visuals are not allowed inside Workbench.

## Invariants

- Workbench visibility, modules, and layout mode never create a player, video Surface, video panel, or Media3 output.
- Only the Workbench GrabHandle moves the workbench.
- Keyboard is the top-most application-owned Workbench module and may cover transport while active.
