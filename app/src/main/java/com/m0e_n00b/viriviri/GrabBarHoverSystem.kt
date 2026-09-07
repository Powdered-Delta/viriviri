package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Transform

/**
 * Hover feedback for the visual grab bar: when a controller/hand ray hits the bar, lift it
 * slightly toward the user (local +Z) and brighten it so the user knows it is grabbable;
 * restore when the ray leaves. Pure visual; grab motion itself is driven by [GrabPilotSystem]
 * (task 09-02-workbench-lift-and-grab).
 */
internal class GrabBarHoverSystem(
    private val pointerInfo: PointerInfoSystem,
    private val grabBarEntity: Entity,
    /** Stage-local Z lift while hovered (toward the user). */
    private val hoverLiftLocalZ: Float = 0.03f,
) : SystemBase() {
  private var hovering = false

  override fun execute() {
    val barTransform = grabBarEntity.tryGetComponent<Transform>() ?: return
    val isHit = pointerInfo.rightEntity == grabBarEntity || pointerInfo.leftEntity == grabBarEntity
    if (isHit != hovering) {
      hovering = isHit
      grabBarEntity.setComponent(PanelLayerAlpha(if (isHit) 1f else 0.85f))
    }
    val pose = barTransform.transform
    val targetZ = if (isHit) hoverLiftLocalZ else 0f
    val nextZ = lerp(pose.t.z, targetZ, 0.25f)
    if (kotlin.math.abs(nextZ - pose.t.z) > 0.0001f) {
      pose.t = Vector3(pose.t.x, pose.t.y, nextZ)
      grabBarEntity.setComponent(Transform(pose))
    }
  }
}
