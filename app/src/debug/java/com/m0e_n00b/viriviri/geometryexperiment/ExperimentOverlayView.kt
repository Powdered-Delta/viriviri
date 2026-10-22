package com.m0e_n00b.viriviri.geometryexperiment

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Transparent UI-only display. It never creates or borrows a media output Surface. */
internal class ExperimentOverlayView(
    context: Context,
    private val layer: ExperimentLayer,
    private val positionMs: () -> Long?,
) : View(context) {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
  private var stopped = false
  private val tick = object : Runnable {
    override fun run() {
      if (stopped || !isAttachedToWindow) return
      invalidate()
      postDelayed(this, 33L)
    }
  }

  init {
    setBackgroundColor(Color.TRANSPARENT)
    isClickable = false
    isFocusable = false
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (!stopped) post(tick)
  }

  override fun onDetachedFromWindow() {
    removeCallbacks(tick)
    super.onDetachedFromWindow()
  }

  fun stop() {
    stopped = true
    removeCallbacks(tick)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (stopped || width == 0 || height == 0) return
    val time = positionMs() ?: return
    val saved = canvas.save()
    canvas.scale(width / 1280f, height / 720f)
    if (layer == ExperimentLayer.DANMAKU) {
      paint.textSize = 42f
      ExperimentOverlayTimeline.danmaku.forEachIndexed { lane, text ->
        val x = 1280f - (1280f + paint.measureText(text)) * ExperimentOverlayTimeline.danmakuProgress(time, lane)
        outlinedText(canvas, text, x, 100f + lane * 85f, if (lane == 1) Color.YELLOW else Color.WHITE)
      }
    } else {
      val text = ExperimentOverlayTimeline.subtitle(time)
      paint.textSize = 40f
      val x = (1280f - paint.measureText(text)) / 2f
      paint.style = Paint.Style.FILL
      paint.color = 0x99000000.toInt()
      canvas.drawRoundRect(x - 20f, 610f, 1280f - x + 20f, 676f, 12f, 12f, paint)
      outlinedText(canvas, text, x, 657f, Color.WHITE)
    }
    canvas.restoreToCount(saved)
  }

  private fun outlinedText(canvas: Canvas, text: String, x: Float, y: Float, color: Int) {
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    paint.color = Color.BLACK
    canvas.drawText(text, x, y, paint)
    paint.style = Paint.Style.FILL
    paint.color = color
    canvas.drawText(text, x, y, paint)
  }
}
