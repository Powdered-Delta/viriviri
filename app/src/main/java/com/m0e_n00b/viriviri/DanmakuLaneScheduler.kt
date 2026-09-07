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
    /** Events rejected by lane admission / weight filtering on the default canvas (not rendered). */
    val rejectedEventIds: Set<String> = emptySet(),
)

internal fun prepareDanmaku(
    events: List<DanmakuEvent>,
    mergeConfig: DanmakuMergeConfig = DanmakuMergeConfig(),
    config: DanmakuRenderConfig = DanmakuRenderConfig.DEFAULT,
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
  // Default-canvas scheduling for the single wired overlay. The scheduler is pure and takes a
  // track width, so additional canvases can schedule independently later (multi-canvas ready).
  val scheduling =
      scheduleDanmakuLanes(
          events = sortedEvents,
          metrics = metrics,
          config = config,
          // Legacy stage overlay aspect; per-canvas width is supplied at the render call sites.
          trackWidthPx = 1280f,
      )
  return PreparedDanmaku(
      events = sortedEvents,
      laneAssignments = scheduling.assignments,
      renderMetrics = metrics,
      rejectedEventIds = scheduling.rejectedIds,
  )
}

/** Result of admitting events onto one canvas: accepted assignments and the ids turned away. */
internal data class DanmakuAdmissionResult(
    val assignments: Map<String, DanmakuLaneAssignment>,
    val rejectedIds: Set<String>,
)

/**
 * Assigns lanes for ONE danmaku canvas. Pure and parameterized by [config]/[trackWidthPx] so each
 * canvas in a multi-canvas setup schedules independently against its own capacity/geometry.
 *
 * Admission policy (mirrors canvas_danmaku density control):
 *  - weight filtering: events with a known weight below [DanmakuRenderConfig.weightThreshold] are
 *    dropped first; weight-unknown events are always kept;
 *  - lane capacity: a scrolling event needs the lane clear of its leading edge by the time it
 *    enters; fixed events need the pinned slot free for their dwell window. When no lane qualifies
 *    and [DanmakuRenderConfig.rejectWhenNoFreeLane] is set, the event is rejected instead of being
 *    force-stacked (bounds on-screen count and prevents overlap).
 */
internal fun scheduleDanmakuLanes(
    events: List<DanmakuEvent>,
    metrics: Map<String, DanmakuRenderMetrics> = emptyMap(),
    config: DanmakuRenderConfig = DanmakuRenderConfig.DEFAULT,
    trackWidthPx: Float = 1280f,
): DanmakuAdmissionResult {
  require(trackWidthPx > 0f) { "trackWidthPx must be positive" }

  val scrollingAvailability = LongArray(config.scrollingLaneCount)
  val topAvailability = LongArray(config.fixedLaneCount)
  val bottomAvailability = LongArray(config.fixedLaneCount)
  val assignments = LinkedHashMap<String, DanmakuLaneAssignment>()
  val rejected = LinkedHashSet<String>()

  for (event in events.sortedBy(DanmakuEvent::startMs)) {
    if (!passesWeight(event, config)) {
      rejected.add(event.id)
      continue
    }

    when (event.laneFamily) {
      DanmakuLaneFamily.SCROLLING -> {
        // Time for the previous item's trailing edge to fully leave the right edge.
        val prevWidth = metrics[event.id]?.textWidthPx ?: 0f
        val clearMs = crossingFreeMs(prevWidth, trackWidthPx, config.scrollingDurationMs)
        val needFreeBy = event.startMs - clearMs
        val lane = pickLane(scrollingAvailability, needFreeBy, config.rejectWhenNoFreeLane)
        if (lane < 0) {
          rejected.add(event.id)
        } else {
          scrollingAvailability[lane] = event.startMs + config.scrollingDurationMs
          assignments[event.id] = DanmakuLaneAssignment(scrollingLane = lane, fixedLane = 0)
        }
      }
      DanmakuLaneFamily.TOP_FIXED ->
          placeFixed(event, topAvailability, isScrolling = false, assignments, rejected, config)
      DanmakuLaneFamily.BOTTOM_FIXED ->
          placeFixed(event, bottomAvailability, isScrolling = false, assignments, rejected, config)
    }
  }

  return DanmakuAdmissionResult(assignments, rejected)
}

private fun passesWeight(event: DanmakuEvent, config: DanmakuRenderConfig): Boolean {
  if (config.weightThreshold <= 0) return true
  if (event.weight == DanmakuEvent.WEIGHT_UNKNOWN) return true
  return event.weight >= config.weightThreshold
}

private fun placeFixed(
    event: DanmakuEvent,
    availability: LongArray,
    isScrolling: Boolean,
    assignments: MutableMap<String, DanmakuLaneAssignment>,
    rejected: MutableSet<String>,
    config: DanmakuRenderConfig,
) {
  val needFreeBy = event.startMs
  val lane = pickLane(availability, needFreeBy, config.rejectWhenNoFreeLane)
  if (lane < 0) {
    rejected.add(event.id)
    return
  }
  availability[lane] = event.startMs + config.fixedDurationMs
  assignments[event.id] =
      if (isScrolling) DanmakuLaneAssignment(scrollingLane = lane, fixedLane = 0)
      else DanmakuLaneAssignment(scrollingLane = 0, fixedLane = lane)
}

/**
 * Chooses the earliest-free lane that is clear by [needFreeBy]. Returns -1 when none qualifies;
 * in legacy (non-rejecting) mode returns the least-busy lane index instead.
 */
private fun pickLane(availability: LongArray, needFreeBy: Long, rejectWhenBusy: Boolean): Int {
  var best = -1
  var bestTime = Long.MAX_VALUE
  for (index in availability.indices) {
    if (availability[index] <= needFreeBy) return index
    if (availability[index] < bestTime) {
      bestTime = availability[index]
      best = index
    }
  }
  return if (rejectWhenBusy) -1 else best
}

/**
 * Minimum head-start a scrolling item needs before the next can enter without overlap: the prior
 * item must travel its own text width past the right entry edge. Derived from the constant-speed
 * crossing where total distance = trackWidth + textWidth over the scroll duration.
 */
private fun crossingFreeMs(prevTextWidthPx: Float, trackWidthPx: Float, durationMs: Long): Long {
  if (prevTextWidthPx <= 0f) return 0L
  val totalDistance = trackWidthPx + prevTextWidthPx
  if (totalDistance <= 0f) return 0L
  return (durationMs.toDouble() * prevTextWidthPx / totalDistance).toLong()
}
