package com.m0e_n00b.viriviri

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.AvatarAttachment
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.Transform
import kotlin.math.sqrt

/** Tracks the local left/right controller ray hit without owning panel visibility or routing. */
internal class PointerInfoSystem : SystemBase() {
  var leftEntity: Entity? = null
    private set
  var rightEntity: Entity? = null
    private set

  /** Controller ray origins/targets (world), exposed so systems can do proximity checks. */
  var leftRayOrigin: Vector3? = null
    private set
  var leftRayTarget: Vector3? = null
    private set
  var rightRayOrigin: Vector3? = null
    private set
  var rightRayTarget: Vector3? = null
    private set

  override fun execute() {
    val controllers = Query.where { has(Controller.id, Transform.id) }.eval().filter { it.isLocal() }
    for (controller in controllers) {
      val data = controller.getComponent<Controller>()
      if (!data.isActive) continue
      val transform = controller.getComponent<Transform>().transform
      val origin = transform.t
      val target = transform * Vector3(0f, 0f, POINTER_DISTANCE_METERS)
      val isRight = isRightControllerOrRightHand(controller)
      if (isRight) {
        rightRayOrigin = origin
        rightRayTarget = target
        rightEntity = getScene().lineSegmentIntersect(origin, target)?.entity
      } else {
        leftRayOrigin = origin
        leftRayTarget = target
        leftEntity = getScene().lineSegmentIntersect(origin, target)?.entity
      }
    }
  }

  /**
   * Shortest distance from either controller ray to [point] (metres), or null when no active
   * ray exists. Only counts points IN FRONT of the ray origin.
   */
  fun nearestRayDistanceTo(point: Vector3): Float? {
    val candidates = mutableListOf<Float>()
    listOf(leftRayOrigin to leftRayTarget, rightRayOrigin to rightRayTarget).forEach { (o, t) ->
      if (o == null || t == null) return@forEach
      rayDistanceToPoint(o, t, point)?.let(candidates::add)
    }
    return candidates.minOrNull()
  }

  companion object {
    private const val POINTER_DISTANCE_METERS = 5f

    fun isRightControllerOrRightHand(entity: Entity): Boolean {
      val attachment = entity.tryGetComponent<AvatarAttachment>() ?: return false
      return attachment.type == "right_controller" || attachment.type == "right_hand"
    }

    /** Distance from segment [origin]→[target] to [point]; null if [point] is behind the ray. */
    fun rayDistanceToPoint(origin: Vector3, target: Vector3, point: Vector3): Float? {
      val dx = target.x - origin.x
      val dy = target.y - origin.y
      val dz = target.z - origin.z
      val lenSq = dx * dx + dy * dy + dz * dz
      if (lenSq <= 1e-6f) return null
      val px = point.x - origin.x
      val py = point.y - origin.y
      val pz = point.z - origin.z
      val t = (px * dx + py * dy + pz * dz) / lenSq
      if (t < 0f) return null // behind the ray
      val tc = t.coerceIn(0f, 1f)
      val cx = origin.x + dx * tc
      val cy = origin.y + dy * tc
      val cz = origin.z + dz * tc
      val ex = point.x - cx
      val ey = point.y - cy
      val ez = point.z - cz
      return sqrt(ex * ex + ey * ey + ez * ez)
    }
  }
}
