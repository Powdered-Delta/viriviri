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
import androidx.compose.ui.graphics.toArgb
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import kotlinx.coroutines.delay

private const val SCROLL_DURATION_MS = 6_000L
private const val FIXED_DURATION_MS = 4_000L
private const val SCROLLING_LANE_COUNT = 12
private const val FIXED_LANE_COUNT = 3
private const val DANMAKU_TEXT_SIZE_PX = 152f
private const val DANMAKU_OUTLINE_WIDTH_PX = 16f

@Composable
internal fun StageBackdrop() {
  Canvas(modifier = Modifier.fillMaxSize()) {
    drawRect(Color.Black.copy(alpha = 0.58f))
  }
}

@Composable
internal fun DanmakuOverlay() {
  val appState by ViriViriApplication.appState.state.collectAsState()
  var positionMs by remember { mutableLongStateOf(0L) }
  LaunchedEffect(Unit) {
    while (true) {
      positionMs = ViriViriApplication.appState.playerSession.player.currentPosition
      delay(16L)
    }
  }
  val scheduledEvents = remember(appState.danmakuEvents) { appState.danmakuEvents.sortedBy { it.startMs } }
  val laneAssignments = remember(appState.danmakuEvents) { scheduleDanmakuLanes(appState.danmakuEvents) }
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
    drawIntoCanvas { canvas ->
      for (event in scheduledEvents) {
        if (event.startMs > positionMs) break
        val duration = if (event.laneFamily == DanmakuLaneFamily.SCROLLING) SCROLL_DURATION_MS else FIXED_DURATION_MS
        if (positionMs > event.startMs + duration) continue
        val assignment = laneAssignments.getValue(event.id)
        val y = (assignment.scrollingLane + 1) * size.height / (SCROLLING_LANE_COUNT + 1)
        val elapsed = (positionMs - event.startMs).coerceIn(0L, duration).toFloat() / duration
        val textWidth = fillPaint.measureText(event.text)
        val x = when (event.laneFamily) {
          DanmakuLaneFamily.SCROLLING -> size.width - elapsed * (size.width + textWidth)
          DanmakuLaneFamily.TOP_FIXED, DanmakuLaneFamily.BOTTOM_FIXED -> (size.width - textWidth) / 2f
        }
        val fixedY = when (event.laneFamily) {
          DanmakuLaneFamily.TOP_FIXED ->
              (assignment.fixedLane + 1) * size.height * 0.24f / (FIXED_LANE_COUNT + 1)
          DanmakuLaneFamily.BOTTOM_FIXED ->
              size.height * (0.76f + (assignment.fixedLane + 1) * 0.24f / (FIXED_LANE_COUNT + 1))
          DanmakuLaneFamily.SCROLLING -> y
        }
        canvas.nativeCanvas.drawText(event.text, x, fixedY, outlinePaint)
        fillPaint.color = Color.White.toArgb()
        canvas.nativeCanvas.drawText(event.text, x, fixedY, fillPaint)
      }
    }
  }
}
