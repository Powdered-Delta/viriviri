package com.m0e_n00b.viriviri

import android.graphics.Paint
import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily

data class DanmakuLaneAssignment(
    val scrollingLane: Int,
    val fixedLane: Int,
)

internal const val DANMAKU_TEXT_SIZE_PX = 152f
internal const val DANMAKU_OUTLINE_WIDTH_PX = 16f

internal data class PreparedDanmaku(
    val events: List<DanmakuEvent>,
    val laneAssignments: Map<String, DanmakuLaneAssignment>,
    val renderMetrics: Map<String, DanmakuRenderMetrics>,
)

internal fun prepareDanmaku(
    events: List<DanmakuEvent>,
    mergeConfig: DanmakuMergeConfig = DanmakuMergeConfig(),
): PreparedDanmaku {
  val mergedEvents = mergeDanmakuEvents(events, mergeConfig)
  val sortedEvents = mergedEvents.map(MergedDanmakuEvent::event)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = DANMAKU_TEXT_SIZE_PX }
  val metrics = sortedEvents.associate { event ->
    val scale = event.styleOverride?.fontScale?.coerceIn(0.6f, 2.5f) ?: 1f
    event.id to DanmakuRenderMetrics(
        textWidthPx = paint.measureText(event.text) * scale,
        fontScale = scale,
        textColorArgb = (event.styleOverride?.textColorArgb ?: 0xFFFFFFFFL).toInt(),
        outlineWidthPx = DANMAKU_OUTLINE_WIDTH_PX * scale,
    )
  }
  return PreparedDanmaku(sortedEvents, scheduleDanmakuLanes(sortedEvents), metrics)
}

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
