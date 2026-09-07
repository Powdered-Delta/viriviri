package com.m0e_n00b.viriviri

/**
 * Centralized tunables for danmaku rendering, mirroring the [WorkbenchLayoutConfig] pattern so a
 * future settings screen can expose them. Values are defaults; callers may override per surface.
 *
 * Multi-canvas note: content (events, measured metrics) is shared across all danmaku canvases,
 * while capacity/density policy is applied *per canvas* through these parameters (each surface
 * has its own size/lane count and runs its own admission). Keep this object plain data so it can
 * be passed into the pure scheduling functions.
 */
data class DanmakuRenderConfig(
    /** Scroll-lane count for one canvas; bounds simultaneously-visible scrolling items. */
    val scrollingLaneCount: Int = 12,
    /** Top/bottom fixed (pinned) lane counts for one canvas. */
    val fixedLaneCount: Int = 3,
    /** Scroll crossing duration at 1x speed (ms). */
    val scrollingDurationMs: Long = 6_000L,
    /** Pinned comment dwell duration (ms). */
    val fixedDurationMs: Long = 4_000L,
    /**
     * Drop provider events whose weight is below this threshold. Events with no weight signal
     * ([com.m0e_n00b.spatialworkbench.core.DanmakuEvent.WEIGHT_UNKNOWN]) are always kept.
     */
    val weightThreshold: Int = 0,
    /**
     * When true, reject events that have no free lane instead of overlapping them; false keeps the
     * legacy "always place" behavior (packs every event into the least-busy lane).
     */
    val rejectWhenNoFreeLane: Boolean = true,
    /** Fraction (0..1) of canvas height usable by scrolling comments (rest stays clear). */
    val showArea: Float = 1f,
) {
  init {
    require(scrollingLaneCount > 0) { "scrollingLaneCount must be positive" }
    require(fixedLaneCount > 0) { "fixedLaneCount must be positive" }
    require(scrollingDurationMs > 0L) { "scrollingDurationMs must be positive" }
    require(fixedDurationMs > 0L) { "fixedDurationMs must be positive" }
    require(showArea in 0f..1f) { "showArea must be within 0..1" }
  }

  companion object {
    /** Single shared default config; intentionally mirrors WorkbenchLayoutConfig's access style. */
    val DEFAULT = DanmakuRenderConfig()
  }
}
