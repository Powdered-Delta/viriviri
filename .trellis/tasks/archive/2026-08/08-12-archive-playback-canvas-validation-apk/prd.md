# Archive Playback Canvas Validation APK

## Goal

Preserve the debug APK containing the immersive Playback Canvas core state
machine under a stable filename, with a sidecar validation note for later Quest
inspection.

## Scope

- Leave Gradle's default `app-debug.apk` untouched.
- Copy the current APK beside it using the Playback Canvas milestone commit in
  the filename.
- Write a sidecar Markdown note with SHA-256, prior immersive-controls package
  relationship, included runtime contract, automated build evidence, and manual
  validation sequence.

## Non-Goals

- No application code, version, manifest, deployment, or device changes.
- Do not claim the pure canvas state machine has already driven Quest panel
  visibility; the Spatial panel-layer adapter is the subsequent task.

## Acceptance Criteria

- Named APK and sidecar share the milestone name and are beside `app-debug.apk`.
- Hash and verification boundary are explicit.
- Default debug output remains present for subsequent builds.
