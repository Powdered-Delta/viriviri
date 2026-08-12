package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedControlTest {
  @Test
  fun supportedSpeedsAreFixedAndOrdered() {
    assertEquals(listOf(0.75f, 1f, 1.25f, 1.5f, 2f), PlaybackSpeedControl.supportedSpeeds)
  }

  @Test
  fun labelsUseSupportedSpeedAndSafeFallback() {
    assertEquals("0.75x", PlaybackSpeedControl.label(0.75f))
    assertEquals("1x", PlaybackSpeedControl.label(1f))
    assertEquals("1.25x", PlaybackSpeedControl.label(1.25f))
    assertEquals("1x", PlaybackSpeedControl.label(1.1f))
    assertEquals("1x", PlaybackSpeedControl.label(Float.NaN))
    assertTrue(PlaybackSpeedControl.normalizedForDisplay(2f) in PlaybackSpeedControl.supportedSpeeds)
  }
}
