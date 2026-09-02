# Spatial Playback Canvas Adapter

## Reference Pattern

Meta's local PremiumMedia sample declares a custom float component:

```xml
<Component name="PanelLayerAlpha">
  <FloatAttribute name="layerAlpha" defaultValue="1.0f" />
</Component>
```

Its ECS system observes changed component values, resolves the existing
`PanelSceneObject` from `SceneObjectSystem`, and writes alpha through
`sceneObjectLayer.setColorScaleBias(Vector4(1f, 1f, 1f, alpha), Vector4(0f))`.
Its fading helper sets `Visible(true)` before fade-in and `Visible(false)` only
after fade-out completes.

## App Mapping

No new entity is needed. The adapter changes components only on existing
entities:

| Slot | Existing entity | Theme policy |
| --- | --- | --- |
| `MEDIA_STAGE` | `spatialized_video_panel` | persistent, never canvas-hidden |
| `TRANSPORT` | `controls_id` | auto-fade |
| `SYSTEM_TOOLBAR` | `mode_panel` | persistent |
| `BROWSE` | `video_selector_panel` | on-demand |
| `CONTEXT` | `mr_panel` | on-demand |

## Input Lifecycle

`Visible(false)` is the Spatial layer/panel lifecycle boundary. It removes the
final spatial panel target; the adapter does not disable `Hittable` because that
component belongs to the video stage's direct stage input and is not present on
the registered UI panels. The existing transport Android root still changes to
`INVISIBLE` after its own local fade; the Spatial layer adapter synchronizes the
panel-level visibility for canvas transitions.

## Initial State

Before the first canvas application, the adapter sets alpha/Visible on all
mapped existing panels. Quiet Watch keeps stage and persistent mode panel;
controls, browse, and context are hidden. The current UI registrations may
finish asynchronously, so the alpha system treats absent `PanelSceneObject`
resolution as transient and applies on a later component change/reapply.
