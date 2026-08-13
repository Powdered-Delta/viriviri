package com.m0e_n00b.spatialworkbench.compose

import com.m0e_n00b.spatialworkbench.core.CinemaPalette
import com.m0e_n00b.spatialworkbench.core.ContentAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentAccessBadgeTest {
  @Test
  fun standardAccessHasNoBadgeAndChargingUsesPaletteTokens() {
    assertNull(contentAccessBadgeStyle(ContentAccess.STANDARD, CinemaPalette.DARK))

    val dark = contentAccessBadgeStyle(ContentAccess.CHARGING_EXCLUSIVE, CinemaPalette.DARK)
    val light = contentAccessBadgeStyle(ContentAccess.CHARGING_EXCLUSIVE, CinemaPalette.LIGHT)

    assertEquals("充电", dark?.label)
    assertEquals(CinemaPalette.DARK.chargingBadge, dark?.background?.toRgbColor())
    assertEquals(CinemaPalette.DARK.chargingBadgeLabel, dark?.content?.toRgbColor())
    assertNotEquals(dark?.background, light?.background)
  }
}

private fun androidx.compose.ui.graphics.Color.toRgbColor() =
    com.m0e_n00b.spatialworkbench.core.RgbColor(
        red = (red * 255f).toInt(),
        green = (green * 255f).toInt(),
        blue = (blue * 255f).toInt(),
    )
