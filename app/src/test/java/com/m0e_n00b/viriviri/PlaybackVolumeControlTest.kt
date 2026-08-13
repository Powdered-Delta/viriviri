package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackVolumeControlTest {
  @Test
  fun supportedVolumesAreFixedAndOrdered() {
    assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f, 1f), PlaybackVolumeControl.supportedVolumes)
  }

  @Test
  fun labelsUseSafeFallbackForUnsupportedOrInvalidValues() {
    assertEquals("Vol 0%", PlaybackVolumeControl.label(0f))
    assertEquals("Vol 50%", PlaybackVolumeControl.label(0.5f))
    assertEquals("Vol 100%", PlaybackVolumeControl.label(1f))
    assertEquals("Vol 100%", PlaybackVolumeControl.label(0.6f))
    assertEquals("Vol 100%", PlaybackVolumeControl.label(Float.NaN))
  }
}
