package com.m0e_n00b.viriviri

import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialVideoAspectProbeModeTest {
  @Test
  fun geometryOnlyIsTheSafeDefaultAndAllManualPathsAreNamed() {
    assertEquals(SpatialVideoAspectProbeMode.GEOMETRY_ONLY, SpatialVideoAspectProbeMode.entries.first())
    assertEquals(
        listOf("Geometry only", "Commit false", "Commit true"),
        SpatialVideoAspectProbeMode.entries.map(SpatialVideoAspectProbeMode::label),
    )
  }
}
