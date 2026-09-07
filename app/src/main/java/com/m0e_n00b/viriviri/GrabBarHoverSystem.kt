package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.toolkit.Transform

/**
 * Hover feedback for the visual grab bar: brighten when a controller/hand ray hits the bar,
 * dim slightly when it leaves. Pure visual alpha only — it must NOT touch the bar's Transform
 * (the bar is an independent world entity whose pose is owned by GrabPilotSystem's rest-pose
 * parking; writing world Z here caused a z≈0.3 grab-zone mismatch with the visual).
 */
internal class GrabBarHoverSystem(
    private val pointerInfo: PointerInfoSystem,
    private val grabBarEntity: Entity,
) : SystemBase() {
  private var hovering = false

  override fun execute() {
    if (grabBarEntity.tryGetComponent<Transform>() == null) return
    val isHit = pointerInfo.rightEntity == grabBarEntity || pointerInfo.leftEntity == grabBarEntity
    if (isHit != hovering) {
      hovering = isHit
      grabBarEntity.setComponent(PanelLayerAlpha(if (isHit) 1f else 0.85f))
    }
  }
}
