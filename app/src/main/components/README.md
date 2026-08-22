# Spatial ECS Components

`spatial_video_sample_components.xml` is the source of truth for ViriViri's custom
Meta Spatial SDK components. The Meta Gradle plugin generates the Kotlin classes;
do not edit files under `app/build/generated/`.

The schema targets Spatial SDK 0.13.2, matching `gradle/libs.versions.toml`.
Register every generated companion before registering systems that query it.

| Component | Fields | Runtime consumer | Contract |
| --- | --- | --- | --- |
| `SpatializedAudioPanel` | Marker only | `SpatialAudioSystem` | Add only to a panel entity that also has `Transform`; it identifies the active source used for listener-relative stereo panning. |
| `PanelLayerAlpha` | `layerAlpha: Float = 1.0` | `PanelLayerAlphaSystem`, `SpatialPanelVisibilityController` | Requests panel-layer alpha. Consumers clamp the value to `0..1`; it does not replace `Visible` or hit-test control. |
| `WristAttached` | `position`, `rotation`, `faceUser` | `WristAttachedSystem` | Attaches a local entity to the left hand. Position is a hand-local meter offset, rotation is a local Euler-degree offset, and `faceUser` selects head-facing versus hand orientation before that offset. Requires `Transform`. |

These components are Spatial runtime infrastructure, not the Workbench UI model.
Workbench modules, canvas state, transport settings, and panel-slot ownership stay
in the core/Compose contracts. Do not add one ECS component per UI module, and do
not let a component create another player, video `Surface`, or MediaStage.

After changing the XML schema, run the normal Windows debug build to regenerate
components and verify registration, queries, and constructor call sites together.
