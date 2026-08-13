package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialVideoAspectDiagnosticTest {
  @Test
  fun portraitMetadataProducesContainedPhysicalQuad() {
    val diagnostic = spatialVideoAspectDiagnostic(1.6f, 0.9f, 1080, 1920, 1f)

    assertEquals(0.5625f, diagnostic.displayAspectRatio, 0.0001f)
    assertEquals(0.253125f, diagnostic.contentHalfWidth, 0.0001f)
    assertEquals(0.45f, diagnostic.contentHalfHeight, 0.0001f)
  }

  @Test
  fun invalidMetadataProducesFullStageFallbackAndSafeAspect() {
    val diagnostic = spatialVideoAspectDiagnostic(1.6f, 0.9f, 0, 1920, Float.NaN)

    assertEquals(0f, diagnostic.displayAspectRatio, 0f)
    assertEquals(0.8f, diagnostic.contentHalfWidth, 0.0001f)
    assertEquals(0.45f, diagnostic.contentHalfHeight, 0.0001f)
  }
}
