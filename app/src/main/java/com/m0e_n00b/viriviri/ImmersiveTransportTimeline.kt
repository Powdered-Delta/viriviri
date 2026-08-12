package com.m0e_n00b.viriviri

internal data class ImmersiveTransportTimeline(
    val canSeek: Boolean,
    val maxMs: Int,
    val positionMs: Int,
    val elapsedLabel: String,
    val durationLabel: String,
)

internal fun immersiveTransportTimeline(
    playerPositionMs: Long,
    playerDurationMs: Long,
    dragPositionMs: Long? = null,
): ImmersiveTransportTimeline {
  val durationMs = playerDurationMs.takeIf { it in 0..Int.MAX_VALUE.toLong() }
      ?: return ImmersiveTransportTimeline(
          canSeek = false,
          maxMs = 0,
          positionMs = 0,
          elapsedLabel = UNKNOWN_TIMECODE,
          durationLabel = UNKNOWN_TIMECODE,
      )
  val positionMs = (dragPositionMs ?: playerPositionMs).coerceIn(0L, durationMs)
  return ImmersiveTransportTimeline(
      canSeek = true,
      maxMs = durationMs.toInt(),
      positionMs = positionMs.toInt(),
      elapsedLabel = formatTransportTimecode(positionMs),
      durationLabel = formatTransportTimecode(durationMs),
  )
}

private const val UNKNOWN_TIMECODE = "--:--"

internal fun formatTransportTimecode(timeMs: Long): String {
  val totalSeconds = (timeMs.coerceAtLeast(0L) / 1_000L)
  val seconds = totalSeconds % 60
  val totalMinutes = totalSeconds / 60
  val minutes = totalMinutes % 60
  val hours = totalMinutes / 60
  return if (hours > 0) {
    "%d:%02d:%02d".format(hours, minutes, seconds)
  } else {
    "%d:%02d".format(minutes, seconds)
  }
}
