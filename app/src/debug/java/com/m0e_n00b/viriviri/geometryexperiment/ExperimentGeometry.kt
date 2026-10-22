package com.m0e_n00b.viriviri.geometryexperiment

/** Integer centimetres keep slider values and preset resets exact. */
internal data class ExperimentGeometry(val widthCm: Int, val radiusCm: Int?) {
  init {
    require(widthCm in MIN_WIDTH_CM..MAX_WIDTH_CM)
    require(radiusCm == null || radiusCm in MIN_RADIUS_CM..MAX_RADIUS_CM)
  }

  val width: Float get() = widthCm / 100f
  val height: Float get() = width * 9f / 16f
  val radius: Float? get() = radiusCm?.div(100f)
  val widthProgress: Int get() = widthCm - MIN_WIDTH_CM
  val radiusProgress: Int get() = (radiusCm ?: 100) - MIN_RADIUS_CM

  /** SDK 0.13.2 cylinder centre is at API-local +radius; derive from the fixed base. */
  fun carrierLocalZ(layer: ExperimentLayer): Float = layer.baseLocalZ - (radius ?: 0f)

  fun withWidthProgress(progress: Int) =
      copy(widthCm = MIN_WIDTH_CM + progress.coerceIn(0, WIDTH_STEPS))

  fun withRadiusProgress(progress: Int) =
      if (radiusCm == null) this
      else copy(radiusCm = MIN_RADIUS_CM + progress.coerceIn(0, RADIUS_STEPS))

  fun describe() = "w=$width m  h=$height m  r=${radius ?: "—"} m"

  companion object {
    const val MIN_WIDTH_CM = 100
    const val MAX_WIDTH_CM = 240
    const val MIN_RADIUS_CM = 80
    const val MAX_RADIUS_CM = 300
    const val WIDTH_STEPS = MAX_WIDTH_CM - MIN_WIDTH_CM
    const val RADIUS_STEPS = MAX_RADIUS_CM - MIN_RADIUS_CM
  }
}

internal enum class ExperimentLayer(val baseLocalZ: Float, val zIndex: Int) {
  VIDEO(0f, 0),
  DANMAKU(-0.01f, 1),
  SUBTITLE(-0.02f, 2),
}
