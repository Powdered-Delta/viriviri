# Component Guidelines

> How components are built in this project.

---

## Overview

Compose UI components are shared between low-immersion and high-immersion
surfaces. They receive platform actions as callbacks and receive domain data
from `:core` models.

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

### Convention: Distinct low/high immersion layouts

**What**: Low-immersion browse UI and high-immersion UI should be separate entry
composables.

**Why**: viriviri's high-immersion mode is not a simple enlarged 2D panel; it
has a different layout and later a custom spatial scene.

---

## Props Conventions

* Prefer immutable Kotlin data classes from `:core`.
* Prefer explicit callbacks over passing Activity, Context, or SDK objects.
* Keep default preview/sample data in clearly named placeholders until real
  Bilibili data exists.

---

## Styling Patterns

Use Material 3 for initial scaffolding. Spatial-specific materials, panels, or
scene integration belong in platform modules, not in `:ui-compose`.

---

## Accessibility

Panel UI should keep text readable in a resizable Horizon OS panel. Use scalable
typography and avoid fixed assumptions about physical panel size.

---

## Common Mistakes

### Common Mistake: importing platform SDKs into shared UI

**Symptom**: A future PICO app cannot reuse the UI module.

**Cause**: Shared composables directly depend on Meta Spatial SDK, Activity
classes, or PICO SDK types.

**Fix**: Move platform behavior to `:app-meta` or a future `:app-pico`, then
pass neutral callbacks and state into shared composables.
