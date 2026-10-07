package com.m0e_n00b.spatialworkbench.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Anchor / offset semantics as a contract (R7.3, design §5.5).
 *
 * Fixed here so that a later placement adapter has an implementable target:
 *   1. the anchor world pose belongs to the scene and is read-only at runtime;
 *   2. meters, right-handed; the anchor local frame points -Z at the user and -Y down;
 *   3. rotation order is fixed to Yaw -> Pitch -> Roll ([ROTATION_ORDER]);
 *   4. the local pose is `inverse(parentWorldPose) x anchorWorldPose x themeLocalOffset`, so
 *      reparenting preserves the world pose ("reparent does not jump");
 *   5. theme-static geometry ([PanelSpec.placement]) and user runtime adjustment
 *      ([UserAdjustableProperty]) are separate types and never share a field.
 *
 * This object performs no Spatial SDK call and writes no `Transform`.
 */

/** The fixed rotation application order. */
enum class RotationAxis {
  YAW,
  PITCH,
  ROLL,
}

object ThemePoseComposition {
  /** Yaw first, then pitch, then roll. `pitch/yaw/roll` in the schema are Euler angles in this order. */
  val ROTATION_ORDER: List<RotationAxis> = listOf(RotationAxis.YAW, RotationAxis.PITCH, RotationAxis.ROLL)

  private const val GIMBAL_EPSILON = 1e-6

  /**
   * `localPose = inverse(parentWorldPose) x anchorWorldPose x themeLocalOffset`, or
   * `anchorWorldPose x themeLocalOffset` when the panel has no parent (design §5.5 rule 4).
   */
  fun resolve(
      anchorWorldPose: PoseSpec,
      themeLocal: PoseSpec,
      parentWorldPose: PoseSpec? = null,
  ): PoseSpec {
    val anchored = affine(anchorWorldPose).times(affine(themeLocal))
    if (parentWorldPose == null) return anchored.toPose()
    return affine(parentWorldPose).inverse().times(anchored).toPose()
  }

  /** Pose composition: the result of applying [inner] inside [outer]'s frame. */
  fun compose(outer: PoseSpec, inner: PoseSpec): PoseSpec =
      affine(outer).times(affine(inner)).toPose()

  /** Inverse pose (rotation transposed, translation transformed). */
  fun inverse(pose: PoseSpec): PoseSpec = affine(pose).inverse().toPose()

  /**
   * Recomputes the local pose for a new parent while preserving the world pose, i.e. reparenting
   * never moves the panel.
   */
  fun reparent(
      localPose: PoseSpec,
      fromParentWorldPose: PoseSpec,
      toParentWorldPose: PoseSpec,
  ): PoseSpec {
    if (fromParentWorldPose == toParentWorldPose) return localPose
    val world = affine(fromParentWorldPose).times(affine(localPose))
    return affine(toParentWorldPose).inverse().times(world).toPose()
  }

  /** Applies a rotation (in [ROTATION_ORDER]) to a local vector. */
  fun rotate(rotationDegrees: EulerDegrees, vector: Meters3): Meters3 =
      Rotation.fromEuler(rotationDegrees).apply(vector)

  private fun affine(pose: PoseSpec): Affine =
      Affine(Rotation.fromEuler(pose.rotationDegrees), pose.offsetMeters)

  private class Rotation(
      private val m00: Double,
      private val m01: Double,
      private val m02: Double,
      private val m10: Double,
      private val m11: Double,
      private val m12: Double,
      private val m20: Double,
      private val m21: Double,
      private val m22: Double,
  ) {
    fun times(other: Rotation): Rotation =
        Rotation(
            m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
            m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
            m00 * other.m02 + m01 * other.m12 + m02 * other.m22,
            m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
            m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
            m10 * other.m02 + m11 * other.m12 + m12 * other.m22,
            m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
            m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
            m20 * other.m02 + m21 * other.m12 + m22 * other.m22,
        )

    fun transpose(): Rotation = Rotation(m00, m10, m20, m01, m11, m21, m02, m12, m22)

    fun apply(vector: Meters3): Meters3 =
        Meters3(
            (m00 * vector.x + m01 * vector.y + m02 * vector.z).toFloat(),
            (m10 * vector.x + m11 * vector.y + m12 * vector.z).toFloat(),
            (m20 * vector.x + m21 * vector.y + m22 * vector.z).toFloat(),
        )

    fun toEulerDegrees(): EulerDegrees {
      val pitch = asin(m21.coerceIn(-1.0, 1.0))
      val cosPitch = cos(pitch)
      if (abs(cosPitch) < GIMBAL_EPSILON) {
        // Degenerate gimbal lock: keep yaw at zero and fold the remaining rotation into roll.
        return EulerDegrees(
            pitch = Math.toDegrees(pitch).toFloat(),
            yaw = 0f,
            roll = Math.toDegrees(atan2(m10, m00)).toFloat(),
        )
      }
      val yaw = atan2(-m20, m22)
      val roll = atan2(-m01, m11)
      return EulerDegrees(
          pitch = Math.toDegrees(pitch).toFloat(),
          yaw = Math.toDegrees(yaw).toFloat(),
          roll = Math.toDegrees(roll).toFloat(),
      )
    }

    companion object {
      /**
       * `R = Rz(roll) * Rx(pitch) * Ry(yaw)`: a local vector is first yawed, then pitched, then
       * rolled, which is exactly the fixed Yaw -> Pitch -> Roll order.
       */
      fun fromEuler(euler: EulerDegrees): Rotation {
        val yaw = Math.toRadians(euler.yaw.toDouble())
        val pitch = Math.toRadians(euler.pitch.toDouble())
        val roll = Math.toRadians(euler.roll.toDouble())
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val cr = cos(roll)
        val sr = sin(roll)
        return Rotation(
            cr * cy - sr * sp * sy,
            -sr * cp,
            cr * sy + sr * sp * cy,
            sr * cy + cr * sp * sy,
            cr * cp,
            sr * sy - cr * sp * cy,
            -cp * sy,
            sp,
            cp * cy,
        )
      }
    }
  }

  private class Affine(private val rotation: Rotation, private val translation: Meters3) {
    fun times(other: Affine): Affine {
      val rotated = rotation.apply(other.translation)
      return Affine(
          rotation.times(other.rotation),
          Meters3(rotated.x + translation.x, rotated.y + translation.y, rotated.z + translation.z),
      )
    }

    fun inverse(): Affine {
      val transposed = rotation.transpose()
      val negated = Meters3(-translation.x, -translation.y, -translation.z)
      return Affine(transposed, transposed.apply(negated))
    }

    fun toPose(): PoseSpec = PoseSpec(translation, rotation.toEulerDegrees())
  }

  fun distanceMeters(a: Meters3, b: Meters3): Float {
    val dx = (a.x - b.x).toDouble()
    val dy = (a.y - b.y).toDouble()
    val dz = (a.z - b.z).toDouble()
    return sqrt(dx * dx + dy * dy + dz * dz).toFloat()
  }
}
