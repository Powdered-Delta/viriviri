package com.m0e_n00b.viriviri.geometryexperiment

/** Read-only media-position clock: replacement never restarts the pseudo content timeline. */
internal object ExperimentOverlayTimeline {
  val danmaku = listOf("伪弹幕 · 检查曲面贴合", "宽度变化，弹幕一起变化", "Cozy 场景 · 层间距 1 cm")
  val subtitles = listOf("伪字幕：视频、弹幕、字幕使用同一组几何", "伪字幕：调节曲率，观察中央深度是否固定", "伪字幕：调节尺寸，检查边缘与文字是否同步")

  fun danmakuProgress(positionMs: Long, lane: Int): Float {
    require(lane in danmaku.indices)
    return ((positionMs.coerceAtLeast(0) % 8000L + lane * 1700L) % 8000L) / 8000f
  }

  fun subtitle(positionMs: Long): String = subtitles[
      ((positionMs.coerceAtLeast(0) / 4000L) % subtitles.size).toInt()]
}
