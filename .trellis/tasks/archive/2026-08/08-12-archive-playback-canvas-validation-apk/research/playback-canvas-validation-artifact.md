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

- `0df30dbb0a90361f232834e0b16dad212b5d28cb feat(spatial): integrate immersive media stage`
- `7d93ad743268914fc4dbf88f5d3cf5d978857cb1 fix(spatial): synchronize playback controls`
- `11176129890e8fd1557b31de519ce7b96948bc59 feat(spatial): align transport overlay behavior`
- `01a865ae9fa9561d3926cc0d0eed5ba981d32719 feat(canvas): add immersive playback state machine`
- `1aee8bd80a166cf6ea490090ad6ce1bec226eef7 chore(task): archive 08-12-immersive-playback-canvas`

## Verification Boundary

The source build passed core tests, Compose compilation, app unit tests, and
debug assembly. It has not received Quest device validation. The pure Playback
Canvas reducer is included, but no Spatial panel-layer visibility adapter is
included; Browse/Context panel transitions are therefore not yet expected.
