# Playback Canvas Runtime Boundary

## What Exists

`CinemaTheme` already declares static `WorkbenchCanvas` recipes and presentation
policies. The default cinema theme marks:

- `MEDIA_STAGE` and `SYSTEM_TOOLBAR` as `PERSISTENT`;
- `TRANSPORT` as `AUTO_FADE`;
- `BROWSE` and `CONTEXT` as `ON_DEMAND`.

There is no runtime reducer that chooses the current canvas from stage input,
back/dismiss, pause, or idle events.

## Why the First Increment Is Core Only

The current app controls only the Android `controls_id` root alpha. It does not
register the PremiumMedia sample's `PanelLayerAlpha` component/system needed to
fade an actual Spatial panel layer and then set `Visible(false)`. Directly
adding `Visible` calls to Browse/Context would duplicate renderer behavior,
ignore theme policy, and make input lifecycle hard to verify.

## Chosen Boundary

Implement a platform-neutral reducer first. A later Spatial adapter will map
resolved `PanelSlot` visibility to the existing entities/panels using proper
layer alpha and input disablement. The reducer receives semantic events and an
actual-playing boolean only; it never sees entities, Views, player, Surface, or
timers.
