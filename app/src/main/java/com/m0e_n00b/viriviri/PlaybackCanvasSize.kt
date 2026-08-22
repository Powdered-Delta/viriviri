package com.m0e_n00b.viriviri

/** Physical scale of the existing MediaStage and its attached non-video overlays. */
enum class PlaybackCanvasSize(
    val label: String,
    val scale: Float,
) {
  COMPACT("紧凑", 0.82f),
  STANDARD("标准", 1.0f),
  LARGE("宽大", 1.18f),
}
