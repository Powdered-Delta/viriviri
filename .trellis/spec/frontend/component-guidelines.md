# Component Guidelines

> How components are built in this project.

---

## Overview

Compose UI components are shared between the Horizon OS 2D host and embedded
immersive panel Activities. They receive state and actions from
`ViriViriAppState` rather than owning routing, protocol calls, or media output.

---

## Component Structure

### Convention: Host actions as callbacks

**What**: UI components expose callbacks for platform actions such as entering
immersive mode or returning to the 2D panel.

**Why**: The same UI can be hosted by Meta today and PICO later without
importing vendor SDKs in shared UI code.

**Example**:

```kotlin
@Composable
fun BrowseScreen(
    videos: List<VideoSummary>,
    onSelectVideo: (VideoId) -> Unit,
    onEnterImmersive: () -> Unit,
) {
    // Render shared UI only.
}
```

### Convention: Offline QWERTY Input Method and Search Routes

**What**: Search input uses a pure Kotlin `SearchInputMethod` contract. The
first-party implementation is `ChinesePinyinQwertyInputMethod`, registered
through `SearchInputMethodRegistry`. It owns Pinyin composition, continuous
candidate generation, key semantics, and candidate consumption; shared Compose
UI only renders the method-provided session and keyboard layout.

**Why**: QWERTY Pinyin can evolve independently from Compose browse UI,
`ViriViriAppState`, the Bilibili provider, player ownership, and Spatial panel
lifecycle. Candidate generation remains offline and produces continuous
composition candidates such as `woshi -> 我是` rather than requiring a user to
commit each character separately.

**Example**:

```kotlin
val methods = SearchInputMethodRegistry(listOf(ChinesePinyinQwertyInputMethod()))
val appState = ViriViriAppState(context, inputMethods = methods)
```

The input-method session must not expose a single-character/phrase UI mode.
`OfflinePinyinLexicon.candidatesFor()` is the single candidate contract and
returns ordered continuous candidates with consumed-composition metadata.

Search itself is a center-workspace route:

```text
RECOMMENDATIONS -> SEARCH_EMPTY -> SEARCH_RESULTS
```

- `SEARCH_EMPTY` renders search history and recommended keywords, never a
  video list. Opening this route does not show the keyboard; the near-field panel
  appears only after the query field receives focus or the explicit keyboard action
  is used.
- `SEARCH_RESULTS` renders the result list after committing a query and
  synchronously hides the app-owned keyboard panel.
- The query field belongs in the center workspace header, not inside the
  keyboard panel.
- The keyboard panel is a single near-field `input_method_panel` Spatial Compose
  panel. It is parented to MediaStage and positioned below the center content,
  above and in front of Transport, but it is never a video Surface or player owner.
  Its number, keyboard, and options surfaces are Compose children inside that one panel.
- Keyboard geometry follows the active `InputConsoleSkin`: the number zone uses
  a calculator-oriented `7-9/+`, `4-6/-`, `1-3/x`, `0/./=/÷` grid and the
  QWERTY/symbol space key has a wider weight than adjacent punctuation keys.

System IME input is an optional host callback that updates the same committed
query but must not silently start a search.

### Convention: Transient Messages

**What**: Short-lived feedback uses the shared `TransientMessageState` reducer
and `TransientMessageHost`, not Android system Toasts or ad hoc panel code.
Core owns the FIFO queue and message validity; the Compose host owns the
visible timeout and dispatches `Advance`, `Dismiss`, or `ActionTriggered` back
to its host callback.

**Why**: The same message behavior works in a Horizon OS 2D window and an
immersive panel without adding a platform object, a second panel, a video
Surface, or a coroutine to core state. A persistent inline error remains the
source for recovery; a transient ERROR message is supplemental feedback.

**Required behavior**:

- Place `TransientMessageHost` in an existing Compose overlay slot; do not
  encode spatial coordinates or create a scene entity in the shared host.
- Dispatch actions through explicit callbacks. Shared code must not issue retry,
  routing, player, or network operations itself.
- Resolve severity styling from `CinemaPalette` roles. Do not hardcode colors.
- A host must render at most the reducer's current item; pending messages remain
  FIFO until timeout, dismissal, or action advances the queue.

### Convention: Semantic Palette Tokens

**What**: Shared visual components resolve semantic colors from
`CinemaPalette` / `CinemaColorRole`, including compact content markers such as
`CHARGING_BADGE` and `CHARGING_BADGE_LABEL`.

**Why**: Theme presets remain replaceable and independently validated. A
component may describe a badge's role, but it must not embed an RGB/hex color
that silently diverges from light or high-contrast themes.

**Example**:

```kotlin
val badgeColor = palette.composeColor(CinemaColorRole.CHARGING_BADGE)
```

### Convention: Distinct low/high immersion layouts

**What**: Low-immersion browse UI and high-immersion UI should be separate entry
composables.

**Why**: viriviri's high-immersion mode is not a simple enlarged 2D panel; it
has a different layout and later a custom spatial scene.

---

## Props Conventions

- Prefer immutable Kotlin data classes such as `Recommendation` and
  `ViriViriUiState`.
- Prefer explicit callbacks over passing Activity, Context, or SDK objects.
- Keep default preview/sample data in clearly named placeholders until real
  Bilibili data exists.

---

## Styling Patterns

Use the project's existing Compose Material dependency consistently. Spatial
panel registration, materials, and scene integration belong in
`SpatialVideoSampleActivity`, not in shared composables.

### Convention: Atomic Workbench rail components

**What**: Build Workbench rails from small shared atoms such as panel shell,
section, title, secondary text, action strip, creator row, fixed footer action,
and full-height collapse. Every atom receives one immutable
`WorkbenchPanelStyle` derived from `CinemaPalette`; app-level composables bind
only product data, availability, and callbacks.

**Why**: Browse and Detail may occupy the same existing angled Spatial panel.
Shared atoms keep their typography, surfaces, spacing, border, accent, and
disabled treatment coherent without creating another panel or copying colors
into route-specific composables.

**Forbidden**:

- Do not hardcode route-specific RGB values inside a Workbench rail atom.
- Do not import Meta Spatial SDK, Media3, Bilibili, Activity, player, Surface,
  or network APIs into shared atoms.
- Do not create a Spatial panel to represent a header, footer, comments mode,
  keyboard layer, or another internal state of an existing rail.

---

## Accessibility

Panel UI should keep text readable in a resizable Horizon OS panel. Use scalable
typography and avoid fixed assumptions about physical panel size.

---

## Common Mistakes

### Common Mistake: importing platform SDKs into shared UI

**Symptom**: A Compose screen cannot be hosted outside the current Spatial
Activity or creates a second video output.

**Cause**: Shared composables directly depend on Meta Spatial SDK, Activity
classes, protocol adapters, or `Surface` ownership.

**Fix**: Keep platform behavior in the Activity or `PlayerSession`, then pass
neutral state and callbacks into shared composables.
