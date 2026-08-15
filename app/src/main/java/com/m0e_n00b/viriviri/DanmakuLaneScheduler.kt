package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily

internal data class DanmakuLaneAssignment(
    val scrollingLane: Int,
    val fixedLane: Int,
)

internal fun scheduleDanmakuLanes(
    events: List<DanmakuEvent>,
    scrollingLaneCount: Int = 12,
    fixedLaneCount: Int = 3,
    scrollingDurationMs: Long = 6_000L,
    fixedDurationMs: Long = 4_000L,
): Map<String, DanmakuLaneAssignment> {
  require(scrollingLaneCount > 0)
  require(fixedLaneCount > 0)
  val scrollingAvailability = LongArray(scrollingLaneCount)
  val topAvailability = LongArray(fixedLaneCount)
  val bottomAvailability = LongArray(fixedLaneCount)
  return buildMap {
    events.sortedBy(DanmakuEvent::startMs).forEach { event ->
      val availability =
          when (event.laneFamily) {
            DanmakuLaneFamily.SCROLLING -> scrollingAvailability
            DanmakuLaneFamily.TOP_FIXED -> topAvailability
            DanmakuLaneFamily.BOTTOM_FIXED -> bottomAvailability
          }
      val lane = availability.indices.minBy { availability[it] }
      val duration =
          if (event.laneFamily == DanmakuLaneFamily.SCROLLING) scrollingDurationMs else fixedDurationMs
      availability[lane] = event.startMs + duration
      put(
          event.id,
          DanmakuLaneAssignment(
              scrollingLane = if (event.laneFamily == DanmakuLaneFamily.SCROLLING) lane else 0,
              fixedLane = if (event.laneFamily == DanmakuLaneFamily.SCROLLING) 0 else lane,
          ),
      )
    }
  }
}
