# Immersive Browse Validation Artifact

## Archived Files

```text
app/build/outputs/apk/debug/viriviri-immersive-browse-9c2e930-debug.apk
app/build/outputs/apk/debug/viriviri-immersive-browse-9c2e930-debug.md
```

SHA-256:

```text
d74ae995d5b63e4d28f64bff23683e08a9020ff9a2adf5f368fef6e7d18ebe25
```

The default debug output remains at
`app/build/outputs/apk/debug/app-debug.apk`. The named APK and sidecar are
intentionally untracked under `app/build/`.

## Included Milestones

- `0660a148b0f3b07f04b55a21b16b641c78326d44 feat(spatial): apply playback canvas visibility`
- `a49f02d285114df7061b4029cde8015c1aeb53ab feat(spatial): open immersive browse canvas`
- `7e0691f5d322ed38a1919450bc7f57585f009db5 chore(task): archive 08-12-immersive-browse-canvas-entry`

Earlier immersive MediaStage, playback control, transport, and canvas core
milestones are also included through the code baseline.

## Verification Boundary

The source build passed core tests, Compose compilation, app unit tests, and
debug assembly. Manual Quest validation has not happened. The package adds
reachable Browse/selection/exit flow over the existing selector panel. Context
is still intentionally deferred pending a Meta Spatial Editor correction to the
existing `mr_panel -> video_selector_panel` parent relationship.
