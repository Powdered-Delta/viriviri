# Refine Immersive Transport Toolbar

## Goal

Make the existing immersive `controls_id` panel wider and more ergonomic for Quest interaction: use stable fixed-width control slots, familiar icons where a current state label is not necessary, and explicit gaps between functional groups.

## Scope

- Rework only the existing `app/src/main/res/layout/controls.xml` and its existing `controlsPanelRegistration()` sizing.
- Widen the same controls panel to make a fixed-width layout readable in immersive mode.
- Use the existing back, forward, play, and pause assets for navigation/playback controls.
- Use platform icons with labels for Browse, volume, quality, and speed; labels retain the current state where needed.
- Keep transport, settings, and timeline groups visually distinct with fixed gaps.
- Add content descriptions and tooltip text for icon-first controls.

## Non-goals

- Do not create/reparent a Spatial panel or entity.
- Do not modify the video panel, Media3 player, output Surface, source resolution, or panel lifecycle.
- Do not move or change the controls panel's scene-authored parent/pose.
- Do not expose new playback behavior; existing click handlers and PopupMenus remain unchanged.

## Acceptance Criteria

- [ ] The existing controls panel is wider and its display dimensions accommodate the fixed toolbar layout without clipping.
- [ ] Browse, previous, play/pause, and next use fixed-width icon-first slots.
- [ ] Volume, quality, and speed use fixed-width icon-plus-state slots.
- [ ] Functional groups have a stable visible gap and timeline controls remain usable.
- [ ] No player, Surface, Entity, panel lifecycle, or click-handler regression is introduced.
- [x] Windows JDK 17 app unit tests and debug build pass.
