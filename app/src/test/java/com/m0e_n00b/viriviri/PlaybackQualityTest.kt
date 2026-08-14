package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQualityTest {
  private val streams =
      listOf(
          DashVideoStream(codecs = "avc1.64001F", height = 1080, id = 80),
          DashVideoStream(codecs = "avc1.64001E", height = 720, id = 64),
          DashVideoStream(codecs = "avc1.64001E", height = 480, id = 32),
          DashVideoStream(codecs = "avc1.64001E", height = 360, id = 16),
          DashVideoStream(codecs = "hev1.1.6.L120", height = 1080, id = 80),
      )

  @Test
  fun autoSelectsTheHighestCompatibleAvcStream() {
    assertEquals(1080, selectAvcVideoStream(streams, PlaybackQuality.AUTO)?.height)
  }

  @Test
  fun requestedQualitySelectsTheHighestAvcAtOrBelowItsTargetHeight() {
    assertEquals(360, selectAvcVideoStream(streams, PlaybackQuality.P360)?.height)
    assertEquals(480, selectAvcVideoStream(streams, PlaybackQuality.P480)?.height)
    assertEquals(720, selectAvcVideoStream(streams, PlaybackQuality.P720)?.height)
    assertEquals(1080, selectAvcVideoStream(streams, PlaybackQuality.P1080)?.height)
  }

  @Test
  fun unavailableTargetFallsBackToTheLowestCompatibleAvcStream() {
    val onlyHighQuality = streams.filter { it.height >= 720 }

    assertEquals(720, selectAvcVideoStream(onlyHighQuality, PlaybackQuality.P360)?.height)
  }

  @Test
  fun noAvcStreamReturnsNoSelection() {
    assertNull(
        selectAvcVideoStream(
            listOf(DashVideoStream(codecs = "hev1.1.6.L120", height = 720, id = 64)),
            PlaybackQuality.AUTO,
        )
    )
  }
}
