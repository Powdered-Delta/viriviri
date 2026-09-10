package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.isdk.IsdkGrabState
import com.meta.spatial.isdk.IsdkGrabbable
import com.meta.spatial.toolkit.Transform

/**
 * Persists the immersive workbench's user-adjusted world Y after a grab finishes.
 *
 * Design constraints (see task 09-02-workbench-lift-and-grab):
 * - Only the vertical offset (Y) survives restart; horizontal position (X/Z) and yaw are
 *   reset to their authored defaults on every launch, so a grabbed-away workbench never gets
 *   lost.
 * - This system only OBSERVES the grab: it polls [IsdkGrabbable.grabState] of the grabbable
 *   anchor for the Grabbed -> NotGrabbed edge, then reports that anchor's final [Transform]
 *   translation Y through [onGrabFinished]. It never writes the pose itself.
 * - The anchor is created at runtime (onVRReady), so the entity is resolved lazily through
 *   [anchorProvider] on every frame.
 */
internal class VideoStageGrabPersistenceSystem(
    private val anchorProvider: () -> Entity?,
    private val onGrabFinished: (Float) -> Unit,
) : SystemBase() {
  private var wasGrabbed = false

  override fun execute() {
    val anchor = anchorProvider() ?: return
    val grabbable = anchor.tryGetComponent<IsdkGrabbable>() ?: return
    val isGrabbed = grabbable.grabState == IsdkGrabState.Grabbed
    if (isGrabbed) {
      wasGrabbed = true
      return
    }
    if (!wasGrabbed) return
    // Grabbed -> released edge: capture the final height.
    wasGrabbed = false
    val transform = anchor.tryGetComponent<Transform>() ?: return
    val y = transform.transform.t.y
    if (y.isFinite() && y > 0f) {
      onGrabFinished(y)
    }
  }
}
