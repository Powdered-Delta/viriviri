package com.m0e_n00b.viriviri

import android.os.Handler
import android.os.Looper
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.toolkit.Transform

/**
 * Grab-bar visibility + hover feedback.
 *
 * - Idle: the bar fades down to [idleAlpha] (subtle affordance, no occlusion).
 * - When a controller/hand ray hits the bar: fade UP to 1.0 ("brighten" = the move affordance
 *   becomes clearly grabbable). This drives both the fade-in/fade-out and the hover highlight.
 * - Smoothly animated via PanelLayerAlpha on the UI handler (never a hard cut).
 * - The bar is the workbench anchor (parent of the stage), so this system must NOT scale or
 *   move the bar's Transform (that would scale/move the whole workbench).
 */
internal class GrabBarHoverSystem(
    private val pointerInfo: PointerInfoSystem,
    private val grabBarEntity: Entity,
    private val idleAlpha: Float = 0.45f,
    private val hoverAlpha: Float = 1f,
    private val fadeDurationMs: Long = 220L,
    private val fadeSteps: Int = 6,
) : SystemBase() {
  private val handler = Handler(Looper.getMainLooper())
  private var hovering = false
  private var lastAlpha = idleAlpha

  override fun execute() {
    if (grabBarEntity.tryGetComponent<Transform>() == null) return
    val isHit = pointerInfo.rightEntity == grabBarEntity || pointerInfo.leftEntity == grabBarEntity
    if (isHit == hovering) return
    hovering = isHit
    val target = if (isHit) hoverAlpha else idleAlpha
    animateTo(target)
  }

  private fun animateTo(targetAlpha: Float) {
    val start = lastAlpha
    val span = targetAlpha - start
    var step = 0
    val tick =
        object : Runnable {
          override fun run() {
            step += 1
            val fraction = step.toFloat() / fadeSteps
            val alpha = start + span * fraction
            lastAlpha = alpha
            grabBarEntity.setComponent(PanelLayerAlpha(alpha.coerceIn(0f, 1f)))
            if (step < fadeSteps) handler.postDelayed(this, fadeDurationMs / fadeSteps)
          }
        }
    handler.post(tick)
  }
}
