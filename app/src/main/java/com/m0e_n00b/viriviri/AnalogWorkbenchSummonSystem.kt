package com.m0e_n00b.viriviri

import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.toolkit.Controller

/**
 * Escape hatch: pressing A performs the MediaStage's primary action WITHOUT needing to hit the stage.
 *
 * The stage click is the only way into the workbench today (stage click -> PlaybackCanvas -> slot
 * visibility -> WorkbenchEvent), and the stage's hit geometry belongs to the video surface entity,
 * which moves with the curvature offset. A bad curvature therefore makes the workbench unreachable,
 * and that also hides the debug rail that owns the curvature reset — a closed loop whose only exit was
 * clearing app data. This system breaks the loop with an input source that depends on no panel being
 * hittable.
 *
 * It drives the same canvas entry point as the click (`onStagePrimaryAction`) rather than dispatching a
 * workbench event itself. An earlier version dispatched `RevealTransport` directly and that was wrong:
 * the workbench is downstream of the canvas, so the canvas stayed in QUIET_WATCH while the workbench
 * went visible, which desynced slot visibility (transport missing) and dismissal.
 *
 * A is also listed as the left rail's `clickButtons` (`PanelInputOptions` in
 * `selectorPanelRegistration`), but that rail is a read-only detail view: its only live control is the
 * comments action, and its other three icon buttons are `enabled = false` no-ops. So a plain press is
 * safe here; there is no meaningful click to collide with.
 *
 * Registered only in DEBUG builds, next to the stage tuning control.
 */
internal class AnalogWorkbenchSummonSystem(private val onSummon: () -> Unit) : SystemBase() {
  override fun execute() {
    Query.where { has(Controller.id) }.eval().filter { it.isLocal() }.forEach { entity ->
      val controller = entity.getComponent<Controller>()
      if (!controller.isActive || !PointerInfoSystem.isRightControllerOrRightHand(entity)) {
        return@forEach
      }
      // Edge, not level: one summon per press.
      if (controller.isPressed(ButtonBits.ButtonA)) onSummon()
    }
  }
}
