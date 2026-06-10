# Build and Run

This project is a Gradle-based Android workspace. Keep commands relative to the
repository root and keep machine-specific paths in local shell or IDE settings.

## Environment

Required tools:

- JDK 17
- Gradle
- Android SDK 34/35
- Android Studio with the Meta Horizon plugin
- Meta Spatial Editor for future glXF scene authoring
- ADB for device installation

Do not document or commit local installation paths. If a machine needs local SDK
configuration, keep it in `local.properties`, which is ignored by Git.

## Common Commands

List projects:

```bash
gradle projects
```

Compile shared modules:

```bash
gradle :core:compileDebugKotlin
gradle :ui-compose:compileDebugKotlin
```

Build the Meta debug APK:

```bash
gradle :app-meta:assembleDebug
```

Clean generated output:

```bash
gradle clean
```

Install the debug APK:

```bash
adb install app-meta/build/outputs/apk/debug/app-meta-debug.apk
```

## Wrapper Note

No Gradle wrapper is required for this foundation. If a wrapper is added later,
use `./gradlew` or `gradlew.bat` instead of `gradle` while keeping the task names
the same.
