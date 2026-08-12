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

## Revision: Offline Extensible Input Board

### Goal

Replace the system-IME-only search field with an application-owned input board
that works when Horizon OS does not provide a Chinese IME. The default board is
a Bilibili-TV-style multi-tap nine-key Pinyin input method with offline Chinese
candidate generation. The system keyboard remains available as an optional
input source, but search must only begin through an explicit `确定搜索` action.

### Requirements

- The search UI is a reusable input-panel composable, not inline text-field
  logic inside the recommendation list.
- The panel provides current input, Pinyin composition, candidate selection,
  a nine-key layout, `清空输入`, `确定搜索`, and an icon-only system-keyboard
  action.
- The default Chinese input method works without network access. It must accept
  multi-tap Pinyin, show offline Hanzi or phrase candidates, and append a
  selected candidate to the committed search text.
- The input-method engine is a pure Kotlin extension point. It exposes its own
  keyboard layout, composition/candidate state, and event reducer. A developer
  can register another language engine and offline lexicon without changing the
  Compose input panel, `ViriViriAppState`, or Bilibili provider.
- System-keyboard text updates the same committed query state and clears only
  the active custom-method composition. It must not issue a search implicitly.
- The panel has no Meta Spatial SDK, Activity-routing, player, Surface, or
  Bilibili-protocol dependency.

### Validation

- Unit tests cover multi-tap letter cycling, offline Pinyin phrase candidates,
  candidate commit, deletion, reset, and registry selection for a custom
  language method.
- `:app:testDebugUnitTest` and `:app:assembleDebug` pass.
- Quest validation verifies hand/controller clicks, Chinese candidate commit,
  explicit search, clear, and system-keyboard fallback in the embedded panel.

### Follow-up And Known Limitations

- The nine-key board still uses explicit multi-tap entry. A future interaction
  redesign must support candidate generation from every letter represented by
  the entered number sequence; that work is intentionally deferred.
- Candidate mode presents `词组` first and `单字` second. The bundled offline
  lexicon is a compact first-party dictionary, not a full frequency-ranked IME
  language model, so ranking and coverage remain limited.
- Search input text styling and candidate-strip layout are dark-workbench-safe;
  the composition line and candidate strip reserve fixed height so typing does
  not shift the keypad. The latest mode-switch behavior has unit/build
  coverage, but has not yet had a dedicated Quest interaction regression pass.
