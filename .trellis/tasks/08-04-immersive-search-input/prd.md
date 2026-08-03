# Immersive Search Input

## Goal

Add a minimal search entry to the immersive recommendation panel. The entry must
accept text through the Horizon OS system virtual keyboard rather than a custom
spatial keyboard. Also correct the 2D player output so source video keeps its
aspect ratio and unused space is filled black rather than stretching the image.
Both changes must preserve the existing single-player and panel lifecycle
contracts.

## What I Already Know

- The active Quest application is the root Gradle `:app` module.
- `MoviePanel` is an embedded `ComponentActivity` that already hosts the
  Compose recommendation panel.
- `AndroidManifest.xml` already declares the optional
  `com.oculus.feature.VIRTUAL_KEYBOARD` and `oculus.software.overlay_keyboard`
  features.
- The shared panel UI is currently in `RecommendationUi.kt`; it must not create
  Spatial entities, player Surfaces, or an Activity route.
- The 2D player currently hosts a raw `TextureView` at a fixed layout size; it
  does not yet react to Media3 video dimensions or provide a black letterbox
  background.
- `BilibiliPlaybackProvider` already owns Bilibili endpoint and WBI signing
  details, so search protocol code belongs there rather than in the UI.

## Requirements

- The immersive recommendation panel exposes a search entry.
- Focusing the input invokes the Horizon OS system virtual keyboard through the
  normal Android input-method path; no custom 3D keyboard is created.
- Search UI state is shared with the application state rather than being held in
  a Spatial SDK object.
- The 2D video output preserves the source aspect ratio, centers the visible
  image, and fills unused area with black; it must not crop or stretch the
  source frame.
- The feature must not create a second player, Surface, panel entity, or alter
  immersive/2D routing.

## Decision (ADR-lite)

**Decision**: Submit search text to Bilibili's WBI-signed video search endpoint
and render mapped video results through the existing recommendation list UI.
The search field is a normal focusable Compose input hosted by the embedded
panel Activity, so Horizon OS owns the virtual keyboard overlay.

**Consequences**: Search inherits the public endpoint's rate-limit and contract
risks. The provider maps only video results in this slice; suggestions, filters,
history, live, article, and mixed-type results remain out of scope.

## Acceptance Criteria

- [ ] A user can focus an immersive search field and see the Horizon OS virtual
  keyboard.
- [ ] Text submission is received by app state without crashing the embedded
  panel.
- [ ] The recommendation panel remains usable after the keyboard is dismissed.
- [ ] A non-matching source/video-container aspect ratio shows black letterbox
  or pillarbox space with an undistorted, centered frame.
- [ ] Existing playback and 2D/immersive switching remain unchanged.

## Out of Scope

- A custom spatial keyboard.
- Login, cookies, or other credentials.
- Search history, filters, suggestions, and non-video search result types.

## Technical Notes

- UI host: `app/src/main/java/com/m0e_n00b/viriviri/MoviePanel.kt`.
- Shared UI: `app/src/main/java/com/m0e_n00b/viriviri/RecommendationUi.kt`.
- Provider boundary: `app/src/main/java/com/m0e_n00b/viriviri/BilibiliPlaybackProvider.kt`.
