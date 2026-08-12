# Directory Structure

> How frontend code is organized in this project.

---

## Overview

ViriViri uses a Quest-focused Android application module, a pure Kotlin
workbench-core module, and a Compose-only workbench-compose module. Compose UI
is grouped by feature under the app package or reusable Compose module; Spatial
SDK registration and video Surface ownership remain in the immersive Activity
and must not leak into reusable UI composables or workbench contracts.

---

## Directory Layout

```
app/src/main/java/com/m0e_n00b/viriviri/
├── RecommendationUi.kt          # Shared Compose browse/viewer content
├── ViriViriAppState.kt          # Application-scoped session state
├── BilibiliPlaybackProvider.kt   # Platform protocol adapter
├── PancakeActivity.kt            # Horizon OS 2D host
└── SpatialVideoSampleActivity.kt # Immersive Spatial SDK host

spatial-workbench-core/
└── src/main/kotlin/.../core/     # Pure theme, slot, layout, component, and action contracts

spatial-workbench-compose/
└── src/main/java/.../compose/    # Compose panel shells and layout primitives
```

---

## Module Organization

### Convention: Shared composables with host callbacks

**What**: Compose functions receive `ViriViriUiState` and callbacks/state
actions. `PancakeActivity` and embedded panel Activities host the same content
without giving UI direct ownership of Activity routing or output Surfaces.

**Why**: This preserves a future extraction path without adding Gradle modules
before they are needed.

**Good case**:

```kotlin
setContent {
    RecommendationContent(state, appState, showPlayer = true)
}
```

**Bad case**:

```kotlin
@Composable
fun BrowseScreen(metaSpatialObject: MetaSpatialObject) = Unit
```

### Boundary Contract

| Area | May depend on | Must not depend on |
| --- | --- | --- |
| `RecommendationUi.kt` | AndroidX Compose, app state | Meta Spatial SDK entities, `Surface`, Activity routing |
| `PancakeActivity.kt` | Compose, Android Activity APIs | Immersive panel construction |
| `SpatialVideoSampleActivity.kt` | Meta Spatial SDK, application state | A second ExoPlayer or protocol parsing |
| `:spatial-workbench-core` | Kotlin standard library | Android, Compose, Meta SDK, Media3, Bilibili, Activity, Surface, network |
| `:spatial-workbench-compose` | AndroidX Compose, `:spatial-workbench-core` | Meta SDK, Media3, Bilibili, Activity, player or video Surface ownership |

---

## Naming Conventions

* Shared UI names describe content, such as `RecommendationContent` and
  `PlayerOutput`.
* Android hosts retain Activity suffixes; protocol adapters retain platform
  names, such as `BilibiliPlaybackProvider`.

---

## Examples

* `app/src/main/java/com/m0e_n00b/viriviri/RecommendationUi.kt`
* `app/src/main/java/com/m0e_n00b/viriviri/PancakeActivity.kt`
