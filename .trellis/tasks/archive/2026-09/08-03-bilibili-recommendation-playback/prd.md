# Bilibili Recommendation Browsing and Playback

## Goal

Make recommendations the first screen shown by ViriViri. A user can select a
recommended video, enter a video-viewing screen, and return to the
recommendation list while preserving the existing Horizon OS 2D and immersive
mode transition.

## What I Already Know

- The active application is `app` on branch `main`.
- `SpatialVideoSampleActivity` remains the VR Launcher and owns the existing
  Horizon OS Home plus `PendingIntent` route to `PancakeActivity`.
- `PancakeActivity` is currently a static XML screen with its return-to-
  immersive action in the body.
- The existing immersive player is an Activity-local `ExoPlayer`; it cannot yet
  render into the 2D Activity.
- The Bilibili recommendation endpoint can return public recommendation metadata
  without credentials, but it returns a video page URL rather than a stable
  Media3-ready stream URL.
- `temp/PiliPlus` is reference-only and must not be committed.
- `temp/TRELLIS_PYTHON.md` documents that Trellis scripts must use
  `C:\\ProgramData\\miniconda3\\python.exe` in this environment.

## Requirements

- Default application content is a recommendation list rather than an
  automatically selected bundled video.
- A recommendation item is selectable and opens a video-viewing screen.
- The viewing screen has an action to return to recommendations.
- Retain the existing immersive-to-2D and 2D-to-immersive route.
- Put the 2D return-to-immersive action in the main container header.
- Keep recommendation data and navigation state shared between immersive and 2D
  UI hosts.
- Do not regress the current panel return-recentering behavior or introduce a
  second player / media Surface during a route transition.

## Decision (ADR-lite)

**Context**: Recommendation metadata only includes a Bilibili video page URL;
the app needs to render selected videos in both Horizon OS 2D and immersive
surfaces while preserving a single playback session.

**Decision**: Implement an in-app Bilibili playback provider. It resolves video
details for `cid`, obtains WBI keys, signs the Bilibili playurl request, maps
supported DASH streams into Media3 media sources, and keeps the protocol details
outside shared recommendation and UI state. A single application-scoped Media3
player switches between the 2D TextureView and immersive Spatial SDK Surface.

**Consequences**: The provider must handle changing public endpoint contracts,
missing compatible DASH tracks, HTTP/API/parse failures, and device validation.
The app does not send user credentials or playback history heartbeats; local
state is the only progress persistence for this phase.

## Acceptance Criteria

- [ ] Launching the app first presents recommendations in immersive mode.
- [ ] Selecting an item opens the selected video-viewing destination.
- [ ] The viewer can return to the recommendation list.
- [ ] The 2D header contains the return-to-immersive action.
- [ ] The existing 2D/immersive route still works.
- [ ] Recommendation data and selected item state remain consistent across both
  UI hosts.
- [ ] The same Media3 playback session resumes in either 2D or immersive output
  after a successful route transition.

## Definition of Done

- Unit tests cover newly introduced state and data transformations.
- `:app:testDebugUnitTest` and `:app:assembleDebug` pass with the documented
  export exclusion.
- The Quest runtime notes document the implemented viewing behavior and test
  results.

## Out of Scope (Unless the Open Question Selects It)

- Login, cookies, SESSDATA, access keys, or other user credentials.
- Changing the established OpenXR/Horizon OS lifecycle workaround boundary.
- A broad Gradle multi-module migration.

## Technical Notes

- Existing immersive routing and return recentering:
  `app/src/main/java/com/m0e_n00b/viriviri/SpatialVideoSampleActivity.kt`.
- Existing 2D host:
  `app/src/main/java/com/m0e_n00b/viriviri/PancakeActivity.kt`.
- Bilibili recommendation handoff:
  `temp/handoff-bilibili-recommendations.md`.
- Reference API organization only:
  `temp/PiliPlus/lib/http/video.dart`.
- PiliPlus demonstrates the required protocol sequence in
  `temp/PiliPlus/lib/http/video.dart` (`videoUrl`) and
  `temp/PiliPlus/lib/pages/video/controller.dart` (`queryVideoUrl`), but its
  Dart, libmpv, request emulation, and credential code must not be copied.
