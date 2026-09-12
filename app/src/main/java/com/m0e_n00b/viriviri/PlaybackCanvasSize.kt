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

    /**
     * Largest stage scale. Raised from 1.50 to 2.00 on request.
     *
     * Note for the curvature work: `centralAngle = width / radius` and a compositor cylinder only shows
     * up to half a turn, so a wider stage at a tight radius is the combination that can exceed it — at
     * r = 1.5 m the limit is roughly a 4.7 m wide stage.
     */
    const val MAX_STAGE_SCALE = 2.00f

    fun clampStageScale(scale: Float): Float =
        scale.takeIf(Float::isFinite)?.coerceIn(MIN_STAGE_SCALE, MAX_STAGE_SCALE) ?: STANDARD.scale
  }
}
