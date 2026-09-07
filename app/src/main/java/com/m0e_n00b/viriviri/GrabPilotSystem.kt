package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.isdk.IsdkGrabState
import com.meta.spatial.isdk.IsdkGrabbable
import com.meta.spatial.toolkit.Transform

/**
 * Generic grab driver: bind ANY grabbable handle entity to ONE OR MORE follower targets, and
 * this system mirrors the handle's drag onto every follower, firing grab lifecycle events.
 *
 * Theme-driven design (task 09-02-workbench-lift-and-grab):
 * - Default theme: the independent grab bar (handle) drags the whole workbench — followers =
 *   the workbench root (stage + children ride along as its TransformParent children).
 * - Future themes: bind different handles to different panel/stage GROUPS by registering more
 *   instances with different follower lists.
 *
 * Motion mirrored:
 * - Translation: the handle's incremental world delta is added to each follower.
 * - Orientation: while grabbed the follower's rotation is synced to the handle's rotation
 *   (whole-quaternion copy — bar grabs are yaw-dominated; pitch/roll follow along).
 *
 * Events: onGrabStarted / onGrabMoved / onGrabEnded.
 */
internal class GrabPilotSystem(
    private val handle: Entity,
    /** Follower targets that receive the handle's world delta while grabbed (lazy per frame). */
    private val followersProvider: () -> List<Entity> = { emptyList() },
    /** Pose for the handle when it is NOT being grabbed (e.g. parked under the stage). Null = leave it. */
    private val restPoseProvider: () -> Pose? = { null },
    /** Mirror the handle's rotation onto followers while grabbed. */
    private val mirrorRotation: Boolean = true,
    private val onGrabStarted: (Entity) -> Unit = {},
    private val onGrabMoved: (Entity, Vector3) -> Unit = { _, _ -> },
    private val onGrabEnded: (Entity) -> Unit = {},
) : SystemBase() {
  private var wasGrabbed = false
  private var lastHandlePosition: Vector3? = null

  override fun execute() {
    val grabbable = handle.tryGetComponent<IsdkGrabbable>() ?: return
    val handleTransform = handle.tryGetComponent<Transform>() ?: return
    val isGrabbed = grabbable.grabState == IsdkGrabState.Grabbed
    val handlePose = handleTransform.transform

    if (isGrabbed) {
      if (!wasGrabbed) {
        wasGrabbed = true
        lastHandlePosition = handlePose.t
        onGrabStarted(handle)
      } else {
        val last = lastHandlePosition ?: handlePose.t
        val delta =
            Vector3(handlePose.t.x - last.x, handlePose.t.y - last.y, handlePose.t.z - last.z)
        lastHandlePosition = handlePose.t
        if (delta.x != 0f || delta.y != 0f || delta.z != 0f) {
          followersProvider().forEach { applyDeltaToTarget(it, delta) }
          onGrabMoved(handle, delta)
        }
        // Sync orientation so yaw (swing) on the bar rotates the whole workbench.
        if (mirrorRotation) {
          followersProvider().forEach { applyRotationToTarget(it, handlePose.q) }
        }
      }
      return
    }

    if (wasGrabbed) {
      wasGrabbed = false
      lastHandlePosition = null
      onGrabEnded(handle)
    }
    // Not grabbed: park the handle at the configured rest pose (e.g. under the stage).
    val rest = restPoseProvider()
    if (rest != null) {
      val current = handleTransform.transform
      if (current.t.x != rest.t.x || current.t.y != rest.t.y || current.t.z != rest.t.z) {
        handle.setComponent(Transform(rest))
      }
    }
  }

  private fun applyDeltaToTarget(target: Entity, delta: Vector3) {
    val targetTransform = target.tryGetComponent<Transform>() ?: return
    val pose = targetTransform.transform
    pose.t = Vector3(pose.t.x + delta.x, pose.t.y + delta.y, pose.t.z + delta.z)
    target.setComponent(Transform(pose))
  }

  /** Syncs the target's rotation to the handle's quaternion (yaw-led rotation mirror). */
  private fun applyRotationToTarget(target: Entity, q: com.meta.spatial.core.Quaternion) {
    val targetTransform = target.tryGetComponent<Transform>() ?: return
    val pose = targetTransform.transform
    if (pose.q != q) {
      pose.q = q
      target.setComponent(Transform(pose))
    }
  }
}
