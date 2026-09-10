package com.m0e_n00b.viriviri

/**
 * Which stage layer the thumbstick curvature control bends.
 *
 * The three layers keep independent curvature (only the shared scale is common), so the control
 * needs an explicit target. [label] is rendered on the debug rail so the current target is never
 * ambiguous while tuning.
 */
enum class PlaybackStageCurvatureLayer(val label: String) {
  VIDEO("video"),
  DANMAKU("danmaku"),
  BACKDROP("backdrop");

  /** Cycles to the next layer in declaration order, wrapping back to [VIDEO]. */
  fun next(): PlaybackStageCurvatureLayer = entries[(ordinal + 1) % entries.size]
}
