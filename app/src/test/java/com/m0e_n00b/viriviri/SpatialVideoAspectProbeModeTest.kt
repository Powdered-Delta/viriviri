package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialVideoAspectProbeModeTest {
  @Test
  fun defaultStateUsesSourceAspect() {
    assertEquals(SpatialVideoAspectProbeTarget.DEFAULT, SpatialVideoAspectProbeState().appliedTarget)
  }

  @Test
  fun targetSelectionStaysPendingUntilApply() {
    val pending =
        SpatialVideoAspectProbeReducer.selectTarget(
            SpatialVideoAspectProbeState(),
            SpatialVideoAspectProbeTarget.PORTRAIT_9_16,
        )

    assertEquals(SpatialVideoAspectProbeTarget.PORTRAIT_9_16, pending.pendingTarget)
    assertEquals(SpatialVideoAspectProbeTarget.DEFAULT, pending.appliedTarget)

    val applied = SpatialVideoAspectProbeReducer.apply(pending)
    assertEquals(SpatialVideoAspectProbeTarget.PORTRAIT_9_16, applied.appliedTarget)
  }

  @Test
  fun targetAspectPresetResolvesToTheExpectedContainedQuad() {
    val portrait =
        spatialVideoContentQuadForAspect(
            stageWidth = 1.6f,
            stageHeight = 0.9f,
            displayAspectRatio = 9f / 16f,
        )
    val square =
        spatialVideoContentQuadForAspect(
            stageWidth = 1.6f,
            stageHeight = 0.9f,
            displayAspectRatio = 1f,
        )

    assertEquals(0.253125f, portrait.halfWidth, 0.0001f)
    assertEquals(0.45f, portrait.halfHeight, 0.0001f)
    assertEquals(0.45f, square.halfWidth, 0.0001f)
    assertEquals(0.45f, square.halfHeight, 0.0001f)
  }
}
