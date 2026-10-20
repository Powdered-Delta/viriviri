package com.m0e_n00b.viriviri

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.Panel

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
 *
 * ## Two gates, one control
 *
 * The analog half and the trigger half have SEPARATE enables:
 *
 * | half | [stageInputEnabled] | [stagePrimaryActionEnabled] |
 * | --- | --- | --- |
 * | thumbstick (scale / curvature) | closed while the workbench is up | — |
 * | trigger (stage primary action) | — | open while the workbench is up |
 *
 * The split is load-bearing. A live thumbstick under the panels made them unusable, so the analog half
 * must keep its old `!workbenchVisible` gate; but the stage is also the workbench's TOGGLE, so a single
 * gate would make the workbench openable and never closable from the stage.
 *
 * ## Rising edge only
 *
 * [onStageAction] and [onStageVoidTap] both fire on the RISING EDGE of a trigger press, and which one
 * fires is resolved by the pure [stageTapOutcome] so the precedence is unit-tested rather than
 * device-only. The previous held state is tracked per controller, and for EVERY frame rather than only
 * while the ray is on the stage, so a press that starts off-stage and then sweeps onto it cannot read as
 * a fresh press, and a release off-stage still clears the state.
 *
 * The edge is derived from `Controller.isDown` — the HELD state — rather than from
 * `Controller.isPressed`. That is deliberate: `isPressed` IS the SDK's own rising edge
 * (`buttonState & changedButtons & bits`), so it is true only on the frame the button goes down. Deriving
 * both the edge and the held state from the SAME signal is what keeps them from disagreeing, and a
 * level-triggered call would flip the workbench every frame the trigger is held.
 *
 * ## Void tap
 *
 * [onStageVoidTap] closes the workbench when the ray points at NO object at all. It exists because the
 * 9x6 m `WorkbenchOuterDismiss` slab used to be the "click outside" target, and its hittability made the
 * SDK draw a target-dot indicator over what should read as empty space. That slab is no longer hittable,
 * so the region is genuinely void and this callback is what dismisses from it.
 *
 * A consequence worth recording: with the slab no longer hittable there is no longer ANY click-on-release
 * path, so the k82 hazard (the release of the press that opened the workbench landing on the slab behind
 * the hit-less stage) cannot occur. That is why the previous `suppressOuterDismissUntilMs` per-frame
 * re-arm is gone rather than merely unused — see `docs/research/workbench-dismiss-hit-layer.md`.
 *
 * ## Panel-occlusion guard
 *
 * [StageRayTargeting] is a pure geometry test against the stage PLANE. It cannot know that a panel sits
 * in front of that plane and already consumes the same trigger through its `clickButtons`, so one press
 * would both activate the panel and toggle the workbench. [occludedByInteractiveEntity] closes that gap
 * with a `pointerInfo.rightEntity` / `leftEntity` check, behind the DEBUG-only
 * [stagePrimaryActionOcclusionGuardEnabled] switch (default ON) because it cannot be settled statically.
 * It only ever vetoes a TOGGLE; it must never veto a void dismiss, where by definition no panel is hit.
 */
