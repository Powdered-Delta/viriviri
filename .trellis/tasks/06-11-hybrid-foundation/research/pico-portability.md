# PICO OS 6 Portability Research

## Summary

PICO OS 6 supports spatial applications, OpenXR, Android apps, web apps, and shared-space multitasking, but its system-level spatial APIs are vendor-specific. Meta Spatial SDK code is not portable to PICO directly. A future PICO port should add a separate `:app-pico` module while reusing `:core` and most of `:ui-compose`.

## Platform Mapping

| Concern | Meta Quest MVP | Future PICO OS 6 Port |
| --- | --- | --- |
| Low-immersion system window | Meta 2D panel Activity | PICO Spatial SDK window/container APIs |
| High-immersion experience | Meta Spatial SDK immersive Activity | PICO Full Space / spatial app layer |
| Shared business logic | `:core` | Reuse `:core` |
| Shared UI | Jetpack Compose in `:ui-compose` | Reuse where compatible, adapt host wrappers |
| Platform transitions | Meta intent + `extra_launch_in_home_pending_intent` | PICO-specific transition/window APIs |

## Architecture Implications

* Do not import Meta Spatial SDK from `:core` or `:ui-compose`.
* Put all Meta-only Activity declarations, manifest categories, Spatial SDK dependencies, and scene integration in `:app-meta`.
* Keep transition APIs small and explicit so a later `:app-pico` can implement equivalent behavior.
* Use neutral names in shared modules: `ImmersionState`, `VideoInfo`, `PlaybackSurfaceState`, `BrowseScreen`, `ImmersiveScreen`.

## Future `:app-pico` Shape

Expected module outline:

```text
app-pico/
└── src/main/java/com/viriviri/app/pico/
    ├── PicoPanelHost.kt
    ├── PicoImmersiveHost.kt
    └── PicoTransitionController.kt
```

This task should not implement PICO code. It should document the boundary and avoid coupling that would make this module difficult to add.

## Risks

* Vendor system-window APIs are not interchangeable even if both vendors support spatial multitasking.
* PICO OS 6 APIs may change as the platform matures.
* Compose UI reuse may need host-specific adapters around sizing, focus, input, and lifecycle.
