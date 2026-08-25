package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.AvatarAttachment
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.Transform

/** Tracks the local left/right controller ray hit without owning panel visibility or routing. */
internal class PointerInfoSystem : SystemBase() {
  var leftEntity: Entity? = null
    private set
  var rightEntity: Entity? = null
    private set

  override fun execute() {
    val controllers = Query.where { has(Controller.id, Transform.id) }.eval().filter { it.isLocal() }
    for (controller in controllers) {
      val data = controller.getComponent<Controller>()
      if (!data.isActive) continue
      val transform = controller.getComponent<Transform>().transform
      val origin = transform.t
      val target = transform * Vector3(0f, 0f, POINTER_DISTANCE_METERS)
      val destination = if (isRightControllerOrRightHand(controller)) {
        { entity: Entity? -> rightEntity = entity }
      } else {
        { entity: Entity? -> leftEntity = entity }
      }
      destination(getScene().lineSegmentIntersect(origin, target)?.entity)
    }
  }

  companion object {
    private const val POINTER_DISTANCE_METERS = 5f

    fun isRightControllerOrRightHand(entity: Entity): Boolean {
      val attachment = entity.tryGetComponent<AvatarAttachment>() ?: return false
      return attachment.type == "right_controller" || attachment.type == "right_hand"
    }
  }
}
