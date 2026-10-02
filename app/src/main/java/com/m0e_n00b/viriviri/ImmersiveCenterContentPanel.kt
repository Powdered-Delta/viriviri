package com.m0e_n00b.viriviri

import androidx.compose.runtime.Composable
import com.m0e_n00b.spatialworkbench.core.CinemaPalette

/**
 * Hosts the center route from SearchWorkspaceState; it does not own a second route bridge.
 *
 * Dismissal ownership: this panel does NOT dismiss the Workbench. `WorkbenchOuterDismiss`
 * (see `attachOuterDismissInput`) is the single owner of the outside-area dismiss, so a click
 * on blank center-panel space reaches the center content instead of hiding the Workbench.
 */
@Composable
internal fun ImmersiveCenterContentPanel(
    appState: ViriViriAppState = ViriViriApplication.appState,
    palette: CinemaPalette = CinemaPalette.DARK,
    onVideoSelected: () -> Unit = {},
) {
  RecommendationPanel(
      appState = appState,
      palette = palette,
      showViewerContent = false,
      onVideoSelected = onVideoSelected,
  )
}
