package com.m0e_n00b.viriviri

/**
 * Ray-vs-stage-rectangle targeting for the stage input.
 *
 * Stage input must NOT be driven by `PointerInfoSystem.rightEntity`. The entity that carries the video
 * panel is the same entity that receives the curvature surface offset, so its hit geometry leaves the
 * authored plane the moment a layer is curved — which is why one curvature nudge used to kill the
 * thumbstick after a single tick and swallow the stage tap (see
 * `docs/research/stage-video-mesh-curvature.md`, Stage 3f). The controller ray itself is rebuilt every
 * frame as a fixed-length segment and does not depend on hit-testing, so intersecting that segment with
 * the stage rectangle keeps working wherever the render carrier has moved.
 *
 * Deliberately plain float math with its own value types: no Spatial types, so it is covered by
 * ordinary JVM tests.
 */
internal object StageRayTargeting {
  /** Minimal 3-component vector, kept local so the math stays free of Spatial types. */
  data class Vec3(val x: Float, val y: Float, val z: Float)

  /**
   * The stage's plane: [centre] plus the stage-local axes expressed in world space, and the rectangle's
   * half extents. Axes do not have to be unit length — they are normalised inside [hits].
   */
  data class Frame(
      val centre: Vec3,
      val normal: Vec3,
      val right: Vec3,
      val up: Vec3,
      val halfWidth: Float,
      val halfHeight: Float,
  )

  /**
   * True when the segment [origin] -> [target] crosses the stage rectangle.
   *
   * The SEGMENT is tested, not an infinite ray: a controller points about [PointerInfoSystem]'s fixed
   * reach, and a stage beyond that (or behind the user) must not react.
   */
  fun hits(frame: Frame, origin: Vec3, target: Vec3): Boolean {
    val normal = normalized(frame.normal) ?: return false
    val right = normalized(frame.right) ?: return false
    val up = normalized(frame.up) ?: return false

    val dx = target.x - origin.x
    val dy = target.y - origin.y
    val dz = target.z - origin.z
    val denominator = normal.x * dx + normal.y * dy + normal.z * dz
    // Parallel to the stage plane: never a crossing.
    if (kotlin.math.abs(denominator) < 1e-6f) return false

    val toCentreX = frame.centre.x - origin.x
    val toCentreY = frame.centre.y - origin.y
    val toCentreZ = frame.centre.z - origin.z
    val s = (normal.x * toCentreX + normal.y * toCentreY + normal.z * toCentreZ) / denominator
    if (s < 0f || s > 1f) return false

    val hitX = origin.x + dx * s - frame.centre.x
    val hitY = origin.y + dy * s - frame.centre.y
    val hitZ = origin.z + dz * s - frame.centre.z
    if (kotlin.math.abs(hitX * right.x + hitY * right.y + hitZ * right.z) > frame.halfWidth) {
      return false
    }
    if (kotlin.math.abs(hitX * up.x + hitY * up.y + hitZ * up.z) > frame.halfHeight) {
      return false
    }
    return true
  }

  private fun normalized(vector: Vec3): Vec3? {
    val length = kotlin.math.sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)
    if (length < 1e-6f) return null
    return Vec3(vector.x / length, vector.y / length, vector.z / length)
  }
}
