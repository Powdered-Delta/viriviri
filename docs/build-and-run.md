# Build and Run

This project is a Gradle-based Android workspace. Keep commands relative to the
repository root and keep machine-specific paths in local shell or IDE settings.

## Environment

Required tools:

- JDK 17
- Android SDK Platform 36 and Build-Tools 36
- Android Studio with the Meta Horizon plugin
- Meta Spatial Editor for future glXF scene authoring
- ADB for device installation

Do not document or commit local installation paths. If a machine needs local SDK
configuration, keep it in `local.properties`, which is ignored by Git.

## Common Commands

All commands use the committed Gradle 8.11.1 Wrapper. On Windows, replace
`./gradlew` below with `./gradlew.bat`.

List projects:

```bash
./gradlew projects
```

Compile shared modules:

```bash
./gradlew :core:compileDebugKotlin
./gradlew :ui-compose:compileDebugKotlin
```

Build the Meta debug APK:

```bash
./gradlew :app-meta:assembleDebug
```

Clean generated output:

```bash
./gradlew clean
```

Install the debug APK:

```bash
adb install -r app-meta/build/outputs/apk/debug/app-meta-debug.apk
```

If a previous installation has the same package name but a different signing
certificate, remove it before installing the debug APK:

```bash
adb uninstall com.viriviri.app
```

## Media3 Surface Handoff PoC

`PanelActivity` plays the bundled `app-meta/src/main/assets/poc/rick.mp4` file,
so the proof of concept does not depend on device network access. Use the button
to switch between compact and cinema-sized TextureViews in the same 2D panel.

Successful Quest device validation requires:

* Video and audio continue playing through repeated target changes.
* `prepare` remains `1`.
* `decoder init` does not increase after initial playback begins.
* Playback position continues advancing; it does not restart from zero.

The current device result is a smooth transfer with an occasional short white
frame. Treat that as a visual transition issue for a future animation layer,
not a Media3 reload or decoder recreation.
