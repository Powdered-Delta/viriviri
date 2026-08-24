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
    // Panel layers are fully opaque when visible. MediaStage dimming belongs only to StageBackdrop.
    private val visibleAlpha: Float = WORKBENCH_PANEL_VISIBLE_ALPHA,
    private val trace: (String) -> Unit = {},
) {
  // UX: semantic slots can resolve to separate Detail and Center entities without sharing animation ownership.
  private val pendingAnimations = mutableMapOf<Entity, Runnable>()

  fun setVisible(slot: PanelSlot, entity: Entity, visible: Boolean) {
    pendingAnimations.remove(entity)?.let(handler::removeCallbacks)
    val startAlpha = entity.tryGetComponent<PanelLayerAlpha>()?.layerAlpha?.coerceIn(0f, 1f)
        ?: if (visible) 0f else 1f
    // UX: every Workbench panel uses one compositor alpha; Compose roots stay opaque to avoid dither.
    val targetAlpha = if (visible) visibleAlpha.coerceIn(0f, 1f) else 0f
    trace("setVisible slot=$slot visible=$visible startAlpha=$startAlpha targetAlpha=$targetAlpha")

    if (visible) entity.setComponent(Visible(true))
    if (startAlpha == targetAlpha) {
      entity.setComponent(PanelLayerAlpha(targetAlpha))
      if (!visible) entity.setComponent(Visible(false))
      trace("setVisible immediate slot=$slot visible=$visible")
      return
    }

    val animation = object : Runnable {
      var step = 0

      override fun run() {
        if (pendingAnimations[entity] !== this) return
        step += 1
        val fraction = step.toFloat() / fadeSteps
        entity.setComponent(PanelLayerAlpha(startAlpha + (targetAlpha - startAlpha) * fraction))
        if (step >= fadeSteps) {
          pendingAnimations.remove(entity)
          if (!visible) entity.setComponent(Visible(false))
          trace("setVisible complete slot=$slot visible=$visible")
        } else {
          handler.postDelayed(this, fadeDurationMs / fadeSteps)
        }
      }
    }
    pendingAnimations[entity] = animation
    handler.post(animation)
  }

  fun clear() {
    pendingAnimations.values.forEach(handler::removeCallbacks)
    pendingAnimations.clear()
  }

  private companion object {
    // Workbench UI is opaque; only the MediaStage backdrop carries a translucent dim layer.
    const val WORKBENCH_PANEL_VISIBLE_ALPHA = 1f
  }
}
