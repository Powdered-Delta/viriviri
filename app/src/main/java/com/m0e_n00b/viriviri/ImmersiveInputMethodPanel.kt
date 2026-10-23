package com.m0e_n00b.viriviri

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.m0e_n00b.spatialworkbench.compose.InputConsoleStyle
import com.m0e_n00b.spatialworkbench.core.CinemaPalette

/**
 * Standalone near-field input panel. It owns no route, player, Spatial entity, or panel lifecycle;
 * [SpatialVideoSampleActivity] owns those concerns and only hosts this Compose content.
 */
@Composable
internal fun ImmersiveInputMethodPanel(
    // Required, not defaulted: see ImmersiveCenterContentPanel.
    appState: ViriViriAppState,
    palette: CinemaPalette = CinemaPalette.DARK,
) {
  val state by appState.state.collectAsState()
  if (!state.inputUi.visible) return

  Box(modifier = Modifier.fillMaxSize()) {
    AppInputConsole(
        controller = appState.inputController,
        style = InputConsoleStyle.fromPalette(palette),
        transparentRoot = true,
    )
  }
}
