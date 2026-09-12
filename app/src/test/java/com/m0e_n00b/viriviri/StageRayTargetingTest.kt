package com.m0e_n00b.viriviri

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageRayTargetingTest {
  private val flatStage =
      StageRayTargeting.Frame(
          centre = StageRayTargeting.Vec3(0f, 1.5f, 2f),
          normal = StageRayTargeting.Vec3(0f, 0f, 1f),
          right = StageRayTargeting.Vec3(1f, 0f, 0f),
          up = StageRayTargeting.Vec3(0f, 1f, 0f),
          halfWidth = 1f,
          halfHeight = 0.5f,
      )

  private fun ray(ox: Float, oy: Float, oz: Float, tx: Float, ty: Float, tz: Float) =
      StageRayTargeting.hits(
          flatStage,
          StageRayTargeting.Vec3(ox, oy, oz),
          StageRayTargeting.Vec3(tx, ty, tz),
      )

  @Test
  fun rayThroughTheRectangleHits() {
    // Crosses z = 2 (the stage plane) at s = 0.5, dead centre.
    assertTrue(ray(0f, 1.5f, 0f, 0f, 1.5f, 4f))
    // Same plane crossing, still inside the extents.
    assertTrue(ray(0.99f, 1.99f, 0f, 0.99f, 1.99f, 4f))
  }

  @Test
  fun rayOutsideTheExtentsMisses() {
    // 3.0 m to the side of a 1.0 m half-width.
    assertFalse(ray(3f, 1.5f, 0f, 3f, 1.5f, 4f))
    // 1.4 m above a 0.5 m half-height.
    assertFalse(ray(0f, 2.9f, 0f, 0f, 2.9f, 4f))
  }

  @Test
  fun segmentThatStopsShortOfThePlaneMisses() {
    // Points at the stage but only reaches z = 1: the controller's fixed reach must not wrap around.
    assertFalse(ray(0f, 1.5f, 0f, 0f, 1.5f, 1f))
  }

  @Test
  fun rayBehindTheStageMisses() {
    // Crosses the plane at s = 1.5, i.e. past the far end of the segment.
    assertFalse(ray(0f, 1.5f, 3f, 0f, 1.5f, 2.5f))
  }

  @Test
  fun rayParallelToThePlaneMisses() {
    assertFalse(ray(0f, 1.5f, 2f, 4f, 1.5f, 2f))
  }

  @Test
  fun degenerateAxesMiss() {
    val degenerate = flatStage.copy(normal = StageRayTargeting.Vec3(0f, 0f, 0f))
    assertFalse(
        StageRayTargeting.hits(
            degenerate,
            StageRayTargeting.Vec3(0f, 1.5f, 0f),
            StageRayTargeting.Vec3(0f, 1.5f, 4f),
        )
    )
  }

  @Test
  fun nonUnitAxesAreNormalisedBeforeUse() {
    // Same frame, axes scaled by 100: the extents must still mean metres.
    val scaled =
        flatStage.copy(
            normal = StageRayTargeting.Vec3(0f, 0f, 100f),
            right = StageRayTargeting.Vec3(100f, 0f, 0f),
            up = StageRayTargeting.Vec3(0f, 100f, 0f),
        )
    assertTrue(
        StageRayTargeting.hits(
            scaled,
            StageRayTargeting.Vec3(0.5f, 1.6f, 0f),
            StageRayTargeting.Vec3(0.5f, 1.6f, 4f),
        )
    )
    assertFalse(
        StageRayTargeting.hits(
            scaled,
            StageRayTargeting.Vec3(3f, 1.6f, 0f),
            StageRayTargeting.Vec3(3f, 1.6f, 4f),
        )
    )
  }

  @Test
  fun yawedStageUsesItsOwnAxes() {
    // Stage yawed 90 degrees: its "right" now runs along world -Z, so an offset in world X is depth.
    val yawed =
        flatStage.copy(
            normal = StageRayTargeting.Vec3(1f, 0f, 0f),
            right = StageRayTargeting.Vec3(0f, 0f, -1f),
            up = StageRayTargeting.Vec3(0f, 1f, 0f),
        )
    // Aimed along +X from 2 m to the side: crosses x = 0 with zero lateral offset -> inside.
    assertTrue(
        StageRayTargeting.hits(
            yawed,
            StageRayTargeting.Vec3(-2f, 1.5f, 2f),
            StageRayTargeting.Vec3(2f, 1.5f, 2f),
        )
    )
    // Same crossing, but 2 m along the yawed "right" axis -> outside the 1 m half-width.
    assertFalse(
        StageRayTargeting.hits(
            yawed,
            StageRayTargeting.Vec3(-2f, 1.5f, 0f),
            StageRayTargeting.Vec3(2f, 1.5f, 0f),
        )
    )
  }

  @Test
  fun aLocalPoseRatherThanTheWorldPoseMakesEveryRayMiss() {
    // Real numbers from the device log that exposed this. The stage root's LOCAL pose reads z = 0 while
    // the stage actually sits at the anchor's world z (~2 m); the controller ray starts at z = 0.25 and
    // points at +z, so it crosses the local plane BEHIND its own origin (s < 0) and every frame missed.
    val origin = StageRayTargeting.Vec3(0.09f, 1.41f, 0.25f)
    val target = StageRayTargeting.Vec3(0.40f, 1.84f, 5.23f)
    val localPlane =
        StageRayTargeting.Frame(
            centre = StageRayTargeting.Vec3(0f, 0.71f, 0f),
            normal = StageRayTargeting.Vec3(0f, 0f, 1f),
            right = StageRayTargeting.Vec3(1f, 0f, 0f),
            up = StageRayTargeting.Vec3(0f, 1f, 0f),
            halfWidth = 1.1627042f,
            halfHeight = 0.6540211f,
        )
    assertFalse(StageRayTargeting.hits(localPlane, origin, target))
    // The same ray against the resolved WORLD plane crosses inside the segment, so the pose resolution
    // is the only difference. The y extent is widened on purpose: the world centre y is not in the log.
    val worldPlane =
        localPlane.copy(centre = StageRayTargeting.Vec3(0f, 0.71f, 2f), halfHeight = 5f)
    assertTrue(StageRayTargeting.hits(worldPlane, origin, target))
  }
}
