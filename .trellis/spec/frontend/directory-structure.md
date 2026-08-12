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
### Convention: Theme Tokens Stay Visual

**What**: Reusable Compose controls accept immutable visual tokens such as
`InputConsoleStyle` and `CinemaInputConsoleActions`. `CinemaPalette` semantic
roles are mapped to Compose colors at the visual boundary; reducers and host
state remain callbacks/data supplied by the app.

**Required behavior**:

- Keep composition and collapsed candidate rows at stable heights even when
  their contents are empty or change.
- Render expanded candidates in a topmost popup positioned above the candidate
  strip; the popup must not push the keyboard geometry.
- Route query-header Voice and keyboard Voice through the same host callback.
  System IME is a separate explicit callback.
- Keep style tokens free of player, Surface, Activity, Meta SDK, network, or
  input reducer ownership.

**Forbidden**:

- Theme JSON or Compose styling may not dispatch arbitrary Kotlin, network
  requests, player operations, or SearchSession transitions.
- Do not place renderer coordinates or CSS-like top offsets in Compose/core
  theme tokens.

| `:spatial-workbench-compose` | AndroidX Compose, `:spatial-workbench-core` | Meta SDK, Media3, Bilibili, Activity, player or video Surface ownership |

### Convention: Core Contracts Validate, Adapters Execute

**What**: `:spatial-workbench-core` owns immutable theme contracts and deterministic
validation only. It may express semantic slots, layout modes, canvas composition,
presentation policies, palette roles, browse-origin snapshots, and input
composition transitions. It must not perform rendering, hit testing, scene-anchor
binding, timer scheduling, cache eviction, player control, Surface handoff, or
network work.

**Why**: Themes remain portable and testable on the JVM. Platform adapters own
Meta scene anchors and spatial panel behavior; application state owns result
snapshots and input-session persistence; media ownership stays with the existing
single-player host.

**Required validation**:

- A `TRANSPORT` placement is a `FRONT_OF_PARENT` relation to `MEDIA_STAGE`, not a
  renderer coordinate.
- A visible-overflow canvas preserves hit testing and references only registered
  component-group members.
- `PERSISTENT` slots are ineligible for default canvas hiding.
- Semantic palette presets meet role contrast/range validation.

### Convention: MediaStage Runtime Is Host-Neutral

**What**: `:spatial-workbench-core` owns `MediaStageState`, renderer target
identity, presentation/geometry, Media3-adapter clock snapshots, and declarative
lifecycle effects. It never owns a platform handle. A host adapter maps a target
ID to its `TextureView` Surface, SDK `PanelSceneObject` Surface, or overlay
renderer instance and executes the returned effect.

**Why**: The redesigned 2D host and the Quest Spatial host must consume the
same stage lifecycle without turning current legacy UI into the canonical
renderer. `PlayerSession` remains the sole process-wide owner of ExoPlayer and
its one active video Surface.

**Required behavior**:

- `VIDEO_OUTPUT` targets are exclusive: zero or one may be active, and target
  replacement detaches the old identity before attaching the new identity.
- `FLAT_OVERLAY` and `SPATIAL_OVERLAY` targets are never video output Surfaces;
  they can coexist with the one video target.
- A seek, target disable/removal, or Stage geometry/presentation change emits
  explicit overlay cleanup effects. A stale detach must not clear a newer video
  target.
- `STAGE_LOCKED` overlay targets clear on a stage geometry/presentation change;
  `GAZE_LOCKED` targets are preserved unless their own lifecycle changes.

**Forbidden**:

- Do not store `Surface`, `TextureView`, `PanelSceneObject`, Meta `Entity`,
  `ExoPlayer`, coroutine job, or renderer object in a core MediaStage contract.
- Do not add an overlay as a second video output or create a second player to
  support a new MediaStage presentation.
- Do not connect overlay rendering to the existing Pancake or Spatial host as a
  shortcut before the corresponding renderer adapter and redesigned UX exist.

### Convention: Overlay Allocation Stops Before Rendering

**What**: The core overlay allocator validates topology and produces immutable
`group + lane + layer + surface + styleSnapshot` assignments. It may use
injected metrics and projection interfaces, but it must not shape glyphs, read
head pose, create entities, attach a video Surface, or issue source/translation
requests.

**Why**: Local track collision and viewer-projection occlusion depend on real
renderer measurements and platform pose data. Keeping them as adapter-provided
contracts avoids fake geometry in JVM core while preserving deterministic,
testable allocation inputs.

**Required behavior**:

- Surface `basicStyle`, layer override, and event override resolve into one
  immutable style snapshot at assignment time.
- A danmaku layer may reference only a surface that declares `DANMAKU` support.
- Disabled, incompatible, full, or topology-invalid targets are never assigned.
- Caption target selection remains independent from danmaku group allocation.


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
