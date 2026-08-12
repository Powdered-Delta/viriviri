# Archive Immersive Browse Validation APK

## Goal

Preserve the debug APK containing the reachable immersive Browse Canvas flow and
write a companion validation note for later Quest inspection.

## Scope

- Copy default debug APK beside Gradle output using the Browse milestone name.
- Write sidecar with SHA-256, included Browse entry/selection/exit behavior,
  automated verification, manual Quest checklist, and Context scene limitation.
- Leave default Gradle output untouched.

## Non-Goals

- No code, version, manifest, deployment, or device modification.
- Do not claim independent Context panel visibility; it remains blocked by the
  scene-authored `mr_panel` parent relationship.

## Acceptance Criteria

- Archived APK/sidecar are co-located with default debug output.
- Hash and unperformed device validation are explicit.
- Default `app-debug.apk` remains available.
