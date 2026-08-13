# Immersive Media Resolution Progress

The existing mode-panel current-media detail uses the already authoritative
`ViriViriUiState.isResolvingPlayback` state. The pure status projection defines
priority:

```text
viewer selected + resolving -> Loading video...
viewer selected + settled error -> error text + Retry
selected + no error -> author
no selection -> Browse to choose a video
```

The Activity passes the state into the existing TextView update path. No timer,
new observer, player operation, Surface handoff, or network call is added.
Browse pagination errors remain excluded by the viewer-only error projection.
