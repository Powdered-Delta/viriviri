package com.m0e_n00b.viriviri

internal object PlaybackVolumeControl {
  val supportedVolumes: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

  fun normalizedForDisplay(volume: Float): Float =
      supportedVolumes.firstOrNull { it == volume } ?: 1f

  fun label(volume: Float): String = "Vol ${(normalizedForDisplay(volume) * 100).toInt()}%"
}
