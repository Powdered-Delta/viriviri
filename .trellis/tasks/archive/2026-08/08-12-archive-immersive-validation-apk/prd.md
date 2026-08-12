# Archive Immersive Validation APK

## Goal

Preserve the current successfully built debug APK under a stable, descriptive
filename and place a companion validation note beside it for later Quest
inspection.

## Scope

- Keep Gradle's default `app-debug.apk` output untouched so subsequent builds
  continue to work normally.
- Copy the current APK into the same debug output directory using a name that
  identifies the immersive-controls milestone commit.
- Write a sidecar Markdown note with SHA-256, source build path, included
  immersive changes, completed automated verification, and a sequential manual
  Quest checklist.

## Non-Goals

- No code, Gradle metadata, versionName, manifest, package ID, deployment, or
  device changes.
- No claim that the APK has received manual Quest visual acceptance.

## Acceptance Criteria

- The archived APK and sidecar note have matching milestone names and sit beside
  the default debug output.
- The note identifies the APK hash and does not claim unperformed device checks.
- The default `app-debug.apk` remains available.
