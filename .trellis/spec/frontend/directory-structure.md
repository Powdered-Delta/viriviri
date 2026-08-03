# Directory Structure

> How frontend code is organized in this project.

---

## Overview

ViriViri currently uses one Quest-focused Android application module. Compose UI
is grouped by feature under the app package; Spatial SDK registration and Surface
ownership remain in the immersive Activity and must not leak into reusable UI
composables.

---

## Directory Layout

```
app/src/main/java/com/m0e_n00b/viriviri/
├── RecommendationUi.kt          # Shared Compose browse/viewer content
├── ViriViriAppState.kt          # Application-scoped session state
├── BilibiliPlaybackProvider.kt   # Platform protocol adapter
├── PancakeActivity.kt            # Horizon OS 2D host
└── SpatialVideoSampleActivity.kt # Immersive Spatial SDK host
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
