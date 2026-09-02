# Refine Immersive Stage Interaction

## Goal

Make a click on the visible immersive video stage an explicit controls-reveal action. Play/pause must be performed only by the fixed toolbar button.

## Scope

- Replace the stage-primary action policy with a single `REVEAL_TRANSPORT` result independent of transport visibility.
- Route the existing video panel `InputListener.onClick` through that policy.
- Keep dispatch of `PlaybackCanvasEvent.PrimaryStageAction` so Quiet Watch and Browse retain their existing canvas transitions.
- Keep the existing `play_pause_button` as the only control that changes player play intent.
- Add pure policy tests that reject the historic visible-stage toggle behavior.

## Non-goals

- Do not add a second hit target, black backdrop, panel, entity, player, Surface, or gesture recognizer.
- Do not modify aspect reshape, ISDK dimensions, Media3 source/output, or scene parentage.
- Do not change Browse selection or 2D handoff behavior.

## Acceptance Criteria

- [ ] Clicking the visible stage always reveals/resets transport and never directly toggles play intent.
- [x] The primary stage canvas event remains dispatched for existing Browse and Quiet Watch transitions.
- [x] `togglePlay()` remains attached to the explicit toolbar button only.
- [x] Pure transport policy tests encode the one-action behavior.
- [x] Windows JDK 17 app unit tests and debug build pass.
