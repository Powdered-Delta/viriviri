package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCanvasSizeTest {
  @Test
  fun canvasSizesKeepStandardScaleAndOfferBoundedAlternatives() {
    assertEquals(1.0f, PlaybackCanvasSize.STANDARD.scale)
    assertTrue(PlaybackCanvasSize.COMPACT.scale < PlaybackCanvasSize.STANDARD.scale)
    assertTrue(PlaybackCanvasSize.LARGE.scale > PlaybackCanvasSize.STANDARD.scale)
  }
}
