# Spatial Content Aspect Validation Artifact

## Files

```text
app/build/outputs/apk/debug/viriviri-spatial-content-aspect-b911ac0-debug.apk
app/build/outputs/apk/debug/viriviri-spatial-content-aspect-b911ac0-debug.md
```

SHA-256:

```text
ee319d5c6ba010ace52612510a0fab9b424e0c0c5927afa32b26fb640662990d
```

The named APK is byte-identical to default `app-debug.apk` and was installed
successfully with `adb install -r` on Quest 2 `1WMHHB63832104`. Its name and
sidecar are intentionally ignored under `app/build/`.

## Included Change

`b252fc8 fix(spatial): contain video content in stage`

The existing shared player listener updates only the pre-existing video mesh
front-content vertices on a valid Media3 `VideoSize`. The pure contain formula
preserves portrait, standard, ultrawide, and non-square-pixel aspect ratios
inside the fixed stage. No player, Surface, panel, entity, transform, or source
operation is introduced in the update path.

Full core/Compose/app unit/assemble verification passed. Manual Quest
validation remains pending for portrait containment, landscape continuity, and
the previously observed right-edge strip. The sidecar holds the exact test
sequence.
