package com.m0e_n00b.spatialworkbench.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R7.3: the anchor/offset/rotation contract of design §5.5. */
class ThemePoseCompositionTest {

  private val epsilon = 1e-3f

  @Test
  fun rotationOrderIsFixedToYawThenPitchThenRoll() {
    assertEquals(
        listOf(RotationAxis.YAW, RotationAxis.PITCH, RotationAxis.ROLL),
        ThemePoseComposition.ROTATION_ORDER,
    )
  }

  @Test
  fun compositionAppliesTheChildOffsetInTheParentFrame() {
    val parent = PoseSpec(offsetMeters = Meters3(1f, 0f, 0f))
    val child = PoseSpec(offsetMeters = Meters3(0f, 1f, 0f))

    val composed = ThemePoseComposition.compose(parent, child)

    assertApprox(Meters3(1f, 1f, 0f), composed.offsetMeters)
  }

  @Test
  fun yawRotatesTheChildOffsetAndOrderMatters() {
    val yawOnly = PoseSpec(rotationDegrees = EulerDegrees(yaw = 90f))
    val child = PoseSpec(offsetMeters = Meters3(0f, 0f, -1f))

    // Yaw by +90 degrees maps -Z onto -X.
    assertApprox(Meters3(-1f, 0f, 0f), ThemePoseComposition.compose(yawOnly, child).offsetMeters)
    assertApprox(Meters3(-1f, 0f, 0f), ThemePoseComposition.rotate(EulerDegrees(yaw = 90f), Meters3(0f, 0f, -1f)))
  }

  @Test
  fun rotationOrderAppliesYawBeforePitch() {
    val vector = Meters3(0f, 0f, -1f)

    // The fixed order: yaw first, then pitch.
    val fixed = ThemePoseComposition.rotate(EulerDegrees(pitch = 90f, yaw = 90f), vector)
    val yawThenPitch =
        ThemePoseComposition.rotate(
            EulerDegrees(pitch = 90f),
            ThemePoseComposition.rotate(EulerDegrees(yaw = 90f), vector),
        )
    assertApprox(yawThenPitch, fixed)

    // The opposite order would give a different result, which is why the order is part of the contract.
    val pitchThenYaw =
        ThemePoseComposition.rotate(
            EulerDegrees(yaw = 90f),
            ThemePoseComposition.rotate(EulerDegrees(pitch = 90f), vector),
        )
    assertTrue(ThemePoseComposition.distanceMeters(fixed, pitchThenYaw) > epsilon)
  }

  @Test
  fun inverseUndoesComposition() {
    val pose =
        PoseSpec(
            offsetMeters = Meters3(0.3f, -0.2f, 0.7f),
            rotationDegrees = EulerDegrees(pitch = 20f, yaw = -35f, roll = 12f),
        )

    val roundTrip = ThemePoseComposition.compose(pose, ThemePoseComposition.inverse(pose))

    assertApprox(Meters3(), roundTrip.offsetMeters)
    assertEquals(0f, roundTrip.rotationDegrees.pitch, epsilon)
    assertEquals(0f, roundTrip.rotationDegrees.yaw, epsilon)
    assertEquals(0f, roundTrip.rotationDegrees.roll, epsilon)
  }

  @Test
  fun localPoseIsTheParentRelativeCompositionOfAnchorAndThemeOffset() {
    val anchorWorld =
        PoseSpec(
            offsetMeters = Meters3(0f, 1.6f, -2f),
            rotationDegrees = EulerDegrees(yaw = 30f),
        )
    val themeLocal = PoseSpec(offsetMeters = Meters3(0f, -0.2f, -0.14f), rotationDegrees = EulerDegrees(pitch = 20f))

    val withoutParent = ThemePoseComposition.resolve(anchorWorld, themeLocal)
    // The theme offset is applied inside the anchor's *rotated* frame, not added in world space.
    assertTrue(
        ThemePoseComposition.distanceMeters(
            withoutParent.offsetMeters,
            Meters3(
                anchorWorld.offsetMeters.x + themeLocal.offsetMeters.x,
                anchorWorld.offsetMeters.y + themeLocal.offsetMeters.y,
                anchorWorld.offsetMeters.z + themeLocal.offsetMeters.z,
            ),
        ) > epsilon
    )

    val parentWorld = PoseSpec(offsetMeters = Meters3(1f, 1.5f, -2.5f), rotationDegrees = EulerDegrees(yaw = -10f))
    val withParent = ThemePoseComposition.resolve(anchorWorld, themeLocal, parentWorld)

    // The resolved local pose under the parent reproduces the same world pose.
    val rebuiltWorld = ThemePoseComposition.compose(parentWorld, withParent)
    assertApprox(withoutParent.offsetMeters, rebuiltWorld.offsetMeters)
    assertEquals(withoutParent.rotationDegrees.pitch, rebuiltWorld.rotationDegrees.pitch, epsilon)
    assertEquals(withoutParent.rotationDegrees.yaw, rebuiltWorld.rotationDegrees.yaw, epsilon)
    assertEquals(withoutParent.rotationDegrees.roll, rebuiltWorld.rotationDegrees.roll, epsilon)
  }

  @Test
  fun reparentingKeepsTheWorldPoseAndDoesNotJump() {
    val local = PoseSpec(offsetMeters = Meters3(0.1f, -0.3f, -0.5f), rotationDegrees = EulerDegrees(pitch = 15f, yaw = 5f))
    val oldParent = PoseSpec(offsetMeters = Meters3(0f, 1.5f, -2f), rotationDegrees = EulerDegrees(yaw = 25f))
    val newParent = PoseSpec(offsetMeters = Meters3(0.5f, 1.4f, -2.6f), rotationDegrees = EulerDegrees(yaw = -40f, roll = 8f))

    val worldBefore = ThemePoseComposition.compose(oldParent, local)
    val reparented = ThemePoseComposition.reparent(local, oldParent, newParent)
    val worldAfter = ThemePoseComposition.compose(newParent, reparented)

    assertApprox(worldBefore.offsetMeters, worldAfter.offsetMeters)
    assertEquals(worldBefore.rotationDegrees.yaw, worldAfter.rotationDegrees.yaw, epsilon)
    assertEquals(worldBefore.rotationDegrees.pitch, worldAfter.rotationDegrees.pitch, epsilon)

    // Reparenting to the same frame is an exact no-op.
    assertEquals(local, ThemePoseComposition.reparent(local, oldParent, oldParent))
  }

  @Test
  fun themeStaticGeometryAndUserAdjustableSettingsAreSeparateTypes() {
    // The only user-adjustable values are the two existing user settings; geometry is not on the list.
    assertEquals(
        listOf(UserAdjustableProperty.STAGE_SCALE, UserAdjustableProperty.STAGE_HEIGHT),
        UserAdjustableProperty.entries.toList(),
    )
    assertEquals(setOf("stageScale", "stageHeight"), UserAdjustableProperty.entries.map { it.wireName }.toSet())

    val media = ThemeTestKit.cinema().panels.getValue(PanelId("mediaStage"))
    assertTrue(media.isThemeStatic(ThemeableGeometry.SIZE_METERS))
    assertTrue(!media.isThemeStatic(ThemeableGeometry.ROTATION_DEGREES))
    assertTrue(media.userAdjustable.isEmpty())
  }

  private fun assertApprox(expected: Meters3, actual: Meters3) {
    assertEquals(expected.x, actual.x, epsilon)
    assertEquals(expected.y, actual.y, epsilon)
    assertEquals(expected.z, actual.z, epsilon)
  }
}
