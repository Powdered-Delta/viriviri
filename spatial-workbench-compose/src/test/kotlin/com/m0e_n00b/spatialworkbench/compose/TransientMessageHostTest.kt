package com.m0e_n00b.spatialworkbench.compose

import com.m0e_n00b.spatialworkbench.core.CinemaPalette
import com.m0e_n00b.spatialworkbench.core.TransientMessageSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TransientMessageHostTest {
  @Test
  fun severityStylesResolveOnlyThroughSemanticPaletteRoles() {
    val info = transientMessageStyle(TransientMessageSeverity.INFO, CinemaPalette.DARK)
    val success = transientMessageStyle(TransientMessageSeverity.SUCCESS, CinemaPalette.DARK)
    val warning = transientMessageStyle(TransientMessageSeverity.WARNING, CinemaPalette.DARK)
    val error = transientMessageStyle(TransientMessageSeverity.ERROR, CinemaPalette.DARK)

    assertEquals(CinemaPalette.DARK.surface, info.background.toRgbColor())
    assertEquals(CinemaPalette.DARK.primaryButton, success.background.toRgbColor())
    assertEquals(CinemaPalette.DARK.highlightText, warning.background.toRgbColor())
    assertEquals(CinemaPalette.DARK.danger, error.background.toRgbColor())
    assertNotEquals(info.background, error.background)
  }
}

private fun androidx.compose.ui.graphics.Color.toRgbColor() =
    com.m0e_n00b.spatialworkbench.core.RgbColor(
        red = (red * 255f).toInt(),
        green = (green * 255f).toInt(),
        blue = (blue * 255f).toInt(),
    )
