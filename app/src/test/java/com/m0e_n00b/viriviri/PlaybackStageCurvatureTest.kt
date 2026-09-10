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
        PlaybackStageCurvature.Cylinder(PlaybackStageCurvature.MAX_RADIUS_METERS),
        PlaybackStageCurvature.fromRadius(100f),
    )
    assertEquals(
        PlaybackStageCurvature.Cylinder(3.5f),
        PlaybackStageCurvature.fromRadius(3.5f),
    )
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
}
