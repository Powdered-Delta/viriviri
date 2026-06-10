# PICO Migration Notes

viriviri starts as a Meta Quest-first Hybrid App, but the module boundary is designed so a future PICO OS 6 port can replace only the platform layer.

## Reusable Modules

- `:core`: reusable as-is for Bilibili models, repository contracts, network interfaces, and playback state. It must not import Meta Spatial SDK, PICO Spatial SDK, Android UI, or Compose.
- `:ui-compose`: reusable for browse and immersive Compose surfaces where PICO hosts support Android Compose. PICO-specific sizing, focus, input, and lifecycle adapters should wrap these composables from the platform module.

## Meta-Specific Module

- `:app-meta`: owns Horizon OS manifest metadata, `com.oculus.intent.category.2D`, `com.oculus.intent.category.VR`, Meta Spatial SDK dependencies, Activity routing, and `extra_launch_in_home_pending_intent` transitions.
- Nothing in `:app-meta` should become a required dependency of `:core` or `:ui-compose`.

## Future `:app-pico` Shape

A later PICO port should add a sibling app module instead of modifying shared modules for vendor APIs:

```text
app-pico/
`-- src/main/java/com/viriviri/app/pico/
    |-- PicoPanelHost.kt
    |-- PicoImmersiveHost.kt
    `-- PicoTransitionController.kt
```

That module would declare PICO Spatial SDK dependencies, PICO window/container metadata, and PICO-specific transition behavior while depending on `:core` and `:ui-compose`.
