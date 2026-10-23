package com.m0e_n00b.viriviri

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.m0e_n00b.spatialworkbench.core.DanmakuLaneFamily
import kotlinx.coroutines.delay

/**
 * Overlay-only render cadence (ms).
 *
 * Lane counts and durations are deliberately NOT declared here: the renderer reads them from the
 * canvas runtime's [DanmakuRenderConfig]. Keeping a second copy in the overlay let the renderer
 * silently diverge from the scheduler that admitted items into those lanes.
 */
private const val DANMAKU_FRAME_INTERVAL_MS = 33L

@Composable
internal fun StageBackdrop(alpha: Float = WorkbenchLayoutConfig.DEFAULT.stageBackdropAlpha) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    drawRect(Color.Black.copy(alpha = alpha))
  }
}

/**
 * Everything the overlay needs from its host.
 *
 * It used to read `ViriViriApplication.appState` directly — both the selected video and, inside a
 * polling loop, `playerSession.player`. That made it impossible to render without the app singleton
 * (and therefore impossible to preview, test, or re-host), so the three facts it actually depends on
 * are now parameters. The function types are deliberate: the overlay needs the *current* clock value
 * each frame, not a snapshot taken at composition.
 */
internal data class DanmakuOverlayProps(
    /** Video identity; null renders nothing. Also the key that rebuilds the canvas runtime. */
    val selectedVideoId: String?,
    /** Danmaku source for a video id. Called once per [selectedVideoId] change. */
    val streamSourceFor: (String) -> DanmakuStreamSource,
    /** Playback position in ms, read once per frame. */
    val playerPositionMs: () -> Long,
    /** Whether playback is running, which selects the frame cadence. */
    val isPlaying: () -> Boolean,
    /** Parts of the same BV need separate runtimes and cid-backed sources. */
    val selectedContentId: Long? = null,
)

@Composable
internal fun DanmakuOverlay(props: DanmakuOverlayProps) {
  var frameTick by remember { mutableLongStateOf(0L) }
  // The loop is keyed on Unit, not on `props`: `props` holds lambdas whose identity changes on every
  // recomposition, so keying on it would tear down and restart the frame loop continuously and the
  // overlay would stutter. `rememberUpdatedState` keeps one long-lived loop that always reads the
  // latest props instead.
  val currentProps by rememberUpdatedState(props)
  LaunchedEffect(Unit) {
    while (true) {
      frameTick = currentProps.playerPositionMs()
      // UX: danmaku uses a bounded animation cadence; video playback remains on Media3's clock.
      delay(if (currentProps.isPlaying()) DANMAKU_FRAME_INTERVAL_MS else 100L)
    }
  }
  val selectedVideoId = props.selectedVideoId
  val runtime = remember(selectedVideoId, props.selectedContentId) {
    val videoId = selectedVideoId ?: return@remember null
    DanmakuCanvasRuntime(props.streamSourceFor(videoId))
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
  val fontMetrics = remember { Paint.FontMetrics() }
  Canvas(modifier = Modifier.fillMaxSize()) {
    val activeRuntime = runtime ?: return@Canvas
    activeRuntime.advance(frameTick)
    // UX: lane counts and durations come from the runtime's [DanmakuRenderConfig] instead of
    // overlay-local copies, so the renderer cannot drift from the scheduler that placed these
    // items into their lanes.
    val config = activeRuntime.config
    val drawableHeight = size.height * config.showArea
    if (drawableHeight <= 0f) return@Canvas
    clipRect(bottom = drawableHeight) {
      drawIntoCanvas { canvas ->
        for (item in activeRuntime.active) {
          val duration =
              if (item.family == DanmakuLaneFamily.SCROLLING) config.scrollingDurationMs
              else config.fixedDurationMs
          val metrics = item.metrics
          fillPaint.textSize = DANMAKU_TEXT_SIZE_PX * metrics.fontScale
          outlinePaint.textSize = DANMAKU_TEXT_SIZE_PX * metrics.fontScale
          outlinePaint.strokeWidth = metrics.outlineWidthPx
          fillPaint.color = metrics.textColorArgb
          val elapsed = (frameTick - item.startTickMs).coerceIn(0L, duration).toFloat() / duration
          val x = when (item.family) {
            DanmakuLaneFamily.SCROLLING -> size.width - elapsed * (size.width + metrics.textWidthPx)
            DanmakuLaneFamily.TOP_FIXED, DanmakuLaneFamily.BOTTOM_FIXED -> (size.width - metrics.textWidthPx) / 2f
          }
          fillPaint.getFontMetrics(fontMetrics)
          val baseline = danmakuBaseline(item.family, item.lane, config, size.height,
              fontMetrics.top, fontMetrics.bottom, metrics.outlineWidthPx)
          canvas.nativeCanvas.drawText(item.event.text, x, baseline, outlinePaint)
          canvas.nativeCanvas.drawText(item.event.text, x, baseline, fillPaint)
        }
      }
    }
  }
}