internal class AnalogMediaStageTuningSystem(
    private val pointerInfo: PointerInfoSystem,
    /** Stage plane plus scaled footprint in world space; null while the stage has no `Transform`. */
    private val stageFrame: () -> StageRayTargeting.Frame?,
    /**
     * Gate for the ANALOG half (thumbstick scale / curvature). False while the workbench is on screen.
     * The stage control is a playing-only affordance: with the panels up the user is working them, and a
     * stick that still drove the stage underneath made the panels unusable.
     */
    private val stageInputEnabled: () -> Boolean,
    /**
     * Gate for the TRIGGER half. Deliberately a DIFFERENT predicate from [stageInputEnabled]: the stage
     * is the workbench's toggle, so this stays open while the panels are up.
     */
    private val stagePrimaryActionEnabled: () -> Boolean,
    /** Whether the workbench is on screen. The void tap only applies while it is. */
    private val workbenchVisible: () -> Boolean,
    /**
     * DEBUG-only switch for [occludedByInteractiveEntity]. Defaults to ON: the guard is the conservative
     * choice, and a device session can turn it off to prove whether a panel is what absorbed the ray.
     */
    private val stagePrimaryActionOcclusionGuardEnabled: () -> Boolean = { true },
    /** Legacy fallback target, used only when [stageFrame] yields nothing. */
    private val fallbackStageEntity: () -> Entity?,
    private val onScaleDelta: (Float) -> Unit,
    private val onCurvatureDelta: (Float) -> Unit,
    /** Toggle the workbench; RISING EDGE of a trigger press, resolved by [stageTapOutcome]. */
    private val onStageAction: () -> Unit,
    /** The ray pointed at no object at all; RISING EDGE only. Closes the workbench. */
    private val onStageVoidTap: () -> Unit,
    private val onInteractionFinished: () -> Unit,
    private val scaleSpeed: Float = 0.42f,
    private val curvatureSpeed: Float = 4f,
) : SystemBase() {
  private var lastTimeMs = System.currentTimeMillis()
  private var lastDiagnosticMs = 0L
  private var lastGuardLogMs = 0L
  private var wasAdjusting = false

  /**
   * Previous HELD trigger state, per controller, for the rising-edge test.
   *
   * Two booleans rather than a map: the only controllers that reach this system are the two local ones,
   * and the earlier `isRight` classification is already the key the geometric gate uses.
   */
  private var rightTriggerHeld = false
  private var leftTriggerHeld = false

  override fun execute() {
    val now = System.currentTimeMillis()
    val deltaSeconds = ((now - lastTimeMs).coerceIn(0L, 100L)) / 1_000f
    lastTimeMs = now

    val analogEnabled = stageInputEnabled()
    val actionEnabled = stagePrimaryActionEnabled()
    // Targeting is resolved while EITHER half is live: with the workbench up the analog gate is closed
    // but the trigger still has to know whether the ray is on the stage.
    val targetingEnabled = analogEnabled || actionEnabled
    val frame = if (targetingEnabled) stageFrame() else null
    val rightGeometry =
        if (targetingEnabled) {
          geometryTargets(frame, pointerInfo.rightRayOrigin, pointerInfo.rightRayTarget)
        } else {
          null
        }
    val leftGeometry =
        if (targetingEnabled) {
          geometryTargets(frame, pointerInfo.leftRayOrigin, pointerInfo.leftRayTarget)
        } else {
          null
        }
    // A null frame means the geometric test cannot run at all; fall back per side to entity equality so
    // the stage stays controllable (without the curvature immunity).
    val rightTargets = targetingEnabled && (rightGeometry ?: entityTargets(isRight = true))
    val leftTargets = targetingEnabled && (leftGeometry ?: entityTargets(isRight = false))
    logTargeting(
        now,
        targetingEnabled,
        analogEnabled,
        actionEnabled,
        frame,
        rightGeometry,
        rightTargets,
    )

    var adjusted = false
    Query.where { has(Controller.id) }.eval().filter { it.isLocal() }.forEach { entity ->
      val controller = entity.getComponent<Controller>()
      val isRight = PointerInfoSystem.isRightControllerOrRightHand(entity)
      if (!controller.isActive) {
        // An inactive controller cannot be holding a press. Drop its edge state so the next press is a
        // real rising edge instead of one inherited from before the controller went away.
        setTriggerHeld(isRight, false)
        return@forEach
      }
      // `isDown` is the HELD state, and it is deliberately the one used here:
      //
      // `Controller.isPressed` is NOT a held state — it is the SDK's own rising edge
      // (`buttonState & changedButtons & bits`), so it is true only on the frame the button goes down and
      // false for every frame of the hold. Driving the per-frame callback below from it would make that
      // callback fire once per press, which is precisely the k82 hole this system must not reopen. Both
      // the edge test and the hold callback are therefore derived from the same held-state signal, so the
      // two can never disagree about what "held" means.
      val triggerHeld =
          controller.isDown(ButtonBits.ButtonTriggerR) ||
              controller.isDown(ButtonBits.ButtonTriggerL)
      val risingEdge = triggerHeld && !isTriggerHeld(isRight)
      // Tracked every frame, before the targeting test: a press that starts off-stage and sweeps onto
      // it must not read as a fresh press, and a release off-stage must still clear the state.
      setTriggerHeld(isRight, triggerHeld)

      val targetsStage = if (isRight) rightTargets else leftTargets
      if (risingEdge && actionEnabled) {
        val hitEntityPresent =
            (if (isRight) pointerInfo.rightEntity else pointerInfo.leftEntity) != null
        val occluded = occludedByInteractiveEntity(isRight)
        val visible = workbenchVisible()
        val outcome =
            stageTapOutcome(
                actionEnabled = true,
                workbenchVisible = visible,
                hitEntityPresent = hitEntityPresent,
                targetsStage = targetsStage,
                occludedByPanel = occluded,
            )
        // One line per press, carrying the facts the outcome was derived from. This is what separates
        // "the panel guard ate it" from "the ray hit nothing" from "the ray hit some non-panel object"
        // in a single device run, and it is the only way to find out what sits behind the stage now that
        // the old dismiss slab is no longer hittable.
        logStageTap(isRight, visible, hitEntityPresent, targetsStage, occluded, outcome)
        when (outcome) {
          StageTapOutcome.TOGGLE_WORKBENCH -> onStageAction()
          StageTapOutcome.VOID_DISMISS -> onStageVoidTap()
          StageTapOutcome.NONE -> Unit
        }
      }

      if (!targetsStage) return@forEach
      // The thumbstick stays on the right controller; the left one only contributes taps. It is also the
      // half that stays OFF while the workbench is visible.
      if (!isRight || !analogEnabled) return@forEach
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
   * True when an interactive panel — not the stage itself — is what this controller's ray is actually
   * pointing at, so the trigger belongs to that panel and must not also toggle the workbench.
   *
   * The geometric gate tests the stage PLANE, so a ray that passes through a panel and reaches that
   * plane still satisfies it. The panel consumes the same trigger through its `clickButtons`
   * (`video_selector_panel` takes `ButtonA | TriggerL | TriggerR`), which is the "one press, two
   * actions" case this guard removes.
   *
   * Both stage handles are excluded from the verdict even though the stage carries no hit geometry
   * (`MeshCollision.NoCollision`): if a collider is ever added to the stage root, the guard must not be
   * what silences the toggle.
   *
   * This cannot be settled statically — the stage tap and a panel click look identical from here — so a
   * suppression is logged (throttled) on [DIAGNOSTIC_TAG]. That is what separates "the guard ate the tap"
   * from "the tap never reached the stage" in a single device run.
   */
  private fun occludedByInteractiveEntity(isRight: Boolean): Boolean {
    if (!stagePrimaryActionOcclusionGuardEnabled()) return false
    val hit = (if (isRight) pointerInfo.rightEntity else pointerInfo.leftEntity) ?: return false
    if (hit == Entity(R.id.spatialized_video_panel)) return false
    if (hit == fallbackStageEntity()) return false
    if (hit.tryGetComponent<Panel>() == null) return false
    logGuardSuppression(isRight)
    return true
  }

  private fun logGuardSuppression(isRight: Boolean) {
    if (!BuildConfig.DEBUG) return
    val now = System.currentTimeMillis()
    if (now - lastGuardLogMs < DIAGNOSTIC_INTERVAL_MS) return
    lastGuardLogMs = now
    Log.i(
        DIAGNOSTIC_TAG,
        "stage tap suppressed: the ${if (isRight) "right" else "left"} ray points at a panel, " +
            "not at the bare stage plane",
    )
  }

  /**
   * One line per trigger press, carrying every fact [stageTapOutcome] was derived from.
   *
   * Deliberately not throttled: a press is a discrete user action, and the whole purpose is to line the
   * press up with what the user was pointing at. `hitEntity` prints `null` when the ray reaches nothing,
   * which is the case the void tap exists for — and, now that the old dismiss slab is no longer
   * hittable, also the only way to discover what (if anything) sits behind the stage.
   */
  private fun logStageTap(
      isRight: Boolean,
      workbenchVisible: Boolean,
      hitEntityPresent: Boolean,
      targetsStage: Boolean,
      occludedByPanel: Boolean,
      outcome: StageTapOutcome,
  ) {
    if (!BuildConfig.DEBUG) return
    val hit = if (isRight) pointerInfo.rightEntity else pointerInfo.leftEntity
    Log.i(
        STAGE_TAP_TAG,
        "press side=${if (isRight) "right" else "left"} workbenchVisible=$workbenchVisible " +
            "hitEntity=${hit ?: "null"} targetsStage=$targetsStage occludedByPanel=$occludedByPanel " +
            "hitEntityPresent=$hitEntityPresent outcome=$outcome",
    )
  }

  private fun isTriggerHeld(isRight: Boolean): Boolean =
      if (isRight) rightTriggerHeld else leftTriggerHeld

  private fun setTriggerHeld(isRight: Boolean, held: Boolean) {
    if (isRight) {
      rightTriggerHeld = held
    } else {
      leftTriggerHeld = held
    }
  }

  /**
   * About one line a second describing why the stage is or is not targeted. Left in deliberately: the
   * previous regression was a silently always-false gate, and this turns that class of failure into a
   * single readable run.
   *
   * Both gates are reported because they can now disagree — with the workbench visible `analog=false`
   * and `action=true` is the expected, healthy reading.
   */
  private fun logTargeting(
      now: Long,
      targetingEnabled: Boolean,
      analogEnabled: Boolean,
      actionEnabled: Boolean,
      frame: StageRayTargeting.Frame?,
      rightGeometry: Boolean?,
      rightTargets: Boolean,
  ) {
    if (!BuildConfig.DEBUG) return
    if (now - lastDiagnosticMs < DIAGNOSTIC_INTERVAL_MS) return
    lastDiagnosticMs = now
    if (!targetingEnabled) {
      Log.i(DIAGNOSTIC_TAG, "disabled: analog=$analogEnabled action=$actionEnabled")
      return
    }
    if (frame == null) {
      Log.i(
          DIAGNOSTIC_TAG,
          "frame=NULL (stage Transform missing?) analog=$analogEnabled action=$actionEnabled " +
              "fallbackEntityHit=${pointerInfo.rightEntity} targets=$rightTargets",
      )
      return
    }
    Log.i(
        DIAGNOSTIC_TAG,
        "analog=$analogEnabled action=$actionEnabled " +
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
