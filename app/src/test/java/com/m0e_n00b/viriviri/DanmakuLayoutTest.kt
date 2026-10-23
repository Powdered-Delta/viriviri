package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import org.junit.Assert.*
import org.junit.Test

class DanmakuLayoutTest {
  @Test fun fixedRowsUseTheScrollingGridAcrossCanvasSizesAndDensities() {
    for (height in listOf(720f, 1080f)) {
      for (lanes in listOf(8, 12, 16)) {
        val config = DanmakuRenderConfig(scrollingLaneCount = lanes)
        for (lane in 0 until config.fixedLaneCount) {
          fun y(family: DanmakuLaneFamily, index: Int) =
              danmakuBaseline(family, index, config, height, -32f, 8f, 3f)
          assertEquals(y(DanmakuLaneFamily.SCROLLING, lane),
              y(DanmakuLaneFamily.TOP_FIXED, lane), .001f)
          assertEquals(y(DanmakuLaneFamily.SCROLLING, lanes - config.fixedLaneCount + lane),
              y(DanmakuLaneFamily.BOTTOM_FIXED, lane), .001f)
        }
      }
    }
  }

  @Test fun defaultFixedPitchMatchesRegularPitchInsteadOfAddingAnotherGap() {
    val config = DanmakuRenderConfig.DEFAULT
    val expectedPitch = 720f * config.showArea / (config.scrollingLaneCount + 1)
    for (family in DanmakuLaneFamily.entries) {
      val first = danmakuBaseline(family, 0, config, 720f, -32f, 8f, 3f)
      val second = danmakuBaseline(family, 1, config, 720f, -32f, 8f, 3f)
      assertEquals(expectedPitch, second - first, .001f)
    }
  }

  @Test fun allFamiliesKeepGlyphAndOutlineAboveSubtitleBoundary() {
    val config = DanmakuRenderConfig.DEFAULT
    for (height in listOf(720f, 1080f)) {
      for (scale in listOf(0.6f, 1f, 2.5f)) {
        val top = -32f * scale
        val bottom = 8f * scale
        val outline = 3f * scale
        for (family in DanmakuLaneFamily.entries) {
          val count = if (family == DanmakuLaneFamily.SCROLLING) config.scrollingLaneCount else config.fixedLaneCount
          for (lane in 0 until count) {
            val y = danmakuBaseline(family, lane, config, height, top, bottom, outline)
            assertTrue("Top ink must stay inside drawable canvas", y + top - outline / 2 >= -0.001f)
            assertTrue("Bottom ink must leave subtitle area clear",
                y + bottom + outline / 2 <= height * config.showArea + 0.001f)
          }
        }
      }
    }
  }

  @Test fun bottomPinnedCommentsMoveUpWithReservedArea() {
    val full = DanmakuRenderConfig(showArea = 1f)
    val reserved = full.copy(showArea = 0.85f)
    for (lane in 0 until full.fixedLaneCount) {
      val oldY = danmakuBaseline(DanmakuLaneFamily.BOTTOM_FIXED, lane, full, 720f, -32f, 8f, 3f)
      val newY = danmakuBaseline(DanmakuLaneFamily.BOTTOM_FIXED, lane, reserved, 720f, -32f, 8f, 3f)
      assertTrue(newY < oldY)
      assertTrue(newY + 9.5f < 612f)
    }
  }

  @Test fun laneOrderIsPreservedWithinEveryFamily() {
    val config = DanmakuRenderConfig.DEFAULT
    for (family in DanmakuLaneFamily.entries) {
      val count = if (family == DanmakuLaneFamily.SCROLLING) config.scrollingLaneCount else config.fixedLaneCount
      val positions = (0 until count).map { danmakuBaseline(family, it, config, 720f, -32f, 8f, 3f) }
      assertTrue(positions.zipWithNext().all { (a, b) -> a < b })
    }
  }

  @Test fun emptyAreaAndOversizedGlyphsProduceFinitePositions() {
    val hidden = DanmakuRenderConfig(showArea = 0f)
    assertEquals(0f, danmakuBaseline(DanmakuLaneFamily.BOTTOM_FIXED, 0, hidden, 720f, -32f, 8f, 3f), 0f)
    // Oversized text is additionally clipped by the Canvas to preserve the reserved pixels.
    val y = danmakuBaseline(DanmakuLaneFamily.BOTTOM_FIXED, 2, hidden.copy(showArea = 0.1f),
        720f, -300f, 100f, 20f)
    assertTrue(y.isFinite() && y in 0f..72f)
  }
}
