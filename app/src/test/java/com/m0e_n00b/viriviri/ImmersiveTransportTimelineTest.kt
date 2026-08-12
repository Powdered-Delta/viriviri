package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveTransportTimelineTest {
  @Test
  fun knownDurationClampsPositionAndFormatsTimecodes() {
    val timeline = immersiveTransportTimeline(playerPositionMs = 90_999L, playerDurationMs = 90_000L)

    assertTrue(timeline.canSeek)
    assertEquals(90_000, timeline.maxMs)
    assertEquals(90_000, timeline.positionMs)
    assertEquals("1:30", timeline.elapsedLabel)
    assertEquals("1:30", timeline.durationLabel)
  }

  @Test
  fun activeDragTakesPrecedenceOverPlayerPosition() {
    val timeline = immersiveTransportTimeline(
        playerPositionMs = 10_000L,
        playerDurationMs = 120_000L,
        dragPositionMs = 75_250L,
    )

    assertEquals(75_250, timeline.positionMs)
    assertEquals("1:15", timeline.elapsedLabel)
  }

  @Test
  fun unavailableDurationIsNonSeekable() {
    val timeline = immersiveTransportTimeline(playerPositionMs = 20_000L, playerDurationMs = -1L)

    assertFalse(timeline.canSeek)
    assertEquals(0, timeline.maxMs)
    assertEquals(0, timeline.positionMs)
    assertEquals("--:--", timeline.elapsedLabel)
    assertEquals("--:--", timeline.durationLabel)
  }

  @Test
  fun timecodeSupportsHoursAndNegativeValues() {
    assertEquals("1:01:01", formatTransportTimecode(3_661_000L))
    assertEquals("0:00", formatTransportTimecode(-1L))
  }
}
