# viriviri

viriviri is a Quest-first Hybrid App foundation for a spatial Bilibili client.
The MVP uses a Meta Spatial SDK Android application with a system-level 2D panel
for low immersion and a separate immersive Activity for high immersion.

## Project Layout

```text
core/          Pure Kotlin domain, data, network contracts, and repositories.
ui-compose/    Shared Jetpack Compose UI for browse and immersive layouts.
app-meta/      Meta Spatial SDK application layer and Hybrid Activity wiring.
docs/          Architecture and porting notes.
```

`core` must stay platform-neutral. Meta Spatial SDK and future PICO Spatial SDK
code belongs only in platform app modules such as `app-meta` or a future
`app-pico`.

## Prerequisites

Use JDK 17, Gradle, Android SDK 34/35, Android Studio, the Meta Horizon Android
Studio plugin, Meta Spatial Editor, and a Quest device or emulator setup.

Do not commit local machine configuration such as `local.properties`.

## Build Commands

Run commands from the repository root.

```bash
gradle projects
gradle :core:compileDebugKotlin
gradle :ui-compose:compileDebugKotlin
gradle :app-meta:assembleDebug
```

Clean generated output:

```bash
gradle clean
```

Install a debug APK after a successful build:

```bash
adb install app-meta/build/outputs/apk/debug/app-meta-debug.apk
```

If a Gradle wrapper is added later, replace `gradle` with `./gradlew` on Unix
shells or `gradlew.bat` on Windows shells.

## Hybrid Flow

`PanelActivity` is the low-immersion Horizon OS 2D panel. It launches
`ImmersiveActivity` through an explicit Activity intent and then removes the
panel task.

`ImmersiveActivity` is the high-immersion entry point. Returning to Home with a
2D panel uses the Meta Hybrid `extra_launch_in_home_pending_intent` pattern.

## Current Scope

This foundation intentionally does not implement Bilibili APIs, real playback,
danmaku, store signing, or PICO runtime code. Those layers should build on top
of the module boundaries established here.
