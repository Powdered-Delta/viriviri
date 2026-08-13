package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.ui.graphics.Color
import com.m0e_n00b.spatialworkbench.core.CinemaPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class InputConsoleStyleTest {
  @Test
  fun paletteMappingUsesSemanticCinemaRoles() {
    val dark = InputConsoleStyle.fromPalette(CinemaPalette.DARK)
    val light = InputConsoleStyle.fromPalette(CinemaPalette.LIGHT)

    assertNotEquals(dark.shell.background, light.shell.background)
    assertNotEquals(dark.alphabetKey.background, light.alphabetKey.background)
    val expectedSurfaceAlpha = (CinemaPalette.DARK.surfaceOpacity * 255f).roundToInt() / 255f
    assertEquals(expectedSurfaceAlpha, dark.shell.background.alpha, 0f)
    assertTrue(dark.candidateStripHeight.value > 0f)
  }

  @Test
  fun geometryTokensRemainStableForEmptyAndNonEmptyCandidates() {
    val style = InputConsoleStyle.fromPalette(CinemaPalette.DARK)

    assertEquals(20f, style.compositionHeight.value, 0.001f)
    assertEquals(48f, style.candidateStripHeight.value, 0.001f)
    assertEquals(180f, style.candidatePopupHeight.value, 0.001f)
  }

  @Test
  fun voiceCallbackContractIsOneSharedActionForBothEntryPoints() {
    var voiceCalls = 0
    val voice: () -> Unit = { voiceCalls++; Unit }
    val actions =
        CinemaInputConsoleActions(
            onBackspace = {},
            onClear = {},
            onSearch = {},
            onVoice = voice,
        )

    actions.onVoice()
    actions.onVoice()

    assertEquals(2, voiceCalls)
  }
}
