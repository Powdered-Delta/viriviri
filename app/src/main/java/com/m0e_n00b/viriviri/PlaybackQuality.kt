package com.m0e_n00b.viriviri

enum class PlaybackQuality(
    val label: String,
    val qn: Int,
    val maximumHeight: Int?,
) {
  AUTO("Auto", 80, null),
  P360("360p", 16, 360),
  P480("480p", 32, 480),
  P720("720p", 64, 720),
  P1080("1080p", 80, 1080),
}

internal data class DashVideoStream(
    val codecs: String,
    val height: Int,
    val id: Int,
)

internal fun selectAvcVideoStream(
    streams: List<DashVideoStream>,
    quality: PlaybackQuality,
): DashVideoStream? {
  val avcStreams = streams.filter { it.codecs.contains("avc", ignoreCase = true) }
  if (avcStreams.isEmpty()) return null
  return when (val maximumHeight = quality.maximumHeight) {
    null -> avcStreams.maxWithOrNull(compareBy<DashVideoStream> { it.height }.thenBy { it.id })
    else ->
        avcStreams
            .filter { it.height <= maximumHeight }
            .maxWithOrNull(compareBy<DashVideoStream> { it.height }.thenBy { it.id })
            ?: avcStreams.minWithOrNull(compareBy<DashVideoStream> { it.height }.thenBy { it.id })
  }
}
