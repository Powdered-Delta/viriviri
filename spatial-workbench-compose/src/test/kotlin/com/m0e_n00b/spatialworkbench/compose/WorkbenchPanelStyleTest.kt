package com.m0e_n00b.spatialworkbench.compose

import com.m0e_n00b.spatialworkbench.core.CinemaColorRole
import com.m0e_n00b.spatialworkbench.core.CinemaPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkbenchPanelStyleTest {
  @Test
  fun styleResolvesEveryVisibleRoleFromOnePalette() {
    val palette = CinemaPalette.LIGHT
    val style = WorkbenchPanelStyle.fromPalette(palette)

    assertEquals(palette.composeColor(CinemaColorRole.BACKGROUND), style.background)
    assertEquals(palette.composeColor(CinemaColorRole.SURFACE), style.surface)
    assertEquals(palette.composeColor(CinemaColorRole.SECONDARY_BUTTON), style.surfaceStrong)
    assertEquals(palette.composeColor(CinemaColorRole.BORDER), style.border)
    assertEquals(palette.composeColor(CinemaColorRole.NORMAL_TEXT), style.text)
    assertEquals(palette.composeColor(CinemaColorRole.SECONDARY_TEXT), style.secondaryText)
    assertEquals(palette.composeColor(CinemaColorRole.PRIMARY_BUTTON), style.accent)
    assertEquals(palette.composeColor(CinemaColorRole.PRIMARY_BUTTON_LABEL), style.accentContent)
  }
}
