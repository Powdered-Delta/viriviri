package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaThumbnailFrameTest {
  @Test
  fun defaultStyleKeepsStableRecommendationThumbnailGeometry() {
    val style = MediaThumbnailFrameStyle()

    assertEquals(128.dp, style.width)
    assertEquals(72.dp, style.height)
    assertEquals(Color(0xFF24333A), style.placeholderBackground)
  }

  @Test
  fun styleCanAdaptGeometryWithoutChangingContentOrOverlayContracts() {
    val style = MediaThumbnailFrameStyle(width = 96.dp, height = 54.dp)

    assertEquals(96.dp, style.width)
    assertEquals(54.dp, style.height)
  }
}
