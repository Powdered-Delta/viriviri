# Upgrade Android Build Baseline

## Goal

Align Android Gradle Plugin and compile SDK versions with the AndroidX
dependencies already declared by the project, and commit a reproducible Gradle
Wrapper.

## Requirements

* Use AGP 8.9.1.
* Generate and commit Gradle Wrapper 8.11.1.
* Compile all Android modules against API 36.
* Preserve minSdk 29 and app targetSdk 35.
* Validate a debug APK assembly using the Wrapper.

## Result

* Upgraded to AGP 8.9.1 and compileSdk 36 while retaining targetSdk 35.
* Added a committed Gradle 8.11.1 Wrapper.
* Removed an invalid KSP configuration: the Meta Spatial Gradle plugin is
  applied through the Gradle plugins block and must not be declared as a KSP
  processor.
* `./gradlew :app-meta:assembleDebug` succeeds.

## Out of Scope

* Upgrading targetSdk.
* Updating Kotlin, KSP, Meta Spatial SDK, Compose, or AndroidX versions.
* Fixing any remaining Meta KSP configuration problem unless it directly blocks
  the upgraded build.
