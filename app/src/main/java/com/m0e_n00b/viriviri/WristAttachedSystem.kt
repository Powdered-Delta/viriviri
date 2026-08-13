package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.AvatarBody
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible

/** Positions the optional debug panel on the local user's left wrist. */
class WristAttachedSystem : SystemBase() {
  private val wristEntities = mutableListOf<Entity>()

  private companion object {
    const val WRIST_VISIBLE_HEAD_DOT = 0.35f
  }

  override fun execute() {
    findNewEntities()
    val avatarBody =
        Query.where { has(AvatarBody.id) }
            .eval()
            .firstOrNull { it.isLocal() && it.getComponent<AvatarBody>().isPlayerControlled }
            ?.getComponent<AvatarBody>()
        ?: run {
          wristEntities.forEach { it.setComponent(Visible(false)) }
          return
        }
    val headTransform = avatarBody.head.tryGetComponent<Transform>()
    val handTransform = avatarBody.leftHand.tryGetComponent<Transform>()
    if (headTransform == null || handTransform == null) {
      wristEntities.forEach { it.setComponent(Visible(false)) }
      return
    }

    wristEntities.forEach { entity ->
      val attached = entity.getComponent<WristAttached>()
      val handPose = handTransform.transform
      val position = handPose.t + handPose.q.times(attached.position)
      val pose = Pose(position, if (attached.faceUser) headTransform.transform.q else handPose.q)
      val headToWrist = (position - headTransform.transform.t).normalize()
      val wristIsInFrontOfHead = headTransform.transform.forward().dot(headToWrist) > WRIST_VISIBLE_HEAD_DOT
      entity.setComponent(Transform(pose))
      entity.setComponent(Visible(wristIsInFrontOfHead))
    }
  }

  override fun delete(entity: Entity) {
    super.delete(entity)
    wristEntities.remove(entity)
  }

  private fun findNewEntities() {
    for (entity in Query.where { has(WristAttached.id, Transform.id) and changed(WristAttached.id) }.eval()) {
      if (entity.isLocal() && !wristEntities.contains(entity)) wristEntities.add(entity)
    }
  }
}
