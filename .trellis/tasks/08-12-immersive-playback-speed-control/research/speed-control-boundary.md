# Playback Speed Control Boundary

The documented `PlaybackConfigPopup` includes speed as a controlled playback
preference. This task adds only that first item to the existing transport panel.

`ThemeAction.SetPlaybackSpeed` is a pure theme action contract, but applying it
to Media3 belongs in the app/Spatial adapter. The implementation therefore uses
the process-wide `PlayerSession.player` already owned by `ViriViriAppState`.

The UI exposes an intentionally fixed set:

```text
0.75x, 1x, 1.25x, 1.5x, 2x
```

A standard Android anchored menu is appropriate for the current legacy panel
because it creates no Spatial panel/entity or video output. Its embedded Quest
rendering/input behavior must still be verified manually. Invalid/external
speeds render as `1x` in UI but are not silently written back to the player.
