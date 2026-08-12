package com.m0e_n00b.viriviri

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveVideoOutputConfigTest {
  @Test
  fun immersiveVideoOutputMatchesFixedSixteenByNineStage() {
    assertEquals(16f / 9f, IMMERSIVE_VIDEO_OUTPUT_WIDTH.toFloat() / IMMERSIVE_VIDEO_OUTPUT_HEIGHT, 0.0001f)
  }

  @Test
  fun playerConfigurationUsesFitScalingAndAvoidsStereoBufferWidth() {
    assertEquals(C.VIDEO_SCALING_MODE_SCALE_TO_FIT, IMMERSIVE_VIDEO_SCALING_MODE)
    assertTrue(IMMERSIVE_VIDEO_OUTPUT_WIDTH < 3840)
  }
}
