package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStageCurvatureTest {
  @Test
  fun fromRadiusResolvesFlatForMissingOrInvalid() {
    assertEquals(PlaybackStageCurvature.Flat, PlaybackStageCurvature.fromRadius(null))
    assertEquals(PlaybackStageCurvature.Flat, PlaybackStageCurvature.fromRadius(0f))
    assertEquals(PlaybackStageCurvature.Flat, PlaybackStageCurvature.fromRadius(-1f))
    assertEquals(PlaybackStageCurvature.Flat, PlaybackStageCurvature.fromRadius(Float.NaN))
    assertEquals(PlaybackStageCurvature.Flat, PlaybackStageCurvature.fromRadius(Float.POSITIVE_INFINITY))
  }

  @Test
  fun fromRadiusClampsToSupportedRange() {
    assertEquals(
        PlaybackStageCurvature.Cylinder(PlaybackStageCurvature.MIN_RADIUS_METERS),
        PlaybackStageCurvature.fromRadius(0.5f),
    )
    assertEquals(
        PlaybackStageCurvature.Cylinder(3.5f),
        PlaybackStageCurvature.fromRadius(3.5f),
    )
    // Still just inside the range, so it survives as a (very gentle) cylinder.
    assertEquals(
        PlaybackStageCurvature.Cylinder(PlaybackStageCurvature.MAX_RADIUS_METERS - 0.01f),
        PlaybackStageCurvature.fromRadius(PlaybackStageCurvature.MAX_RADIUS_METERS - 0.01f),
    )
  }

  @Test
  fun fromRadiusResolvesFlatAtOrBeyondTheFlatmostRadius() {
    // Same threshold adjustedBy() uses: anything this flat IS flat, not "a very large cylinder".
    // Clamping these to Cylinder(MAX) resurrected stale 20 m-era preferences as Cylinder(6.0) after
    // the range narrowed — a state the thumbstick cannot produce, which then applied a full-radius
    // surface offset to the danmaku and backdrop layers.
    assertEquals(
        PlaybackStageCurvature.Flat,
        PlaybackStageCurvature.fromRadius(PlaybackStageCurvature.MAX_RADIUS_METERS),
    )
    assertEquals(
        PlaybackStageCurvature.Flat,
        PlaybackStageCurvature.fromRadius(PlaybackStageCurvature.MAX_RADIUS_METERS + 0.001f),
    )
    assertEquals(PlaybackStageCurvature.Flat, PlaybackStageCurvature.fromRadius(20f))
    assertEquals(PlaybackStageCurvature.Flat, PlaybackStageCurvature.fromRadius(100f))
  }

  @Test
  fun radiusOrNullAndGeometryMapConsistently() {
    assertNull(PlaybackStageCurvature.Flat.radiusOrNull())
    assertEquals(StageGeometry.Flat, PlaybackStageCurvature.Flat.toStageGeometry())

    val curved = PlaybackStageCurvature.Cylinder(2.5f)
    assertEquals(2.5f, curved.radiusOrNull()!!, 0f)
    assertEquals(StageGeometry.Cylinder(2.5f), curved.toStageGeometry())
  }

  @Test
  fun surfaceAnchorOffsetIsZeroWhenFlatAndOneRadiusWhenCurved() {
    // A flat layer's surface already sits on the entity origin, so nothing may move.
    assertEquals(0f, PlaybackStageCurvature.Flat.surfaceAnchorOffsetMeters(), 0f)
    // A cylinder has to move by exactly one radius, in the sign the runtime needs.
    assertEquals(
        PlaybackStageCurvature.SURFACE_ANCHOR_OFFSET_SIGN * 2.5f,
        PlaybackStageCurvature.Cylinder(2.5f).surfaceAnchorOffsetMeters(),
        0f,
    )
    assertEquals(
        PlaybackStageCurvature.SURFACE_ANCHOR_OFFSET_SIGN *
            PlaybackStageCurvature.MIN_RADIUS_METERS,
        PlaybackStageCurvature.Cylinder(PlaybackStageCurvature.MIN_RADIUS_METERS)
            .surfaceAnchorOffsetMeters(),
        0f,
    )
  }

  @Test
  fun codecRoundTripsFlatAndCylinder() {
    assertEquals(
        PlaybackStageCurvature.Flat,
        AppPreferenceCodec.decodeCurvature(AppPreferenceCodec.encodeCurvature(PlaybackStageCurvature.Flat)),
    )
    val cylinder = PlaybackStageCurvature.Cylinder(4.25f)
    assertEquals(cylinder, AppPreferenceCodec.decodeCurvature(AppPreferenceCodec.encodeCurvature(cylinder)))
  }

  @Test
  fun codecIgnoresMalformedRadius() {
    assertEquals(PlaybackStageCurvature.Flat, AppPreferenceCodec.decodeCurvature("not-a-number"))
    assertEquals(PlaybackStageCurvature.Flat, AppPreferenceCodec.decodeCurvature(null))
  }

  @Test
  fun cylinderRequiresPositiveRadius() {
    var threw = false
    try {
      PlaybackStageCurvature.Cylinder(0f)
    } catch (error: IllegalArgumentException) {
      threw = true
    }
    assertTrue(threw)
  }

  @Test
  fun adjustedByTightensAndFlattensInsideTheSupportedRange() {
    // A flat layer has no radius, so its first tightening step starts at the flatmost radius.
    assertEquals(
        PlaybackStageCurvature.Cylinder(PlaybackStageCurvature.MAX_RADIUS_METERS - 2f),
        PlaybackStageCurvature.Flat.adjustedBy(2f),
    )
    assertEquals(
        PlaybackStageCurvature.Cylinder(4f),
        PlaybackStageCurvature.Cylinder(6f).adjustedBy(2f),
    )
    // Loosening stays a cylinder while it is still inside the supported range; overshooting the
    // flatmost radius collapses to Flat instead (covered below).
    assertEquals(
        PlaybackStageCurvature.Cylinder(5f),
        PlaybackStageCurvature.Cylinder(4f).adjustedBy(-1f),
    )
  }

  @Test
  fun adjustedByClampsAtTheTightestRadius() {
    assertEquals(
        PlaybackStageCurvature.Cylinder(PlaybackStageCurvature.MIN_RADIUS_METERS),
        PlaybackStageCurvature.Cylinder(PlaybackStageCurvature.MIN_RADIUS_METERS).adjustedBy(5f),
    )
  }

  @Test
  fun adjustedByCollapsesToFlatAtOrBeyondTheFlatmostRadius() {
    // Overshooting the flatmost radius must mean "flat", not "a very large cylinder".
    assertEquals(PlaybackStageCurvature.Flat, PlaybackStageCurvature.Flat.adjustedBy(-1f))
    assertEquals(
        PlaybackStageCurvature.Flat,
        PlaybackStageCurvature.Cylinder(PlaybackStageCurvature.MAX_RADIUS_METERS).adjustedBy(-0.1f),
    )
    assertEquals(
        PlaybackStageCurvature.Flat,
        PlaybackStageCurvature.Cylinder(PlaybackStageCurvature.MAX_RADIUS_METERS - 1f).adjustedBy(-2f),
    )
  }

  @Test
  fun adjustedByIgnoresZeroAndNonFiniteDeltas() {
    assertEquals(PlaybackStageCurvature.Flat, PlaybackStageCurvature.Flat.adjustedBy(0f))
    assertEquals(
        PlaybackStageCurvature.Cylinder(5f),
        PlaybackStageCurvature.Cylinder(5f).adjustedBy(Float.NaN),
    )
    assertEquals(
        PlaybackStageCurvature.Cylinder(5f),
        PlaybackStageCurvature.Cylinder(5f).adjustedBy(Float.POSITIVE_INFINITY),
    )
  }

  @Test
  fun curvatureTargetCyclesThroughEveryLayerAndWraps() {
    var layer = PlaybackStageCurvatureLayer.VIDEO
    val visited = mutableListOf(layer)
    repeat(PlaybackStageCurvatureLayer.entries.size - 1) {
      layer = layer.next()
      visited += layer
    }
    assertEquals(PlaybackStageCurvatureLayer.entries.toList(), visited)
    assertEquals(PlaybackStageCurvatureLayer.VIDEO, layer.next())
  }
}
