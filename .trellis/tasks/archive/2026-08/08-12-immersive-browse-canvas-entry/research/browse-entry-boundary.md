# Immersive Browse Entry Boundary

## Existing Content and Selection

`video_selector_panel` hosts `MoviePanel`, which renders the existing shared
`RecommendationPanel`. Selecting a row invokes `ViriViriAppState.selectRecommendation`.
That method owns selected state, stale-request protection, Bilibili media-source
loading, and use of the process-wide `PlayerSession`.

The Spatial Activity must not duplicate that work. It observes only the selected
video ID while its Browse canvas is active.

## Entry and Completion

- A new command in existing `controls_id` dispatches `OpenBrowse` to the canvas
  host and records the selected video ID at entry.
- The existing canvas visibility adapter displays the selector panel for Browse.
- A change from that baseline selected ID means the user selected another video
  through the shared panel. The Spatial host dispatches pure `OpenPlayback`,
  which hides Browse and restores transport while the existing state/player flow
  loads the chosen source.

No entity, panel, player, Surface, request, or static transform is created by
this adapter.
