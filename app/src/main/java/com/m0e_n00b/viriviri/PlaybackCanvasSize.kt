package com.m0e_n00b.viriviri

/** Physical scale of the existing MediaStage and its attached non-video overlays. */
enum class PlaybackCanvasSize(
    val label: String,
    val scale: Float,
) {
  COMPACT("紧凑", 0.82f),
  STANDARD("标准", 1.0f),
  LARGE("宽大", 1.18f),

  ;

  companion object {
    const val MIN_STAGE_SCALE = 0.70f
    const val MAX_STAGE_SCALE = 1.50f

    fun clampStageScale(scale: Float): Float =
        scale.takeIf(Float::isFinite)?.coerceIn(MIN_STAGE_SCALE, MAX_STAGE_SCALE) ?: STANDARD.scale
  }
}
