package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuEmissionDirection
import com.m0e_n00b.spatialworkbench.core.DanmakuEvent
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily

/** Configurable duplicate reduction applied before lane scheduling and rendering. */
data class DanmakuMergeConfig(
    val windowMs: Long = DEFAULT_DANMAKU_MERGE_WINDOW_MS,
    val duplicateThreshold: Int = DEFAULT_DANMAKU_DUPLICATE_THRESHOLD,
) {
  init {
    require(windowMs > 0L) { "windowMs must be positive" }
    require(duplicateThreshold > 0) { "duplicateThreshold must be positive" }
  }

  companion object {
    const val DEFAULT_DANMAKU_MERGE_WINDOW_MS = 1_000L
    const val DEFAULT_DANMAKU_DUPLICATE_THRESHOLD = 5
  }
}

data class MergedDanmakuEvent(
    val event: DanmakuEvent,
    val mergedCount: Int = 1,
)

private data class DanmakuMergeKey(
    val windowIndex: Long,
    val text: String,
    val laneFamily: DanmakuLaneFamily,
    val emissionDirection: DanmakuEmissionDirection?,
)

/**
 * Mirrors PiliPlus's segment-local content merge without importing its Flutter/UI dependencies.
 * Groups are fixed time windows so identical text in distant parts of a video is preserved.
 */
internal fun mergeDanmakuEvents(
    events: List<DanmakuEvent>,
    config: DanmakuMergeConfig = DanmakuMergeConfig(),
): List<MergedDanmakuEvent> {
  if (events.isEmpty()) return emptyList()

  val groups = linkedMapOf<DanmakuMergeKey, MutableList<DanmakuEvent>>()
  events.sortedBy(DanmakuEvent::startMs).forEach { event ->
    val key =
        DanmakuMergeKey(
            windowIndex = event.startMs.coerceAtLeast(0L) / config.windowMs,
            text = event.text,
            laneFamily = event.laneFamily,
            emissionDirection = event.emissionDirection,
        )
    groups.getOrPut(key) { mutableListOf() }.add(event)
  }

  return groups.values
      .flatMap { group ->
        if (group.size > config.duplicateThreshold) {
          listOf(MergedDanmakuEvent(event = group.first(), mergedCount = group.size))
        } else {
          group.map { event -> MergedDanmakuEvent(event = event) }
        }
      }
      .sortedBy { it.event.startMs }
}
