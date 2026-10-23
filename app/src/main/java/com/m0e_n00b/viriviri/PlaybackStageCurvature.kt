package com.m0e_n00b.viriviri

/**
 * Shared curvature of the immersive MediaStage. Video, backdrop, danmaku, and future attached layers
 * all consume this one value; authored local-Z offsets distinguish their roles, not their curvature.
 *
 * Persistence keeps this type self-contained: [Flat] is encoded as the [FLAT_SENTINEL] sentinel
 * (a flat quad has an effectively infinite radius), a [Cylinder] as its raw control radius.
 * Render layouts derive an effective radius with [scaledBy]; never persist that derived radius.
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

  /** Similarity scaling for rendering, without the raw control/persistence radius clamp. */
  fun scaledBy(scale: Float): PlaybackStageCurvature {
    require(scale.isFinite() && scale > 0f)
    return when (this) {
      Flat -> Flat
      is Cylinder -> Cylinder(radiusMeters * scale)
    }
  }

  /**
   * Steps this curvature by [curveDelta] metres of radius. A positive delta curves the layer
   * tighter (smaller radius), a negative delta flattens it back out.
   *
   * [Flat] has no radius, so its first positive step enters [MAX_RADIUS_METERS] exactly without
   * consuming the delta. Subsequent steps adjust the cylinder radius. Exactly [MAX_RADIUS_METERS]
   * remains curved; only a larger radius becomes [Flat]. Loosening [Flat] keeps it flat.
   */
  fun adjustedBy(curveDelta: Float): PlaybackStageCurvature {
    if (curveDelta == 0f || !curveDelta.isFinite()) return this
    if (this == Flat) return if (curveDelta > 0f) Cylinder(MAX_RADIUS_METERS) else Flat
    val nextRadiusMeters = (this as Cylinder).radiusMeters - curveDelta
    if (nextRadiusMeters > MAX_RADIUS_METERS) return Flat
    return Cylinder(nextRadiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS))
  }

  /**
   * SDK 0.13.2 cylinder geometry has its axis at the entity origin and its central surface point
   * at API-local Z=+radius. Verified against native makeCylinderSurface and the API/native Z
   * reflection at mesh upload. Moving each compositor axis carrier by -radius pins that point
   * to its authored base. See docs/spatial-coordinates.md for the coordinate boundary.
   */
  fun surfaceAnchorOffsetMeters(): Float =
      when (this) {
        Flat -> 0f
        is Cylinder -> SURFACE_ANCHOR_OFFSET_SIGN * radiusMeters
      }

  /**
   * Absolute local-Z offset for the entity carrying this curved surface. The offset is always
   * recomputed from the authored base position (never accumulated), so changing radius from r1 to r2
   * moves the entity by exactly the correction delta needed for r2.
   */
  fun entityLocalZForSurface(baseLocalZ: Float): Float =
      baseLocalZ + surfaceAnchorOffsetMeters()

  companion object {
    /** Smallest allowed cylinder radius (tightest supported curve). */
    const val MIN_RADIUS_METERS: Float = 1.5f

    /**
     * Inclusive maximum raw control/persistence radius. Larger raw radii become [Flat].
     * Effective render radii derived by [scaledBy] may exceed this boundary.
     */
    const val MAX_RADIUS_METERS: Float = 8f

    /** Persisted value meaning "flat" (non-positive radius is not a real cylinder). */
    const val FLAT_SENTINEL: Float = 0f

    /**
     * Inverse of the verified API-local +radius surface displacement. This is not a tuning knob:
     * never flip it based on a headset symptom without checking the API/native conversion and
     * the aperture's independent footprint scale first.
     */
    const val SURFACE_ANCHOR_OFFSET_SIGN: Float = -1f

    /**
     * Rehydrates a persisted radius.
     *
     * Null, non-finite, non-positive, or greater than [MAX_RADIUS_METERS] values resolve to [Flat].
     * The maximum remains a cylinder and positive values below [MIN_RADIUS_METERS] clamp upward.
     * This raw boundary must not be applied to effective render radii derived by [scaledBy].
     */
    fun fromRadius(radiusMeters: Float?): PlaybackStageCurvature {
      if (radiusMeters == null || !radiusMeters.isFinite() || radiusMeters <= 0f) return Flat
      if (radiusMeters > MAX_RADIUS_METERS) return Flat
      return Cylinder(radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS))
    }
  }
}
