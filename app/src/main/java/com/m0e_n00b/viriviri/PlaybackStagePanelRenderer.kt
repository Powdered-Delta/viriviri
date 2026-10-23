package com.m0e_n00b.viriviri

import android.util.Log
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.PanelConfigOptions
import com.meta.spatial.runtime.PanelSceneObject
import com.meta.spatial.runtime.DepthWrite
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible

/** One complete production geometry commit: replace video, retain UI, suppress its scene mask. */
internal class PlaybackStagePanelRenderer(
    private val config: (PlaybackStageLayout, PlaybackStageLayerGeometry) -> PanelConfigOptions,
    private val prepareStage: (PlaybackStageGeometry) -> Unit,
    private val stageWorldPose: () -> Pose?,
    private val replaceVideo: (PlaybackStageLayout) -> PanelSceneObject,
) {
  fun apply(layout: PlaybackStageLayout, panels: Map<PlaybackStageLayer, PanelSceneObject>): Boolean {
    // No partial acknowledgement if the ECS hierarchy has not been initialized yet.
    val entities = panels.mapValues { (_, panel) ->
      val entity = panel.entity ?: return false
      if (entity.tryGetComponent<Transform>() == null) return false
      entity
    }
    if (stageWorldPose() == null) return false
    prepareStage(layout.geometry)
    if (stageWorldPose() == null) return false
    val currentPanels = panels.toMutableMap()
    currentPanels[PlaybackStageLayer.VIDEO] = replaceVideo(layout)
    for (layer in layout.layers) {
      if (layer.role == PlaybackStageLayer.VIDEO) continue // Never reshape video: device regression.
      val panel = currentPanels.getValue(layer.role)
      val uiSurface = panel.surface
      val uiDisplay = panel.display
      panel.reshape(config(layout, layer))
      check(panel.surface === uiSurface && panel.display === uiDisplay) { "Stage UI output changed during reshape" }
      panel.clearCrop()
      panel.setScale(Vector3(1f, 1f, 1f))
      if (layer.role == PlaybackStageLayer.DANMAKU) {
        val materials = checkNotNull(panel.materials) { "Danmaku scene material unavailable" }
        check(materials.isNotEmpty())
        materials.forEach {
          it.setColorWrite(0) // RGBA, including alpha. Keep the compositor text layer alive.
          it.setDepthWrite(DepthWrite.DISABLE)
        }
      }
    }
    for (layer in layout.layers) {
      // Fresh absolute local pose: never retain a world rotation propagated during reparenting.
      entities.getValue(layer.role).setComponent(Transform(layer.localPose()))
      if (BuildConfig.DEBUG) {
        Log.i("ViriViriStage", "batch ${layer.role} content=${layer.content.widthMeters}x${layer.content.heightMeters} " +
            "scale=${layout.scale} radius=${layer.curvature.radiusOrNull()} baseZ=${layer.baseLocalZ} " +
            "carrierZ=${layer.localPose().t.z} uiMaskWrites=${layer.role != PlaybackStageLayer.DANMAKU}")
      }
    }
    entities.getValue(PlaybackStageLayer.DANMAKU).setComponent(Visible(true))
    return true
  }
}
