# Playback stage specification review

## Original review scope (superseded by implementation approval below)
User confirms 4:3/portrait texture fix passes on headset, but aperture drift still fails. User explicitly requests specification first and review before further implementation. Produce a Chinese review document covering current facts, proposed geometry/layer contracts, evidence gaps and acceptance. No runtime edits, build, deploy or device mutations.

## Artifact
- docs/playback-stage-rendering-spec.md: draft for user review.
- Keep factual current-coordinate/UI docs linked and distinguish texture acceptance from unresolved aperture drift.

## Original review state
Waiting for user review of product conventions (aperture extent, full-stage danmaku, center anchoring, scale-versus-radius semantics, layer order) and acceptance criteria. Do not activate implementation before approval.

## Existing accepted change
Texture repair is already committed as 0f8115c6e5c4f06f1fe4e7199784fdd3067f051a; user also explicitly permits that commit. Do not repeat it or mix runtime changes into this documentation work.

## User steering: scale and component identity audit
User explicitly confirms whole-group scaling: effective radius r0*s, carrier local Z (base-r0)*s. Inspect curvature versus scale delivery, identify the actual black curved window, and create a diagram with diagram-design using the local project design system. Current runtime remains unchanged pending review; investigation and spec/diagram edits are authorized. Actual compiled controller probe confirms width/height scale but radius and local Z do not. User identifies a curved black window revealing video; outside becomes grid-like translucent when workbench opens. Do not prematurely identify that appearance with one mesh.

## Implementation authorized (2026-10-21)
User approved proceeding with the confirmed scaling correction. Implement effective radius r0*s and local carrier Z (base-r0)*s for the complete existing panel group, keeping raw control/persistence radius independent of rendered radius. Preserve full-frame texture mapping and full-stage danmaku. Verify scale/radius transitions, delayed readiness, restored parameters, flat resets and rotated parents; run Windows debug verification. Update current-state docs and distinguish historical probes from the fixed behavior. Headset drift acceptance remains pending; no device writes authorized.

## Verification result
Windows scripts/build-windows-debug.ps1 passed :app:testDebugUnitTest and :app:assembleDebug. UI identifiers and diagram structural checks passed; git diff --check clean. APK build DEV c4bce0f5; SHA256 EED2D9E8CE2F49B136E27E8EA8C18A80251F5C29276C41CA172D1D33030A405F. Headset acceptance pending, no install/launch performed.
