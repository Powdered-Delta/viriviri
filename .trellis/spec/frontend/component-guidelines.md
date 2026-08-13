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

### Convention: Extensible Offline Input Methods

**What**: Search input methods implement the pure Kotlin `SearchInputMethod`
contract and are registered through `SearchInputMethodRegistry`. The shared
`SearchInputPanel` only renders the method-provided session, candidates, and
keyboard rows.

**Why**: Chinese T9, Japanese kana, Korean, and future language layouts can use
separate offline dictionaries without adding language conditionals to Compose
browse UI, `ViriViriAppState`, or the Bilibili provider.

**Example**:

```kotlin
val methods = SearchInputMethodRegistry(listOf(MyKanaInputMethod(), ChineseT9InputMethod()))
val appState = ViriViriAppState(context, inputMethods = methods)
```

A method must keep text conversion and candidate generation offline unless its
own documented contract explicitly says otherwise. System IME input is an
optional fallback that updates the same committed query but must not silently
start a search.

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

* Prefer immutable Kotlin data classes such as `Recommendation` and
  `ViriViriUiState`.
* Prefer explicit callbacks over passing Activity, Context, or SDK objects.
* Keep default preview/sample data in clearly named placeholders until real
  Bilibili data exists.

---

## Styling Patterns

Use the project's existing Compose Material dependency consistently. Spatial
panel registration, materials, and scene integration belong in
`SpatialVideoSampleActivity`, not in shared composables.

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
