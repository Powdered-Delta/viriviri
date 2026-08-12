# Archive Spatial Content Aspect Validation APK

## Goal

Preserve the installed debug APK that dynamically contains the existing Spatial
video content quad according to Media3 video dimensions.

## Scope

- Copy current debug APK under the content-aspect milestone name.
- Create ignored sidecar with hash, installation result, scope, automated
  verification, and specific portrait/landscape Quest checks.
- Preserve default Gradle output.

## Non-Goals

- No source/device/deployment change beyond the already completed installation.
- Do not claim the geometry fix is accepted without Quest observation.

## Acceptance Criteria

- Named ignored APK/sidecar match installed debug build hash.
- Checklist distinguishes portrait contain, standard landscape, ultrawide, and
  prior right-edge-strip behavior while checking Player/Surface continuity.
