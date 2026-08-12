# Archive Media List and Aspect Validation APK

## Goal

Preserve the installed debug APK for list pagination, thumbnail rendering, and
immersive video-aspect fixes with a traceable Quest validation checklist.

## Scope

- Copy the current debug APK under a milestone-derived name beside Gradle output.
- Record hash, installation result, behavioral scope, automated verification,
  and manual Quest checks in an ignored sidecar.
- Keep default Gradle output unchanged.

## Non-Goals

- No source, device, Gradle, or deployment changes.
- Do not claim device acceptance until manual results are reported.

## Acceptance Criteria

- Named APK/sidecar remain ignored under `app/build/`.
- Sidecar covers recommended/search next-page append, covers, portrait fit,
  standard/non-standard landscape, and prior transport behavior.
- SHA-256 matches the APK installed on Quest 2.
