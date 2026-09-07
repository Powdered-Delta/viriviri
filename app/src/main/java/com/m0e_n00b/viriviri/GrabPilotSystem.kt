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
 * - Default theme: the visual grab bar (handle) drags the whole workbench — followers = the
 *   video stage (whose TransformParent children incl. rails ride along).
 * - Future themes: bind different handles to different panel/stage GROUPS (a handle per group,
 *   or no grab at all for fixed elements), all through the same system by registering more
 *   instances with different follower lists.
 *
 * Events (bind any of them):
 * - [onGrabStarted] fires on the Grabbed edge.
 * - [onGrabMoved] fires per frame while grabbed with the incremental world delta.
 * - [onGrabEnded] fires on release (after the handle has been re-parked).
 */
internal class GrabPilotSystem(
    private val handle: Entity,
    /** Follower targets that receive the handle's world delta while grabbed. */
    private val followers: List<Entity>,
    /** Pose for the handle when it is NOT being grabbed (e.g. parked under the stage). Null = leave it. */
    private val restPoseProvider: () -> Pose? = { null },
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
    val handlePos = handleTransform.transform.t

    if (isGrabbed) {
      if (!wasGrabbed) {
        wasGrabbed = true
        lastHandlePosition = handlePos
        onGrabStarted(handle)
      } else {
        val last = lastHandlePosition ?: handlePos
        val delta = Vector3(handlePos.x - last.x, handlePos.y - last.y, handlePos.z - last.z)
        lastHandlePosition = handlePos
        if (delta.x != 0f || delta.y != 0f || delta.z != 0f) {
          followers.forEach { applyDeltaToTarget(it, delta) }
          onGrabMoved(handle, delta)
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
}
