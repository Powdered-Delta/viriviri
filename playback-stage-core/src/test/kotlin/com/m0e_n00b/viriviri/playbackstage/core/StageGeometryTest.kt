package com.m0e_n00b.viriviri.playbackstage.core

import org.junit.Assert.*
import org.junit.Test

class StageGeometryTest {
  private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.0001f)

  @Test fun aspectFittingUsesWholeFrameAndFullStageOverlays() {
    val cases = listOf(
        StageVideoMetrics(1920, 1080) to StageSize(1.6f, 0.9f),
        StageVideoMetrics(1440, 1080) to StageSize(1.2f, 0.9f),
        StageVideoMetrics(1080, 1920) to StageSize(0.50625f, 0.9f),
        StageVideoMetrics(720, 576, 16f / 15f) to StageSize(1.2f, 0.9f),
    )
    for ((metrics, expected) in cases) {
      val result = solveStageLayout(StageInput(video = metrics))
      near(expected.width, result.video.size.width)
      near(expected.height, result.video.size.height)
      assertEquals(StageSize(1.6f, 0.9f), result.danmaku.size)
      assertEquals(result.viewport, result.backdrop.size)
    }
    val overridden = solveStageLayout(StageInput(StageParameters(targetAspect = 1f), StageVideoMetrics(1920, 1080)))
    near(0.9f, overridden.video.size.width)
    near(0.9f, overridden.video.size.height)
  }

  @Test fun scalingAndRawCurvatureMatrixHasFixedBottomAnchorWithoutAxisKnowledge() {
    for (scale in listOf(0.7f, 1f, 2f)) {
      for (rawRadius in listOf(null, 1.5f, 2.7f, 7.9f)) {
        val curvature = rawRadius?.let { StageCurvature.Cylinder(it) } ?: StageCurvature.Flat
        val layout = solveStageLayout(StageInput(StageParameters(scale, curvature = curvature)))
        near(1.6f * scale, layout.viewport.width)
        near(0.9f * scale, layout.viewport.height)
        near(0.45f * scale + 0.06f, layout.centerFromBottomAnchor.y)
        near(0.06f, layout.centerFromBottomAnchor.y - layout.viewport.height / 2f)
        assertEquals(0f, layout.centerFromBottomAnchor.z)
        near(0.01f * scale, layout.backdrop.surfaceDistanceMeters)
        near(-0.01f * scale, layout.danmaku.surfaceDistanceMeters)
        if (rawRadius == null) assertNull(layout.video.effectiveRadiusMeters)
        else near(rawRadius * scale, layout.video.effectiveRadiusMeters!!)
      }
    }
    near(15.8f, solveStageLayout(StageInput(StageParameters(2f, curvature = StageCurvature.Cylinder(7.9f)))).video.effectiveRadiusMeters!!)
    near(1.05f, solveStageLayout(StageInput(StageParameters(0.7f, curvature = StageCurvature.Cylinder(1.5f)))).video.effectiveRadiusMeters!!)
  }

  @Test fun invalidInputsRetainControlNormalizationAndPixelRatioFallback() {
    for (ratio in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -2f)) {
      val layout = solveStageLayout(StageInput(video = StageVideoMetrics(1440, 1080, ratio)))
      near(1.2f, layout.video.size.width)
    }
    for (scale in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
      near(1.6f, solveStageLayout(StageInput(StageParameters(scale))).viewport.width)
    }
    near(1.12f, solveStageLayout(StageInput(StageParameters(-1f))).viewport.width)
    near(3.2f, solveStageLayout(StageInput(StageParameters(99f))).viewport.width)
    for (radius in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f, 8f, 99f)) {
      assertNull(solveStageLayout(StageInput(StageParameters(curvature = StageCurvature.Cylinder(radius)))).video.effectiveRadiusMeters)
    }
    near(1.5f, solveStageLayout(StageInput(StageParameters(curvature = StageCurvature.Cylinder(0.2f)))).video.effectiveRadiusMeters!!)
    for (aspect in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f)) {
      near(1.2f, solveStageLayout(StageInput(StageParameters(targetAspect = aspect), StageVideoMetrics(4, 3))).video.size.width)
    }
    assertEquals(StageSize(1.6f, 0.9f), solveStageLayout(StageInput(video = StageVideoMetrics(-1, 0))).video.size)
  }

  @Test fun repeatedRoundTripsAreAbsoluteAndDoNotAccumulate() {
    val original = solveStageLayout(StageInput())
    repeat(100) {
      solveStageLayout(StageInput(StageParameters(2f, 9f / 16f, StageCurvature.Cylinder(1.5f))))
      assertEquals(original, solveStageLayout(StageInput()))
    }
  }

  @Test(expected = IllegalArgumentException::class) fun nonFiniteGeometryIsRejected() { StageSize(Float.NaN, 1f) }
  @Test(expected = IllegalArgumentException::class) fun emptyGeometryIsRejected() { StageSize(1f, 0f) }
}
