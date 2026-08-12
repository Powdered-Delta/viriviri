package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveTransportOverlayPolicyTest {
  @Test
  fun primaryStageClickRevealsHiddenTransportBeforeTogglingPlayback() {
    assertEquals(
        ImmersiveTransportPrimaryAction.REVEAL_TRANSPORT,
        ImmersiveTransportOverlayPolicy.primaryAction(ImmersiveTransportOverlayState(visible = false)),
    )
    assertEquals(
        ImmersiveTransportPrimaryAction.TOGGLE_PLAY_INTENT,
        ImmersiveTransportOverlayPolicy.primaryAction(ImmersiveTransportOverlayState(visible = true)),
    )
  }

  @Test
  fun idleFadeIsScheduledOnlyForActualPlayback() {
    assertTrue(ImmersiveTransportOverlayPolicy.shouldScheduleIdleFade(isActuallyPlaying = true))
    assertFalse(ImmersiveTransportOverlayPolicy.shouldScheduleIdleFade(isActuallyPlaying = false))
  }
}
