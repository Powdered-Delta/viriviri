# Gradle and Compose Foundation Research

## Summary

The project should become a standard Kotlin/Android Gradle multi-module app. Shared code should live in `:core` and `:ui-compose`; Meta Spatial SDK integration should live in `:app-meta`. Version declarations should be centralized in `gradle/libs.versions.toml` so the future PICO app can reuse the same Kotlin, Compose, and Android plugin versions.

## Recommended Modules

* `:core`
  * Android library or Kotlin library module.
  * Holds data models, repository interfaces, and future network contracts.
  * No dependency on Compose, Android UI, Meta Spatial SDK, or PICO Spatial SDK unless Android APIs are explicitly needed.
* `:ui-compose`
  * Android library module with Compose enabled.
  * Depends on `:core`.
  * Exposes `BrowseScreen` and `ImmersiveScreen`.
* `:app-meta`
  * Android application module.
  * Depends on `:core` and `:ui-compose`.
  * Applies Meta Spatial SDK plugin and dependencies.
  * Owns manifest, Activities, transition controller, and scene assets.

## Version Management

Use `gradle/libs.versions.toml` for:

* Android Gradle Plugin
* Kotlin
* Compose BOM / compiler plugin if required by the chosen Kotlin version
* Meta Spatial SDK version

The plan references Meta Spatial SDK `0.13.0` from setup documentation. If Gradle resolution fails, verify the current public version and update only the version catalog.

## Initial Source Sets

Suggested source roots:

```text
core/src/main/java/com/viriviri/core/
ui-compose/src/main/java/com/viriviri/ui/
app-meta/src/main/java/com/viriviri/app/
```

Prefer small placeholder classes/composables over large business logic in this foundation task.

## Build Caveats

* Spatial SDK projects may require Android Studio and Meta Spatial Editor for full scene export workflows.
* Placeholder glXF/scene files can be documented rather than fully generated if the Spatial Editor CLI is not installed.
* Keep Gradle configuration conservative so the initial skeleton is easy to adjust once dependencies resolve locally.
