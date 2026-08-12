# Archive Transport Timeline Validation APK

## Goal

Preserve the debug APK containing the lifecycle-owned immersive transport
timeline and document required Quest validation.

## Scope

- Copy the current default debug APK using a transport-timeline milestone name.
- Co-locate ignored sidecar with hash, behavior, automated verification, and
  manual Quest checks.
- Preserve default Gradle output unchanged.

## Non-Goals

- No source, Gradle, device, or deployment changes.
- Do not claim Quest acceptance before headset validation.

## Acceptance Criteria

- Named APK and sidecar exist beside `app-debug.apk`.
- Hash and manual checks cover finite/unknown timelines, scrubbing, lifecycle,
  and existing speed-menu validation boundary.
- Artifact stays ignored under `app/build/`.
