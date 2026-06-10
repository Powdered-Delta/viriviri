# Meta Spatial SDK Hybrid Research

## Summary

Meta Horizon OS Hybrid Apps use separate Android Activities for low-immersion 2D panels and high-immersion OpenXR/Spatial SDK experiences. This directly matches viriviri's target: a system-level 2D window that can coexist with other Horizon OS panels, plus a custom immersive scene with a different layout.

## Required Manifest Shape

Hybrid apps must declare Horizon OS SDK metadata:

```xml
<manifest xmlns:horizonos="http://schemas.horizonos/sdk">
    <horizonos:uses-horizonos-sdk
        horizonos:minSdkVersion="69"
        horizonos:targetSdkVersion="76" />
</manifest>
```

Each Activity identifies its rendering mode through intent categories:

* 2D panel Activity: `com.oculus.intent.category.2D`
* Immersive Activity: `com.oculus.intent.category.VR`

The panel Activity can include a `<layout>` declaration for default and minimum panel size.

## Switching Patterns

### 2D Panel to Immersive

Create an explicit intent targeting the immersive Activity, set `ACTION_MAIN`, add `FLAG_ACTIVITY_NEW_TASK`, start it, then call `finishAndRemoveTask()` on the panel Activity so the low-immersion panel is not left running in the background.

### Immersive to 2D Panel in Home

Returning to Home with a panel requires three steps:

1. Build an intent for the panel Activity.
2. Wrap it in an immutable `PendingIntent`.
3. Launch Home with `Intent.CATEGORY_HOME` and attach the pending intent as `extra_launch_in_home_pending_intent`.

This ensures the panel launches in the Horizon OS Home environment rather than inside the immersive session.

### Cooperative Overlay

If an immersive Activity wants to show a panel overlay without leaving immersion, start the panel Activity without calling `finishAndRemoveTask()`. This is useful later but not required for the MVP foundation.

## Official HybridSample Shape

The official sample uses:

* `PancakeActivity.kt` for the 2D panel Activity.
* `HybridSampleActivity.kt` for the immersive Activity.
* Shared Compose UI through a reusable composable.
* `app/scenes/` for Spatial Editor scene assets and glXF output.

For viriviri, equivalent names should be:

* `PanelActivity.kt`
* `ImmersiveActivity.kt`
* shared UI from `:ui-compose`
* `app-meta/scenes/` for future glXF scene assets

## Implementation Guidance

* Keep `PanelActivity` and `ImmersiveActivity` thin; route UI through shared composables and transition helpers.
* Keep Meta-specific intent constants and Activity glue in `:app-meta`.
* Do not place Bilibili data/network logic in the app layer.
* MVP can scaffold scene assets and lifecycle points without implementing full video playback.
