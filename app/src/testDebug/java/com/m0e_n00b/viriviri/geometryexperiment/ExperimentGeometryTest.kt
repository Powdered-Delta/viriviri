package com.m0e_n00b.viriviri.geometryexperiment

import org.junit.Assert.*
import org.junit.Test

class ExperimentGeometryTest {
  @Test fun sizeSweepKeepsAspectAndIndependentRadius() {
    val start = ExperimentGeometry(160, 100)
    for (progress in 0..ExperimentGeometry.WIDTH_STEPS) {
      val geometry = start.withWidthProgress(progress)
      assertEquals(16f / 9f, geometry.width / geometry.height, 0.00001f)
      assertEquals(1f, geometry.radius!!, 0f)
      assertEquals(progress, geometry.widthProgress)
    }
    assertEquals(1f, start.withWidthProgress(-10).width, 0f)
    assertEquals(2.4f, start.withWidthProgress(1000).width, 0f)
    assertEquals(1.35f, start.withWidthProgress(140).height, 0.00001f)
  }

  @Test fun radiusSweepIsFiniteAndDoesNotChangeSize() {
    val start = ExperimentGeometry(240, 100)
    for (progress in 0..ExperimentGeometry.RADIUS_STEPS) {
      val geometry = start.withRadiusProgress(progress)
      assertEquals(240, geometry.widthCm)
      assertEquals(progress, geometry.radiusProgress)
      assertTrue(geometry.width / geometry.radius!! <= 3f)
    }
    assertEquals(0.8f, start.withRadiusProgress(-1).radius!!, 0f)
    assertEquals(3f, start.withRadiusProgress(1000).radius!!, 0f)
  }

  @Test fun flatRemainsFlatAndInvalidParametersAreRejected() {
    val flat = ExperimentGeometry(160, null)
    assertSame(flat, flat.withRadiusProgress(100))
    assertNull(flat.withWidthProgress(140).radius)
    assertThrows(IllegalArgumentException::class.java) { ExperimentGeometry(99, null) }
    assertThrows(IllegalArgumentException::class.java) { ExperimentGeometry(241, 100) }
    assertThrows(IllegalArgumentException::class.java) { ExperimentGeometry(160, 79) }
    assertThrows(IllegalArgumentException::class.java) { ExperimentGeometry(160, 301) }
  }

  @Test fun everyCurvedRadiusPinsEachSurfaceCentreToItsOwnBaseDepth() {
    for (width in listOf(100, 160, 240)) {
      for (radius in 80..300) {
        val geometry = ExperimentGeometry(width, radius)
        for (layer in ExperimentLayer.entries) {
          val worldSurfaceCentreZ = 2.5f + geometry.carrierLocalZ(layer) + radius / 100f
          assertEquals(2.5f + layer.baseLocalZ, worldSurfaceCentreZ, 0.000001f)
        }
      }
    }
  }

  @Test fun switchingFlatAndCurvedNeverAccumulatesOffsetsAndKeepsLayerSeparation() {
    val changes = listOf(ExperimentGeometry(160, 100), ExperimentGeometry(240, 300),
        ExperimentGeometry(100, 80), ExperimentGeometry(160, null), ExperimentGeometry(160, 100))
    val depths = changes.map { geometry ->
      val video = geometry.carrierLocalZ(ExperimentLayer.VIDEO)
      val danmaku = geometry.carrierLocalZ(ExperimentLayer.DANMAKU)
      val subtitle = geometry.carrierLocalZ(ExperimentLayer.SUBTITLE)
      assertEquals(0.01f, video - danmaku, 0.000001f)
      assertEquals(0.02f, video - subtitle, 0.000001f)
      assertTrue(ExperimentLayer.VIDEO.zIndex < ExperimentLayer.DANMAKU.zIndex)
      assertTrue(ExperimentLayer.DANMAKU.zIndex < ExperimentLayer.SUBTITLE.zIndex)
      video
    }
    assertEquals(listOf(-1f, -3f, -0.8f, 0f, -1f), depths)
  }
}
