# Archive Spatial Foreground Backdrop Validation APK

## Goal

Preserve the debug APK that separates the existing Spatial video foreground from
a translucent black backdrop and embeds a visible Git hash for headset-side
build identification.

## Scope

- Copy current debug APK with a foreground/backdrop milestone name.
- Write ignored sidecar with SHA-256, `BuildConfig.GIT_SHA`, feature behavior,
  automated verification, and future Quest validation sequence.
- Record explicitly that this artifact was not installed or deployed.

## Non-Goals

- No source/device/deployment changes.
- Do not claim visual acceptance before manual Quest validation.

## Acceptance Criteria

- Named APK and sidecar are ignored under `app/build/`.
- Hash and embedded `DEV c0dabe06` label are recorded.
- Checklist covers black backdrop, portrait contain, landscape continuity,
  SceneMesh dynamic commit, and no-extra-output invariant.
