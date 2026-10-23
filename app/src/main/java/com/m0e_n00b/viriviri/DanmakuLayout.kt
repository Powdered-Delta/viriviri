package com.m0e_n00b.viriviri

import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily

/** Position within the drawable area; the caller clips oversized glyphs to that area too. */
internal fun danmakuBaseline(
    family: DanmakuLaneFamily,
    lane: Int,
    config: DanmakuRenderConfig,
    canvasHeight: Float,
    fontTop: Float,
    fontBottom: Float,
    outlineWidth: Float,
): Float {
  val height = canvasHeight * config.showArea
  if (height <= 0f) return 0f
  val halfOutline = outlineWidth.coerceAtLeast(0f) / 2f
  val top = (-fontTop + halfOutline).coerceIn(0f, height)
  val bottom = (height - fontBottom - halfOutline).coerceIn(top, height)
  // One baseline grid for every family; fixed rows occupy its first/last slots.
  val gridLane = when (family) {
    DanmakuLaneFamily.SCROLLING, DanmakuLaneFamily.TOP_FIXED -> lane
    DanmakuLaneFamily.BOTTOM_FIXED -> config.scrollingLaneCount - config.fixedLaneCount + lane
  }
  val position = height * (gridLane + 1f) / (config.scrollingLaneCount + 1f)
  return position.coerceIn(top, bottom)
}
