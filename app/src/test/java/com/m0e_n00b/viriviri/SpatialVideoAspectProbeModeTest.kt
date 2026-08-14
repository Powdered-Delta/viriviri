package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialVideoAspectProbeModeTest {
  @Test
  fun defaultStateUsesSourceAspectAndPanelReshape() {
    assertEquals(SpatialVideoAspectProbeTarget.DEFAULT, SpatialVideoAspectProbeState().appliedTarget)
    assertEquals(SpatialVideoAspectProbePlan.PANEL_RESHAPE, SpatialVideoAspectProbeState().appliedPlan)
  }

  @Test
  fun targetAndPlanSelectionStayPendingUntilApply() {
    val pendingTarget =
        SpatialVideoAspectProbeReducer.selectTarget(
            SpatialVideoAspectProbeState(),
            SpatialVideoAspectProbeTarget.PORTRAIT_9_16,
        )
    val pendingPlan =
        SpatialVideoAspectProbeReducer.selectPlan(
            pendingTarget,
            SpatialVideoAspectProbePlan.PANEL_RESHAPE,
        )

    assertEquals(SpatialVideoAspectProbeTarget.PORTRAIT_9_16, pendingPlan.pendingTarget)
    assertEquals(SpatialVideoAspectProbePlan.PANEL_RESHAPE, pendingPlan.pendingPlan)
    assertEquals(SpatialVideoAspectProbeTarget.DEFAULT, pendingPlan.appliedTarget)
    assertEquals(SpatialVideoAspectProbePlan.PANEL_RESHAPE, pendingPlan.appliedPlan)

    val applied = SpatialVideoAspectProbeReducer.apply(pendingPlan)

    assertEquals(SpatialVideoAspectProbeTarget.PORTRAIT_9_16, applied.appliedTarget)
    assertEquals(SpatialVideoAspectProbePlan.PANEL_RESHAPE, applied.appliedPlan)
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
