package com.m0e_n00b.viriviri

import com.meta.spatial.core.EntityContext
import com.meta.spatial.core.Query
import com.meta.spatial.core.SpatialSDKExperimentalAPI
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector4
import com.meta.spatial.runtime.PanelSceneObject
import com.meta.spatial.toolkit.SceneObjectSystem

/** Applies panel-layer alpha requested by [ImmersivePlaybackCanvasHost] to existing panel entities. */
class PanelLayerAlphaSystem(
    private val sceneObjectSystem: SceneObjectSystem,
) : SystemBase() {
  private var lastUpdateVersion = 0UL

  @OptIn(SpatialSDKExperimentalAPI::class)
  override fun execute() {
    for (entity in Query.where { changedSince(PanelLayerAlpha.id, lastUpdateVersion) }.eval()) {
      val alpha = entity.getComponent<PanelLayerAlpha>().layerAlpha.coerceIn(0f, 1f)
      sceneObjectSystem.getSceneObject(entity)?.thenAccept { sceneObject ->
        (sceneObject as? PanelSceneObject)
            ?.layer
            ?.setColorScaleBias(Vector4(1f, 1f, 1f, alpha), Vector4(0f))
      }
    }
    lastUpdateVersion = EntityContext.getDataModel()!!.getLastUpdateVersion()
  }
}
