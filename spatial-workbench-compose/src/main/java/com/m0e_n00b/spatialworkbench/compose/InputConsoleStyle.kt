package com.m0e_n00b.spatialworkbench.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m0e_n00b.spatialworkbench.core.CinemaColorRole
import com.m0e_n00b.spatialworkbench.core.CinemaPalette
import com.m0e_n00b.spatialworkbench.core.RgbColor

fun CinemaPalette.composeColor(role: CinemaColorRole): Color = color(role).toComposeColor()

/** Visual tokens for the application-owned input console. */
data class InputConsoleKeyStyle(
    val background: Color,
    val content: Color,
    val disabledBackground: Color,
    val disabledContent: Color,
    val height: Dp = 44.dp,
)

data class InputConsoleCandidateStyle(
    val background: Color,
    val content: Color,
    val selectedBackground: Color,
    val selectedContent: Color,
    val border: Color,
)

data class InputConsoleStyle(
    val shell: SpatialPanelShellStyle,
    val compositionText: Color,
    val compositionBackground: Color,
    val candidate: InputConsoleCandidateStyle,
    val popupBackground: Color,
    val popupContent: Color,
    val popupBorder: Color,
    val selectedLanguage: Color,
    val secondaryText: Color,
    val numberKey: InputConsoleKeyStyle,
    val alphabetKey: InputConsoleKeyStyle,
    val actionKey: InputConsoleKeyStyle,
    val skin: InputConsoleSkin = GboardQwertyInputConsoleSkin,
) {
  companion object {
    fun fromPalette(
        palette: CinemaPalette,
        shell: SpatialPanelShellStyle = SpatialPanelShellStyle(),
    ): InputConsoleStyle {
      fun color(role: CinemaColorRole): Color = palette.composeColor(role)
      val surface = color(CinemaColorRole.SURFACE)
      val normalText = color(CinemaColorRole.NORMAL_TEXT)
      val secondaryText = color(CinemaColorRole.SECONDARY_TEXT)
      val primary = color(CinemaColorRole.PRIMARY_BUTTON)
      val primaryLabel = color(CinemaColorRole.PRIMARY_BUTTON_LABEL)
      val secondary = color(CinemaColorRole.SECONDARY_BUTTON)
      val secondaryLabel = color(CinemaColorRole.SECONDARY_BUTTON_LABEL)
      val border = color(CinemaColorRole.BORDER)
      val highlight = color(CinemaColorRole.HIGHLIGHT_TEXT)
      val disabled = secondary.copy(alpha = 0.45f)
      val disabledContent = secondaryText.copy(alpha = 0.62f)

      return InputConsoleStyle(
          shell = shell.copy(background = surface.copy(alpha = palette.surfaceOpacity)),
          compositionText = normalText,
          compositionBackground = surface,
          candidate =
              InputConsoleCandidateStyle(
                  background = secondary,
                  content = secondaryLabel,
                  selectedBackground = primary,
                  selectedContent = primaryLabel,
                  border = border,
              ),
          popupBackground = surface,
          popupContent = normalText,
          popupBorder = border,
          selectedLanguage = highlight,
          secondaryText = secondaryText,
          numberKey = InputConsoleKeyStyle(secondary, secondaryLabel, disabled, disabledContent),
          alphabetKey = InputConsoleKeyStyle(primary, primaryLabel, disabled, disabledContent),
          actionKey = InputConsoleKeyStyle(secondary, secondaryLabel, disabled, disabledContent),
          skin = GboardQwertyInputConsoleSkin,
      )
    }
  }
}

data class CinemaInputConsoleActions(
    val onBackspace: () -> Unit,
    val onClear: () -> Unit,
    val onSearch: () -> Unit,
    val onVoice: () -> Unit = {},
    val onSystemIme: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

private fun RgbColor.toComposeColor(): Color =
    Color(
        red = red / 255f,
        green = green / 255f,
        blue = blue / 255f,
    )
