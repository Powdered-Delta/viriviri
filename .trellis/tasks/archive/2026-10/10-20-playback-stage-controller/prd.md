# Refactor playback stage geometry ownership

## Acceptance target
One controller owns stage geometry and pending/applied lifecycle. Video and SDK aperture share final physical dimensions; all layers share scale/curvature and the verified absolute base-radius anchor. Danmaku remains full viewport without aspect squeezing.

## Current failure and scope
User reports headset acceptance still fails after coordinate/crop patch. Source currently contains independent video rebuild, overlay replay, one-shot video replay, creation-time and debug pose writes. Refactor these into one batch with latest-state coalescing and explicit panel readiness. Preserve existing player, Surface, scene hierarchy, input/visibility owners and coordinate signs. No new static entities or device install/launch.

## Design
- PlaybackStageController owns immutable tuning/source inputs, derived layout and pending revision.
- Resolve all existing stage panels before applying; absent/replaced panels receive the newest complete layout, never an acknowledged partial update.
- PlaybackStagePanelRenderer is the only SDK geometry writer: shapes, UV selection, unit scale, local carrier poses and native world poses in one synchronous batch.
- Frame system flushes controller; creation and debug controls only submit/invalidate.
- SDK-backed math and scheduling regression cases cover delayed creation, rapid tuning/source changes, replacement, rotated stage, flat restoration and disposal.

## Validation
Windows unit tests/debug build, diff hygiene and source/doc identifier checks. Headset visual acceptance remains pending unless separately authorized.

## Completed host verification
- scripts/build-windows-debug.ps1 passed on Windows after the final review patch.
- 201 JVM tests, zero failures/errors, including 10 controller/SDK-pose scheduling regressions.
- UI documentation has no newly unresolved bare identifiers; git diff --check passed; existing LF preserved.
- Activity independent geometry/replay paths removed; source/parameters only submit; one frame owner and renderer apply group.
- APK SHA-256: F6B8B66A89C3B95BED2CF32DFE7398062191D46635BFF2DA41D9A3DD1103F524 (DEV/build SHA 0c278276).
- No installation or device launch. Headset visual acceptance is pending; host synchronous submission does not imply a native compositor transaction.
