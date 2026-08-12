# Spatial Foreground/Backdrop Validation Artifact

## Files

```text
app/build/outputs/apk/debug/viriviri-spatial-foreground-backdrop-c0dabe06-debug.apk
app/build/outputs/apk/debug/viriviri-spatial-foreground-backdrop-c0dabe06-debug.md
```

SHA-256:

```text
0c5fa5d3b743bb21ff17075748a4340440226945c7218741b6177791481a99f8
```

The named APK is byte-identical to default `app-debug.apk`. It is intentionally
ignored and was not installed/deployed by this task.

## Build Identification

The generated `BuildConfig.java` for this APK contains:

```text
GIT_SHA = "c0dabe06"
```

Debug mode panel displays `DEV c0dabe06`, so headset testing can identify the
actual embedded build independent of APK filename.

## Included Work

- `041b81c fix(spatial): separate video foreground and backdrop`
- `c0dabe06 fix(build): resolve debug hash from repository root`

The existing SceneMesh now commits contained foreground geometry after
`VideoSize` using `updateWithTriangleMesh`, while a same-mesh translucent black
backdrop remains fixed to the 16:9 stage. No player, Surface, entity, panel, or
media source is added. Automated core/Compose/app unit/assemble verification
passed. Manual Quest validation remains pending and is documented in sidecar.
