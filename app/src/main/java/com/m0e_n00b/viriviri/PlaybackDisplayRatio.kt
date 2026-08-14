package com.m0e_n00b.viriviri

enum class PlaybackDisplayRatio(
    val label: String,
    val displayAspectRatio: Float?,
) {
  AUTO("Auto", null),
  WIDESCREEN_16_9("16:9", 16f / 9f),
  STANDARD_4_3("4:3", 4f / 3f),
  SQUARE_1_1("1:1", 1f),
  PORTRAIT_9_16("9:16", 9f / 16f),
}
