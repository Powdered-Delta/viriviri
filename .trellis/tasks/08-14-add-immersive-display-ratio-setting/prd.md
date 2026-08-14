# Add Immersive Display Ratio Setting

## Goal

Promote the validated immersive panel reshape path into a release-visible display-ratio setting in the existing `mode_panel`, while retaining debug-only aspect diagnostics separately.

## Scope

- Add a shared pure Kotlin `PlaybackDisplayRatio` preference with `Auto`, `16:9`, `4:3`, `1:1`, and `9:16` values.
- Store the selected ratio in existing `ViriViriAppState` UI state so it survives 2D/immersive activity handoff.
- Add one release-visible `Display ratio` menu button to the existing `mode_panel`; a selection immediately applies the existing `Panel reshape` path.
- Keep debug target/plan/apply controls debug-only; release users never select a render plan.
- Resize only the existing `mode_panel` as necessary to make its user-facing controls readable.
- Create a controls-only semi-transparent background drawable and restore the shared `layout_bg` opacity for non-toolbar panels.

## Non-goals

- Do not create a new panel, entity, player, Surface, renderer, or Media3 output.
- Do not change source streams, VideoSize, decoder scaling, 2D TextureView behavior, or scene-authored parent/pose.
- Do not add persistent disk storage or per-video ratio history in this task.

## Acceptance Criteria

- [x] Release mode panel exposes Auto, 16:9, 4:3, 1:1, and 9:16 through one display-ratio menu.
- [x] Choosing a ratio immediately applies `Panel reshape` and updates the visible menu label.
- [ ] The selection persists across the existing 2D/immersive handoff through shared AppState.
- [x] Debug diagnostic controls remain available only in debug builds.
- [x] Toolbar transparency does not alter the mode panel background.
- [x] No additional player, output Surface, panel, entity, or scene reparenting is introduced.
- [x] Windows JDK 17 unit tests and debug build pass.
