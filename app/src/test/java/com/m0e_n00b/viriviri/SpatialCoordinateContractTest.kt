package com.m0e_n00b.viriviri

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test

/** Executes the actual pinned SDK math, rather than restating a generic OpenXR convention. */
class SpatialCoordinateContractTest {
  private fun assertVector(expected: Vector3, actual: Vector3) {
    assertEquals(expected.x, actual.x, 0.0001f)
    assertEquals(expected.y, actual.y, 0.0001f)
    assertEquals(expected.z, actual.z, 0.0001f)
  }

  @Test
  fun pinnedSdkIdentityUsesPositiveZForwardAndPositiveXRight() {
    assertVector(Vector3(0f, 0f, 1f), Pose().forward())
    assertVector(Vector3(1f, 0f, 0f), Pose().right())
    assertVector(Vector3(1f, 0f, 0f), Quaternion(0f, 90f, 0f) * Vector3(0f, 0f, 1f))
  }

  @Test
  fun sharedCarrierPinsCylinderCentralPointAcrossRadiiEvenWhenStageIsRotated() {
    // Native SDK cylinder generation has a central API-local point at (0,0,+radius).
    val stage = Pose(Vector3(0f, 1.25f, 2f), Quaternion(0f, 35f, 0f))
    for (baseZ in listOf(0f, 0.01f, -0.01f)) {
      for (radius in listOf(1.5f, 2.7f, 7.9f)) {
        val curvature = PlaybackStageCurvature.Cylinder(radius)
        val carrier = Pose(Vector3(0f, 0f, curvature.entityLocalZForSurface(baseZ)))
        assertVector(stage * Vector3(0f, 0f, baseZ), stage * (carrier * Vector3(0f, 0f, radius)))
      }
      assertVector(
          stage * Vector3(0f, 0f, baseZ),
          stage * Vector3(0f, 0f, PlaybackStageCurvature.Flat.entityLocalZForSurface(baseZ)),
      )
    }
  }
}
