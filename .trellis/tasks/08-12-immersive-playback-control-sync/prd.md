# Synchronize Immersive Playback Controls

## Goal

Make the existing immersive control panel reflect the process-wide Media3
player, rather than relying on an Activity-local `isPlaying` value that becomes
stale when selection, buffering, output handoff, or another player entry point
changes playback.

## Scope

- Treat the process-wide `ExoPlayer` as the only playback truth source.
- Add a small pure playback-control visual state helper:
  - the play/pause icon follows `player.playWhenReady`, so a buffering player
    that is still intended to play exposes Pause;
  - controller fade and environment light state follow `player.isPlaying`, so
    they reflect actual playback rather than intent.
- Make the existing immersive Media3 listener update the play/pause drawable,
  local actual-playback state, controller fade, and lights when player state or
  playing state changes.
- Replace local toggle branching with `player.playWhenReady`.
- During seek drag, save the pre-drag `playWhenReady` intent, mark seeking,
  temporarily pause only when that intent was true, then restore exactly that
  intent when the drag ends. Do not resume a video that was already paused.
- Prevent position-discontinuity callbacks and periodic progress updates from
  overwriting a user's active seek drag.
- Add focused JVM tests for visual-state derivation and seek-resume policy.
- Update the Spatial playback runbook with the player-as-source-of-truth rule.

## Non-Goals

- No 2D UX change, no new Compose UI, no MediaStage target/Surface change, and
  no new player or panel.
- No changes to Bilibili source handling, seek precision, recommendations,
  transport layout, or controller visual redesign.
- No frame polling beyond the existing 500ms seekbar refresh; player listener
  callbacks remain the source for playback-state synchronization.

## Acceptance Criteria

- Selecting a video through `ViriViriAppState` updates the immersive play/pause
  icon without requiring a panel click.
- A player state change from buffering, pause, play, output handoff, or an
  external code path updates controls from the actual player values.
- A video that was paused before seek remains paused after seek; one that had
  play intent resumes after seek.
- While the user drags the seekbar, callback/periodic progress updates do not
  move the thumb unexpectedly.
- Exactly one ExoPlayer and one active video Surface remain in use.
- `:app:testDebugUnitTest`, `:spatial-workbench-core:test`, and
  `:app:assembleDebug` pass.
