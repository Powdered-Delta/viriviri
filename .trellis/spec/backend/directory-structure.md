# Directory Structure

> How backend code is organized in this project.

---

## Overview

The `:core` module owns platform-neutral business, data, network, repository,
and playback state contracts. It is the reusable foundation for Quest-first
Meta Spatial SDK and future PICO Spatial SDK app modules.

---

## Directory Layout

```
core/
└── src/main/java/com/viriviri/core/
    ├── model/       # Domain models such as VideoSummary and ImmersionState
    ├── network/     # Bilibili API contracts, no platform UI/runtime code
    ├── repository/  # Data access interfaces and placeholder implementations
    └── state/       # Playback and surface state models
```

---

## Module Organization

### Convention: Core has no spatial SDK dependencies

**What**: `:core` must not import Meta Spatial SDK, PICO Spatial SDK, Android UI,
Jetpack Compose, Activity, or View types.

**Why**: The same Bilibili data flow must be reusable across Meta and PICO
platform app modules.

### Boundary Contract

| Module | Responsibility | Forbidden imports |
| --- | --- | --- |
| `:core` | Domain models, repository contracts, network contracts, playback state | Meta Spatial SDK, PICO Spatial SDK, Compose UI, Activity |
| `:ui-compose` | Shared UI rendering | Meta Spatial SDK, PICO Spatial SDK |
| `:app-meta` | Meta Hybrid manifest, Activities, Spatial SDK, transitions | PICO Spatial SDK |

### Good/Base/Bad Cases

* Good: `VideoRepository` returns `VideoSummary` and knows nothing about the
  panel or immersive host.
* Base: placeholder repository data is acceptable before real Bilibili APIs are
  implemented.
* Bad: `VideoRepository` starts an Activity or reads Horizon OS panel state.

---

## Naming Conventions

* Domain data classes use neutral names: `VideoSummary`, `VideoDetail`,
  `ImmersionState`, `PlaybackSurfaceState`.
* Platform-specific classes must include the platform in their module or package
  path, such as `com.viriviri.app.meta`.

---

## Examples

* `core/src/main/java/com/viriviri/core/model/VideoModels.kt`
* `core/src/main/java/com/viriviri/core/repository/VideoRepository.kt`
