package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.isdk.IsdkGrabState
import com.meta.spatial.isdk.IsdkGrabbable
import com.meta.spatial.toolkit.Transform

/**
 * Persists the immersive video stage's user-adjusted world Y after a grab finishes.
 *
 * Design constraints (see task 09-02-workbench-lift-and-grab):
 * - Only the vertical offset (Y) survives restart; horizontal position (X/Z) and yaw are
 *   reset to their authored defaults on every launch, so a grabbed-away stage never gets lost.
 * - This system only OBSERVES the existing ISDK grab: it polls [IsdkGrabbable.grabState]
 *   for the Grabbed -> NotGrabbed edge, then reports the final stage [Transform] translation
 *   Y through [onStageGrabFinished]. It never writes the stage pose itself.
 */
internal class VideoStageGrabPersistenceSystem(
    private val mediaStageEntity: Entity,
    private val onStageGrabFinished: (Float) -> Unit,
) : SystemBase() {
  private var wasGrabbed = false

  override fun execute() {
    val grabbable = mediaStageEntity.tryGetComponent<IsdkGrabbable>() ?: return
    val isGrabbed = grabbable.grabState == IsdkGrabState.Grabbed
    if (isGrabbed) {
      wasGrabbed = true
      return
    }
    if (!wasGrabbed) return
    // Grabbed -> released edge: capture the final height.
    wasGrabbed = false
    val transform = mediaStageEntity.tryGetComponent<Transform>() ?: return
    val y = transform.transform.t.y
    if (y.isFinite() && y > 0f) {
      onStageGrabFinished(y)
    }
  }
}
