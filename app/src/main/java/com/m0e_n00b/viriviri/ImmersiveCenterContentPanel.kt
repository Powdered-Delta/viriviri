package com.m0e_n00b.viriviri

import androidx.compose.runtime.Composable
import com.m0e_n00b.spatialworkbench.core.CinemaPalette

/** Hosts the center route from SearchWorkspaceState; it does not own a second route bridge. */
@Composable
internal fun ImmersiveCenterContentPanel(
    appState: ViriViriAppState = ViriViriApplication.appState,
    palette: CinemaPalette = CinemaPalette.DARK,
    onVideoSelected: () -> Unit = {},
    onDismissWorkbench: () -> Unit = {},
) {
  RecommendationPanel(
      appState = appState,
      palette = palette,
      showViewerContent = false,
      onVideoSelected = onVideoSelected,
      onDismissWorkbench = onDismissWorkbench,
  )
}
