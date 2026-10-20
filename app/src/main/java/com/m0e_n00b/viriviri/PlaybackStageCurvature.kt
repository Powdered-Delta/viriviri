package com.m0e_n00b.viriviri

/**
 * Shared curvature of the immersive MediaStage. Video, backdrop, danmaku, and future attached layers
 * all consume this one value; authored local-Z offsets distinguish their roles, not their curvature.
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

  /**
   * Steps this curvature by [curveDelta] metres of radius. A positive delta curves the layer
   * tighter (smaller radius), a negative delta flattens it back out.
   *
   * [Flat] has no radius, so the first tightening step starts from [MAX_RADIUS_METERS]; and any step
   * that reaches or passes [MAX_RADIUS_METERS] collapses back to [Flat]. That keeps "flat" reachable
   * from either direction, which is what the thumbstick control needs.
   */
  fun adjustedBy(curveDelta: Float): PlaybackStageCurvature {
    if (curveDelta == 0f || !curveDelta.isFinite()) return this
    val currentRadiusMeters = radiusOrNull() ?: MAX_RADIUS_METERS
    val nextRadiusMeters = currentRadiusMeters - curveDelta
    if (nextRadiusMeters >= MAX_RADIUS_METERS) return Flat
    return Cylinder(nextRadiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS))
  }

  /**
   * Measured local-Z displacement of the cylinder surface relative to its carrier entity. In the
   * connected SDK 0.13.2 / Quest 2 build, a curved panel with no correction drifted by one radius;
   * [surfaceAnchorOffsetMeters] is the inverse displacement needed to hold the surface centre at its
   * authored stage position. This is based on device evidence, not inferred from a generic cylinder API
   * description.
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

  /**
   * Inverse local-Z correction baked into custom mesh vertices that share the shifted entity.
   * Entity offset + mesh correction is zero, leaving the mesh surface's centre at [baseLocalZ].
   */
  fun meshVertexPositionCorrectionMeters(): Float = -surfaceAnchorOffsetMeters()

  /** Test/debug invariant: the carrier and its inverse-shifted mesh preserve the authored centre. */
  fun compensatedMeshCentreLocalZ(baseLocalZ: Float): Float =
      entityLocalZForSurface(baseLocalZ) + meshVertexPositionCorrectionMeters()

  companion object {
    /** Smallest allowed cylinder radius (tightest supported curve). */
    const val MIN_RADIUS_METERS: Float = 1.5f

    /**
     * Largest allowed cylinder radius; beyond this it is treated as flat.
     *
     * Deliberately close to [MIN_RADIUS_METERS]. The visible edge offset of a curved stage is
     * The visible edge offset of a curved stage is `halfWidth^2 / (2R)`, so the top of the range is
     * always its least legible part: about 6 cm at 8 m for the 2 m wide stage, against 33 cm at the
     * 1.5 m minimum. Expect the first thumbstick step out of [Flat] to look almost flat — tighten
     * further before judging a curvature.
     */
    const val MAX_RADIUS_METERS: Float = 8f

    /** Persisted value meaning "flat" (non-positive radius is not a real cylinder). */
    const val FLAT_SENTINEL: Float = 0f

    /**
     * Direction of [surfaceAnchorOffsetMeters] before the radius is applied: -1 pulls the layer
     * toward the viewer (stage local -Z), which is what pins the surface back when the runtime has
     * pushed it away by one radius.
     *
     * Flip this single constant if a device run shows a curved layer moving the wrong way (or
     * moving twice as far) instead of staying put.
     */
    const val SURFACE_ANCHOR_OFFSET_SIGN: Float = -1f

    /**
     * Rehydrates a persisted radius.
     *
     * Null, non-finite, or non-positive values resolve to [Flat] — and so does any radius at or beyond
     * [MAX_RADIUS_METERS], the same threshold [adjustedBy] uses to collapse back to [Flat].
     *
     * Clamping those to `Cylinder(MAX_RADIUS_METERS)` instead produced a state the control cannot
     * reach. Once the flatmost radius dropped from 20 m to 6 m, every stale preference landed on
     * exactly `Cylinder(6.0)`: not flat, but a barely-curved cylinder — and one that carried a full
     * 6 m surface offset plus a `CylinderShapeOptions` instead of a quad. Remaining positive values
     * are clamped up to [MIN_RADIUS_METERS].
     */
    fun fromRadius(radiusMeters: Float?): PlaybackStageCurvature {
      if (radiusMeters == null || !radiusMeters.isFinite() || radiusMeters <= 0f) return Flat
      if (radiusMeters >= MAX_RADIUS_METERS) return Flat
      return Cylinder(radiusMeters.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS))
    }
  }
}
