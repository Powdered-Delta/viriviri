package com.m0e_n00b.viriviri

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Transform
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Grab-bar visibility state machine (fade in/out) + hover highlight.
 *
 * Rules (user-specified):
 * - While PLAYING-ONLY (video visible, workbench panels hidden), the bar fades OUT after
 *   [fadeOutDelayMs] (5s) without interaction.
 * - When a controller/hand ray comes within [nearDistanceMeters] (0.1m) of the bar AND the ray
 *   is actively MOVING (above [rayMoveThresholdMeters]), the bar fades IN.
 * - Outside playing-only (workbench open) the bar stays visible so it can be grabbed.
 *
 * Visibility is alpha-only (PanelLayerAlpha on the visual panel) — this must never touch the
 * anchor's Transform, which owns the whole workbench pose.
 */
internal class GrabBarHoverSystem(
    private val pointerInfo: PointerInfoSystem,
    /** Visual panel entity whose alpha is animated. */
    private val barEntity: Entity,
    /** Anchor entity used as the bar's world position reference. */
    private val barAnchorProvider: () -> Entity?,
    /** True when only the video is shown (workbench collapsed) — the state that fades out. */
    private val playingOnlyProvider: () -> Boolean,
    private val nearDistanceMeters: Float = 0.1f,
    private val rayMoveThresholdMeters: Float = 0.02f,
    private val fadeOutDelayMs: Long = 5_000L,
    private val visibleAlpha: Float = 1f,
    private val hiddenAlpha: Float = 0f,
    private val fadeDurationMs: Long = 220L,
    private val fadeSteps: Int = 6,
) : SystemBase() {
  private val handler = Handler(Looper.getMainLooper())
  private var displayedAlpha = visibleAlpha
  private var targetAlpha = visibleAlpha
  private var lastInteractionMs = 0L
  private var wasPlayingOnly = false
  private var lastRayOrigin: Vector3? = null
  private var lastRayTarget: Vector3? = null

  override fun execute() {
    val anchor = barAnchorProvider() ?: return
    if (barEntity.tryGetComponent<Transform>() == null) return
    val barPos = anchor.tryGetComponent<Transform>()?.transform?.t ?: return

    val distance = pointerInfo.nearestRayDistanceTo(barPos)
    val rayMoved = consumeRayMoved()
    val now = SystemClock.uptimeMillis()

    val near = distance != null && distance <= nearDistanceMeters
    val playingOnly = playingOnlyProvider()
    // Entering playing-only starts the fade-out countdown (and showing the workbench cancels it).
    if (playingOnly != wasPlayingOnly) {
      lastInteractionMs = now
      wasPlayingOnly = playingOnly
    }

    val desired =
        when {
          // Approaching with an active pointing motion -> reveal (and reset the fade timer).
          near && rayMoved -> {
            lastInteractionMs = now
            visibleAlpha
          }
          // Playing-only after 5s without interaction -> fade away.
          playingOnly && now - lastInteractionMs >= fadeOutDelayMs -> hiddenAlpha
          // Workbench open -> stay visible for grabbing.
          !playingOnly -> visibleAlpha
          else -> targetAlpha
        }
    animateTo(desired)
  }

  /** True when either ray moved more than the threshold since the previous frame. */
  private fun consumeRayMoved(): Boolean {
    val o = pointerInfo.rightRayOrigin ?: pointerInfo.leftRayOrigin
    val t = pointerInfo.rightRayTarget ?: pointerInfo.leftRayTarget
    val previousO = lastRayOrigin
    val previousT = lastRayTarget
    lastRayOrigin = o
    lastRayTarget = t
    if (o == null || t == null || previousO == null || previousT == null) return false
    return distanceBetween(previousO, o) >= rayMoveThresholdMeters ||
        distanceBetween(previousT, t) >= rayMoveThresholdMeters
  }

  private fun distanceBetween(a: Vector3, b: Vector3): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val dz = a.z - b.z
    return sqrt(dx * dx + dy * dy + dz * dz)
  }

  private fun animateTo(target: Float) {
    val clamped = target.coerceIn(0f, 1f)
    if (abs(clamped - targetAlpha) < 0.0001f) return
    targetAlpha = clamped
    val start = displayedAlpha
    val span = clamped - start
    var step = 0
    val tick =
        object : Runnable {
          override fun run() {
            step += 1
            val fraction = step.toFloat() / fadeSteps
            val alpha = start + span * fraction
            displayedAlpha = alpha
            barEntity.setComponent(PanelLayerAlpha(alpha.coerceIn(0f, 1f)))
            if (step < fadeSteps) handler.postDelayed(this, fadeDurationMs / fadeSteps)
          }
        }
    handler.post(tick)
  }
}
