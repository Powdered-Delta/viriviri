package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuLaneSchedulerTest {
  @Test
  fun overlappingScrollingEventsFillDistinctLanesBeforeReuse() {
    val assignments =
        scheduleDanmakuLanes(
            events = (0 until 4).map { DanmakuEvent("$it", 0L, "event $it") },
            scrollingLaneCount = 4,
        )

    assertEquals(setOf(0, 1, 2, 3), assignments.values.map { it.scrollingLane }.toSet())
  }

  @Test
  fun fixedCommentsUseIndependentTopAndBottomLanes() {
    val assignments =
        scheduleDanmakuLanes(
            events =
                listOf(
                    DanmakuEvent("top", 0L, "top", DanmakuLaneFamily.TOP_FIXED),
                    DanmakuEvent("bottom", 0L, "bottom", DanmakuLaneFamily.BOTTOM_FIXED),
                ),
        )

    assertEquals(0, assignments.getValue("top").fixedLane)
    assertEquals(0, assignments.getValue("bottom").fixedLane)
  }
}
