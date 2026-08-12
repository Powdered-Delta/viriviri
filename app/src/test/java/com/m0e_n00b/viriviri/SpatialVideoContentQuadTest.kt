package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialVideoContentQuadTest {
  @Test
  fun sixteenByNineUsesFullStage() {
    assertEquals(SpatialVideoContentQuad(0.8f, 0.45f), spatialVideoContentQuad(1.6f, 0.9f, 1920, 1080))
  }

  @Test
  fun portraitPillarboxesInsideFixedStage() {
    val quad = spatialVideoContentQuad(1.6f, 0.9f, 1080, 1920)

    assertEquals(0.253125f, quad.halfWidth, 0.0001f)
    assertEquals(0.45f, quad.halfHeight, 0.0001f)
  }

  @Test
  fun ultrawideLetterboxesInsideFixedStage() {
    val quad = spatialVideoContentQuad(1.6f, 0.9f, 21, 9)

    assertEquals(0.8f, quad.halfWidth, 0.0001f)
    assertEquals(0.34285715f, quad.halfHeight, 0.0001f)
  }

  @Test
  fun pixelAspectRatioParticipatesInContainCalculation() {
    val quad = spatialVideoContentQuad(1.6f, 0.9f, 720, 480, pixelWidthHeightRatio = 1.2f)

    assertEquals(0.8f, quad.halfWidth, 0.0001f)
    assertEquals(0.44444445f, quad.halfHeight, 0.0001f)
  }

  @Test
  fun invalidVideoSizeKeepsFullStageGeometry() {
    assertEquals(SpatialVideoContentQuad(0.8f, 0.45f), spatialVideoContentQuad(1.6f, 0.9f, 0, 1920))
    assertEquals(SpatialVideoContentQuad(0.8f, 0.45f), spatialVideoContentQuad(1.6f, 0.9f, 1080, 1920, Float.NaN))
  }
}
