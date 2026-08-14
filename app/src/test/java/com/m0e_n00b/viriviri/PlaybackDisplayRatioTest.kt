package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDisplayRatioTest {
  @Test
  fun displayRatiosExposeTheExpectedAspectValues() {
    assertEquals(null, PlaybackDisplayRatio.AUTO.displayAspectRatio)
    assertEquals(16f / 9f, PlaybackDisplayRatio.WIDESCREEN_16_9.displayAspectRatio)
    assertEquals(4f / 3f, PlaybackDisplayRatio.STANDARD_4_3.displayAspectRatio)
    assertEquals(1f, PlaybackDisplayRatio.SQUARE_1_1.displayAspectRatio)
    assertEquals(9f / 16f, PlaybackDisplayRatio.PORTRAIT_9_16.displayAspectRatio)
  }

  @Test
  fun debugTargetsMapOneToOneToReleaseDisplayRatios() {
    PlaybackDisplayRatio.entries.forEach { displayRatio ->
      assertEquals(displayRatio, SpatialVideoAspectProbeTarget.from(displayRatio).displayRatio)
    }
  }
}
