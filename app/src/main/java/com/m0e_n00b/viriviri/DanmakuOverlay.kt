package com.m0e_n00b.viriviri

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import kotlinx.coroutines.delay

private const val SCROLL_DURATION_MS = 6_000L
private const val FIXED_DURATION_MS = 4_000L
private const val SCROLLING_LANE_COUNT = 12
private const val FIXED_LANE_COUNT = 3
private const val DANMAKU_FRAME_INTERVAL_MS = 33L

@Composable
internal fun StageBackdrop(alpha: Float = WorkbenchLayoutConfig.DEFAULT.stageBackdropAlpha) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    drawRect(Color.Black.copy(alpha = alpha))
  }
}

@Composable
internal fun DanmakuOverlay() {
  val appState by ViriViriApplication.appState.state.collectAsState()
  var frameTick by remember { mutableLongStateOf(0L) }
  LaunchedEffect(Unit) {
    while (true) {
      val player = ViriViriApplication.appState.playerSession.player
      frameTick = player.currentPosition
      // UX: danmaku uses a bounded animation cadence; video playback remains on Media3's clock.
      delay(if (player.isPlaying) DANMAKU_FRAME_INTERVAL_MS else 100L)
    }
  }
  val selectedVideoId = appState.selected?.videoId
  val runtime = remember(selectedVideoId) {
    val videoId = selectedVideoId ?: return@remember null
    DanmakuCanvasRuntime(ViriViriApplication.appState.danmakuStreamSource(videoId))
  }
  val fillPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = DANMAKU_TEXT_SIZE_PX } }
  val outlinePaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = DANMAKU_TEXT_SIZE_PX
      style = Paint.Style.STROKE
      strokeWidth = DANMAKU_OUTLINE_WIDTH_PX
      color = android.graphics.Color.BLACK
    }
  }
  Canvas(modifier = Modifier.fillMaxSize()) {
    val activeRuntime = runtime ?: return@Canvas
    activeRuntime.advance(frameTick)
    drawIntoCanvas { canvas ->
      for (item in activeRuntime.active) {
        val duration =
            if (item.family == DanmakuLaneFamily.SCROLLING) SCROLL_DURATION_MS else FIXED_DURATION_MS
        val metrics = item.metrics
        fillPaint.textSize = DANMAKU_TEXT_SIZE_PX * metrics.fontScale
        outlinePaint.textSize = DANMAKU_TEXT_SIZE_PX * metrics.fontScale
        outlinePaint.strokeWidth = metrics.outlineWidthPx
        fillPaint.color = metrics.textColorArgb
        val y = (item.lane + 1) * size.height / (SCROLLING_LANE_COUNT + 1)
        val elapsed = (frameTick - item.startTickMs).coerceIn(0L, duration).toFloat() / duration
        val x = when (item.family) {
          DanmakuLaneFamily.SCROLLING -> size.width - elapsed * (size.width + metrics.textWidthPx)
          DanmakuLaneFamily.TOP_FIXED, DanmakuLaneFamily.BOTTOM_FIXED -> (size.width - metrics.textWidthPx) / 2f
        }
        val fixedY = when (item.family) {
          DanmakuLaneFamily.TOP_FIXED -> (item.lane + 1) * size.height * 0.24f / (FIXED_LANE_COUNT + 1)
          DanmakuLaneFamily.BOTTOM_FIXED ->
              size.height * (0.76f + (item.lane + 1) * 0.24f / (FIXED_LANE_COUNT + 1))
          DanmakuLaneFamily.SCROLLING -> y
        }
        canvas.nativeCanvas.drawText(item.event.text, x, fixedY, outlinePaint)
        canvas.nativeCanvas.drawText(item.event.text, x, fixedY, fillPaint)
      }
    }
  }
}
