# Directory Structure

> How domain and provider code is organized in this project.

---

## Overview

The current `:app` module contains the domain, provider, playback state, UI,
and Meta Spatial SDK host while the project remains a single-module Quest MVP.
Package/file boundaries preserve a future extraction path without maintaining a
second unused Gradle architecture.

---

## Directory Layout

```
app/src/main/java/com/m0e_n00b/viriviri/
├── BilibiliPlaybackProvider.kt  # Bilibili protocol, DTO parsing, DASH source
├── ViriViriAppState.kt          # Recommendation and playback session state
├── RecommendationUi.kt          # Shared Compose rendering
└── *Activity.kt                 # 2D and immersive platform hosts
```

---

## Module Organization

### Convention: Protocol boundary stays out of UI

**What**: `BilibiliPlaybackProvider` owns API URLs, WBI signing, JSON parsing,
and DASH source construction. UI and Activities consume `Recommendation`,
`MediaSource`, and application state instead of Bilibili DTOs.

**Why**: A future platform adapter can replace provider behavior without
rewriting recommendation state or UI.

### Boundary Contract

| Area | Responsibility | Forbidden dependencies |
| --- | --- | --- |
| `BilibiliPlaybackProvider` | Bilibili protocol and source resolution | Activity, panel, Compose UI |
| `ViriViriAppState` | Shared recommendation and player session | Bilibili JSON fields or endpoint strings |
| UI and Activities | Render state and route between hosts | Bilibili protocol parsing |

### Good/Base/Bad Cases

* Good: the provider returns `Recommendation` or `MediaSource` and knows nothing
  about the panel or immersive host.
* Base: placeholder provider data is acceptable when a platform adapter is not
  ready.
* Bad: a provider starts an Activity or reads Horizon OS panel state.

---

## Naming Conventions

* Domain data classes use neutral names such as `Recommendation` and
  `ViriViriUiState`.
* Provider classes include their platform name, such as
  `BilibiliPlaybackProvider`.

---

## Examples

* `app/src/main/java/com/m0e_n00b/viriviri/BilibiliPlaybackProvider.kt`
* `app/src/main/java/com/m0e_n00b/viriviri/ViriViriAppState.kt`
