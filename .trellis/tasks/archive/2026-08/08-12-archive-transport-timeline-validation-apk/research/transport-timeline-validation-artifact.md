# Transport Timeline Validation Artifact

## Archived Files

```text
app/build/outputs/apk/debug/viriviri-transport-timeline-1e94ecb-debug.apk
app/build/outputs/apk/debug/viriviri-transport-timeline-1e94ecb-debug.md
```

SHA-256:

```text
62e983ecf681453227509aacd3462193be4b4bf72d18fda22d8fa28c5128fe02
```

`app/build/outputs/apk/debug/app-debug.apk` remains the default Gradle output.
The named artifact and its sidecar are intentionally ignored.

## Included Work

- `5b288bd feat(spatial): synchronize transport timeline`
- `1e94ecb chore(task): archive 08-12-immersive-transport-timeline`
- All earlier immersive MediaStage, visibility, Browse, speed, and transport
  control milestones from the code baseline.

Automated core/unit/assemble verification passed before copying. Manual Quest
validation remains pending. The artifact sidecar lists finite/unknown timeline,
scrubbing, lifecycle, and speed-popup checks. Context is still blocked by the
scene-authored `mr_panel -> video_selector_panel` relationship and must not be
reparented in Kotlin.
