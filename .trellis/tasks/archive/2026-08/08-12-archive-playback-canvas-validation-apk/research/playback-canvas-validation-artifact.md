# Playback Canvas Validation Artifact

## Archived Files

```text
app/build/outputs/apk/debug/viriviri-playback-canvas-ca10df6-debug.apk
app/build/outputs/apk/debug/viriviri-playback-canvas-ca10df6-debug.md
```

SHA-256:

```text
81264d98fbd5ea3f62b4a9a8639c8c658b74df214653fe3ef43185381e8665c1
```

The default debug output remains at
`app/build/outputs/apk/debug/app-debug.apk`. All package files are intentionally
untracked because `app/.gitignore` excludes `app/build/`.

## Included Milestones

- `3f48373 feat(spatial): integrate immersive media stage`
- `ccbdfe1 fix(spatial): synchronize playback controls`
- `1b71d85 feat(spatial): align transport overlay behavior`
- `95d621d feat(canvas): add immersive playback state machine`
- `ca10df6 chore(task): archive 08-12-immersive-playback-canvas`

## Verification Boundary

The source build passed core tests, Compose compilation, app unit tests, and
debug assembly. It has not received Quest device validation. The pure Playback
Canvas reducer is included, but no Spatial panel-layer visibility adapter is
included; Browse/Context panel transitions are therefore not yet expected.
