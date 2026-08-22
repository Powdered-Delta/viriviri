package com.m0e_n00b.viriviri

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.m0e_n00b.spatialworkbench.core.CinemaPalette

@Composable
internal fun ImmersiveCenterContentPanel(
    appState: ViriViriAppState = ViriViriApplication.appState,
    palette: CinemaPalette = CinemaPalette.DARK,
    onVideoSelected: () -> Unit = {},
    onDismissWorkbench: () -> Unit = {},
) {
  val mode by CenterContentSession.mode.collectAsState()
  // UX: one center panel swaps Search and VideoList modules without changing MediaStage ownership.
  RecommendationPanel(
      appState = appState,
      palette = palette,
      searchOpenByDefault = mode == CenterContentMode.SEARCH,
      showViewerContent = false,
      onVideoSelected = onVideoSelected,
      onDismissWorkbench = onDismissWorkbench,
  )
}
