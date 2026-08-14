package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveTransportOverlayPolicyTest {
  @Test
  fun primaryStageClickAlwaysRevealsTransport() {
    assertEquals(
        ImmersiveTransportPrimaryAction.REVEAL_TRANSPORT,
        ImmersiveTransportOverlayPolicy.primaryAction(),
    )
  }

  @Test
  fun idleFadeIsScheduledOnlyForActualPlayback() {
    assertTrue(ImmersiveTransportOverlayPolicy.shouldScheduleIdleFade(isActuallyPlaying = true))
    assertFalse(ImmersiveTransportOverlayPolicy.shouldScheduleIdleFade(isActuallyPlaying = false))
  }
}
