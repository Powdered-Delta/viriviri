# Immersive Controls Validation Artifact

## Archived Files

The current debug build was copied without changing Gradle output behavior:

```text
app/build/outputs/apk/debug/viriviri-immersive-controls-069647d-debug.apk
app/build/outputs/apk/debug/viriviri-immersive-controls-069647d-debug.md
```

The APK SHA-256 is:

```text
69bc65e1ad67c29a6911689e5dd618fc3f2e865be4fdd30c475705e60cdf6b66
```

The default `app/build/outputs/apk/debug/app-debug.apk` remains unchanged and
will continue to be overwritten by later Gradle builds. The copied APK and
sidecar are both excluded through `app/.gitignore` because they live under
`app/build/`.

## Included Milestones

- `3f48373 feat(spatial): integrate immersive media stage`
- `ccbdfe1 fix(spatial): synchronize playback controls`
- `1b71d85 feat(spatial): align transport overlay behavior`
- `069647d chore(task): archive 08-12-immersive-transport-overlay`

## Verification Boundary

The source build passed core tests, Compose compilation, app unit tests, and
debug assembly before the copy. No Quest device validation has been claimed.
The sidecar document carries the required sequential device checklist.
