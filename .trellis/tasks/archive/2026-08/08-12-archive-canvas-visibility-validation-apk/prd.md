# Archive Canvas Visibility Validation APK

## Goal

Preserve the debug APK containing the applied Quest Spatial Playback Canvas
visibility adapter and provide a companion note for later device validation.

## Scope

- Copy the current default debug APK beside its Gradle output without changing
  `app-debug.apk` behavior.
- Use the Spatial canvas visibility milestone in the artifact name.
- Record SHA-256, included adapter behavior, automated verification, manual
  Quest checks, and the explicitly deferred Context rail scene-authoring
  blocker.

## Non-Goals

- No source, Gradle metadata, deployment, or device change.
- Do not claim `CONTEXT` independent Spatial visibility: `mr_panel` remains a
  child of Browse pending a Meta Spatial Editor scene-parent correction.

## Acceptance Criteria

- Named APK/sidecar are next to default debug output and share the milestone.
- Hash and manual validation boundary are clear.
- The default output remains available for later builds.
