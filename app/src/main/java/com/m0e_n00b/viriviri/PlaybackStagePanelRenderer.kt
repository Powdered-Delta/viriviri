package com.m0e_n00b.viriviri

import android.util.Log
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.PanelConfigOptions
import com.meta.spatial.runtime.PanelSceneObject
import com.meta.spatial.toolkit.Transform

/** The only writer of live stage panel shapes and carrier poses. Never owns player or Surface. */
internal class PlaybackStagePanelRenderer(
    private val config: (PlaybackStageLayout, PlaybackStageLayerGeometry) -> PanelConfigOptions,
    private val prepareStage: (PlaybackStageGeometry) -> Unit,
    private val stageWorldPose: () -> Pose?,
) {
  fun apply(layout: PlaybackStageLayout, panels: Map<PlaybackStageLayer, PanelSceneObject>): Boolean {
    // No partial acknowledgement if the ECS hierarchy has not been initialized yet.
    val entities = panels.mapValues { (_, panel) ->
      val entity = panel.entity ?: return false
      if (entity.tryGetComponent<Transform>() == null) return false
      entity
    }
    if (stageWorldPose() == null) return false
    val configs = layout.layers.associate { it.role to config(layout, it) }
    prepareStage(layout.geometry)
    // Sample the parent once AFTER adjusting the stage/bar offset. Every child uses this same pose.
    val stage = stageWorldPose() ?: return false
    for (layer in layout.layers) {
      val panel = panels.getValue(layer.role)
      panel.reshape(configs.getValue(layer.role))
      // SDK setCrop(Crop.NONE) restores both unit footprint and full-frame layer UVs.
      // The direct decoder Surface is not a PlayerView FIT canvas with inferred black bars.
      if (layer.role == PlaybackStageLayer.VIDEO) panel.setCrop(layout.geometry.videoTextureCrop)
      else panel.clearCrop()
      panel.setScale(Vector3(1f, 1f, 1f))
    }
    for (layer in layout.layers) {
      val panel = panels.getValue(layer.role)
      // Fresh absolute local pose: never retain a world rotation propagated during reparenting.
      entities.getValue(layer.role).setComponent(Transform(layer.localPose()))
      val world = layer.worldPose(stage)
      panel.setPosition(world.t)
      panel.setRotationQuat(world.q)
      if (BuildConfig.DEBUG) {
        Log.i("ViriViriStage", "batch ${layer.role} content=${layer.content.widthMeters}x${layer.content.heightMeters} " +
            "scale=${layout.scale} radius=${layer.curvature.radiusOrNull()} baseZ=${layer.baseLocalZ} " +
            "carrierZ=${layer.localPose().t.z} submittedWorldZ=${world.t.z} " +
            "apertureLocalBounds=${panel.mesh?.computeCombinedBounds()}")
      }
    }
    return true
  }
}
