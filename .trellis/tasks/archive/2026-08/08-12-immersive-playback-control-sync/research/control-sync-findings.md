# Playback Control Sync Findings

## Root Cause

`SpatialVideoSampleActivity` maintains a local `isPlaying` value and updates
the play/pause drawable only inside `playVideo()` and `pauseVideo()`. The
process-wide `PlayerSession.setMediaSource()` independently sets
`player.playWhenReady = true` when a new recommendation is selected. The
Activity-local value and drawable therefore do not update for that path.

The existing panel listener updates the seekbar maximum and position but does
not synchronize local playback state, button icon, controller fade, or lights.

## Seek Problem

The seekbar listener pauses/resumes based on stale `isPlaying`, never sets
`isSeeking`, and allows position-discontinuity and periodic refresh paths to
move the thumb during a user drag.

## Correct State Model

- `player.playWhenReady` is playback intent and controls the play/pause icon.
- `player.isPlaying` is actual playback and controls fade/light behavior.
- Seek stores pre-drag `playWhenReady`. It only pauses during drag when that
  intent was true and restores the same intent on stop.
- `isSeeking` guards all non-user seekbar progress writes until the drag ends.
