package com.m0e_n00b.viriviri

/**
 * Per-layer curvature of the immersive stage. The video mesh, the danmaku overlay, and the
 * backdrop each hold their own value so curving one layer never affects the others (layers stay
 * decoupled). Default is [Flat], which reproduces today's behaviour exactly.
 *
 * Persistence keeps this type self-contained: [Flat] is encoded as the [FLAT_SENTINEL] sentinel
 * (a flat quad has an effectively infinite radius), a [Cylinder] as its clamped radius.
 */
sealed interface PlaybackStageCurvature {
  /** Flat panel / quad (infinite radius). Default and current behaviour. */
  data object Flat : PlaybackStageCurvature

  /** Curved into a cylinder segment with the given radius in metres. Larger radius = flatter. */
  data class Cylinder(val radiusMeters: Float) : PlaybackStageCurvature {
    init {
      require(radiusMeters > 0f) { "radius must be positive, was $radiusMeters" }
    }
  }

  /** Radius in metres, or null when flat. */
  fun radiusOrNull(): Float? =
      when (this) {
        Flat -> null
        is Cylinder -> radiusMeters
      }

  /** Maps to the mesh-builder geometry (shared by the video surface). */
  fun toStageGeometry(): StageGeometry =
      when (this) {
        Flat -> StageGeometry.Flat
        is Cylinder -> StageGeometry.Cylinder(radiusMeters)
      }

  companion object {
    /** Smallest allowed cylinder radius (tightest supported curve). */
    const val MIN_RADIUS_METERS: Float = 1.5f

    /** Largest allowed cylinder radius; beyond this it is treated as flat. */
    const val MAX_RADIUS_METERS: Float = 20f

    /** Persisted value meaning "flat" (non-positive radius is not a real cylinder). */
    const val FLAT_SENTINEL: Float = 0f

    /**
     * Rehydrates a persisted radius. Null, non-finite, or non-positive values resolve to [Flat];
     * positive values are clamped to [MIN_RADIUS_METERS]..[MAX_RADIUS_METERS].
     */
    fun fromRadius(radiusMeters: Float?): PlaybackStageCurvature {
      if (radiusMeters == null || !radiusMeters.isFinite() || radiusMeters <= 0f) return Flat
      return Cylinder(radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS))
    }
  }
}
