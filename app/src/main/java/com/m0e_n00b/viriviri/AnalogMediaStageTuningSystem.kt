package com.m0e_n00b.viriviri

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.Controller

/**
 * Tunes the MediaStage with the right thumbstick while the user points at the stage:
 *
 * - up / down adjusts the shared scale of every stage layer,
 * - right / left adjusts the curvature of the layer currently selected as the curvature target, and
 * - either trigger on either controller runs the stage's primary action, matching the panel's default
 *   `clickButtons`.
 *
 * Targeting is GEOMETRIC ([StageRayTargeting]) rather than `pointerInfo.rightEntity == <stage>`. The
 * entity that carries the panel is the same entity the curvature surface offset moves, so an
 * entity-equality gate stops matching the instant anything is curved — that is what limited the
 * thumbstick to a single tick and swallowed the stage tap once a layer bent
 * (`docs/research/stage-video-mesh-curvature.md`, Stage 3f).
 *
 * The geometric gate falls back to the old entity comparison whenever a frame cannot be built, so a
 * missing stage `Transform` can never silence the control outright — it just loses the curvature
 * immunity. [DIAGNOSTIC_TAG] reports which of the two is in use about once a second.
 */
internal class AnalogMediaStageTuningSystem(
    private val pointerInfo: PointerInfoSystem,
    /** Stage plane plus scaled footprint in world space; null while the stage has no `Transform`. */
    private val stageFrame: () -> StageRayTargeting.Frame?,
    /**
     * False while the workbench is on screen. The stage control is a playing-only affordance: with the
     * panels up the user is working them, and a stick or trigger that still drove the stage underneath
     * made the panels unusable.
     */
    private val stageInputEnabled: () -> Boolean,
    /** Legacy fallback target, used only when [stageFrame] yields nothing. */
    private val fallbackStageEntity: () -> Entity?,
    private val onScaleDelta: (Float) -> Unit,
    private val onCurvatureDelta: (Float) -> Unit,
    private val onStageAction: () -> Unit,
    private val onInteractionFinished: () -> Unit,
    private val scaleSpeed: Float = 0.42f,
    private val curvatureSpeed: Float = 4f,
) : SystemBase() {
  private var lastTimeMs = System.currentTimeMillis()
  private var lastDiagnosticMs = 0L
  private var wasAdjusting = false

  override fun execute() {
    val now = System.currentTimeMillis()
    val deltaSeconds = ((now - lastTimeMs).coerceIn(0L, 100L)) / 1_000f
    lastTimeMs = now

    val enabled = stageInputEnabled()
    val frame = if (enabled) stageFrame() else null
    val rightGeometry =
        if (enabled) {
          geometryTargets(frame, pointerInfo.rightRayOrigin, pointerInfo.rightRayTarget)
        } else {
          null
        }
    val leftGeometry =
        if (enabled) {
          geometryTargets(frame, pointerInfo.leftRayOrigin, pointerInfo.leftRayTarget)
        } else {
          null
        }
    // A null frame means the geometric test cannot run at all; fall back per side to entity equality so
    // the stage stays controllable (without the curvature immunity).
    val rightTargets = enabled && (rightGeometry ?: entityTargets(isRight = true))
    val leftTargets = enabled && (leftGeometry ?: entityTargets(isRight = false))
    logTargeting(now, enabled, frame, rightGeometry, rightTargets)

    var adjusted = false
    Query.where { has(Controller.id) }.eval().filter { it.isLocal() }.forEach { entity ->
      val controller = entity.getComponent<Controller>()
      if (!controller.isActive) return@forEach
      val isRight = PointerInfoSystem.isRightControllerOrRightHand(entity)
      val targetsStage = if (isRight) rightTargets else leftTargets
      if (!targetsStage) return@forEach
      // Either trigger on either controller: this is what the panels' default clickButtons accept.
      if (controller.isPressed(ButtonBits.ButtonTriggerR) ||
          controller.isPressed(ButtonBits.ButtonTriggerL)) {
        onStageAction()
      }
      // The thumbstick stays on the right controller; the left one only contributes taps.
      if (!isRight) return@forEach
      // Positive = away from the user: up grows the stage, right tightens the curve.
      val scaleDelta =
          when {
            controller.isDown(ButtonBits.ButtonThumbRU) -> deltaSeconds * scaleSpeed
            controller.isDown(ButtonBits.ButtonThumbRD) -> -deltaSeconds * scaleSpeed
            else -> 0f
          }
      if (scaleDelta != 0f) {
        onScaleDelta(scaleDelta)
        adjusted = true
      }
      val curvatureDelta =
          when {
            controller.isDown(ButtonBits.ButtonThumbRR) -> deltaSeconds * curvatureSpeed
            controller.isDown(ButtonBits.ButtonThumbRL) -> -deltaSeconds * curvatureSpeed
            else -> 0f
          }
      if (curvatureDelta != 0f) {
        onCurvatureDelta(curvatureDelta)
        adjusted = true
      }
    }

    if (wasAdjusting && !adjusted) onInteractionFinished()
    wasAdjusting = adjusted
  }

  /** Geometric verdict, or null when no frame / no ray is available. */
  private fun geometryTargets(
      frame: StageRayTargeting.Frame?,
      origin: Vector3?,
      target: Vector3?,
  ): Boolean? {
    if (frame == null || origin == null || target == null) return null
    return StageRayTargeting.hits(
        frame,
        StageRayTargeting.Vec3(origin.x, origin.y, origin.z),
        StageRayTargeting.Vec3(target.x, target.y, target.z),
    )
  }

  /** Legacy `rightEntity` comparison, kept as the fallback when the geometric test cannot run. */
  private fun entityTargets(isRight: Boolean): Boolean {
    val stage = fallbackStageEntity() ?: return false
    val hit = if (isRight) pointerInfo.rightEntity else pointerInfo.leftEntity
    return hit != null && hit == stage
  }

  /**
   * About one line a second describing why the stage is or is not targeted. Left in deliberately: the
   * previous regression was a silently always-false gate, and this turns that class of failure into a
   * single readable run.
   */
  private fun logTargeting(
      now: Long,
      enabled: Boolean,
      frame: StageRayTargeting.Frame?,
      rightGeometry: Boolean?,
      rightTargets: Boolean,
  ) {
    if (!BuildConfig.DEBUG) return
    if (now - lastDiagnosticMs < DIAGNOSTIC_INTERVAL_MS) return
    lastDiagnosticMs = now
    if (!enabled) {
      Log.i(DIAGNOSTIC_TAG, "disabled while the workbench is visible")
      return
    }
    if (frame == null) {
      Log.i(
          DIAGNOSTIC_TAG,
          "frame=NULL (stage Transform missing?) fallbackEntityHit=${pointerInfo.rightEntity} " +
              "targets=$rightTargets",
      )
      return
    }
    Log.i(
        DIAGNOSTIC_TAG,
        "centre=${frame.centre.short()} normal=${frame.normal.short()} right=${frame.right.short()} " +
            "up=${frame.up.short()} half=${frame.halfWidth}x${frame.halfHeight} " +
            "ray=${pointerInfo.rightRayOrigin?.short()}->${pointerInfo.rightRayTarget?.short()} " +
            "geometry=$rightGeometry targets=$rightTargets",
    )
  }

  private fun StageRayTargeting.Vec3.short(): String =
      "(%.2f,%.2f,%.2f)".format(x, y, z)

  private fun Vector3.short(): String = "(%.2f,%.2f,%.2f)".format(x, y, z)

  private companion object {
    const val DIAGNOSTIC_TAG = "ViriViriTarget"
    const val DIAGNOSTIC_INTERVAL_MS = 1_000L
  }
}
