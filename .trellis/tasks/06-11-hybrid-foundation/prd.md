# Cross-Platform Hybrid App Foundation

## Goal

Build the initial foundation for viriviri as a Meta Quest-first Hybrid App: a system-level 2D panel experience for low immersion and a separate custom immersive 3D experience for high immersion. The architecture must preserve maximum future portability to PICO OS 6 by keeping business/data logic and shared UI code outside vendor-specific app layers.

## Requirements

* Replace the current disposable Unity template foundation with an Android Gradle project targeting Meta Spatial SDK first.
* Use a multi-module architecture:
  * `:core` for pure Kotlin Bilibili data, network, repository, and state models with no Meta/PICO/Spatial SDK dependencies.
  * `:ui-compose` for shared Jetpack Compose UI surfaces, split into browse/low-immersion UI and immersive/high-immersion UI.
  * `:app-meta` for Meta Spatial SDK integration, Horizon OS hybrid Activity routing, glXF scene assets, and Meta-specific app lifecycle.
* Configure Meta Hybrid behavior with two Activities:
  * `PanelActivity` as the low-immersion system 2D panel using `com.oculus.intent.category.2D`.
  * `ImmersiveActivity` as the high-immersion VR Activity using `com.oculus.intent.category.VR`.
* Implement transition scaffolding for:
  * 2D panel to immersive via an explicit `Intent` and `finishAndRemoveTask()`.
  * Immersive back to a 2D panel in Home via the `extra_launch_in_home_pending_intent` pattern.
* Preserve a distinct high-immersion layout rather than simply enlarging the 2D panel.
* Add Android/Gradle `.gitignore` rules and remove Unity-generated project artifacts from the active project root.
* Add a PICO migration note explaining how a future `:app-pico` layer can reuse `:core` and `:ui-compose` while replacing the platform layer with PICO Spatial SDK.

## Acceptance Criteria

* [ ] Unity template folders/files are removed from the active project root or replaced by the Android project structure.
* [ ] Root `.gitignore` ignores Gradle, Android Studio, APK/AAB/AAR, build, and local machine files.
* [ ] `settings.gradle.kts` includes `:core`, `:ui-compose`, and `:app-meta`.
* [ ] Root and module Gradle files define Kotlin/Android/Compose/Meta Spatial SDK dependencies in a centralized and maintainable way.
* [ ] `:core` compiles as a platform-neutral Kotlin/Android library and contains no imports from Meta Spatial SDK or PICO Spatial SDK.
* [ ] `:ui-compose` depends on `:core` and exposes separate browse and immersive composable entry points.
* [ ] `:app-meta` depends on `:core` and `:ui-compose` and declares both `PanelActivity` and `ImmersiveActivity`.
* [ ] Manifest contains Horizon OS SDK metadata and the correct 2D/VR intent categories.
* [ ] Transition helper code contains the official Meta Hybrid intent patterns.
* [ ] PICO migration documentation exists and states what is reusable versus platform-specific.

## Definition of Done

* Code and project files match this PRD and the attached plan.
* Trellis `implement.jsonl` and `check.jsonl` include relevant spec and research context.
* A Trellis check pass reviews module boundaries, dependency direction, and plan compliance.
* Any new durable architecture decisions are captured in `.trellis/spec/`.
* Work is committed before `/trellis:finish-work` archives the task.

## Technical Approach

Use Meta Spatial SDK as the first production platform because Quest is the launch target and the required low/high immersion behavior is a platform Hybrid App capability. Do not use Unity/OpenXR for this MVP because Unity can provide immersive app-internal panel scaling, but not a system-level Horizon OS 2D panel that can coexist with other windows while also switching into a custom immersive Activity.

The project will be a standard Android Gradle multi-module repository. `:core` and `:ui-compose` form the reusable cross-platform foundation. `:app-meta` owns all Meta-specific Activity declarations, Spatial SDK dependencies, and immersive scene integration. A future `:app-pico` can reuse the first two modules and provide a PICO Spatial SDK implementation.

## Decision (ADR-lite)

**Context**: viriviri needs YouTube VR-like low/high immersion: a system-level 2D panel for multitasking and a custom high-immersion scene with a different layout. The app should launch on Quest first while keeping a credible PICO OS 6 migration path.

**Decision**: Build the MVP foundation as a Meta Spatial SDK Hybrid App with Kotlin, Jetpack Compose, and Android Gradle modules. Share business logic and UI through `:core` and `:ui-compose`; keep platform features inside `:app-meta`.

**Consequences**: Quest gets the correct system-level 2D panel and custom immersive Activity behavior. PICO will require a later `:app-pico` platform layer because system Hybrid APIs are vendor-specific, but business logic and Compose UI remain reusable.

## Out of Scope

* Implementing actual Bilibili APIs, authentication, comments, danmaku, recommendations, or playback networking.
* Implementing PICO Spatial SDK code in this task.
* Building a polished custom cinema environment beyond placeholder scene scaffolding.
* Integrating third-party video playback libraries such as AVPro.
* Publishing store metadata or signing/release pipelines.

## Technical Notes

* Meta official Hybrid flow uses separate 2D panel and immersive Activities, `com.oculus.intent.category.2D`, `com.oculus.intent.category.VR`, and `extra_launch_in_home_pending_intent` for returning to Home with a panel.
* PICO OS 6 exposes similar spatial-app concepts through its own PICO Spatial SDK and `WindowContainer`, but this is not API-compatible with Meta Spatial SDK.
* Current repository began as a disposable Unity 6 Universal 3D template; the user explicitly approved deleting it.
* Package name defaults to `com.viriviri.app`.
* High-immersion scene may begin with placeholder/sample glXF structure; production art can come later.
