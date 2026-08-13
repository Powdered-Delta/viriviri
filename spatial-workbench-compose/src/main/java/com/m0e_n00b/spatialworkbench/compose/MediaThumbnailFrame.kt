package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stable media-thumbnail geometry with independent main-content and overlay slots. */
data class MediaThumbnailFrameStyle(
    val width: Dp = 128.dp,
    val height: Dp = 72.dp,
    val placeholderBackground: Color = Color(0xFF24333A),
)

@Composable
fun MediaThumbnailFrame(
    modifier: Modifier = Modifier,
    style: MediaThumbnailFrameStyle = MediaThumbnailFrameStyle(),
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
  Box(
      modifier =
          modifier
              .width(style.width)
              .height(style.height)
              .background(style.placeholderBackground),
      contentAlignment = Alignment.Center,
  ) {
    content()
    overlay()
  }
}
