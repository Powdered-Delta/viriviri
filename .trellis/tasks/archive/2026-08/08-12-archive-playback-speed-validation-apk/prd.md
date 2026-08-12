# Archive Playback Speed Validation APK

## Goal

Preserve the debug APK containing immersive playback-speed control and document
its later Quest validation requirements.

## Scope

- Copy default debug APK beside Gradle output with playback-speed milestone name.
- Write sidecar with SHA-256, fixed speed menu behavior, automated verification,
  manual Quest checklist, and Context scene limitation.
- Preserve default Gradle output.

## Non-Goals

- No source, Gradle metadata, deployment, or device modification.
- Do not claim the anchored Android popup is validated inside an embedded Quest
  panel before device inspection.

## Acceptance Criteria

- Named APK/sidecar sit beside default debug output.
- Hash, supported speeds, and manual validation boundary are explicit.
- Default `app-debug.apk` remains available.
