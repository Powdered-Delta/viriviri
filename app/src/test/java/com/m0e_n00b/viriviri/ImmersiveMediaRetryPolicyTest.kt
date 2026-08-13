package com.m0e_n00b.viriviri

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveMediaRetryPolicyTest {
  private val selected =
      Recommendation("BV1retry", "Retry video", "Creator", null, null, null, null, "https://www.bilibili.com/video/BV1retry")

  @Test
  fun retryIsAvailableOnlyForSettledViewerPlaybackErrors() {
    assertTrue(canRetryImmersiveMedia(ViriViriDestination.VIEWER, selected, "DASH unavailable", false))
    assertFalse(canRetryImmersiveMedia(ViriViriDestination.RECOMMENDATIONS, selected, "DASH unavailable", false))
    assertFalse(canRetryImmersiveMedia(ViriViriDestination.VIEWER, null, "DASH unavailable", false))
    assertFalse(canRetryImmersiveMedia(ViriViriDestination.VIEWER, selected, null, false))
    assertFalse(canRetryImmersiveMedia(ViriViriDestination.VIEWER, selected, "DASH unavailable", true))
  }
}
