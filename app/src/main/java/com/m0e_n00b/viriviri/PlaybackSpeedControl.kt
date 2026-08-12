package com.m0e_n00b.viriviri

internal object PlaybackSpeedControl {
  val supportedSpeeds: List<Float> = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

  fun normalizedForDisplay(speed: Float): Float =
      supportedSpeeds.firstOrNull { it == speed } ?: 1f

  fun label(speed: Float): String {
    val normalized = normalizedForDisplay(speed)
    return if (normalized == normalized.toInt().toFloat()) "${normalized.toInt()}x" else "${normalized}x"
  }
}
