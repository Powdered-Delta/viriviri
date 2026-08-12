# Media List and Aspect Validation Artifact

## Files

```text
app/build/outputs/apk/debug/viriviri-media-list-aspect-1217886-debug.apk
app/build/outputs/apk/debug/viriviri-media-list-aspect-1217886-debug.md
```

SHA-256:

```text
61e71bbf483d81a2f08da3342afcf63d34ae321bda262ac37f9aa8bb1408b66d
```

The named APK is byte-identical to `app-debug.apk` and was successfully
installed with `adb install -r` onto authorized Quest 2 device
`1WMHHB63832104`. The named APK and sidecar remain intentionally ignored.

## Included Change

`5456f97 fix(media): paginate lists and preserve video aspect`

It adds recommendation/search pagination and de-duplication, bounded
app-state-owned thumbnail loading, and an aspect-preserving single-player
Spatial output configuration (`1920x1080` mono buffer plus Media3
`SCALE_TO_FIT`). It does not create a player, Surface, or Spatial panel/entity.

Automated core/Compose/app unit/assemble verification passed before install.
Manual Quest validation is pending for pagination, covers, portrait fit, and the
reported landscape right-edge strip. The co-located sidecar contains the exact
acceptance sequence.
