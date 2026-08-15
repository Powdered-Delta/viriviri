package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEmissionDirection
import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuMergeTest {
  @Test
  fun moreThanThresholdSameTextWithinOneSecondMergesToOneEvent() {
    val events = (0..5).map { index -> DanmakuEvent("id-$index", index * 100L, "同一条") }

    val merged = mergeDanmakuEvents(events)

    assertEquals(1, merged.size)
    assertEquals(6, merged.single().mergedCount)
    assertEquals("id-0", merged.single().event.id)
  }

  @Test
  fun thresholdOfFiveIsPreservedWithoutMerge() {
    val events = (0 until 5).map { index -> DanmakuEvent("id-$index", index * 100L, "重复") }

    assertEquals(5, mergeDanmakuEvents(events).size)
  }

  @Test
  fun sameTextInDifferentWindowsIsNotMerged() {
    val events = (0..11).map { index -> DanmakuEvent("id-$index", if (index < 6) index * 100L else 1_000L + (index - 6) * 100L, "重复") }

    assertEquals(2, mergeDanmakuEvents(events).size)
  }

  @Test
  fun differentDirectionOrLaneFamilyIsNotMerged() {
    val events =
        (0..5).flatMap { index ->
          listOf(
              DanmakuEvent(
                  id = "scroll-$index",
                  startMs = index * 100L,
                  text = "重复",
                  emissionDirection = DanmakuEmissionDirection.RIGHT_TO_LEFT,
              ),
              DanmakuEvent(
                  id = "reverse-$index",
                  startMs = index * 100L,
                  text = "重复",
                  emissionDirection = DanmakuEmissionDirection.LEFT_TO_RIGHT,
              ),
              DanmakuEvent(
                  id = "top-$index",
                  startMs = index * 100L,
                  text = "重复",
                  laneFamily = DanmakuLaneFamily.TOP_FIXED,
                  emissionDirection = null,
              ),
          )
        }

    assertEquals(3, mergeDanmakuEvents(events).size)
  }

  @Test
  fun mergeWindowAndThresholdAreConfigurable() {
    val config = DanmakuMergeConfig(windowMs = 500L, duplicateThreshold = 2)
    val events = listOf(
        DanmakuEvent("a", 0L, "x"),
        DanmakuEvent("b", 100L, "x"),
        DanmakuEvent("c", 200L, "x"),
    )

    val merged = mergeDanmakuEvents(events, config)

    assertEquals(1, merged.size)
    assertEquals(3, merged.single().mergedCount)
  }
}
