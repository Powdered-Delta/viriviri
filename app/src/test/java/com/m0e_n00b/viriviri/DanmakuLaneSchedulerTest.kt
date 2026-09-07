package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuLaneSchedulerTest {
  @Test
  fun overlappingScrollingEventsFillDistinctLanesBeforeReuse() {
    val result =
        scheduleDanmakuLanes(
            events = (0 until 4).map { DanmakuEvent("$it", 0L, "event $it") },
            config = DanmakuRenderConfig(scrollingLaneCount = 4),
        )

    assertEquals(setOf(0, 1, 2, 3), result.assignments.values.map { it.scrollingLane }.toSet())
    assertTrue(result.rejectedIds.isEmpty())
  }

  @Test
  fun fixedCommentsUseIndependentTopAndBottomLanes() {
    val result =
        scheduleDanmakuLanes(
            events =
                listOf(
                    DanmakuEvent("top", 0L, "top", DanmakuLaneFamily.TOP_FIXED),
                    DanmakuEvent("bottom", 0L, "bottom", DanmakuLaneFamily.BOTTOM_FIXED),
                ),
        )

    assertEquals(0, result.assignments.getValue("top").fixedLane)
    assertEquals(0, result.assignments.getValue("bottom").fixedLane)
  }

  @Test
  fun busyTimelineRejectsOverflowInsteadOfOverlappingLanes() {
    // More overlapping scrollers than lanes: with admission on, the surplus is rejected so the
    // on-screen count is bounded and nothing overlaps.
    val config = DanmakuRenderConfig(scrollingLaneCount = 2, scrollingDurationMs = 6_000L)
    val events =
        (0 until 6).map { DanmakuEvent("e$it", it * 100L, "comment $it") }
    val result = scheduleDanmakuLanes(events = events, config = config, trackWidthPx = 1280f)

    val accepted = result.assignments.keys
    assertEquals(2, accepted.size)
    // Each accepted lane holds at most one item (no overlapping assignment).
    val lanes = result.assignments.values.map { it.scrollingLane }.toSet()
    assertEquals(2, lanes.size)
    assertTrue(result.rejectedIds.isNotEmpty())
  }

  @Test
  fun legacyModeAcceptsEveryEventByPackingLeastBusyLane() {
    val config =
        DanmakuRenderConfig(scrollingLaneCount = 2, scrollingDurationMs = 6_000L, rejectWhenNoFreeLane = false)
    val events = (0 until 6).map { DanmakuEvent("e$it", it * 100L, "comment $it") }
    val result = scheduleDanmakuLanes(events = events, config = config, trackWidthPx = 1280f)

    assertEquals(6, result.assignments.size)
    assertTrue(result.rejectedIds.isEmpty())
  }

  @Test
  fun weightThresholdDropsLowWeightEventsButKeepsUnknown() {
    val config = DanmakuRenderConfig(scrollingLaneCount = 8, weightThreshold = 5)
    val events =
        listOf(
            DanmakuEvent("dense", 0L, "high weight", weight = 9),
            DanmakuEvent("sparse", 100L, "low weight", weight = 1),
            DanmakuEvent("legacy", 200L, "no weight", weight = DanmakuEvent.WEIGHT_UNKNOWN),
        )
    val result = scheduleDanmakuLanes(events = events, config = config)

    assertTrue(result.assignments.containsKey("dense"))
    assertTrue(result.assignments.containsKey("legacy"))
    assertFalse(result.assignments.containsKey("sparse"))
    assertTrue(result.rejectedIds.contains("sparse"))
  }

  @Test
  fun eachCanvasSchedulesIndependentlyAgainstItsOwnCapacity() {
    // Same content on a small and a large canvas yields independent acceptance counts.
    val events = (0 until 12).map { DanmakuEvent("e$it", it * 50L, "c$it") }
    val small = scheduleDanmakuLanes(events, config = DanmakuRenderConfig(scrollingLaneCount = 2))
    val large = scheduleDanmakuLanes(events, config = DanmakuRenderConfig(scrollingLaneCount = 12))

    assertEquals(2, small.assignments.size)
    assertTrue(large.assignments.size > small.assignments.size)
    // Scheduling is pure: the small result is unaffected by the large one.
    assertEquals(0, small.assignments.getValue("e0").scrollingLane)
  }
}
