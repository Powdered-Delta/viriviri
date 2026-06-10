# Directory Structure

> How frontend code is organized in this project.

---

## Overview

viriviri's user interface is organized as a shared Jetpack Compose module that
can be hosted by vendor-specific spatial app modules. UI code must not import
Meta Spatial SDK or future PICO Spatial SDK APIs directly.

---

## Directory Layout

```
ui-compose/
└── src/main/java/com/viriviri/ui/
    ├── browse/      # Low-immersion 2D panel UI
    └── immersive/   # High-immersion panel/control UI
```

---

## Module Organization

### Convention: Shared Compose module

**What**: Shared screens live in `:ui-compose`. Platform modules such as
`:app-meta` host these composables but do not own reusable UI state or layout
logic.

**Why**: This keeps Quest-first UI reusable when a future `:app-pico` platform
module is added.

**Good case**:

```kotlin
// app-meta hosts shared UI.
setContent {
    BrowseScreen(onEnterImmersive = ::launchImmersive)
}
```

**Bad case**:

```kotlin
// Do not import Meta/PICO SDK classes in ui-compose.
@Composable
fun BrowseScreen(metaSpatialObject: MetaSpatialObject) = Unit
```

### Boundary Contract

| Module | May depend on | Must not depend on |
| --- | --- | --- |
| `:ui-compose` | `:core`, AndroidX Compose | Meta Spatial SDK, PICO Spatial SDK |
| `:app-meta` | `:core`, `:ui-compose`, Meta Spatial SDK | PICO Spatial SDK |

---

## Naming Conventions

* Low-immersion panel UI belongs under `com.viriviri.ui.browse`.
* High-immersion UI belongs under `com.viriviri.ui.immersive`.
* Entry composables should be named by surface, such as `BrowseScreen` and
  `ImmersiveScreen`.

---

## Examples

* `ui-compose/src/main/java/com/viriviri/ui/browse/BrowseScreen.kt`
* `ui-compose/src/main/java/com/viriviri/ui/immersive/ImmersiveScreen.kt`
