package com.m0e_n00b.viriviri

import android.os.Handler
import com.meta.spatial.core.Entity
import com.meta.spatial.toolkit.Visible
import com.m0e_n00b.spatialworkbench.core.PanelSlot

/** Fades existing Spatial panel layers, then removes their final Spatial hit target with Visible. */
internal class SpatialPanelVisibilityController(
    private val handler: Handler,
    private val fadeDurationMs: Long = 200L,
    private val fadeSteps: Int = 4,
) {
  private val pendingAnimations = mutableMapOf<PanelSlot, Runnable>()

  fun setVisible(slot: PanelSlot, entity: Entity, visible: Boolean) {
    pendingAnimations.remove(slot)?.let(handler::removeCallbacks)
    val startAlpha = entity.tryGetComponent<PanelLayerAlpha>()?.layerAlpha?.coerceIn(0f, 1f)
        ?: if (visible) 0f else 1f
    val targetAlpha = if (visible) 1f else 0f

    if (visible) entity.setComponent(Visible(true))
    if (startAlpha == targetAlpha) {
      entity.setComponent(PanelLayerAlpha(targetAlpha))
      if (!visible) entity.setComponent(Visible(false))
      return
    }

    val animation = object : Runnable {
      var step = 0

      override fun run() {
        if (pendingAnimations[slot] !== this) return
        step += 1
        val fraction = step.toFloat() / fadeSteps
        entity.setComponent(PanelLayerAlpha(startAlpha + (targetAlpha - startAlpha) * fraction))
        if (step >= fadeSteps) {
          pendingAnimations.remove(slot)
          if (!visible) entity.setComponent(Visible(false))
        } else {
          handler.postDelayed(this, fadeDurationMs / fadeSteps)
        }
      }
    }
    pendingAnimations[slot] = animation
    handler.post(animation)
  }

  fun clear() {
    pendingAnimations.values.forEach(handler::removeCallbacks)
    pendingAnimations.clear()
  }
}
